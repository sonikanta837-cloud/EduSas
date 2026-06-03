package com.emp.management.service;

import com.emp.management.dto.ResumeParseResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ResumeService {

    // ── Section-heading keyword lists ─────────────────────────────────────────

    private static final List<String> SKILL_HEADERS = List.of(
            "technical skills", "key skills", "core competencies", "areas of expertise",
            "technical expertise", "tools & technologies", "competencies",
            "skills", "technologies", "expertise", "proficiencies"
    );
    private static final List<String> EXP_HEADERS = List.of(
            "work experience", "professional experience", "employment history",
            "career history", "work history", "professional background",
            "experience", "employment", "career summary"
    );
    private static final List<String> SUMMARY_HEADERS = List.of(
            "professional summary", "career objective", "executive summary",
            "career profile", "professional profile", "about me",
            "summary", "objective", "profile", "overview", "about"
    );
    private static final List<String> STOP_HEADERS = List.of(
            "education", "academic", "qualifications", "certifications",
            "projects", "awards", "achievements", "references",
            "interests", "hobbies", "declaration", "personal information",
            "personal details", "languages known", "extracurricular"
    );

    // ── Job-title keywords for header-area position detection ─────────────────
    private static final List<String> JOB_TITLE_KEYWORDS = List.of(
            "engineer", "developer", "analyst", "manager", "consultant", "designer",
            "architect", "administrator", "specialist", "coordinator", "executive",
            "officer", "associate", "intern", "trainee", "lead", "head", "director",
            "scientist", "researcher", "accountant", "auditor", "advisor", "technician",
            "programmer", "tester", "devops", "full stack", "frontend", "backend",
            "software", "product owner", "scrum master", "data scientist", "qa "
    );

    // ── Compiled regex patterns ───────────────────────────────────────────────

    private static final Pattern EMAIL_PAT = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE_PAT = Pattern.compile(
            "(?:\\+?\\d{1,3}[\\s\\-.])?(?:\\(?\\d{2,4}\\)?[\\s\\-.]){1,3}\\d{3,4}");

    // Short lines that are likely section headings
    private static final Pattern HEADING_PAT = Pattern.compile(
            "^[A-Z][A-Za-z\\s&/]{1,35}:?\\s*$");

    private static final Pattern TOTAL_EXP_LABEL = Pattern.compile(
            "(?i)(?:total|overall)\\s+experience\\s*[:–\\-]\\s*([^\\n]+)");

    private static final Pattern EXP_YEARS_PAT = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*years?(?:\\s+of)?(?:\\s+(?:total\\s+)?experience)?");

    private static final Pattern POSITION_LABEL_PAT = Pattern.compile(
            "(?i)(?:current\\s+)?(?:designation|position|title|job\\s+title|role|current\\s+role)\\s*[:–\\-]\\s*([^\\n]+)");

    private static final Pattern CURRENT_EXP_LABEL = Pattern.compile(
            "(?i)(?:current\\s+experience|experience\\s+at\\s+current\\s+company)\\s*[:–\\-]\\s*([^\\n]+)");

    private static final Pattern PRESENT_DURATION_PAT = Pattern.compile(
            "(?i)(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+\\d{4}\\s*[-–—]\\s*(?:present|current|till\\s+date|now|ongoing)");

    private static final Pattern LOCATION_LABEL_PAT = Pattern.compile(
            "(?i)(?:location|city|current\\s+location|current\\s+city|based\\s+in|residing\\s+at)\\s*[:–\\-]\\s*([^\\n,]+)");

    private static final Pattern CITY_STATE_PAT = Pattern.compile(
            "^([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)\\s*,\\s*([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)$");

    private static final Pattern ADDRESS_LABEL_PAT = Pattern.compile(
            "(?i)(?:address|residential\\s+address|current\\s+address|present\\s+address)\\s*[:–\\-]\\s*([^\\n]+)");

    private static final Pattern DOB_PAT = Pattern.compile(
            "(?i)(?:dob|date\\s+of\\s+birth|d\\.o\\.b\\.?)\\s*[:–\\-]\\s*" +
            "(\\d{1,2}[/\\-.\\s]\\d{1,2}[/\\-.\\s]\\d{2,4}" +
            "|\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}" +
            "|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[.,\\s]+\\d{1,2}[,\\s]+\\d{4}" +
            "|\\d{1,2}[\\s]+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\\s,]+\\d{4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GENDER_PAT = Pattern.compile(
            "(?i)(?:gender|sex)\\s*[:–\\-]\\s*(male|female|other|m\\b|f\\b)");

    private static final Pattern MARITAL_STATUS_PAT = Pattern.compile(
            "(?i)(?:marital\\s+status|marital)\\s*[:–\\-]\\s*(single|married|divorced|widowed|unmarried|separated)",
            Pattern.CASE_INSENSITIVE);

    // Ordered from most-specific to least-specific so the first match wins
    private static final List<DateTimeFormatter> DOB_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy",   Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy",  Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy",  Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("M/d/yyyy")
    );

    // ── Public entry point ────────────────────────────────────────────────────

    public ResumeParseResponse parseResume(MultipartFile file) throws IOException {
        String text = extractText(file);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Could not extract text from the uploaded file");
        }
        return parse(text);
    }

    // ── Text extraction ───────────────────────────────────────────────────────

    private String extractText(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        if (name.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                return new PDFTextStripper().getText(doc);
            }
        }
        if (name.endsWith(".docx")) {
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream());
                 XWPFWordExtractor ext = new XWPFWordExtractor(doc)) {
                return ext.getText();
            }
        }
        throw new IllegalArgumentException(
                "Unsupported file type. Please upload a PDF or DOCX file.");
    }

    // ── Main orchestrator ─────────────────────────────────────────────────────

    private ResumeParseResponse parse(String raw) {
        String[] lines = raw.lines()
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .toArray(String[]::new);

        // Contact
        List<String> allEmails = extractAllEmails(raw);
        String email         = allEmails.isEmpty() ? "" : allEmails.get(0);
        String personalEmail = allEmails.size() > 1 ? allEmails.get(1) : "";
        String phone         = extractPhone(raw);
        String[] name        = extractName(lines, email, phone);

        // Professional
        String position          = safe(extractPosition(raw, lines, name[0], email, phone));
        String totalExperience   = safe(extractTotalExperience(raw));
        String currentExperience = safe(extractCurrentExperience(raw));
        String skills            = safe(extractSection(raw, SKILL_HEADERS));
        String workHistory       = safe(extractSection(raw, EXP_HEADERS));
        String summary           = safe(extractSection(raw, SUMMARY_HEADERS));

        // Personal
        String dateOfBirth   = normalizeDateToIso(safe(extractDob(raw)));
        String gender        = normaliseGender(safe(extractGender(raw)));
        String maritalStatus = normaliseMaritalStatus(safe(extractMaritalStatus(raw)));

        // Location / address
        String seatingLocation = safe(extractLocation(raw, lines, email, phone, name[0]));
        String presentAddress  = safe(extractAddress(raw));

        // experience field: prefer work history; fall back to summary
        String experience = workHistory.isBlank() ? summary : workHistory;

        // ── Build parsed / review metadata ───────────────────────────────────
        List<String> parsedFields   = new ArrayList<>();
        List<String> reviewFields   = new ArrayList<>();
        List<String> reviewMessages = new ArrayList<>();

        if (!name[0].isBlank())           parsedFields.add("firstName");
        if (!name[1].isBlank())           parsedFields.add("lastName");
        if (!email.isBlank())             parsedFields.add("email");
        if (!phone.isBlank())             parsedFields.add("phone");
        if (!personalEmail.isBlank())     parsedFields.add("personalEmail");
        if (!position.isBlank())          parsedFields.add("position");
        if (!totalExperience.isBlank())   parsedFields.add("totalExperience");
        if (!currentExperience.isBlank()) parsedFields.add("currentExperience");
        if (!skills.isBlank())            parsedFields.add("skills");
        if (!experience.isBlank())        parsedFields.add("experience");
        if (!summary.isBlank())           parsedFields.add("summary");
        if (!seatingLocation.isBlank())   parsedFields.add("seatingLocation");
        if (!presentAddress.isBlank())    parsedFields.add("presentAddress");
        if (!dateOfBirth.isBlank())       parsedFields.add("dateOfBirth");
        if (!gender.isBlank())            parsedFields.add("gender");
        if (!maritalStatus.isBlank())     parsedFields.add("maritalStatus");

        // Amber flags for important missing fields
        if (name[0].isBlank()) {
            reviewFields.add("firstName");
            reviewFields.add("lastName");
            reviewMessages.add("Name not detected — please enter first and last name manually");
        }
        if (email.isBlank()) {
            reviewFields.add("email");
            reviewMessages.add("Work email not found — add manually in Basic Info tab");
        }
        if (phone.isBlank()) {
            reviewFields.add("phone");
            reviewMessages.add("Phone number not detected — add manually in Basic Info tab");
        }
        if (position.isBlank()) {
            reviewFields.add("position");
            reviewMessages.add("Job title / designation not found — select from Employment tab");
        }
        if (skills.isBlank()) {
            reviewFields.add("skills");
            reviewMessages.add("Skills section not detected — add manually in Work Details tab");
        }
        if (experience.isBlank()) {
            reviewMessages.add("Work history not clearly parsed — add experience summary in Work Details tab");
        }

        int score = computeConfidence(name[0], email, phone, position, skills,
                experience, totalExperience, summary, seatingLocation, presentAddress);

        return ResumeParseResponse.builder()
                .firstName(name[0])
                .lastName(name[1])
                .email(email)
                .phone(phone)
                .personalEmail(personalEmail)
                .position(position)
                .totalExperience(totalExperience)
                .currentExperience(currentExperience)
                .skills(skills)
                .experience(experience)
                .summary(summary)
                .seatingLocation(seatingLocation)
                .presentAddress(presentAddress)
                .dateOfBirth(dateOfBirth)
                .gender(gender)
                .maritalStatus(maritalStatus)
                .confidenceScore(score)
                .parsedFields(parsedFields)
                .reviewFields(reviewFields)
                .reviewMessages(reviewMessages)
                .build();
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private List<String> extractAllEmails(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = EMAIL_PAT.matcher(text);
        while (m.find()) {
            String e = m.group().trim().toLowerCase();
            if (!found.contains(e)) found.add(e);
        }
        return found;
    }

    // ── Phone ─────────────────────────────────────────────────────────────────

    private String extractPhone(String text) {
        Matcher m = PHONE_PAT.matcher(text);
        while (m.find()) {
            String candidate = m.group().trim();
            long digits = candidate.chars().filter(Character::isDigit).count();
            if (digits >= 7) return candidate;
        }
        return "";
    }

    // ── Name ──────────────────────────────────────────────────────────────────

    private String[] extractName(String[] lines, String email, String phone) {
        for (int i = 0; i < Math.min(lines.length, 10); i++) {
            String line = lines[i];
            if (line.contains("@")) continue;
            if (!phone.isEmpty() && line.contains(phone)) continue;
            if (line.length() > 60) continue;
            if (line.chars().anyMatch(Character::isDigit)) continue;
            if (line.contains(":") || line.contains("–") || line.contains("-")) continue;

            String[] words = line.split("\\s+");
            if (words.length < 2 || words.length > 4) continue;
            boolean allTitleCase = true;
            for (String w : words) {
                if (w.isEmpty() || !Character.isUpperCase(w.charAt(0))) {
                    allTitleCase = false;
                    break;
                }
            }
            if (!allTitleCase) continue;

            String firstName = capitalize(words[0]);
            String lastName  = capitalize(
                    String.join(" ", Arrays.copyOfRange(words, 1, words.length)));
            return new String[]{ firstName, lastName };
        }
        return new String[]{ "", "" };
    }

    // ── Position / Designation ────────────────────────────────────────────────

    private String extractPosition(String raw, String[] lines,
                                   String firstName, String email, String phone) {
        // 1. Labeled field — most reliable
        Matcher m = POSITION_LABEL_PAT.matcher(raw);
        if (m.find()) return firstLine(m.group(1));

        // 2. Header-area scan: first 15 lines, look for job-title keywords
        int scanned = 0;
        for (String line : lines) {
            if (scanned++ > 15) break;
            if (line.contains("@")) continue;
            if (!phone.isEmpty() && line.contains(phone)) continue;
            if (!firstName.isEmpty() && line.toLowerCase().contains(firstName.toLowerCase())) continue;
            if (line.length() > 90 || line.length() < 5) continue;
            if (line.chars().filter(Character::isDigit).count() > 3) continue;

            String lower = line.toLowerCase();
            for (String kw : JOB_TITLE_KEYWORDS) {
                if (lower.contains(kw)) return line.trim();
            }
        }
        return "";
    }

    // ── Total experience ──────────────────────────────────────────────────────

    private String extractTotalExperience(String raw) {
        // 1. Explicit label: "Total Experience: 5 years"
        Matcher m = TOTAL_EXP_LABEL.matcher(raw);
        if (m.find()) return firstLine(m.group(1));

        // 2. Pattern: "5 years of experience" / "5+ years"
        Matcher m2 = EXP_YEARS_PAT.matcher(raw);
        if (m2.find()) {
            int end = Math.min(m2.end() + 15, raw.length());
            boolean plus = raw.substring(m2.start(), end).contains("+");
            return m2.group(1).trim() + (plus ? "+" : "") + " years";
        }
        return "";
    }

    // ── Current experience ────────────────────────────────────────────────────

    private String extractCurrentExperience(String raw) {
        // 1. Explicit label
        Matcher m = CURRENT_EXP_LABEL.matcher(raw);
        if (m.find()) return firstLine(m.group(1));

        // 2. "Month Year – Present" pattern implies still in this role
        Matcher dm = PRESENT_DURATION_PAT.matcher(raw);
        if (dm.find()) {
            int start = Math.max(0, dm.start() - 5);
            return raw.substring(start, dm.end()).trim().replaceAll("\\s+", " ");
        }
        return "";
    }

    // ── Generic section extractor (skills / summary / work history) ───────────

    private String extractSection(String raw, List<String> headers) {
        String lower = raw.toLowerCase();
        for (String header : headers) {
            int idx = lower.indexOf(header);
            if (idx == -1) continue;
            // Require whole-word match — no letter immediately before the keyword
            if (idx > 0 && Character.isLetterOrDigit(lower.charAt(idx - 1))) continue;

            int start = raw.indexOf('\n', idx);
            if (start == -1) continue;
            start++;

            String[] remaining = raw.substring(start).split("\n");
            List<String> collected = new ArrayList<>();
            for (String line : remaining) {
                String trimmed = line.trim().replaceFirst("^[•\\-–*>]+\\s*", "");
                if (trimmed.isBlank()) continue;
                if (isStopHeader(trimmed)) break;
                if (HEADING_PAT.matcher(trimmed).matches() && collected.size() > 1) break;
                if (trimmed.length() < 4) continue;
                collected.add(trimmed);
                if (collected.size() >= 12) break;
            }
            if (!collected.isEmpty()) return String.join(" | ", collected);
        }
        return "";
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private String extractLocation(String raw, String[] lines,
                                   String email, String phone, String firstName) {
        // 1. Explicit label
        Matcher m = LOCATION_LABEL_PAT.matcher(raw);
        if (m.find()) return firstLine(m.group(1));

        // 2. "City, State" pattern in header area
        int scanned = 0;
        for (String line : lines) {
            if (scanned++ > 20) break;
            if (line.contains("@")) continue;
            if (!phone.isEmpty() && line.contains(phone)) continue;
            if (!firstName.isEmpty() && line.toLowerCase().contains(firstName.toLowerCase())) continue;
            Matcher cm = CITY_STATE_PAT.matcher(line.trim());
            if (cm.matches()) return line.trim();
        }
        return "";
    }

    // ── Address ───────────────────────────────────────────────────────────────

    private String extractAddress(String raw) {
        Matcher m = ADDRESS_LABEL_PAT.matcher(raw);
        return m.find() ? firstLine(m.group(1)) : "";
    }

    // ── Date of birth ─────────────────────────────────────────────────────────

    private String extractDob(String raw) {
        Matcher m = DOB_PAT.matcher(raw);
        return m.find() ? m.group(1).trim() : "";
    }

    /** Normalizes a raw DOB string to YYYY-MM-DD for the HTML date input.
     *  Returns the original string unchanged if no known format matches. */
    private String normalizeDateToIso(String raw) {
        if (raw.isBlank()) return "";
        // Already ISO?
        try { LocalDate.parse(raw); return raw; } catch (Exception ignored) {}
        for (DateTimeFormatter fmt : DOB_FORMATTERS) {
            try {
                LocalDate d = LocalDate.parse(raw, fmt);
                if (d.getYear() >= 1920 && d.getYear() <= 2010) return d.toString();
            } catch (Exception ignored) {}
        }
        return raw; // return raw if no format matched
    }

    // ── Gender ────────────────────────────────────────────────────────────────

    private String extractGender(String raw) {
        Matcher m = GENDER_PAT.matcher(raw);
        return m.find() ? m.group(1).trim() : "";
    }

    private String normaliseGender(String raw) {
        if (raw.isBlank()) return "";
        String lc = raw.toLowerCase();
        if (lc.equals("m") || lc.startsWith("male"))   return "Male";
        if (lc.equals("f") || lc.startsWith("female")) return "Female";
        return "Other";
    }

    // ── Marital status ────────────────────────────────────────────────────────

    private String extractMaritalStatus(String raw) {
        Matcher m = MARITAL_STATUS_PAT.matcher(raw);
        return m.find() ? m.group(1).trim() : "";
    }

    private String normaliseMaritalStatus(String raw) {
        if (raw.isBlank()) return "";
        String lc = raw.toLowerCase();
        if (lc.equals("single") || lc.equals("unmarried")) return "Single";
        if (lc.equals("married"))                           return "Married";
        if (lc.equals("divorced") || lc.equals("separated")) return "Divorced";
        if (lc.equals("widowed"))                           return "Widowed";
        return raw.trim();
    }

    // ── Confidence score (0–100) ──────────────────────────────────────────────

    private int computeConfidence(String firstName, String email, String phone,
                                   String position, String skills, String experience,
                                   String totalExperience, String summary,
                                   String location, String address) {
        int score = 0;
        if (!firstName.isBlank())       score += 15;
        if (!email.isBlank())           score += 15;
        if (!phone.isBlank())           score += 10;
        if (!position.isBlank())        score += 10;
        if (!skills.isBlank())          score += 15;
        if (!experience.isBlank())      score += 15;
        if (!totalExperience.isBlank()) score += 5;
        if (!summary.isBlank())         score += 5;
        if (!location.isBlank())        score += 5;
        if (!address.isBlank())         score += 5;
        return Math.min(score, 100);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isStopHeader(String line) {
        String low = line.toLowerCase().replaceAll("[:\\-]$", "").trim();
        return STOP_HEADERS.stream().anyMatch(h -> h.equalsIgnoreCase(low));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String safe(String s) {
        return s == null ? "" : s.strip();
    }

    private String firstLine(String s) {
        if (s == null) return "";
        return s.strip().split("\\n")[0].strip();
    }
}
