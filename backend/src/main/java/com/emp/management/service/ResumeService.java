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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ResumeService {

    // ── Section heading keywords ──────────────────────────────────────────────
    private static final List<String> SKILL_HEADERS = List.of(
            "skills", "technical skills", "core competencies", "technologies",
            "expertise", "proficiencies", "tools", "languages"
    );
    private static final List<String> EXP_HEADERS = List.of(
            "experience", "work experience", "employment history",
            "professional experience", "career history", "work history"
    );
    private static final List<String> STOP_HEADERS = List.of(
            "education", "qualifications", "certifications", "projects",
            "awards", "achievements", "references", "summary", "objective",
            "profile", "interests", "hobbies", "languages"
    );

    // ── Patterns ──────────────────────────────────────────────────────────────
    private static final Pattern EMAIL_PAT = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE_PAT = Pattern.compile(
            "(?:\\+?\\d{1,3}[\\s\\-.])?(?:\\(?\\d{2,4}\\)?[\\s\\-.]){1,3}\\d{3,4}");

    // Lines that look like section headings (short, possibly ALL-CAPS or followed by colon)
    private static final Pattern HEADING_PAT = Pattern.compile(
            "^[A-Z][A-Za-z\\s&/]{1,35}:?\\s*$");

    public ResumeParseResponse parseResume(MultipartFile file) throws IOException {
        String text = extractText(file);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Could not extract text from the uploaded file");
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

    // ── Main parser ───────────────────────────────────────────────────────────

    private ResumeParseResponse parse(String raw) {
        String[] lines = raw.lines()
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .toArray(String[]::new);

        String email      = extractEmail(raw);
        String phone      = extractPhone(raw);
        String[] nameParts = extractName(lines, email, phone);
        String skills     = extractSection(raw, SKILL_HEADERS);
        String experience = extractExperience(raw, lines);

        return ResumeParseResponse.builder()
                .firstName(nameParts[0])
                .lastName(nameParts[1])
                .email(email)
                .phone(phone)
                .skills(skills)
                .experience(experience)
                .build();
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private String extractEmail(String text) {
        Matcher m = EMAIL_PAT.matcher(text);
        return m.find() ? m.group().trim() : "";
    }

    // ── Phone ─────────────────────────────────────────────────────────────────

    private String extractPhone(String text) {
        Matcher m = PHONE_PAT.matcher(text);
        while (m.find()) {
            String candidate = m.group().trim();
            // Must contain at least 7 digits to be a real phone number
            long digits = candidate.chars().filter(Character::isDigit).count();
            if (digits >= 7) return candidate;
        }
        return "";
    }

    // ── Name ──────────────────────────────────────────────────────────────────
    // Strategy: scan the first ~10 meaningful lines; pick the first one that
    // looks like a personal name (no @, no digits, 2–4 words, title-cased or upper).

    private String[] extractName(String[] lines, String email, String phone) {
        for (int i = 0; i < Math.min(lines.length, 10); i++) {
            String line = lines[i];
            if (line.contains("@")) continue;                      // skip email lines
            if (line.contains(phone) && !phone.isEmpty()) continue; // skip phone lines
            if (line.length() > 60) continue;                      // too long for a name
            if (line.chars().anyMatch(Character::isDigit)) continue;// skip lines with digits

            // Must match a plausible name: 2–4 tokens, each starting with uppercase
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

            // Split into firstName (first word) and lastName (remaining)
            String firstName = words[0];
            String lastName  = String.join(" ", java.util.Arrays.copyOfRange(words, 1, words.length));
            return new String[]{ capitalize(firstName), capitalize(lastName) };
        }
        return new String[]{ "", "" };
    }

    // ── Skills / generic section extractor ───────────────────────────────────

    private String extractSection(String raw, List<String> headers) {
        String lower = raw.toLowerCase();
        for (String header : headers) {
            int idx = lower.indexOf(header);
            if (idx == -1) continue;

            // Move past the header line
            int start = raw.indexOf('\n', idx);
            if (start == -1) continue;
            start++;

            // Collect until we hit the next section heading or run out
            String[] remaining = raw.substring(start).split("\n");
            List<String> collected = new ArrayList<>();
            for (String line : remaining) {
                String trimmed = line.trim();
                if (trimmed.isBlank()) continue;
                if (isStopHeader(trimmed)) break;
                if (HEADING_PAT.matcher(trimmed).matches() && collected.size() > 1) break;
                collected.add(trimmed);
                if (collected.size() >= 15) break; // cap
            }
            if (!collected.isEmpty()) return String.join(", ", collected);
        }
        return "";
    }

    // ── Experience ────────────────────────────────────────────────────────────
    // Returns first 3 meaningful bullet/sentence lines under the experience section.

    private String extractExperience(String raw, String[] lines) {
        String lower = raw.toLowerCase();
        for (String header : EXP_HEADERS) {
            int idx = lower.indexOf(header);
            if (idx == -1) continue;

            int start = raw.indexOf('\n', idx);
            if (start == -1) continue;
            start++;

            String[] remaining = raw.substring(start).split("\n");
            List<String> collected = new ArrayList<>();
            for (String line : remaining) {
                String trimmed = line.trim()
                        .replaceFirst("^[•\\-–*]+\\s*", ""); // strip bullets
                if (trimmed.isBlank()) continue;
                if (isStopHeader(trimmed)) break;
                if (HEADING_PAT.matcher(trimmed).matches() && collected.size() > 1) break;
                if (trimmed.length() < 8) continue; // skip noise
                collected.add(trimmed);
                if (collected.size() >= 4) break;
            }
            if (!collected.isEmpty()) return String.join(". ", collected);
        }
        return "";
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
}
