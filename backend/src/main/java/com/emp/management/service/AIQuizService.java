package com.emp.management.service;

import com.emp.management.entity.Course;
import com.emp.management.entity.Exam;
import com.emp.management.repository.CourseRepository;
import com.emp.management.repository.ExamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Fully free, zero-API-key quiz generator.
 *
 * Transcript source priority:
 *   1. Fetch signed caption URL embedded in the YouTube video page (most reliable)
 *   2. YouTube public timedtext endpoint fallback
 *   3. Course Content / Description text
 *   4. Keyword fallback from title
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIQuizService {

    private final CourseRepository courseRepository;
    private final ExamRepository examRepository;
    private final ObjectMapper objectMapper;

    private static final int TARGET = 5;

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
        "(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([^&?/\\s]{11})");

    private static final Set<String> STOP_WORDS = Set.of(
        "the","a","an","and","or","but","is","are","was","were","be","been",
        "being","have","has","had","do","does","did","will","would","could",
        "should","may","might","can","to","of","in","for","on","with","at",
        "by","from","this","that","these","those","it","its","they","them",
        "their","we","our","you","your","he","she","his","her","as","not",
        "no","so","if","then","when","which","who","what","how","where","why",
        "there","here","also","such","each","more","some","any","than","into",
        "about","after","between","through","during","just","very","only",
        "over","most","both","used","using","going","get","got","let","like",
        "know","make","take","come","give","think","look","want","need","right",
        "well","said","okay","yeah","gonna","wanna","thing"
    );

    private static final Pattern DEF_PATTERN = Pattern.compile(
        "([A-Z]?[\\w][\\w\\s]{1,35})\\s+" +
        "(?:is|are|means|refers to|is defined as|is known as)\\s+" +
        "([a-zA-Z][^.!?]{10,90})[.!?]",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern PURPOSE_PATTERN = Pattern.compile(
        "([a-zA-Z][\\w\\s]{3,30})\\s+" +
        "(?:is used|are used|can be used|is designed|helps|allows|enables)\\s+" +
        "(?:to |for )?([^.!?]{10,80})[.!?]",
        Pattern.CASE_INSENSITIVE);

    // ─── Public entry point ───────────────────────────────────────────────────

    /** Returns generated questions without saving — for admin preview before saving an exam. */
    public List<Map<String, Object>> generateQuestionsOnly(Long courseId) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) return List.of();
        return generateQuestions(course);
    }

    @Async
    public void generateAndSaveQuiz(Long courseId) {
        try {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) return;
            if (examRepository.findByCourseId(courseId).isPresent()) {
                log.info("Exam already exists for course {} — skipping", courseId);
                return;
            }

            List<Map<String, Object>> questions = generateQuestions(course);
            if (questions.isEmpty()) {
                log.warn("No questions generated for course {}", courseId);
                return;
            }

            Exam exam = Exam.builder().course(course).build();
            exam.setQuestions(objectMapper.writeValueAsString(questions));
            exam.setPassingScore(70);
            examRepository.save(exam);
            log.info("Auto-generated {} questions for course '{}'", questions.size(), course.getTitle());
        } catch (Exception e) {
            log.error("Quiz generation failed for course {}: {}", courseId, e.getMessage());
        }
    }

    // ─── Main pipeline ────────────────────────────────────────────────────────

    private List<Map<String, Object>> generateQuestions(Course course) {
        String text = buildText(course);
        List<Map<String, Object>> qs = new ArrayList<>();

        if (!text.isBlank()) {
            List<String> sentences = extractSentences(text);
            List<String> keyTerms  = extractKeyTerms(text);

            addDefinitionQuestions(text, sentences, qs);
            addFillBlankQuestions(sentences, keyTerms, qs);
            addPurposeQuestions(text, sentences, qs);
        }

        addFallbackQuestions(course, text, qs);
        return qs.stream().limit(TARGET).collect(Collectors.toList());
    }

    // ─── Text building ────────────────────────────────────────────────────────

    private String buildText(Course course) {
        StringBuilder sb = new StringBuilder();

        if (notBlank(course.getYoutubeUrl())) {
            String videoId = extractVideoId(course.getYoutubeUrl());
            if (videoId != null) {
                String transcript = fetchTranscript(videoId);
                if (!transcript.isBlank()) {
                    log.info("Using YouTube transcript ({} chars) for course quiz", transcript.length());
                    sb.append(transcript).append(" ");
                } else {
                    log.info("No transcript found for video — using course text content");
                }
            }
        }

        if (notBlank(course.getContent()))     sb.append(course.getContent()).append(" ");
        if (notBlank(course.getDescription())) sb.append(course.getDescription()).append(" ");

        return sb.toString().trim();
    }

    // ─── Transcript fetching ──────────────────────────────────────────────────

    private String fetchTranscript(String videoId) {
        RestTemplate rest = buildRestTemplate(6000);

        // Method 1: parse the YouTube page and extract the embedded signed caption URL
        String transcript = fetchViaPageScraping(rest, videoId);
        if (!transcript.isBlank()) return transcript;

        // Method 2: direct public timedtext endpoint (older / unlisted videos)
        transcript = fetchViaDirectEndpoint(rest, videoId);
        return transcript;
    }

    /**
     * Fetches the YouTube watch page and extracts the captionTrack URL that
     * YouTube embeds inside ytInitialPlayerResponse.  Those URLs are already
     * signed so they work without an API key.
     */
    private String fetchViaPageScraping(RestTemplate rest, String videoId) {
        try {
            String pageUrl = "https://www.youtube.com/watch?v=" + videoId;

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
            headers.set(HttpHeaders.ACCEPT,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            ResponseEntity<String> resp = rest.exchange(
                pageUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String html = resp.getBody();
            if (html == null || html.isBlank()) return "";

            String captionUrl = extractCaptionUrlFromPage(html);
            if (captionUrl == null) {
                log.debug("No caption track found in YouTube page for videoId={}", videoId);
                return "";
            }

            log.debug("Fetching caption from: {}", captionUrl.substring(0, Math.min(80, captionUrl.length())));
            String xml = rest.getForObject(captionUrl, String.class);
            return xml != null ? parseXmlTranscript(xml) : "";

        } catch (Exception e) {
            log.debug("Page scraping failed for videoId={}: {}", videoId, e.getMessage());
            return "";
        }
    }

    /**
     * Extracts the first English (or any) caption baseUrl from the YouTube page HTML.
     * YouTube embeds caption URLs inside ytInitialPlayerResponse as:
     *   "baseUrl":"https://www.youtube.com/api/timedtext?..."
     * They contain & escapes that must be decoded.
     */
    private String extractCaptionUrlFromPage(String html) {
        // Locate the captionTracks block so we only look inside it
        int captionIdx = html.indexOf("\"captionTracks\"");
        if (captionIdx == -1) return null;

        // Work with a slice of the HTML to avoid picking up unrelated URLs
        String slice = html.substring(captionIdx, Math.min(captionIdx + 8000, html.length()));

        Pattern urlPat = Pattern.compile(
            "\"baseUrl\":\"(https://www\\.youtube\\.com/api/timedtext[^\"]+)\"");
        Matcher m = urlPat.matcher(slice);

        String englishUrl = null;
        String anyUrl     = null;

        while (m.find()) {
            // Decode unicode escapes (& → &) and slash escapes (\/ → /)
            String url = m.group(1)
                .replace("\\u0026", "&")
                .replace("\\/", "/");

            if (url.contains("lang=en") && englishUrl == null) {
                englishUrl = url;
            }
            if (anyUrl == null) {
                anyUrl = url;
            }
        }
        return englishUrl != null ? englishUrl : anyUrl;
    }

    /** Fallback: try YouTube's public timedtext endpoint directly (no page fetch). */
    private String fetchViaDirectEndpoint(RestTemplate rest, String videoId) {
        String[] candidates = {"en", "en-US", "en-GB", "a.en"};
        for (String lang : candidates) {
            try {
                String url  = "https://www.youtube.com/api/timedtext?v=" + videoId + "&lang=" + lang;
                String body = rest.getForObject(url, String.class);
                if (body != null && !body.isBlank()) {
                    String text = parseXmlTranscript(body);
                    if (!text.isBlank()) return text;
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    /** Parses YouTube timedtext XML:  {@code <text start="1.0" dur="2.5">Hello world</text>} */
    private String parseXmlTranscript(String xml) {
        Pattern textPat = Pattern.compile("<text[^>]*>([^<]*)</text>");
        Matcher m = textPat.matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String seg = m.group(1)
                .replace("&#39;", "'")
                .replace("&amp;",  "&")
                .replace("&lt;",   "<")
                .replace("&gt;",   ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replaceAll("\\s+", " ")
                .trim();
            if (!seg.isBlank()) sb.append(seg).append(" ");
        }
        return sb.toString().trim();
    }

    private String extractVideoId(String url) {
        if (url == null) return null;
        Matcher m = VIDEO_ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        return new RestTemplate(f);
    }

    // ─── Strategy 1: Definition questions  (What is X?) ─────────────────────

    private void addDefinitionQuestions(String text, List<String> sentences,
                                         List<Map<String, Object>> qs) {
        Matcher m = DEF_PATTERN.matcher(text);
        Set<String> seen = new HashSet<>();
        while (m.find() && qs.size() < TARGET) {
            String subject = m.group(1).trim();
            String def     = capitalize(m.group(2).trim());
            String key     = subject.toLowerCase();
            if (subject.split("\\s+").length > 4 || seen.contains(key)) continue;
            seen.add(key);
            def = truncate(def, 85);

            List<String> distractors = pickDistractors(sentences, def, 3);
            if (distractors.size() < 3) continue;
            qs.add(buildMCQ("What is " + subject + "?", def, distractors));
        }
    }

    // ─── Strategy 2: Fill-in-the-blank ───────────────────────────────────────

    private void addFillBlankQuestions(List<String> sentences, List<String> keyTerms,
                                        List<Map<String, Object>> qs) {
        Set<String> usedTerms = new HashSet<>();
        for (String sentence : sentences) {
            if (qs.size() >= TARGET) break;
            for (String term : keyTerms) {
                if (usedTerms.contains(term)) continue;
                if (!sentence.toLowerCase().contains(term.toLowerCase())) continue;

                String blanked = sentence.replaceFirst(
                    "(?i)\\b" + Pattern.quote(term) + "\\b", "_____");
                List<String> distractors = keyTerms.stream()
                    .filter(t -> !t.equalsIgnoreCase(term))
                    .limit(3)
                    .collect(Collectors.toList());
                if (distractors.size() < 3) continue;

                qs.add(buildMCQ("Fill in the blank: " + blanked, term, distractors));
                usedTerms.add(term);
                break;
            }
        }
    }

    // ─── Strategy 3: Purpose questions  (What does X do?) ───────────────────

    private void addPurposeQuestions(String text, List<String> sentences,
                                      List<Map<String, Object>> qs) {
        Matcher m = PURPOSE_PATTERN.matcher(text);
        Set<String> seen = new HashSet<>();
        while (m.find() && qs.size() < TARGET) {
            String subject = m.group(1).trim();
            String purpose = capitalize(m.group(2).trim());
            String key     = subject.toLowerCase();
            if (seen.contains(key)) continue;
            seen.add(key);
            purpose = truncate(purpose, 85);

            List<String> distractors = pickDistractors(sentences, purpose, 3);
            if (distractors.size() < 3) continue;
            qs.add(buildMCQ("What does " + subject + " do?", purpose, distractors));
        }
    }

    // ─── Fallback: keyword / topic questions (always generates ≥1 question) ──

    private void addFallbackQuestions(Course course, String text,
                                       List<Map<String, Object>> qs) {
        if (qs.size() >= TARGET) return;
        String title      = course.getTitle();
        String sourceText = notBlank(text) ? text : title;
        List<String> terms = extractKeyTerms(sourceText);

        if (qs.size() < TARGET && terms.size() >= 4) {
            qs.add(buildMCQ(
                "Which of the following is a key topic covered in \"" + title + "\"?",
                capitalize(terms.get(0)),
                List.of(capitalize(terms.get(1)), capitalize(terms.get(2)), capitalize(terms.get(3)))));
        }

        if (qs.size() < TARGET) {
            String correct = notBlank(course.getDescription())
                ? truncate(course.getDescription(), 90)
                : "A course on " + title;
            qs.add(buildMCQ(
                "Which of the following best describes \"" + title + "\"?",
                correct,
                List.of(
                    "An introduction to financial accounting principles",
                    "A comprehensive guide to supply chain management",
                    "An overview of software architecture patterns")));
        }

        if (qs.size() < TARGET && !terms.isEmpty()) {
            qs.add(buildMCQ(
                "The primary subject of the course \"" + title + "\" is:",
                capitalize(terms.get(0)),
                List.of("Financial auditing", "Contract law", "Operations research")));
        }

        if (qs.size() < TARGET && terms.size() >= 2) {
            qs.add(buildMCQ(
                "Which term is most closely associated with \"" + title + "\"?",
                capitalize(terms.get(0)),
                terms.size() >= 4
                    ? List.of(capitalize(terms.get(1)), capitalize(terms.get(2)), capitalize(terms.get(3)))
                    : List.of("Depreciation", "Arbitration", "Procurement")));
        }

        if (qs.size() < TARGET) {
            qs.add(buildMCQ(
                "After completing \"" + title + "\", an employee will have knowledge of:",
                !terms.isEmpty() ? capitalize(terms.get(0)) : title,
                List.of("Corporate tax filing procedures",
                    "Import/export regulations",
                    "Payroll processing rules")));
        }
    }

    // ─── Text utilities ───────────────────────────────────────────────────────

    private List<String> extractSentences(String text) {
        return Arrays.stream(text.split("(?<=[.!?])\\s+"))
            .map(String::trim)
            .filter(s -> s.length() >= 25 && s.length() <= 250
                && s.split("\\s+").length >= 5)
            .collect(Collectors.toList());
    }

    private List<String> extractKeyTerms(String text) {
        return Arrays.stream(text.split("[\\s,.:;!?()\"/\\-]+"))
            .map(w -> w.replaceAll("[^a-zA-Z]", "").toLowerCase())
            .filter(w -> w.length() >= 5 && !STOP_WORDS.contains(w))
            .collect(Collectors.groupingBy(w -> w, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .limit(15)
            .collect(Collectors.toList());
    }

    // ─── MCQ builder ─────────────────────────────────────────────────────────

    private Map<String, Object> buildMCQ(String question, String correct,
                                          List<String> distractors) {
        List<String> options = new ArrayList<>();
        options.add(correct);
        distractors.stream().limit(3).forEach(options::add);
        Collections.shuffle(options);
        int correctIdx = options.indexOf(correct);

        Map<String, Object> q = new LinkedHashMap<>();
        q.put("question", question);
        q.put("options", options);
        q.put("correctAnswer", correctIdx);
        return q;
    }

    private List<String> pickDistractors(List<String> sentences, String correct, int count) {
        String prefix = correct.toLowerCase().substring(0, Math.min(12, correct.length()));
        return sentences.stream()
            .filter(s -> !s.toLowerCase().contains(prefix))
            .map(s -> truncate(s, 90))
            .distinct()
            .limit(count)
            .collect(Collectors.toList());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
