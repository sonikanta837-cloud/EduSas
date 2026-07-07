package com.emp.management.service;

import com.emp.management.dto.*;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoInterviewService {

    private final VideoInterviewRepository       interviewRepo;
    private final VideoInterviewQuestionRepository viqRepo;
    private final VideoInterviewAnswerRepository  answerRepo;
    private final VideoInterviewViolationRepository violationRepo;
    private final InterviewQuestionRepository    questionRepo;
    private final EmailService                   emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadDir;

    private static final int MAX_VIOLATIONS    = 3;
    private static final int TOKEN_LENGTH      = 12;
    private static final int EXPIRY_DAYS       = 7;
    private static final String TOKEN_CHARS    = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // ── Public candidate-facing methods ───────────────────────────────────────

    public CandidateInterviewDTO validateToken(String token) {
        VideoInterview vi = findByToken(token);
        checkNotExpired(vi);

        if (vi.getStatus() == VideoInterviewStatus.COMPLETED ||
            vi.getStatus() == VideoInterviewStatus.AUTO_SUBMITTED) {
            throw new BadRequestException("This interview has already been submitted.");
        }

        return CandidateInterviewDTO.builder()
                .candidateName(vi.getCandidateName())
                .position(vi.getPosition())
                .technology(vi.getTechnology())
                .durationMinutes(vi.getDurationMinutes())
                .numQuestions(vi.getNumQuestions())
                .status(vi.getStatus())
                .startedAt(vi.getStartedAt())
                .build();
    }

    @Transactional
    public CandidateInterviewDTO startInterview(String token, String ipAddress) {
        VideoInterview vi = findByToken(token);
        checkNotExpired(vi);

        if (vi.getStatus() == VideoInterviewStatus.COMPLETED ||
            vi.getStatus() == VideoInterviewStatus.AUTO_SUBMITTED) {
            throw new BadRequestException("Interview already submitted.");
        }

        if (vi.getStatus() == VideoInterviewStatus.IN_PROGRESS) {
            // Resume existing session — return already-assigned questions + saved answers
            return buildCandidateDTO(vi, true);
        }

        // First start
        vi.setStatus(VideoInterviewStatus.IN_PROGRESS);
        vi.setStartedAt(LocalDateTime.now());
        vi.setIpAddress(ipAddress);

        List<InterviewQuestion> pool = selectQuestions(vi.getTechnology(), vi.getDifficulty(), vi.getNumQuestions());
        if (pool.isEmpty())
            throw new BadRequestException("No active questions found for technology: " + vi.getTechnology());

        Collections.shuffle(pool);

        for (int i = 0; i < pool.size(); i++) {
            InterviewQuestion q = pool.get(i);
            String optionOrder = null;
            if (q.getQuestionType() == QuestionType.MCQ) {
                optionOrder = shuffleOptions(q);
            }
            viqRepo.save(VideoInterviewQuestion.builder()
                    .interview(vi)
                    .question(q)
                    .questionOrder(i + 1)
                    .optionOrder(optionOrder)
                    .build());
        }

        interviewRepo.save(vi);
        return buildCandidateDTO(vi, false);
    }

    @Transactional
    public void saveAnswer(String token, SaveAnswerRequest req) {
        VideoInterview vi = findActiveInterview(token);
        if (req.getQuestionId() == null) return;

        VideoInterviewAnswer ans = answerRepo
                .findByInterviewAndQuestionId(vi, req.getQuestionId())
                .orElseGet(() -> VideoInterviewAnswer.builder()
                        .interview(vi)
                        .questionId(req.getQuestionId())
                        .build());

        if (req.getAnswerText() != null)   ans.setAnswerText(req.getAnswerText());
        if (req.getSelectedOption() != null) ans.setSelectedOption(req.getSelectedOption());
        if (req.getIsMarked() != null)     ans.setIsMarked(req.getIsMarked());
        ans.setSavedAt(LocalDateTime.now());
        answerRepo.save(ans);
    }

    @Transactional
    public Map<String, Object> logViolation(String token, ViolationRequest req, String ipAddress) {
        VideoInterview vi = findByToken(token);

        if (vi.getStatus() == VideoInterviewStatus.COMPLETED ||
            vi.getStatus() == VideoInterviewStatus.AUTO_SUBMITTED) {
            return Map.of("totalViolations", vi.getViolationCount(), "autoSubmitted", true);
        }

        int newCount = vi.getViolationCount() + 1;
        vi.setViolationCount(newCount);

        violationRepo.save(VideoInterviewViolation.builder()
                .interview(vi)
                .violationType(req.getViolationType())
                .browserEvent(req.getBrowserEvent())
                .description(req.getDescription())
                .ipAddress(ipAddress)
                .violationNumber(newCount)
                .occurredAt(LocalDateTime.now())
                .build());

        boolean autoSubmit = newCount >= MAX_VIOLATIONS;
        if (autoSubmit) {
            vi.setStatus(VideoInterviewStatus.AUTO_SUBMITTED);
            vi.setSubmittedAt(LocalDateTime.now());
        }
        interviewRepo.save(vi);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalViolations", newCount);
        result.put("autoSubmitted", autoSubmit);
        result.put("warningMessage", buildWarningMessage(newCount, autoSubmit));
        return result;
    }

    @Transactional
    public void submitInterview(String token) {
        VideoInterview vi = findByToken(token);
        if (vi.getStatus() == VideoInterviewStatus.COMPLETED ||
            vi.getStatus() == VideoInterviewStatus.AUTO_SUBMITTED) return;

        vi.setStatus(VideoInterviewStatus.COMPLETED);
        vi.setSubmittedAt(LocalDateTime.now());

        // Calculate score for MCQ questions
        List<VideoInterviewQuestion> assigned = viqRepo.findByInterviewOrderByQuestionOrder(vi);
        List<VideoInterviewAnswer>   answers  = answerRepo.findByInterview(vi);
        Map<Long, String> answerMap = answers.stream()
                .collect(Collectors.toMap(VideoInterviewAnswer::getQuestionId,
                                          a -> a.getSelectedOption() != null ? a.getSelectedOption() : "",
                                          (a, b) -> a));

        int score = 0, totalMarks = 0;
        for (VideoInterviewQuestion viq : assigned) {
            InterviewQuestion q = viq.getQuestion();
            totalMarks += q.getMarks();
            if (q.getQuestionType() == QuestionType.MCQ && q.getCorrectAnswer() != null) {
                String given = answerMap.getOrDefault(q.getId(), "");
                if (q.getCorrectAnswer().equalsIgnoreCase(given)) score += q.getMarks();
            }
        }
        vi.setScore(score);
        vi.setTotalMarks(totalMarks);
        interviewRepo.save(vi);
    }

    @Transactional
    public Map<String, String> uploadRecording(String token, MultipartFile file) throws IOException {
        VideoInterview vi = findByToken(token);
        Path dir = Paths.get(uploadDir, "recordings");
        Files.createDirectories(dir);
        String filename = token + ".webm";
        Files.write(dir.resolve(filename), file.getBytes());
        String url = "/uploads/recordings/" + filename;
        vi.setVideoUrl(url);
        interviewRepo.save(vi);
        return Map.of("videoUrl", url);
    }

    // ── HR / Manager facing methods ───────────────────────────────────────────

    public List<VideoInterviewDTO> getAll() {
        return interviewRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(v -> toDTO(v, false)).collect(Collectors.toList());
    }

    public VideoInterviewDTO getById(Long id) {
        VideoInterview vi = interviewRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
        return toDTO(vi, true);
    }

    @Transactional
    public VideoInterviewDTO create(CreateVideoInterviewRequest req, String createdBy) {
        if (req.getCandidateName() == null || req.getCandidateName().isBlank())
            throw new BadRequestException("Candidate name is required");
        if (req.getEmail() == null || req.getEmail().isBlank())
            throw new BadRequestException("Candidate email is required");
        if (req.getTechnology() == null || req.getTechnology().isBlank())
            throw new BadRequestException("Technology is required");

        int numQ    = req.getNumQuestions()    != null ? req.getNumQuestions()    : 25;
        int duration= req.getDurationMinutes() != null ? req.getDurationMinutes() : 45;
        InterviewDifficulty diff = req.getDifficulty() != null ? req.getDifficulty() : InterviewDifficulty.MIXED;

        // Validate question pool availability
        long available = questionRepo.countByTechnologyIgnoreCaseAndStatus(req.getTechnology(), QuestionStatus.ACTIVE);
        if (available == 0)
            throw new BadRequestException("No active questions found for technology: " + req.getTechnology()
                    + ". Add questions to the Question Bank first.");

        String token = generateToken();
        VideoInterview vi = VideoInterview.builder()
                .candidateName(req.getCandidateName().trim())
                .email(req.getEmail().trim().toLowerCase())
                .mobile(req.getMobile())
                .position(req.getPosition())
                .technology(req.getTechnology().trim())
                .experience(req.getExperience())
                .durationMinutes(duration)
                .numQuestions(numQ)
                .difficulty(diff)
                .token(token)
                .createdBy(createdBy)
                .expiresAt(LocalDateTime.now().plusDays(EXPIRY_DAYS))
                .build();

        vi = interviewRepo.save(vi);

        String link = baseUrl + "/interview/" + token;
        try {
            emailService.sendVideoInterviewInvitation(
                    vi.getEmail(), vi.getCandidateName(), vi.getPosition(),
                    vi.getTechnology(), vi.getDurationMinutes(), vi.getNumQuestions(), link,
                    vi.getExpiresAt());
        } catch (Exception e) {
            log.warn("Failed to send interview invitation email to {}: {}", vi.getEmail(), e.getMessage());
        }

        return toDTO(vi, false);
    }

    @Transactional
    public void delete(Long id) {
        VideoInterview vi = interviewRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
        answerRepo.deleteByInterview(vi);
        violationRepo.deleteByInterview(vi);
        viqRepo.deleteByInterview(vi);
        interviewRepo.delete(vi);
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total",       interviewRepo.count());
        stats.put("pending",     interviewRepo.countByStatus(VideoInterviewStatus.PENDING));
        stats.put("inProgress",  interviewRepo.countByStatus(VideoInterviewStatus.IN_PROGRESS));
        stats.put("completed",   interviewRepo.countByStatus(VideoInterviewStatus.COMPLETED));
        stats.put("autoSubmitted", interviewRepo.countByStatus(VideoInterviewStatus.AUTO_SUBMITTED));
        stats.put("expired",     interviewRepo.countByStatus(VideoInterviewStatus.EXPIRED));
        return stats;
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private VideoInterview findByToken(String token) {
        return interviewRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid interview link."));
    }

    private VideoInterview findActiveInterview(String token) {
        VideoInterview vi = findByToken(token);
        if (vi.getStatus() != VideoInterviewStatus.IN_PROGRESS)
            throw new BadRequestException("Interview is not active.");
        return vi;
    }

    private void checkNotExpired(VideoInterview vi) {
        if (vi.getStatus() == VideoInterviewStatus.EXPIRED)
            throw new BadRequestException("This interview link has expired.");
        if (vi.getExpiresAt() != null && vi.getExpiresAt().isBefore(LocalDateTime.now())) {
            vi.setStatus(VideoInterviewStatus.EXPIRED);
            interviewRepo.save(vi);
            throw new BadRequestException("This interview link has expired.");
        }
    }

    private List<InterviewQuestion> selectQuestions(String technology, InterviewDifficulty difficulty, int count) {
        if (difficulty == InterviewDifficulty.MIXED) {
            List<InterviewQuestion> all = questionRepo.findRandomByTechnology(technology, count);
            if (all.size() < count) {
                // If fewer questions exist than requested, return all
                log.info("Only {} questions available for technology={}, requested={}", all.size(), technology, count);
            }
            return all;
        }

        if (difficulty == InterviewDifficulty.EASY) {
            List<InterviewQuestion> easy = questionRepo.findRandomByTechnologyAndDifficulty(technology, "EASY", count);
            if (easy.size() < count) {
                easy = new ArrayList<>(easy);
                easy.addAll(questionRepo.findRandomByTechnology(technology, count - easy.size()));
            }
            return easy.stream().distinct().limit(count).collect(Collectors.toList());
        }

        if (difficulty == InterviewDifficulty.HARD) {
            List<InterviewQuestion> hard = questionRepo.findRandomByTechnologyAndDifficulty(technology, "HARD", count);
            if (hard.size() < count) {
                hard = new ArrayList<>(hard);
                hard.addAll(questionRepo.findRandomByTechnology(technology, count - hard.size()));
            }
            return hard.stream().distinct().limit(count).collect(Collectors.toList());
        }

        // MEDIUM
        List<InterviewQuestion> medium = questionRepo.findRandomByTechnologyAndDifficulty(technology, "MEDIUM", count);
        if (medium.size() < count) {
            medium = new ArrayList<>(medium);
            medium.addAll(questionRepo.findRandomByTechnology(technology, count - medium.size()));
        }
        return medium.stream().distinct().limit(count).collect(Collectors.toList());
    }

    private String shuffleOptions(InterviewQuestion q) {
        List<String> letters = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
        // Only include letters that have text
        letters.removeIf(l -> getOptionText(q, l).isBlank());
        Collections.shuffle(letters);
        return String.join(",", letters);
    }

    private String getOptionText(InterviewQuestion q, String letter) {
        return switch (letter) {
            case "A" -> q.getOptionA() != null ? q.getOptionA() : "";
            case "B" -> q.getOptionB() != null ? q.getOptionB() : "";
            case "C" -> q.getOptionC() != null ? q.getOptionC() : "";
            case "D" -> q.getOptionD() != null ? q.getOptionD() : "";
            default  -> "";
        };
    }

    private String generateToken() {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++)
            sb.append(TOKEN_CHARS.charAt(rng.nextInt(TOKEN_CHARS.length())));
        String token = sb.toString();
        // Ensure uniqueness (extremely rare collision, but guard it)
        if (interviewRepo.findByToken(token).isPresent()) return generateToken();
        return token;
    }

    private CandidateInterviewDTO buildCandidateDTO(VideoInterview vi, boolean includeAnswers) {
        List<VideoInterviewQuestion> viqs = viqRepo.findByInterviewOrderByQuestionOrder(vi);
        List<CandidateQuestionDTO> questions = viqs.stream()
                .map(viq -> buildCandidateQuestion(viq))
                .collect(Collectors.toList());

        List<SaveAnswerRequest> savedAnswers = null;
        if (includeAnswers) {
            savedAnswers = answerRepo.findByInterview(vi).stream()
                    .map(a -> SaveAnswerRequest.builder()
                            .questionId(a.getQuestionId())
                            .answerText(a.getAnswerText())
                            .selectedOption(a.getSelectedOption())
                            .isMarked(a.getIsMarked())
                            .build())
                    .collect(Collectors.toList());
        }

        return CandidateInterviewDTO.builder()
                .candidateName(vi.getCandidateName())
                .position(vi.getPosition())
                .technology(vi.getTechnology())
                .durationMinutes(vi.getDurationMinutes())
                .numQuestions(questions.size())
                .status(vi.getStatus())
                .startedAt(vi.getStartedAt())
                .questions(questions)
                .savedAnswers(savedAnswers)
                .build();
    }

    private CandidateQuestionDTO buildCandidateQuestion(VideoInterviewQuestion viq) {
        InterviewQuestion q = viq.getQuestion();
        List<CandidateQuestionDTO.McqOption> options = null;

        if (q.getQuestionType() == QuestionType.MCQ) {
            String order = viq.getOptionOrder();
            List<String> letters = order != null
                    ? Arrays.asList(order.split(","))
                    : Arrays.asList("A", "B", "C", "D");
            options = letters.stream()
                    .map(l -> new CandidateQuestionDTO.McqOption(l, getOptionText(q, l)))
                    .filter(opt -> !opt.getText().isBlank())
                    .collect(Collectors.toList());
        }

        return CandidateQuestionDTO.builder()
                .viqId(viq.getId())
                .questionId(q.getId())
                .questionNumber(viq.getQuestionOrder())
                .questionText(q.getQuestionText())
                .category(q.getCategory())
                .difficulty(q.getDifficulty())
                .questionType(q.getQuestionType())
                .marks(q.getMarks())
                .options(options)
                .build();
    }

    private VideoInterviewDTO toDTO(VideoInterview vi, boolean withDetails) {
        String link = baseUrl + "/interview/" + vi.getToken();
        VideoInterviewDTO.VideoInterviewDTOBuilder builder = VideoInterviewDTO.builder()
                .id(vi.getId())
                .candidateName(vi.getCandidateName())
                .email(vi.getEmail())
                .mobile(vi.getMobile())
                .position(vi.getPosition())
                .technology(vi.getTechnology())
                .experience(vi.getExperience())
                .durationMinutes(vi.getDurationMinutes())
                .numQuestions(vi.getNumQuestions())
                .difficulty(vi.getDifficulty())
                .token(vi.getToken())
                .interviewLink(link)
                .status(vi.getStatus())
                .createdBy(vi.getCreatedBy())
                .createdAt(vi.getCreatedAt())
                .startedAt(vi.getStartedAt())
                .submittedAt(vi.getSubmittedAt())
                .expiresAt(vi.getExpiresAt())
                .videoUrl(vi.getVideoUrl())
                .score(vi.getScore())
                .totalMarks(vi.getTotalMarks())
                .violationCount(vi.getViolationCount());

        if (withDetails) {
            List<VideoInterviewQuestion> viqs = viqRepo.findByInterviewOrderByQuestionOrder(vi);
            List<VideoInterviewAnswer>   answers = answerRepo.findByInterview(vi);
            Map<Long, VideoInterviewAnswer> ansMap = answers.stream()
                    .collect(Collectors.toMap(VideoInterviewAnswer::getQuestionId, a -> a, (a, b) -> a));

            List<VideoInterviewDTO.VideoAnswerDTO> answerDTOs = viqs.stream().map(viq -> {
                InterviewQuestion q = viq.getQuestion();
                VideoInterviewAnswer a = ansMap.get(q.getId());
                return VideoInterviewDTO.VideoAnswerDTO.builder()
                        .questionId(q.getId())
                        .questionText(q.getQuestionText())
                        .questionType(q.getQuestionType().name())
                        .answerText(a != null ? a.getAnswerText() : null)
                        .selectedOption(a != null ? a.getSelectedOption() : null)
                        .isMarked(a != null ? a.getIsMarked() : false)
                        .correctAnswer(q.getCorrectAnswer())
                        .marks(q.getMarks())
                        .build();
            }).collect(Collectors.toList());

            List<VideoInterviewDTO.VideoViolationDTO> violDTOs = violationRepo
                    .findByInterviewOrderByOccurredAtAsc(vi).stream()
                    .map(v -> VideoInterviewDTO.VideoViolationDTO.builder()
                            .id(v.getId())
                            .violationType(v.getViolationType())
                            .browserEvent(v.getBrowserEvent())
                            .description(v.getDescription())
                            .violationNumber(v.getViolationNumber())
                            .occurredAt(v.getOccurredAt())
                            .build())
                    .collect(Collectors.toList());

            builder.answers(answerDTOs).violations(violDTOs);
        }

        return builder.build();
    }

    private String buildWarningMessage(int count, boolean autoSubmit) {
        if (autoSubmit) return "Maximum violations reached. Your interview has been auto-submitted.";
        if (count == 2) return "Warning #2 of 3: One more violation will auto-submit your interview!";
        return "Warning #1 of 3: Violation detected. Please stay on this screen.";
    }
}
