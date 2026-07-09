package com.emp.management.service;

import com.emp.management.dto.*;
import com.emp.management.entity.*;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.*;

import java.time.LocalDateTime;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtsService {

    private final AtsCandidateRepository              candidateRepo;
    private final AtsHrScreeningRepository            hrRepo;
    private final AtsTechnicalInterviewRepository     techRepo;
    private final AtsFinalRoundRepository             finalRepo;
    private final EmployeeDetailsRepository           employeeRepo;
    private final AtsCvHistoryRepository              cvHistoryRepo;
    private final JavaMailSender                      mailSender;
    private final InterviewQuestionRepository         questionRepo;
    private final AtsTechInterviewQuestionRepository  techQRepo;
    private final AtsTechInterviewAnswerRepository    techARepo;
    private final AtsTechInterviewViolationRepository techVRepo;

    // In-memory store for WebRTC ICE candidates (ephemeral — cleared on restart)
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>> candidateIceMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>> managerIceMap   = new java.util.concurrent.ConcurrentHashMap<>();

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${app.claude.api-key:}")
    private String claudeApiKey;

    @Value("${app.claude.model:claude-haiku-4-5-20251001}")
    private String claudeModel;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    // Matches Indian mobile numbers with optional +91/91 country code prefix.
    // Allows optional spaces or dashes within the 10-digit number (e.g. "98765 43210").
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:(?:\\+91|91)[\\s\\-]?)?([6-9](?:[\\s\\-]?\\d){9})(?![\\s\\-]?\\d)");

    // Words that appear in CVs but are NOT person-name components.
    // Used by all name-extraction strategies to reject false matches.
    private static final Set<String> NAME_STOP_WORDS = new HashSet<>(Arrays.asList(
            // CV section headers and common filename prefixes/suffixes
            "cv", "resume", "profile", "summary", "objective", "experience", "education", "skills",
            "contact", "address", "phone", "email", "linkedin", "github",
            "work", "career", "background", "achievement", "project", "reference",
            "declaration", "information", "detail", "about", "curriculum", "vitae",
            "updated", "latest", "new", "final", "format", "template", "sample",
            "my", "the", "your", "our",
            // Designation role nouns (the most common false-name false positives)
            "expert", "professional", "specialist", "developer", "engineer", "operator",
            "manager", "analyst", "consultant", "designer", "architect", "programmer",
            "intern", "trainee", "director", "officer", "executive", "associate",
            "accountant", "accounts", "account", "keeper", "bookkeeper", "cashier",
            "clerk", "assistant", "coordinator", "supervisor", "administrator",
            "auditor", "recruiter", "strategist", "helper", "worker", "staff",
            "payroll", "cashbook", "taxation", "finance", "banking",
            // Common English nouns used as the FIRST word of a job title (e.g. "Book Keeper")
            "book", "cash", "store", "stock", "supply", "office", "field",
            // Seniority prefixes / suffixes that appear after the real name
            "senior", "junior", "lead", "head", "chief", "deputy", "general",
            "principal", "assistant", "vice", "president",
            // Tech/tools
            "passion", "computer", "tally", "microsoft", "data", "photo", "python",
            "java", "html", "excel", "freelance", "opportunities", "collaborations",
            // Experience / time words — never part of a person's name
            "year", "years", "month", "months", "experience", "experienced",
            "fresher", "graduate", "postgraduate", "undergraduate", "student", "candidate",
            // Job-description nouns that appear in CV headers but are NOT names
            "service", "services", "support", "technical", "customer", "industry",
            "operations", "operation", "business", "solutions", "solution", "systems",
            "system", "process", "processes", "department", "division", "sector",
            // Filler / common words
            "please", "feel", "free", "volunteer", "language", "english", "hindi",
            "certificate", "diploma", "bachelor", "master", "current", "working",
            "government", "college", "university", "school", "institute", "national",
            "commerce", "science", "arts", "technology", "management",
            // Qualification levels / certification terms
            "finalist", "qualified", "certified", "chartered", "licensed", "registered",
            "applying", "seeking", "looking", "available", "open",
            // Certification abbreviations
            "acca", "cpa", "cfa", "mba", "bca", "mca", "bsc", "msc", "bcom", "mcom",
            "bba", "llb", "llm", "phd", "pgdm", "icai", "icsi", "icwa",
            // More section headings not already covered above/by substring matching
            "qualification", "qualifications", "educational", "personal", "details",
            "particulars", "declare", "hereby", "sincerely", "signature", "annexure",
            "undersigned", "enclosed", "achievements", "hobbies", "interests",
            "extracurricular", "strengths", "responsibilities", "overview"
    ));

    // Real given/family-name words are almost never this long. PDF text extraction
    // sometimes glues two adjacent section-heading words together with no space in
    // between (e.g. "Career Objective" -> "Careerobjective"), which otherwise slips
    // past an exact NAME_STOP_WORDS match. Only tokens at least this long are checked
    // for an embedded stop word, so short legitimate names are never rejected.
    private static final int GLUED_HEADING_MIN_LENGTH = 10;

    /** True if {@code word} is CV boilerplate/section-heading text and therefore can
     *  never be part of a candidate's real name — see {@link #GLUED_HEADING_MIN_LENGTH}. */
    private boolean isNameStopToken(String word) {
        if (word == null || word.isEmpty()) return false;
        String w = word.toLowerCase();
        if (NAME_STOP_WORDS.contains(w)) return true;
        if (w.length() >= GLUED_HEADING_MIN_LENGTH) {
            for (String stop : NAME_STOP_WORDS) {
                if (stop.length() >= 5 && w.contains(stop)) return true;
            }
        }
        return false;
    }

    private static final Pattern LINKEDIN_PATTERN =
            Pattern.compile("linkedin\\.com/in/[a-zA-Z0-9_%\\-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_PATTERN =
            Pattern.compile("github\\.com/[a-zA-Z0-9_\\-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXP_YEARS_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*(?:year|yr)s?[\\s\\w]{0,20}?experience",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern DEGREE_PATTERN =
            Pattern.compile("(?:B\\.?\\s*Tech|B\\.?\\s*E\\.?|B\\.?\\s*Sc|B\\.?\\s*C\\.?A|B\\.?\\s*Com|"
                          + "M\\.?\\s*Tech|M\\.?\\s*E\\.?|M\\.?\\s*Sc|M\\.?\\s*C\\.?A|M\\.?\\s*B\\.?A|"
                          + "MBA|MCA|BCA|BSc|MSc|BE|ME|BBA|Ph\\.?D|Bachelor|Master|Diploma|B\\.?S|M\\.?S|"
                          + "PGDM|ACCA|CPA|CMA|CFA|ICAI|ICSI|Chartered|LLB|LLM|"
                          + "B\\.?\\s*Arch|B\\.?\\s*Ed|M\\.?\\s*Ed|B\\.?\\s*Pharm|M\\.?\\s*Pharm|"
                          + "BHM|MHM|B\\.?\\s*Des|M\\.?\\s*Des|BFA|MFA)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern DESIGNATION_PATTERN =
            Pattern.compile("(?:(?:Senior|Junior|Lead|Principal|Staff|Sr\\.?|Jr\\.?|Associate|"
                          + "Assistant|Chief|Head|VP|Vice\\s+President|Deputy)\\s+)?"
                          + "(?:Software|Full[\\s\\-]?Stack|Backend|Front[\\s\\-]?End|Cloud|Data|"
                          + "DevOps|ML|AI|Mobile|iOS|Android|QA|Test|Product|Project|Program|"
                          + "Business|Solutions?|Finance|Account|HR|Human\\s+Resources?|"
                          + "Marketing|Digital|Content|SEO|SEM|Social\\s*Media|Graphic|Creative|"
                          + "Sales|Operations?|Supply\\s+Chain|Field|Network|Tele)?"
                          + "\\s*(?:Software\\s+)?(?:Engineer|Developer|Architect|Manager|Analyst|"
                          + "Consultant|Designer|Scientist|Specialist|Director|Lead|Intern|Trainee|"
                          + "Executive|Officer|Administrator|Coordinator|Supervisor|Accountant|"
                          + "Recruiter|Auditor|Strategist|Marketer|Writer|Operator|Teacher|"
                          + "Lecturer|Professor|Technician|Journalist|Blogger|Freelancer|Clerk|"
                          + "Representative|Associate|Advisor|Planner|Developer|Agent|Assistant)",
                    Pattern.CASE_INSENSITIVE);
    // High-confidence address labels — specific enough that they can appear anywhere in the document
    // (including personal-details sections at the end) without ambiguity.
    private static final Pattern ADDRESS_LABEL_PATTERN =
            Pattern.compile("(?:Address|Residence|Residing\\s+at|Current\\s+Address|"
                          + "Permanent\\s+Address|Residential\\s+Address|Local\\s+Address|"
                          + "Present\\s+Address|Home\\s+Address|Correspondence\\s+Address|"
                          + "Native\\s+Place|Domicile|Postal\\s+Address|Mailing\\s+Address|"
                          + "Hometown|Home\\s+Town|Native\\s+Address|Contact\\s+Address|"
                          + "Communication\\s+Address|Temporary\\s+Address|Village\\s+Address)"
                          + "\\s*[:\\-]\\s*(.{3,200})",
                    Pattern.CASE_INSENSITIVE);

    // Ambiguous labels that can also appear inside work-experience blocks (guarded separately)
    private static final Pattern ADDRESS_LOCATION_LABEL_PATTERN =
            Pattern.compile("(?:Location|Current\\s+Location|City|Place|Native)\\s*[:\\-]\\s*(.{3,200})",
                    Pattern.CASE_INSENSITIVE);

    // "Personal Information / Details" section headers common in Indian CVs
    private static final Pattern PERSONAL_SECTION_HEADER =
            Pattern.compile("^(?:personal\\s+(?:details?|information|info|profile|data|"
                          + "particulars?|background)|personal(?:\\s+and\\s+contact)?\\s+info(?:rmation)?|"
                          + "contact\\s+(?:details?|information|info)|about\\s+me|"
                          + "biodata|bio(?:\\s*data)?)\\s*[:\\-]?$",
                    Pattern.CASE_INSENSITIVE);

    // Words that appear as standalone footer lines in declaration sections — never an address value.
    // These appear when "Address:" label has no inline value and the next line is a footer label.
    private static final Pattern DECLARATION_FOOTER_PATTERN =
            Pattern.compile("^(?:signature|date|place|witness|thumb\\s*impression|"
                          + "seal|stamp|photo|photograph|declaration|countersign\\w*|"
                          + "authoris[ei]d\\s+signatory|name\\s+&\\s+designation|"
                          + "above\\s+declaration|verified|self\\s+attested?)\\s*[:\\-]?$",
                    Pattern.CASE_INSENSITIVE);

    // Major section headers that terminate a personal-details section scan
    private static final Pattern MAJOR_SECTION_HEADER =
            Pattern.compile("^(?:education(?:al)?|academic|qualifications?|experience|"
                          + "work\\s+(?:experience|history)|employment|professional\\s+experience|"
                          + "internship|projects?|technical\\s+skills?|skills?|certif\\w*|"
                          + "achievements?|references?|declarations?|objectives?|summary|"
                          + "profile|publications?|awards?|training|strengths?|hobbies?|"
                          + "interests?|activities|languages?)\\s*[:\\-]?$",
                    Pattern.CASE_INSENSITIVE);
    // Location pin / home icons commonly used in modern CV templates.
    // PDFBox extracts icon-font glyphs as their Unicode code points. Two sources:
    //
    // A) Standard Unicode symbols / emoji:
    //    📍 U+1F4CD  🏠 U+1F3E0  ⌖ U+2316  ⊙ U+2299  ⦿ U+29BF
    //    ● U+25CF   ◆ U+25C6   ➤ U+27A4  ✔ U+2714  • U+2022
    //    ★ U+2605   ☆ U+2606   ✦ U+2756  ✉ U+2709  ☏ U+260F
    //
    // B) Private-Use-Area (PUA) characters emitted by icon fonts (Font Awesome, etc.):
    //    Font Awesome map-marker: U+F041
    //    Font Awesome home:       U+F015
    //    Font Awesome map-pin:    U+F276
    //    Material Icons place:    U+E0C8
    //    Common PUA range:        U+E000–U+F8FF
    //    Wingdings/Webdings range emitted by PDF fonts: U+F000–U+F0FF
    //
    // The regex below matches any line that STARTS with one such character.
    private static final Pattern LOCATION_ICON_LINE_PATTERN = Pattern.compile(
            "^(?:"
          // Standard emoji & symbols
          + "[\\u2022\\u2023\\u25AA\\u25AB\\u25B6\\u25C6\\u25CF\\u25E6"
          + "\\u2316\\u2299\\u29BF\\u27A4\\u2714\\u2756\\u2739\\u2766"
          + "\\u2605\\u2606\\u260F\\u2709\\u279C\\u279F\\u27B3\\u27B8\\u2794"
          + "\\u00A9\\u00AE]"
          // Emoji (surrogate pairs as char sequences)
          + "|\\uD83D\\uDCCD"   // 📍 U+1F4CD map pin
          + "|\\uD83C\\uDFE0"   // 🏠 U+1F3E0 house
          + "|\\uD83D\\uDCCC"   // 📌 U+1F4CC pushpin
          // Private Use Area — Font Awesome, Material Icons, Wingdings, custom CV icon fonts
          + "|[\\uE000-\\uF8FF]"
          + ")\\s*(.*)",
            Pattern.DOTALL);
    // Indian states and union territories for address extraction
    private static final Pattern STATE_PATTERN = Pattern.compile(
            "\\b(?:India|Maharashtra|Karnataka|Tamil\\s*Nadu|Kerala|Rajasthan|Gujarat|" +
            "Uttar\\s*Pradesh|Madhya\\s*Pradesh|Punjab|Haryana|Bihar|West\\s*Bengal|" +
            "Andhra\\s*Pradesh|Telangana|Odisha|Jharkhand|Uttarakhand|Himachal|Goa|" +
            "Delhi|NCR|Chhattisgarh|Assam|Tripura|Meghalaya|Manipur|Arunachal|" +
            "Nagaland|Mizoram|Sikkim|Jammu|Kashmir|Ladakh|Chandigarh|Puducherry|Pondicherry|" +
            // Dot-separated standard abbreviations (period optional to catch "M.P" without trailing dot)
            "M\\.P\\.?|U\\.P\\.?|T\\.N\\.?|A\\.P\\.?|H\\.P\\.?|" +
            // Short-form abbreviations common in Indian CVs: Raj./Raj, M.H./MH, K.A./KA, T.S./TS, W.B./WB
            "Raj\\.?|M\\.H\\.?|K\\.A\\.?|T\\.S\\.?|W\\.B\\.?|" +
            // No-dot two-letter abbreviations (only when standalone — \b prevents matching mid-word)
            "MP|UP|TN|AP|HP|MH|KA|TS|WB)\\b", Pattern.CASE_INSENSITIVE);
    // Indian cities — tier-1, tier-2, and common tier-3 towns appearing in CVs
    private static final Pattern CITY_PATTERN = Pattern.compile(
            "\\b(?:Mumbai|Delhi|Bangalore|Bengaluru|Hyderabad|Chennai|Kolkata|Ahmedabad|Pune|" +
            "Surat|Jaipur|Lucknow|Kanpur|Nagpur|Indore|Thane|Bhopal|Visakhapatnam|Vizag|" +
            "Patna|Vadodara|Ghaziabad|Ludhiana|Agra|Nashik|Faridabad|Meerut|Rajkot|" +
            "Aurangabad|Ranchi|Coimbatore|Jabalpur|Gwalior|Vijayawada|Jodhpur|Madurai|" +
            "Raipur|Kota|Guwahati|Solapur|Mysore|Mysuru|Kochi|Cochin|Bhubaneswar|" +
            "Dehradun|Shimla|Mandsaur|Ujjain|Noida|Gurugram|Gurgaon|Prayagraj|Varanasi|" +
            "Mangalore|Mangaluru|Hubli|Belagavi|Navi\\s*Mumbai|Srinagar|Amritsar|" +
            "Jodhpur|Udaipur|Ajmer|Bikaner|Kochi|Thrissur|Kozhikode|Calicut|" +
            "Palakkad|Thiruvananthapuram|Trivandrum|Kollam|Alappuzha|Kottayam|" +
            "Malappuram|Kannur|Kasaragod|Ernakulam|" +
            // Madhya Pradesh tier-3 towns
            "Jawad|Neemuch|Ratlam|Dewas|Sagar|Satna|Rewa|Katni|Chhindwara|Betul|" +
            "Hoshangabad|Harda|Khandwa|Khargone|Barwani|Dhar|Jhabua|Alirajpur|Mandla|" +
            "Seoni|Balaghat|Narsinghpur|Raisen|Vidisha|Sehore|Shajapur|Agar|Rajgarh|" +
            "Shivpuri|Guna|Ashoknagar|Datia|Bhind|Morena|Sheopur|Tikamgarh|Chhatarpur|" +
            "Panna|Damoh|Umaria|Shahdol|Anuppur|Dindori|Mandleshwar|Mhow|Pithampur|" +
            // Rajasthan tier-3 towns
            "Bhilwara|Sikar|Sri\\s*Ganganagar|Hanumangarh|Churu|Jhunjhunu|Alwar|" +
            "Bharatpur|Dholpur|Karauli|Sawai\\s*Madhopur|Tonk|Bundi|Baran|Jhalawar|" +
            "Chittorgarh|Rajsamand|Dungarpur|Banswara|Pratapgarh|Pali|Sirohi|Jalore|" +
            "Barmer|Jaisalmer|Nagaur|Didwana|Nimbaheda|Neem\\s*ka\\s*Thana|Sujangarh|" +
            // Uttar Pradesh tier-3
            "Mathura|Aligarh|Bareilly|Moradabad|Saharanpur|Gorakhpur|Firozabad|" +
            "Jhansi|Muzaffarnagar|Hapur|Shamli|Bulandshahr|Etawah|Mainpuri|" +
            // Maharashtra tier-3
            "Latur|Osmanabad|Nanded|Parbhani|Hingoli|Washim|Yavatmal|Amravati|" +
            "Akola|Buldhana|Jalgaon|Dhule|Nandurbar|Sindhudurg|Ratnagiri|" +
            // Gujarat tier-3
            "Anand|Nadiad|Gandhinagar|Mehsana|Patan|Banaskantha|Sabarkantha|" +
            "Bharuch|Surat|Valsad|Navsari|Tapi|Narmada|Dahod|Panchmahal|" +
            // Karnataka tier-3
            "Davangere|Shimoga|Shivamogga|Tumkur|Raichur|Bellary|Ballari|" +
            "Bidar|Vijayapura|Bagalkot|Gadag|Haveri|Dharwad|Uttara\\s*Kannada|" +
            // General
            "Panipat|Rohtak|Hisar|Karnal|Sonipat|Yamunanagar|Ambala|Kurukshetra|" +
            "Patiala|Bathinda|Pathankot|Hoshiarpur|Gurdaspur|Sangrur|" +
            "Guntur|Nellore|Tirupati|Warangal|Karimnagar|Dhanbad|Bokaro|Jamshedpur)\\b",
            Pattern.CASE_INSENSITIVE);
    // Education qualification entry lines — these often contain city/state/school names that
    // look like address content but are NOT home addresses.
    // Covers both school-level and degree-level entries.
    private static final Pattern EDUCATION_CONTENT_PATTERN = Pattern.compile(
            "(?i)^(?:" +
            // School / board level
            "higher\\s+secondary|secondary\\s+(?:school|education|certificate)|" +
            "h\\.?s\\.?c\\.?|matriculation|matric|" +
            "intermediate(?:\\s+(?:level|class|pass))?|" +
            "(?:10|12)th(?:\\s+(?:pass|std|class|grade))?|class\\s+(?:x{1,3}|10|12)|" +
            "ssc|hsc|cbse|icse|igcse|nios|" +
            // Degree / diploma level (full words)
            "bachelor(?:'?s)?|master(?:'?s)?|" +
            "diploma(?:\\s+in)?|post\\s*graduate|pgdm|" +
            "doctorate|ph\\.?d\\.?" +
            ")\\b.*"
    );

    // Institution name keywords — a segment containing these (without address keywords)
    // is a school/college name, not a location component of a home address.
    private static final Pattern INSTITUTION_PATTERN = Pattern.compile(
            "(?i)\\b(?:university|universities|college|collegiate|institute|institution|" +
            "academy|school|vidyapeeth|vidyalaya|mahavidyalaya|polytechnic)\\b"
    );

    // Canonical state names keyed by abbreviation or alternate form (all lowercase).
    private static final Map<String, String> STATE_NORMALIZE_MAP = new java.util.HashMap<>();
    static {
        STATE_NORMALIZE_MAP.put("m.p.", "Madhya Pradesh");
        STATE_NORMALIZE_MAP.put("m.p",  "Madhya Pradesh");
        STATE_NORMALIZE_MAP.put("mp",   "Madhya Pradesh");
        STATE_NORMALIZE_MAP.put("u.p.", "Uttar Pradesh");
        STATE_NORMALIZE_MAP.put("u.p",  "Uttar Pradesh");
        STATE_NORMALIZE_MAP.put("up",   "Uttar Pradesh");
        STATE_NORMALIZE_MAP.put("t.n.", "Tamil Nadu");
        STATE_NORMALIZE_MAP.put("t.n",  "Tamil Nadu");
        STATE_NORMALIZE_MAP.put("tn",   "Tamil Nadu");
        STATE_NORMALIZE_MAP.put("a.p.", "Andhra Pradesh");
        STATE_NORMALIZE_MAP.put("a.p",  "Andhra Pradesh");
        STATE_NORMALIZE_MAP.put("ap",   "Andhra Pradesh");
        STATE_NORMALIZE_MAP.put("h.p.", "Himachal Pradesh");
        STATE_NORMALIZE_MAP.put("h.p",  "Himachal Pradesh");
        STATE_NORMALIZE_MAP.put("hp",   "Himachal Pradesh");
        STATE_NORMALIZE_MAP.put("raj.", "Rajasthan");
        STATE_NORMALIZE_MAP.put("raj",  "Rajasthan");
        STATE_NORMALIZE_MAP.put("m.h.", "Maharashtra");
        STATE_NORMALIZE_MAP.put("m.h",  "Maharashtra");
        STATE_NORMALIZE_MAP.put("mh",   "Maharashtra");
        STATE_NORMALIZE_MAP.put("k.a.", "Karnataka");
        STATE_NORMALIZE_MAP.put("k.a",  "Karnataka");
        STATE_NORMALIZE_MAP.put("ka",   "Karnataka");
        STATE_NORMALIZE_MAP.put("t.s.", "Telangana");
        STATE_NORMALIZE_MAP.put("t.s",  "Telangana");
        STATE_NORMALIZE_MAP.put("ts",   "Telangana");
        STATE_NORMALIZE_MAP.put("w.b.", "West Bengal");
        STATE_NORMALIZE_MAP.put("w.b",  "West Bengal");
        STATE_NORMALIZE_MAP.put("wb",   "West Bengal");
        STATE_NORMALIZE_MAP.put("j&k",  "Jammu & Kashmir");
        STATE_NORMALIZE_MAP.put("ncr",  "Delhi NCR");
        STATE_NORMALIZE_MAP.put("pondicherry", "Puducherry");
    }

    // Words that are unambiguously part of a postal address in Indian CVs.
    // Used to distinguish real address lines from sentences that happen to mention a city/state.
    private static final Pattern ADDRESS_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(?:flat|house|h\\.?no\\.?|h/?no|plot|sector|sec\\.?|block|ward|" +
            "mohalla|gali|galli|lane|colony|nagar|nagara|vihar|enclave|society|soc\\.?|" +
            "apartment|apt\\.?|layout|extension|ext\\.?|phase|wing|building|bldg\\.?|" +
            "road|rd\\.?|street|st\\.?|marg|chowk|bazaar|bazar|near|opp\\.?|opposite|" +
            "behind|beside|next\\s+to|area|locality|village|vill\\.?|taluka|taluk|tehsil|" +
            "dist\\.?|district|main\\s+road|cross|junction|market|complex|tower|residency|" +
            "garden|park|naka|peth|wada|pada|pur|puram|ganj|gunj|quarters?)\\b");

    private static final Pattern DATE_RANGE_PATTERN =
            Pattern.compile("(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+)?"
                          + "(?:20|19)\\d{2}\\s*[-–—to]+\\s*"
                          + "(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+)?"
                          + "(?:(?:20|19)\\d{2}|[Pp]resent|[Cc]urrent|[Nn]ow|[Tt]ill date)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern EDUCATION_INSTITUTION_PATTERN =
            Pattern.compile("\\b(?:college|university|school|institute|vidyalay|sangathan|vidya|"
                          + "academy|coaching|polytechnic|convent|kendriya|navodaya|mahavidyalay|"
                          + "vidyapeeth|mahvidyalay|institution|pg\\s+college)\\b",
                    Pattern.CASE_INSENSITIVE);
    // Matches Indian CV experience entries like "1.2 Year - Company (Designation)"
    // Group 1 = company name, Group 2 = designation (optional)
    private static final Pattern DURATION_COMPANY_PATTERN = Pattern.compile(
            "(?:\\d{1,2}(?:\\.\\d+)?\\s*\\+?\\s*[Yy]ears?(?:\\s+and\\s+\\d+\\s*[Mm]onths?)?|\\d+\\s*[Mm]onths?)\\s*[-–]\\s*" +
            "([^(\\n\\r]{3,70})\\s*(?:\\(([^)\\n\\r]{3,60})\\))?",
            Pattern.CASE_INSENSITIVE);


    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    // ── CV Bank ───────────────────────────────────────────────────────────────

    public Map<String, String> parseResume(MultipartFile file) {
        try {
            String text = extractText(file);
            return deepParseText(text);
        } catch (Exception e) {
            log.warn("Resume parse failed: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /** Called by the browser-OCR flow: parses raw OCR text into structured fields. */
    public Map<String, String> parseTextFields(String text) {
        if (text == null || text.isBlank()) return new HashMap<>();
        return deepParseText(text);
    }

    /**
     * Step 1 — save the file immediately and return all parsed fields.
     * Called as soon as the user selects a file, before they fill the form.
     */
    public Map<String, Object> storeAndParseResume(MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        if (!Set.of(".pdf", ".doc", ".docx").contains(ext.toLowerCase())) {
            throw new RuntimeException("Only PDF, DOC, DOCX files are accepted");
        }
        try {
            String savedPath = saveResume(file);
            Map<String, String> parsed;
            String raw = "";

            if (ext.equalsIgnoreCase(".pdf")) {
                byte[] bytes = file.getBytes();
                try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                    org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    stripper.setSortByPosition(true);
                    String text = stripper.getText(doc);
                    log.info("PDFBox extracted {} chars from '{}'", text.trim().length(), file.getOriginalFilename());
                    raw = text.length() > 12000 ? text.substring(0, 12000) : text;

                    boolean isImagePdf = text.trim().length() < 100;
                    boolean claudeReady = claudeApiKey != null && !claudeApiKey.isBlank()
                                         && !claudeApiKey.equals("your-api-key-here");

                    if (claudeReady) {
                        // Use Claude for both image PDFs (no text) and complex layout PDFs
                        // Claude understands layout directly — more reliable than regex for any CV style
                        log.info("Using Claude Vision for structured field extraction");
                        parsed = extractStructuredWithClaude(doc);
                        // Claude returns a full address string but not the individual components.
                        // Run parseAddressComponents on it so city/state/PIN/etc. are available.
                        String claudeAddr = parsed.get("address");
                        if (claudeAddr != null && !claudeAddr.isBlank()) {
                            parseAddressComponents(claudeAddr).forEach((k, v) -> parsed.putIfAbsent(k, v));
                        }
                    } else if (isImagePdf) {
                        log.warn("Image-based PDF detected but Claude API key not configured — no fields extracted");
                        parsed = new HashMap<>();
                    } else {
                        parsed = deepParseText(text);
                    }
                }
            } else {
                // Read from the already-saved file on disk — avoids re-reading the
                // MultipartFile stream which may have been consumed by saveResume().
                Path savedFile = Paths.get(uploadDir).resolve(savedPath);
                log.warn(">>> DOCX extract START: {}", savedFile);
                String text = extractTextFromPath(savedFile, file.getOriginalFilename());
                log.warn(">>> DOCX extract DONE: {} chars\n{}", text.length(), text);
                raw = text.length() > 12000 ? text.substring(0, 12000) : text;
                parsed = deepParseText(text);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resumePath",         savedPath);
            result.put("resumeOriginalName", file.getOriginalFilename());
            result.put("rawResumeText",      raw);
            parsed.forEach(result::put);
            return result;
        } catch (IOException e) {
            log.error("Failed to store/parse CV '{}': {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("Failed to store CV: " + e.getMessage());
        }
    }

    /**
     * Step 2 — create the candidate record using the already-stored file path.
     * All parsed fields arrive from the client (reviewed/edited in the form).
     */
    @Transactional
    public AtsCandidateDTO uploadCandidate(
            String resumePath, String resumeOriginalName,
            String name, String email, String phone, String address,
            String addressStreet, String addressArea, String addressLandmark,
            String addressCity, String addressDistrict, String addressState,
            String addressPostalCode, String addressCountry,
            String appliedProfile, String officeLocation, String source,
            String skills, String totalExperienceYears,
            String linkedinUrl, String githubUrl,
            String educationSummary, String currentDesignation,
            String currentCompanyCv, String rawResumeText,
            String callerEmail) {

        EmployeeDetails creator = findEmployee(callerEmail).orElse(null);

        Long seq = candidateRepo.findMaxCandidateSequence();
        String candidateId = String.format("CAN-%05d", (seq == null ? 10000L : seq) + 1);

        AtsCandidate candidate = AtsCandidate.builder()
                .candidateId(candidateId)
                .name(name.trim())
                .email(blankToNull(email))
                .phone(blankToNull(phone))
                .address(blankToNull(address))
                .addressStreet(blankToNull(addressStreet))
                .addressArea(blankToNull(addressArea))
                .addressLandmark(blankToNull(addressLandmark))
                .addressCity(blankToNull(addressCity))
                .addressDistrict(blankToNull(addressDistrict))
                .addressState(blankToNull(addressState))
                .addressPostalCode(blankToNull(addressPostalCode))
                .addressCountry(blankToNull(addressCountry))
                .appliedProfile(appliedProfile.trim())
                .officeLocation(blankToNull(officeLocation))
                .source(blankToNull(source))
                .skills(blankToNull(skills))
                .totalExperienceYears(blankToNull(totalExperienceYears))
                .linkedinUrl(blankToNull(linkedinUrl))
                .githubUrl(blankToNull(githubUrl))
                .educationSummary(blankToNull(educationSummary))
                .currentDesignation(blankToNull(currentDesignation))
                .currentCompanyCv(blankToNull(currentCompanyCv))
                .rawResumeText(blankToNull(rawResumeText))
                .resumePath(blankToNull(resumePath))
                .resumeOriginalName(blankToNull(resumeOriginalName))
                .resumeUploadedAt(LocalDateTime.now())
                .status(AtsCandidateStatus.NEW)
                .createdBy(creator)
                .build();

        return toCandidateDTO(candidateRepo.save(candidate), false);
    }

    private String nvl(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary.trim() : fallback;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }

    @Transactional(readOnly = true)
    public List<AtsCandidateDTO> getAllCandidates() {
        return candidateRepo.findAllOrderByCvUploadDateDesc().stream()
                .map(c -> toCandidateDTO(c, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AtsCandidateDTO getCandidate(Long id) {
        return toCandidateDTO(findCandidate(id), true);
    }

    @Transactional
    public AtsCandidateDTO updateCandidate(Long id, AtsCandidateDTO dto) {
        AtsCandidate c = findCandidate(id);
        if (dto.getName()          != null) c.setName(dto.getName());
        if (dto.getEmail()         != null) c.setEmail(dto.getEmail());
        if (dto.getPhone()         != null) c.setPhone(dto.getPhone());
        if (dto.getAddress()       != null) c.setAddress(dto.getAddress());
        if (dto.getAddressStreet()    != null) c.setAddressStreet(dto.getAddressStreet());
        if (dto.getAddressArea()      != null) c.setAddressArea(dto.getAddressArea());
        if (dto.getAddressLandmark()  != null) c.setAddressLandmark(dto.getAddressLandmark());
        if (dto.getAddressCity()      != null) c.setAddressCity(dto.getAddressCity());
        if (dto.getAddressDistrict()  != null) c.setAddressDistrict(dto.getAddressDistrict());
        if (dto.getAddressState()     != null) c.setAddressState(dto.getAddressState());
        if (dto.getAddressPostalCode()!= null) c.setAddressPostalCode(dto.getAddressPostalCode());
        if (dto.getAddressCountry()   != null) c.setAddressCountry(dto.getAddressCountry());
        if (dto.getAppliedProfile()!= null) c.setAppliedProfile(dto.getAppliedProfile());
        if (dto.getOfficeLocation()!= null) c.setOfficeLocation(dto.getOfficeLocation());
        if (dto.getSource()        != null) c.setSource(dto.getSource());
        // Parsed fields are updatable
        if (dto.getSkills()              != null) c.setSkills(dto.getSkills());
        if (dto.getTotalExperienceYears()!= null) c.setTotalExperienceYears(dto.getTotalExperienceYears());
        if (dto.getCurrentDesignation()  != null) c.setCurrentDesignation(dto.getCurrentDesignation());
        if (dto.getCurrentCompanyCv()    != null) c.setCurrentCompanyCv(dto.getCurrentCompanyCv());
        if (dto.getEducationSummary()    != null) c.setEducationSummary(dto.getEducationSummary());
        if (dto.getLinkedinUrl()         != null) c.setLinkedinUrl(dto.getLinkedinUrl());
        if (dto.getGithubUrl()           != null) c.setGithubUrl(dto.getGithubUrl());
        return toCandidateDTO(candidateRepo.save(c), false);
    }

    @Transactional
    public void deleteCandidate(Long id) {
        candidateRepo.delete(findCandidate(id));
    }

    @Transactional
    public AtsCandidateDTO rejectFromCvBank(Long id, String callerEmail) {
        AtsCandidate c = findCandidate(id);
        c.setStatus(AtsCandidateStatus.HR_REJECTED);
        c = candidateRepo.save(c);
        sendRejectionEmail(c.getEmail(), c.getName(), c.getAppliedProfile());
        return toCandidateDTO(c, false);
    }

    @Transactional
    public AtsCandidateDTO openHrScreening(Long id, String callerEmail) {
        AtsCandidate c = findCandidate(id);
        c.setStatus(AtsCandidateStatus.UNDER_HR_REVIEW);
        c = candidateRepo.save(c);
        if (hrRepo.findByCandidateId(id).isEmpty()) {
            EmployeeDetails caller = findEmployee(callerEmail).orElse(null);
            hrRepo.save(AtsHrScreening.builder()
                    .candidate(c).decision("PENDING").conductedBy(caller).build());
        }
        return toCandidateDTO(c, true);
    }

    public ResponseEntity<Resource> downloadResume(Long id) {
        AtsCandidate c = findCandidate(id);
        if (c.getResumePath() == null) throw new ResourceNotFoundException("No resume uploaded for candidate " + id);
        Path path = Paths.get(uploadDir).resolve(c.getResumePath());
        if (!Files.exists(path)) throw new ResourceNotFoundException("Resume file not found");
        try {
            Resource resource = new UrlResource(path.toUri());
            String ct = determineContentType(c.getResumePath());
            String disp = "inline; filename=\"" + (c.getResumeOriginalName() != null ? c.getResumeOriginalName() : "resume.pdf") + "\"";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(ct))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disp)
                    .body(resource);
        } catch (Exception e) {
            throw new RuntimeException("Error serving resume", e);
        }
    }

    // ── HR Screening ──────────────────────────────────────────────────────────

    @Transactional
    public AtsHrScreeningDTO saveHrScreening(Long candidateId, AtsHrScreeningDTO dto, String callerEmail) {
        AtsCandidate c = findCandidate(candidateId);
        EmployeeDetails caller = findEmployee(callerEmail).orElse(null);

        AtsHrScreening s = hrRepo.findByCandidateId(candidateId)
                .orElseGet(() -> AtsHrScreening.builder().candidate(c).decision("PENDING").build());

        applyScreeningFields(s, dto, caller);
        return toHrScreeningDTO(hrRepo.save(s));
    }

    @Transactional
    public AtsCandidateDTO submitHrDecision(Long candidateId, AtsHrScreeningDTO dto, String callerEmail) {
        AtsCandidate c = findCandidate(candidateId);
        EmployeeDetails caller = findEmployee(callerEmail).orElse(null);

        if ("NOT_SUITABLE".equals(dto.getDecision())
                && (dto.getRejectionReason() == null || dto.getRejectionReason().isBlank())) {
            throw new com.emp.management.exception.BadRequestException(
                    "Rejection reason is mandatory when marking a candidate as Not Suitable");
        }

        AtsHrScreening s = hrRepo.findByCandidateId(candidateId)
                .orElseGet(() -> AtsHrScreening.builder().candidate(c).build());
        applyScreeningFields(s, dto, caller);
        s.setDecision(dto.getDecision());
        if ("NOT_SUITABLE".equals(dto.getDecision())) {
            s.setRejectionReason(dto.getRejectionReason().trim());
        }
        hrRepo.save(s);

        if ("NOT_SUITABLE".equals(dto.getDecision())) {
            c.setStatus(AtsCandidateStatus.HR_REJECTED);
            sendRejectionEmail(c.getEmail(), c.getName(), c.getAppliedProfile(), dto.getRejectionReason().trim());

        } else if ("SUITABLE".equals(dto.getDecision()) && dto.getInterviewerId() != null) {
            c.setStatus(AtsCandidateStatus.TECHNICAL_PENDING);
            EmployeeDetails interviewer = employeeRepo.findById(dto.getInterviewerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found: " + dto.getInterviewerId()));
            AtsTechnicalInterview tech = AtsTechnicalInterview.builder()
                    .candidate(c)
                    .interviewer(interviewer)
                    .assignedBy(caller)
                    .scheduledAt(dto.getScheduledAt())
                    .decision("PENDING")
                    .build();
            techRepo.save(tech);
            String managerEmail = interviewer.getUser() != null ? interviewer.getUser().getEmail() : interviewer.getWorkEmail();
            String scheduledStr = dto.getScheduledAt() != null ? dto.getScheduledAt().format(DT_FMT) : "TBD";
            sendTechAssignmentEmail(managerEmail,
                    interviewer.getFirstName() + " " + interviewer.getLastName(),
                    c.getName(), c.getAppliedProfile(), scheduledStr);

        } else if ("SUITABLE".equals(dto.getDecision())) {
            c.setStatus(AtsCandidateStatus.TECHNICAL_PENDING);
        }

        return toCandidateDTO(candidateRepo.save(c), true);
    }

    // ── Technical Interviews ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AtsTechnicalInterviewDTO> getMyTechnicalInterviews(String callerEmail) {
        return findEmployee(callerEmail)
                .map(emp -> techRepo.findByInterviewerIdOrderByScheduledAtAsc(emp.getId())
                        .stream().map(this::toTechInterviewDTO).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    /**
     * Active Directors/Admins eligible to conduct a final round — used to populate
     * the "Assign Director" dropdown. Deliberately bypasses EmployeeService's
     * HR-scoped employee list, which excludes directors from HR's view.
     */
    @Transactional(readOnly = true)
    public List<EmployeeDTO> getEligibleDirectors() {
        return employeeRepo.findActiveAdmins().stream()
                .map(e -> EmployeeDTO.builder()
                        .id(e.getId())
                        .firstName(e.getFirstName())
                        .lastName(e.getLastName())
                        .role(e.getUser() != null ? e.getUser().getRole().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Upcoming/past interview rounds (technical + final) assigned to the caller,
     * for calendar display. Final rounds without a startedAt/finalInterviewDate
     * have no meaningful schedule time and are omitted.
     */
    @Transactional(readOnly = true)
    public List<InterviewRoundDTO> getMyRounds(String callerEmail) {
        Optional<EmployeeDetails> empOpt = findEmployee(callerEmail);
        if (empOpt.isEmpty()) return Collections.emptyList();
        Long empId = empOpt.get().getId();

        List<InterviewRoundDTO> rounds = new ArrayList<>();

        techRepo.findByInterviewerIdOrderByScheduledAtAsc(empId).forEach(t -> {
            if (t.getScheduledAt() == null) return;
            rounds.add(InterviewRoundDTO.builder()
                    .id(t.getId())
                    .candidateId(t.getCandidate() != null ? t.getCandidate().getId() : null)
                    .candidateName(t.getCandidate() != null ? t.getCandidate().getName() : null)
                    .roundType("TECHNICAL")
                    .scheduledAt(t.getScheduledAt())
                    .status(t.getInterviewStatus() != null ? t.getInterviewStatus().name() : null)
                    .build());
        });

        finalRepo.findByConductedByIdOrderByCreatedAtDesc(empId).forEach(f -> {
            LocalDateTime scheduledAt = f.getStartedAt() != null
                    ? f.getStartedAt()
                    : (f.getFinalInterviewDate() != null ? f.getFinalInterviewDate().atStartOfDay() : null);
            if (scheduledAt == null) return;
            rounds.add(InterviewRoundDTO.builder()
                    .id(f.getId())
                    .candidateId(f.getCandidate() != null ? f.getCandidate().getId() : null)
                    .candidateName(f.getCandidate() != null ? f.getCandidate().getName() : null)
                    .roundType("FINAL")
                    .scheduledAt(scheduledAt)
                    .status(f.getInterviewStatus() != null ? f.getInterviewStatus().name() : null)
                    .build());
        });

        rounds.sort(Comparator.comparing(InterviewRoundDTO::getScheduledAt));
        return rounds;
    }

    @Transactional
    public AtsTechnicalInterviewDTO assignTechnicalInterview(Long candidateId, AtsTechnicalInterviewDTO dto, String callerEmail) {
        AtsCandidate c = findCandidate(candidateId);
        EmployeeDetails caller = findEmployee(callerEmail).orElse(null);
        EmployeeDetails interviewer = employeeRepo.findById(dto.getInterviewerId())
                .orElseThrow(() -> new ResourceNotFoundException("Interviewer not found: " + dto.getInterviewerId()));

        AtsTechnicalInterview tech = AtsTechnicalInterview.builder()
                .candidate(c).interviewer(interviewer).assignedBy(caller)
                .scheduledAt(dto.getScheduledAt()).decision("PENDING").build();
        tech = techRepo.save(tech);

        c.setStatus(AtsCandidateStatus.TECHNICAL_PENDING);
        candidateRepo.save(c);

        String managerEmail = interviewer.getUser() != null ? interviewer.getUser().getEmail() : null;
        String scheduledStr = dto.getScheduledAt() != null ? dto.getScheduledAt().format(DT_FMT) : "TBD";
        sendTechAssignmentEmail(managerEmail,
                interviewer.getFirstName() + " " + interviewer.getLastName(),
                c.getName(), c.getAppliedProfile(), scheduledStr);

        return toTechInterviewDTO(tech);
    }

    @Transactional
    public AtsCandidateDTO submitTechnicalFeedback(Long techId, AtsTechnicalInterviewDTO dto, String callerEmail) {
        AtsTechnicalInterview tech = techRepo.findById(techId)
                .orElseThrow(() -> new ResourceNotFoundException("Technical interview not found: " + techId));

        tech.setTechnicalSkillsRating(dto.getTechnicalSkillsRating());
        tech.setCommunicationRating(dto.getCommunicationRating());
        tech.setProblemSolvingRating(dto.getProblemSolvingRating());
        tech.setCodingAbilityRating(dto.getCodingAbilityRating());
        tech.setArchitectureKnowledgeRating(dto.getArchitectureKnowledgeRating());
        tech.setComments(dto.getComments());
        tech.setDecision(dto.getDecision());
        techRepo.save(tech);

        AtsCandidate c = tech.getCandidate();

        if ("REJECT".equals(dto.getDecision())) {
            c.setStatus(AtsCandidateStatus.TECHNICAL_REJECTED);
            sendRejectionEmail(c.getEmail(), c.getName(), c.getAppliedProfile());

        } else if ("APPROVE".equals(dto.getDecision())) {
            c.setStatus(AtsCandidateStatus.FINAL_ROUND_PENDING);
            if (finalRepo.findByCandidateId(c.getId()).isEmpty()) {
                finalRepo.save(AtsFinalRound.builder().candidate(c).finalDecision("PENDING").build());
            }
            employeeRepo.findActiveAdmins().forEach(admin -> {
                String adminEmail = admin.getUser() != null ? admin.getUser().getEmail() : null;
                sendFinalRoundNotifEmail(adminEmail, c.getName(), c.getAppliedProfile());
            });
        }

        return toCandidateDTO(candidateRepo.save(c), true);
    }

    // ── Final Round ───────────────────────────────────────────────────────────

    /**
     * Director step: submits their Final Round interview notes and an advisory recommendation.
     * This never decides the candidate's fate — it only records the Director's evaluation and
     * notifies HR that it's ready for review. HR alone records the actual hiring decision
     * (see {@link #submitFinalDecision}), keeping the recommend/decide responsibilities separate
     * and giving each step its own timestamped audit entry.
     */
    @Transactional
    public AtsFinalRoundDTO saveFinalRound(Long candidateId, AtsFinalRoundDTO dto, String callerEmail) {
        AtsCandidate c = findCandidate(candidateId);
        AtsFinalRound round = finalRepo.findByCandidateId(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Final round not found for candidate: " + candidateId));

        if (round.getConductedBy() == null) {
            throw new com.emp.management.exception.BadRequestException(
                    "Assign a Director to this candidate before submitting interview notes.");
        }
        requireFinalRoundOwner(round, callerEmail);

        round.setFinalInterviewDate(dto.getFinalInterviewDate());
        round.setFinalRemarks(dto.getFinalRemarks());
        round.setSalaryRecommendation(dto.getSalaryRecommendation());
        round.setDirectorRecommendation(dto.getDirectorRecommendation() != null ? dto.getDirectorRecommendation() : "PENDING");
        round.setDirectorNotesAt(LocalDateTime.now());
        finalRepo.save(round);

        notifyHrDirectorNotesReady(round, c);

        return toFinalRoundDTO(finalRepo.save(round));
    }

    /** Notifies whoever assigned the Director (falling back to all active Admins/HR) that notes are ready for review. */
    private void notifyHrDirectorNotesReady(AtsFinalRound round, AtsCandidate c) {
        String directorName = round.getConductedBy() != null
                ? round.getConductedBy().getFirstName() + " " + round.getConductedBy().getLastName() : "the Director";
        String recommendation = round.getDirectorRecommendation() != null ? round.getDirectorRecommendation() : "PENDING";
        if (round.getAssignedBy() != null && round.getAssignedBy().getUser() != null) {
            sendDirectorNotesReadyEmail(round.getAssignedBy().getUser().getEmail(), directorName, c.getName(), c.getAppliedProfile(), recommendation);
        } else {
            employeeRepo.findActiveAdmins().forEach(admin -> {
                String adminEmail = admin.getUser() != null ? admin.getUser().getEmail() : null;
                sendDirectorNotesReadyEmail(adminEmail, directorName, c.getName(), c.getAppliedProfile(), recommendation);
            });
        }
    }

    /**
     * HR step: reviews the Director's interview notes/recommendation and records the actual
     * hiring decision, sending the offer or rejection email. Requires the Director to have
     * already submitted notes, and refuses to re-decide a candidate that's already been
     * selected or rejected — both guard against skipping or duplicating a step in the audit trail.
     */
    @Transactional
    public AtsCandidateDTO submitFinalDecision(Long finalId, AtsFinalRoundDTO dto, String callerEmail) {
        AtsFinalRound round = finalRepo.findById(finalId)
                .orElseThrow(() -> new ResourceNotFoundException("Final round not found: " + finalId));
        EmployeeDetails caller = findEmployee(callerEmail).orElse(null);

        if (round.getDirectorNotesAt() == null) {
            throw new com.emp.management.exception.BadRequestException(
                    "The Director must submit their interview notes before a hiring decision can be recorded.");
        }
        AtsCandidate c = round.getCandidate();
        if (AtsCandidateStatus.SELECTED.equals(c.getStatus()) || AtsCandidateStatus.REJECTED.equals(c.getStatus())) {
            throw new com.emp.management.exception.BadRequestException(
                    "A hiring decision has already been recorded for this candidate.");
        }

        round.setOfferedCtc(dto.getOfferedCtc());
        round.setJoiningDate(dto.getJoiningDate());
        round.setFinalDecision(dto.getFinalDecision());
        round.setDecidedBy(caller);
        round.setDecidedAt(LocalDateTime.now());
        finalRepo.save(round);

        String directorEmail = round.getConductedBy() != null && round.getConductedBy().getUser() != null
                ? round.getConductedBy().getUser().getEmail() : null;

        if ("APPROVE".equals(dto.getFinalDecision())) {
            c.setStatus(AtsCandidateStatus.SELECTED);
            String joiningStr = dto.getJoiningDate() != null ? dto.getJoiningDate().toString() : "TBD";
            String salary = dto.getOfferedCtc() != null ? dto.getOfferedCtc() : round.getSalaryRecommendation();
            sendOfferEmail(c.getEmail(), c.getName(), c.getAppliedProfile(), salary, joiningStr);
            sendHrDecisionToDirectorEmail(directorEmail, c.getName(), c.getAppliedProfile(), "Approved — offer sent");

        } else if ("REJECT".equals(dto.getFinalDecision())) {
            c.setStatus(AtsCandidateStatus.REJECTED);
            sendRejectionEmail(c.getEmail(), c.getName(), c.getAppliedProfile());
            sendHrDecisionToDirectorEmail(directorEmail, c.getName(), c.getAppliedProfile(), "Rejected");

        } else if ("HOLD".equals(dto.getFinalDecision())) {
            // Status stays FINAL_ROUND_PENDING, no candidate email
            sendHrDecisionToDirectorEmail(directorEmail, c.getName(), c.getAppliedProfile(), "On hold");
        }

        return toCandidateDTO(candidateRepo.save(c), true);
    }

    // ── Duplicate Detection ───────────────────────────────────────────────────

    /**
     * Returns the first existing candidate that matches by email → phone → name (priority order).
     * Name-only matching is used only when both email and phone are blank on the new CV.
     */
    @Transactional(readOnly = true)
    public AtsCandidateDTO checkDuplicate(String email, String phone, String name) {
        Optional<AtsCandidate> found = Optional.empty();

        if (email != null && !email.isBlank()) {
            found = candidateRepo.findByEmailIgnoreCase(email.trim());
        }

        if (found.isEmpty() && phone != null && !phone.isBlank()) {
            String normalized = phone.replaceAll("[^\\d]", "");
            if (normalized.length() > 10) normalized = normalized.substring(normalized.length() - 10);
            found = candidateRepo.findByPhone(normalized);
        }

        // Name-only search: only when the new CV has neither email nor phone
        if (found.isEmpty()
                && (email == null || email.isBlank())
                && (phone == null || phone.isBlank())
                && name  != null && !name.isBlank()) {
            List<AtsCandidate> byName = candidateRepo.findByNameIgnoreCase(name.trim());
            if (!byName.isEmpty()) found = Optional.of(byName.get(0));
        }

        return found.map(c -> toCandidateDTO(c, false)).orElse(null);
    }

    // ── Replace Resume ────────────────────────────────────────────────────────

    /**
     * Archives the existing CV and replaces it with the newly uploaded version.
     * All parsed fields from the new CV overwrite the old values.
     */
    @Transactional
    public AtsCandidateDTO replaceResume(Long candidateId,
            String resumePath, String resumeOriginalName,
            String name, String email, String phone, String address,
            String addressStreet, String addressArea, String addressLandmark,
            String addressCity, String addressDistrict, String addressState,
            String addressPostalCode, String addressCountry,
            String appliedProfile, String officeLocation, String source,
            String skills, String totalExperienceYears,
            String linkedinUrl, String githubUrl,
            String educationSummary, String currentDesignation,
            String currentCompanyCv, String rawResumeText,
            String callerEmail) {

        AtsCandidate c = findCandidate(candidateId);
        EmployeeDetails caller = findEmployee(callerEmail).orElse(null);
        String callerName = caller != null
                ? caller.getFirstName() + " " + caller.getLastName()
                : callerEmail;

        // The previous CV upload date — fall back to candidate creation date for legacy records
        LocalDateTime previousUploadDate = c.getResumeUploadedAt() != null
                ? c.getResumeUploadedAt() : c.getCreatedAt();

        // Archive the previous CV (audit record)
        cvHistoryRepo.save(AtsCvHistory.builder()
                .candidate(c)
                .previousResumePath(c.getResumePath())
                .previousResumeName(c.getResumeOriginalName())
                .previousUploadDate(previousUploadDate)
                .newResumePath(resumePath)
                .newResumeName(resumeOriginalName)
                .replacedBy(callerName)
                .build());

        // Reset HR screening — the old evaluation belongs to the old CV.
        // Bulk JPQL delete bypasses Hibernate's entity lifecycle so the CascadeType.ALL
        // on AtsCandidate.hrScreening cannot re-persist the record during candidateRepo.save().
        hrRepo.deleteByCandidateId(candidateId);

        // ── New CV file references ───────────────────────────────────────────
        if (resumePath         != null && !resumePath.isBlank())         c.setResumePath(resumePath);
        if (resumeOriginalName != null && !resumeOriginalName.isBlank()) c.setResumeOriginalName(resumeOriginalName);
        c.setResumeUploadedAt(LocalDateTime.now());
        c.setStatus(AtsCandidateStatus.NEW);

        // ── NOT NULL identity fields — only update when the new form provides them ─
        if (name           != null && !name.isBlank())           c.setName(name.trim());
        if (appliedProfile != null && !appliedProfile.isBlank()) c.setAppliedProfile(appliedProfile.trim());

        // ── User-set intake metadata — keep existing unless HR explicitly changes ──
        if (officeLocation != null && !officeLocation.isBlank()) c.setOfficeLocation(officeLocation);
        if (source         != null && !source.isBlank())         c.setSource(source);

        // ── CV-extracted fields — always overwrite so the new CV is the single ────
        // source of truth; blank/null means the new resume did not contain this
        // data, which clears any stale value left over from the previous file.
        c.setEmail(blankToNull(email));
        c.setPhone(blankToNull(phone));
        c.setAddress(blankToNull(address));
        c.setAddressStreet(blankToNull(addressStreet));
        c.setAddressArea(blankToNull(addressArea));
        c.setAddressLandmark(blankToNull(addressLandmark));
        c.setAddressCity(blankToNull(addressCity));
        c.setAddressDistrict(blankToNull(addressDistrict));
        c.setAddressState(blankToNull(addressState));
        c.setAddressPostalCode(blankToNull(addressPostalCode));
        c.setAddressCountry(blankToNull(addressCountry));
        c.setSkills(blankToNull(skills));
        c.setTotalExperienceYears(blankToNull(totalExperienceYears));
        c.setLinkedinUrl(blankToNull(linkedinUrl));
        c.setGithubUrl(blankToNull(githubUrl));
        c.setEducationSummary(blankToNull(educationSummary));
        c.setCurrentDesignation(blankToNull(currentDesignation));
        c.setCurrentCompanyCv(blankToNull(currentCompanyCv));
        c.setRawResumeText(blankToNull(rawResumeText));

        return toCandidateDTO(candidateRepo.save(c), false);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total",             candidateRepo.count());
        m.put("new",               candidateRepo.countByStatus(AtsCandidateStatus.NEW));
        m.put("underHrReview",     candidateRepo.countByStatus(AtsCandidateStatus.UNDER_HR_REVIEW));
        m.put("hrRejected",        candidateRepo.countByStatus(AtsCandidateStatus.HR_REJECTED));
        m.put("technicalPending",  candidateRepo.countByStatus(AtsCandidateStatus.TECHNICAL_PENDING));
        m.put("technicalRejected", candidateRepo.countByStatus(AtsCandidateStatus.TECHNICAL_REJECTED));
        m.put("finalRoundPending", candidateRepo.countByStatus(AtsCandidateStatus.FINAL_ROUND_PENDING));
        m.put("selected",          candidateRepo.countByStatus(AtsCandidateStatus.SELECTED));
        m.put("rejected",          candidateRepo.countByStatus(AtsCandidateStatus.REJECTED));
        return m;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AtsCandidate findCandidate(Long id) {
        return candidateRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
    }

    private Optional<EmployeeDetails> findEmployee(String email) {
        if (email == null) return Optional.empty();
        return employeeRepo.findByUserEmail(email);
    }

    private void applyScreeningFields(AtsHrScreening s, AtsHrScreeningDTO dto, EmployeeDetails caller) {
        s.setCommunicationSkills(dto.getCommunicationSkills());
        s.setCurrentCompany(dto.getCurrentCompany());
        s.setCurrentCtc(dto.getCurrentCtc());
        s.setExpectedCtc(dto.getExpectedCtc());
        s.setNoticePeriod(dto.getNoticePeriod());
        s.setTotalExperience(dto.getTotalExperience());
        s.setRelevantExperience(dto.getRelevantExperience());
        s.setPreferredLocation(dto.getPreferredLocation());
        s.setAvailability(dto.getAvailability());
        s.setReasonForChange(dto.getReasonForChange());
        s.setCurrentRoleResponsibilities(dto.getCurrentRoleResponsibilities());
        s.setWorkBase(dto.getWorkBase());
        s.setScreeningDate(dto.getScreeningDate());
        s.setHrComments(dto.getHrComments());
        if (dto.getRejectionReason() != null) s.setRejectionReason(dto.getRejectionReason());
        s.setConductedBy(caller);
    }

    private String saveResume(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')) : ".bin";
        String filename = UUID.randomUUID() + ext;
        Path dir = Paths.get(uploadDir, "resumes");
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename));
        return "resumes/" + filename;
    }

    private String determineContentType(String path) {
        if (path.endsWith(".pdf"))  return "application/pdf";
        if (path.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (path.endsWith(".doc"))  return "application/msword";
        return "application/octet-stream";
    }

    private String extractTextFromPath(Path filePath, String originalName) throws IOException {
        String name = originalName != null ? originalName.toLowerCase() : filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".docx")) {
            try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                         new org.apache.poi.xwpf.usermodel.XWPFDocument(java.nio.file.Files.newInputStream(filePath))) {
                return extractDocxText(doc);
            }
        }
        if (name.endsWith(".doc")) {
            try (org.apache.poi.hwpf.HWPFDocument doc =
                         new org.apache.poi.hwpf.HWPFDocument(java.nio.file.Files.newInputStream(filePath))) {
                return new org.apache.poi.hwpf.extractor.WordExtractor(doc).getText();
            } catch (Exception e) {
                log.warn("Failed to extract text from .doc file: {}", e.getMessage());
                return "";
            }
        }
        return "";
    }

    private String extractText(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (name.endsWith(".docx")) {
            try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                         new org.apache.poi.xwpf.usermodel.XWPFDocument(file.getInputStream())) {
                return extractDocxText(doc);
            }
        }
        if (name.endsWith(".doc")) {
            try (org.apache.poi.hwpf.HWPFDocument doc =
                         new org.apache.poi.hwpf.HWPFDocument(file.getInputStream())) {
                return new org.apache.poi.hwpf.extractor.WordExtractor(doc).getText();
            } catch (Exception e) {
                log.warn("Failed to extract text from .doc file: {}", e.getMessage());
                return "";
            }
        }
        return "";
    }

    /**
     * Extracts full text from a DOCX, including content inside tables.
     * doc.getParagraphs() only returns top-level paragraphs and silently skips tables,
     * causing sections like "Personal Details" (which are usually in a 2-col table) to
     * vanish. This method walks all body elements and recurses into tables.
     *
     * Two-column label-value rows ("Permanent Address | H.No.5 …") are normalised to
     * "Permanent Address: H.No.5 …" so existing regex label patterns match them.
     */
    private String extractDocxText(org.apache.poi.xwpf.usermodel.XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (org.apache.poi.xwpf.usermodel.IBodyElement elem : doc.getBodyElements()) {
            if (elem instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                String t = ((org.apache.poi.xwpf.usermodel.XWPFParagraph) elem).getText().strip();
                if (!t.isBlank()) sb.append(normalizeTabLine(t)).append("\n");
            } else if (elem instanceof org.apache.poi.xwpf.usermodel.XWPFTable) {
                appendDocxTable((org.apache.poi.xwpf.usermodel.XWPFTable) elem, sb);
            }
        }
        String result = sb.toString();
        return result;
    }

    /**
     * Converts "Label\tValue" tab-formatted paragraphs (common in DOCX "Personal Details"
     * sections that use tab stops instead of a table) to "Label: Value" so that
     * ADDRESS_LABEL_PATTERN and other label-scan regexes can match them.
     * Lines without a tab are returned unchanged.
     */
    private String normalizeTabLine(String line) {
        int tabIdx = line.indexOf('\t');
        if (tabIdx < 0) return line;
        String label = line.substring(0, tabIdx).replaceAll("\\s+", " ").strip();
        if (label.isEmpty() || label.length() > 60) return line;
        // Collapse all trailing tabs and multi-space padding in the value
        String value = line.substring(tabIdx + 1)
                .replaceAll("[\\t ]{2,}", " ")
                .strip();
        if (value.isEmpty()) return label;
        return label + ": " + value;
    }

    private void appendDocxTable(org.apache.poi.xwpf.usermodel.XWPFTable table, StringBuilder sb) {
        for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
            java.util.List<String> cells = new java.util.ArrayList<>();
            for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                // Collect paragraphs + nested tables within each cell
                StringBuilder cellSb = new StringBuilder();
                for (org.apache.poi.xwpf.usermodel.IBodyElement e : cell.getBodyElements()) {
                    if (e instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                        // strip() removes Unicode whitespace (incl. non-breaking spaces) that trim() misses
                        String t = ((org.apache.poi.xwpf.usermodel.XWPFParagraph) e).getText().strip();
                        if (!t.isBlank()) {
                            if (cellSb.length() > 0) cellSb.append(" ");
                            cellSb.append(t);
                        }
                    } else if (e instanceof org.apache.poi.xwpf.usermodel.XWPFTable) {
                        appendDocxTable((org.apache.poi.xwpf.usermodel.XWPFTable) e, sb);
                    }
                }
                // Collapse ALL Unicode whitespace (including non-breaking space U+00A0
                // which appears in middle "separator" columns in some DOCX table templates).
                // \p{Z} matches every Unicode separator category; \s covers ASCII whitespace.
                String cellText = cellSb.toString()
                        .replaceAll("[\\p{Z}\\s]+", " ")
                        .strip();
                if (!cellText.isBlank()) cells.add(cellText);
            }
            if (cells.isEmpty()) continue;
            log.warn(">>> TABLE ROW {} cells: {}", cells.size(), cells);
            if (cells.size() == 1) {
                sb.append(cells.get(0)).append("\n");
            } else if (cells.size() == 2) {
                String label = cells.get(0);
                String value = cells.get(1);
                if (value.isBlank()) {
                    sb.append(label).append("\n");
                } else {
                    // Always normalise 2-column rows to "Label: Value" — no regex needed.
                    // Word pads cell text with spaces/non-breaking spaces that defeat a label
                    // regex; stripping + collapsing spaces above already cleaned the text.
                    sb.append(label).append(": ").append(value).append("\n");
                }
            } else {
                sb.append(String.join("\t", cells)).append("\n");
            }
        }
    }

    /**
     * Renders PDF pages to JPEG images, sends to Claude Vision, and asks Claude
     * to return structured JSON directly — no regex parsing needed.
     * Works for both image-based (scanned/photo) and complex-layout digital PDFs.
     */
    private Map<String, String> extractStructuredWithClaude(org.apache.pdfbox.pdmodel.PDDocument doc) {
        try {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            List<Map<String, Object>> contentBlocks = new ArrayList<>();

            int pages = Math.min(doc.getNumberOfPages(), 2);
            for (int i = 0; i < pages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, 150);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "jpeg", baos);
                String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                contentBlocks.add(Map.of(
                    "type", "image",
                    "source", Map.of("type", "base64", "media_type", "image/jpeg", "data", b64)
                ));
            }

            contentBlocks.add(Map.of("type", "text", "text",
                "Extract information from this CV/resume image. " +
                "Return ONLY a valid JSON object with exactly these keys (use null for missing fields):\n" +
                "{\n" +
                "  \"name\": \"full name of the candidate\",\n" +
                "  \"email\": \"email address\",\n" +
                "  \"phone\": \"10-digit Indian mobile number, digits only, no spaces or dashes\",\n" +
                "  \"address\": \"permanent home/residential postal address (door no., flat, building, colony, street, city, state, PIN) — prefer the full street address over a city/state-only location; if both a current city and a home address are present, use the home address\",\n" +
                "  \"skills\": \"comma-separated technical and professional skills\",\n" +
                "  \"totalExperienceYears\": \"total years of experience e.g. 2 years\",\n" +
                "  \"educationSummary\": \"highest qualification e.g. B.Com, Diploma in Computer Applications\",\n" +
                "  \"currentDesignation\": \"current or most recent job title\",\n" +
                "  \"currentCompanyCv\": \"current or most recent employer name\"\n" +
                "}\n" +
                "Return ONLY the JSON — no explanation, no markdown, no code blocks."
            ));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", claudeModel);
            body.put("max_tokens", 1024);
            body.put("messages", List.of(Map.of("role", "user", "content", contentBlocks)));

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", claudeApiKey);
            headers.set("anthropic-version", "2023-06-01");
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(
                "https://api.anthropic.com/v1/messages",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
            ).getBody();

            String jsonText = null;
            if (response != null && response.get("content") instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?,?> block && block.get("text") instanceof String txt) {
                    jsonText = txt.trim();
                }
            }

            if (jsonText == null || jsonText.isBlank()) return new HashMap<>();

            // Strip markdown code fences if Claude wrapped the JSON anyway
            jsonText = jsonText.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();

            log.info("Claude returned structured JSON ({} chars)", jsonText.length());

            // Parse JSON into Map<String,String>
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(jsonText, Map.class);

            Map<String, String> result = new HashMap<>();
            parsed.forEach((k, v) -> {
                if (v != null && !"null".equals(String.valueOf(v)) && !String.valueOf(v).isBlank()) {
                    result.put(k, String.valueOf(v).trim());
                }
            });
            return result;

        } catch (Exception e) {
            log.warn("Claude structured extraction failed: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String findFirst(Pattern p, String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        // Return capturing group(1) if present (e.g. PHONE_PATTERN strips +91 prefix), else full match
        return m.groupCount() >= 1 && m.group(1) != null ? m.group(1) : m.group();
    }

    private Map<String, String> deepParseText(String text) {
        Map<String, String> r = new HashMap<>();
        if (text == null || text.isBlank()) return r;

        put(r, "email",   findFirst(EMAIL_PATTERN, text));
        // Strip any spaces/dashes from the captured phone group to return 10 clean digits
        String rawPhone = findFirst(PHONE_PATTERN, text);
        if (rawPhone != null) {
            String cleanPhone = rawPhone.replaceAll("[^\\d]", "");
            if (cleanPhone.length() == 10) put(r, "phone", cleanPhone);
        }
        // Name must come from the resume body only — the uploaded file name/title is
        // author-chosen metadata (e.g. "Resume_Sakshi.pdf"), not reliable evidence of
        // who the candidate actually is, so it is never used here.
        put(r, "name", extractNameFromText(text));
        // Log first 40 extracted lines so address-parsing failures can be diagnosed
        String[] _dbgLines = text.split("\\r?\\n");
        StringBuilder _dbg = new StringBuilder("=== CV TEXT LINES ===\n");
        for (int _i = 0; _i < Math.min(40, _dbgLines.length); _i++) {
            String _ln = _dbgLines[_i].trim();
            if (!_ln.isBlank()) {
                // Show hex codes for any non-ASCII characters so icon chars are visible
                StringBuilder _hex = new StringBuilder();
                for (char _c : _ln.toCharArray()) {
                    if (_c > 127) _hex.append(String.format("[U+%04X]", (int)_c));
                    else _hex.append(_c);
                }
                _dbg.append("L").append(_i).append(": ").append(_hex).append("\n");
            }
        }
        try {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "cv_debug.txt"),
                _dbg.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception _ex) { /* ignore */ }

        String fullAddr = finalizeAddress(extractAddress(text));
        put(r, "address", fullAddr);
        parseAddressComponents(fullAddr).forEach((k, v) -> put(r, k, v));
        put(r, "skills",  extractSkills(text));
        put(r, "totalExperienceYears", extractExperienceYears(text));
        put(r, "educationSummary",     extractEducation(text));
        put(r, "currentDesignation",   extractDesignation(text));
        put(r, "currentCompanyCv",     extractCurrentCompany(text));

        Matcher lm = LINKEDIN_PATTERN.matcher(text);
        if (lm.find()) put(r, "linkedinUrl", "https://www." + lm.group());

        Matcher gm = GITHUB_PATTERN.matcher(text);
        if (gm.find()) put(r, "githubUrl", "https://" + gm.group());

        return r;
    }

    private void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value.trim());
    }

    // Standalone Skills section heading: "Technical Skills", "Key Skills:", "Skills"
    private static final Pattern SKILLS_SECTION_HEADER = Pattern.compile(
            "^(?:key|technical|core|professional|functional|it|soft|hard)?\\s*"
            + "skills?(?:\\s+(?:and\\s+)?(?:expertise|set|competenc(?:ies|y)|qualifications?|summary|areas?))?\\s*[:\\-]?$",
            Pattern.CASE_INSENSITIVE);

    // Skills on same line as header: "Technical Skills: Java, Python, React"
    private static final Pattern SKILLS_INLINE_HEADER = Pattern.compile(
            "^(?:key|technical|core|professional|functional|it|soft|hard)?\\s*"
            + "skills?(?:\\s+(?:and\\s+)?(?:expertise|set|competenc(?:ies|y)|qualifications?|summary|areas?))?\\s*[:\\-]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SKILLS_NEXT_SECTION = Pattern.compile(
            "^(?:education(?:al)?(?:\\s+(?:qualifications?|background|details?|history|summary|information|profile|credentials?))?|"
            + "academic(?:\\s+(?:qualifications?|background|details?|history|credentials?))?|"
            + "qualifications?(?:\\s+(?:and\\s+)?(?:education(?:al)?|academic|details?|summary))?|"
            + "experience|work(?:\\s+experience)?|employment|projects?|internship|training|"
            + "certifications?|achievements?|references?|declarations?|languages?|"
            + "objectives?|summar(?:y|ies)|profiles?|academic|publications?|awards?)\\s*[:\\-]?$",
            Pattern.CASE_INSENSITIVE);

    private String extractSkills(String text) {
        List<String> found = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        boolean inSection = false;

        for (String line : lines) {
            String t = line.trim();
            if (t.isBlank()) continue;

            // End of skills section — stop before the next named section
            if (inSection && SKILLS_NEXT_SECTION.matcher(t).matches()) break;

            // Standalone header: "Technical Skills", "Key Skills:", etc.
            if (SKILLS_SECTION_HEADER.matcher(t).matches()) {
                inSection = true;
                continue;
            }

            // Inline header with skills on same line: "Technical Skills: Java, Python, React"
            Matcher inlineMatcher = SKILLS_INLINE_HEADER.matcher(t);
            if (inlineMatcher.matches()) {
                String rest = inlineMatcher.group(1);
                if (!rest.isBlank()) addSkillTokens(rest, found);
                inSection = true;
                continue;
            }

            if (!inSection) continue;

            addSkillTokens(t, found);
        }

        return found.isEmpty() ? null : String.join(", ", found);
    }

    private void addSkillTokens(String text, List<String> found) {
        for (String part : text.split("[,|;•·✓★?]")) {
            String s = part.replaceAll("^[\\s\\-*>#©®?]+", "")
                           .replaceAll("[^a-zA-Z0-9+#.()/\\s\\-&]", "")
                           .trim();
            if (s.length() >= 2 && s.length() <= 60 && !s.isBlank()
                    && !s.matches("\\d+")
                    && found.stream().noneMatch(f -> f.equalsIgnoreCase(s))) {
                found.add(s);
            }
        }
    }

    /**
     * Returns true when a CV line looks like a component of a postal address.
     *
     * True when the line has at least one unambiguous address signal:
     *   - An address keyword (Colony, Flat, Sector, Nagar, Road, etc.)
     *   - A comma  (multi-part address: "Green Colony, Nashik")
     *   - A leading digit  (house/plot number: "42-B, Main Road")
     *   - A 6-digit PIN code
     *   - A city or state name within a short, non-sentence line
     *
     * False when the line is contact info, a designation, a section header,
     * or a sentence that merely mentions a location in passing.
     */
    private boolean isAddressLike(String line) {
        if (line == null || line.isBlank() || line.length() > 100) return false;
        String t = line.trim();
        if (EMAIL_PATTERN.matcher(t).find() || PHONE_PATTERN.matcher(t).find()) return false;
        if (t.toLowerCase().contains("linkedin") || t.toLowerCase().contains("github")) return false;
        // Section header: "Skills:" / "Experience -" etc.
        if (t.matches("(?i)^[A-Za-z][A-Za-z\\s]{2,25}[:\\-]\\s*$")) return false;
        // Designation without digits or address keywords (e.g. "Senior Accountant")
        if (DESIGNATION_PATTERN.matcher(t).find()
                && !t.matches(".*\\d.*")
                && !ADDRESS_KEYWORD_PATTERN.matcher(t).find()) return false;
        // Sentence start words — the line is prose, not an address
        if (t.matches("(?i)^(?:i|the|a|an|my|our|we|they|he|she|it|this|that|" +
                       "in\\s|at\\s|for\\s|with|from\\s|to\\s|of\\s|and|but|" +
                       "currently|previously|having|working|seeking|looking|" +
                       "experienced|responsible|proficient|skilled|well|good|" +
                       "eager|dynamic|passionate|dedicated|motivated|ambitious|" +
                       "hardworking|aspiring|enthusiastic|aiming|willing|capable|" +
                       "desire|aim|objective|goal|result|able|deeply|highly|" +
                       "excellent|extensive|strong|fast|quick|smart|" +
                       // Past-tense CV action verbs that start work-experience bullet points
                       "developed|managed|led|built|created|designed|implemented|" +
                       "maintained|handled|conducted|performed|prepared|analyzed|" +
                       "coordinated|assisted|supervised|achieved|established|" +
                       "identified|improved|increased|reduced|monitored|trained|" +
                       "reviewed|evaluated|generated|provided|delivered|executed|" +
                       "communicated|collaborated|utilized|ensured|processed|" +
                       "administered|oversaw|operated|resolved|troubleshot|" +
                       "spearheaded|streamlined|overseeing|assisting|supporting|" +
                       "preparing|reporting|handling|managing|leading|driving)\\b.*")) return false;

        // Education qualification entries that contain city/state names are NOT home addresses
        // "Higher Secondary Certificate, L.K Singhania Naguar, Rajasthan"
        if (EDUCATION_CONTENT_PATTERN.matcher(t).matches()) return false;

        // Degree abbreviation at the start of the line — it's an education table row, not an address
        // "B.Com, Vikram University, Ujjain, M.P." / "MBA, IIM Ahmedabad"
        {
            Matcher degM = DEGREE_PATTERN.matcher(t);
            if (degM.find() && degM.start() <= 2
                    && (degM.end() >= t.length() || !Character.isLetter(t.charAt(degM.end())))
                    && !ADDRESS_KEYWORD_PATTERN.matcher(t).find()) {
                return false;
            }
        }

        // Lines that mention a school/college/university name are education content, not home addresses
        // "XYZ Engineering College, Nashik, Maharashtra" / "IIM Ahmedabad, Gujarat"
        if (INSTITUTION_PATTERN.matcher(t).find() && !ADDRESS_KEYWORD_PATTERN.matcher(t).find()) {
            return false;
        }

        if (ADDRESS_KEYWORD_PATTERN.matcher(t).find()) return true;   // Colony, Flat, Nagar, wada…
        // Comma alone is too permissive — "eager to learn, explore..." has a comma.
        // Require comma + short line (≤ 40 chars) OR at least one concrete address signal.
        if (t.contains(",") && (t.length() <= 40
                || t.matches(".*\\d.*")
                || ADDRESS_KEYWORD_PATTERN.matcher(t).find()
                || CITY_PATTERN.matcher(t).find()
                || STATE_PATTERN.matcher(t).find())) return true;
        if (t.matches("^\\d.*") || t.matches("^[A-Za-z]-?\\d.*")) return true;  // "12B, Road"
        if (t.matches(".*\\b\\d{6}\\b.*")) return true;               // PIN code
        // Short line with a city or state name (not embedded in a long sentence)
        if (t.length() <= 50
                && (CITY_PATTERN.matcher(t).find() || STATE_PATTERN.matcher(t).find())) return true;
        return false;
    }

    /**
     * Strips trailing noise that appears after the terminal elements of an Indian postal address.
     *
     * An address is complete once it contains a 6-digit PIN code and/or a state name.
     * Everything after the rightmost of those two anchors is CV content that leaked in.
     *
     * "Colony Mahal wada Tall, Ratlam 457001 (M.P.) Pythas"
     *  → "Colony Mahal wada Tall, Ratlam 457001 (M.P.)"  ← 'Pythas' stripped
     *
     * "A 49, Janta Colony, Mandsaur 458001 (M.P.) Applied Profile"
     *  → "A 49, Janta Colony, Mandsaur 458001 (M.P.)"
     */
    private String trimAddressTrailer(String addr) {
        if (addr == null || addr.isBlank()) return addr;

        int terminalEnd = -1;

        // Anchor 1: rightmost 6-digit PIN code
        Matcher pin = Pattern.compile("\\b\\d{6}\\b").matcher(addr);
        while (pin.find()) terminalEnd = pin.end();

        // Anchor 2: rightmost state name / abbreviation (also swallow a trailing ')' that wraps it)
        Matcher sm = STATE_PATTERN.matcher(addr);
        while (sm.find()) {
            int end = sm.end();
            if (end < addr.length() && addr.charAt(end) == ')') end++; // include closing ")" e.g. "(M.P.)"
            if (end > terminalEnd) terminalEnd = end;
        }

        if (terminalEnd > 0 && terminalEnd < addr.length()) {
            return addr.substring(0, terminalEnd).replaceAll("[,\\s\\-–]+$", "").trim();
        }
        return addr;
    }

    /**
     * Returns true when a comma-separated segment of the extracted address string
     * looks like a location component rather than a prose/sentence fragment.
     *
     * Used by finalizeAddress() to purge objective/profile text that PDFBox merges
     * into the address when sorting a two-column PDF by position.
     */
    private boolean isLocationSegment(String seg) {
        String s = seg.trim().replaceAll("^[©®™•·◦●○*#~\\-–]+\\s*", "");
        if (s.isEmpty()) return false;
        if (s.matches(".*\\b\\d{6}\\b.*")) return true;          // PIN code
        if (CITY_PATTERN.matcher(s).find()) return true;
        if (STATE_PATTERN.matcher(s).find()) return true;
        if (ADDRESS_KEYWORD_PATTERN.matcher(s).find()) return true;
        if (s.matches("^\\d.*")) return true;                     // house/plot number
        // Education qualification names are not location segments
        if (EDUCATION_CONTENT_PATTERN.matcher(s).matches()) return false;
        // Degree abbreviation at start of segment: "B.Com" / "MBA" / "M.Tech"
        {
            Matcher degM = DEGREE_PATTERN.matcher(s);
            if (degM.find() && degM.start() <= 2
                    && (degM.end() >= s.length() || !Character.isLetter(s.charAt(degM.end())))
                    && !ADDRESS_KEYWORD_PATTERN.matcher(s).find()) {
                return false;
            }
        }
        // Institution names are not home-address segments
        if (INSTITUTION_PATTERN.matcher(s).find() && !ADDRESS_KEYWORD_PATTERN.matcher(s).find()) {
            return false;
        }
        // Sentence-start words mark the segment as prose, not a place name
        if (s.matches("(?i)^(?:eager|and|or|but|the|a|an|in|at|on|for|to|of|with|by|" +
                       "that|this|not|so|yet|nor|as|if|then|than|when|where|while|" +
                       "motivated|passionate|seeking|looking|working|learn|explore|" +
                       "develop|achieve|dynamic|challenging|responsible|handling|" +
                       "managing|extensive|excellent|good|great|strong|highly|" +
                       "deeply|dedicated|hardworking|ambitious|aspiring|enthusiastic|" +
                       "able|capable|ready|willing|desire|aim|goal|objective)\\b.*")) return false;
        // Short segments without sentence-start words are likely place names
        if (s.length() <= 25) return true;
        // Medium segments — accept if predominantly Title Case (proper nouns)
        if (s.length() <= 40) {
            String[] words = s.split("\\s+");
            long titleCount = java.util.Arrays.stream(words)
                    .filter(w -> w.length() >= 2 && Character.isUpperCase(w.charAt(0)))
                    .count();
            return titleCount >= Math.max(1, words.length - 1);
        }
        return false;
    }

    /**
     * Post-processes a raw extracted address string:
     *  1. Strips leading copyright / bullet / special characters (©, •, etc.)
     *  2. Splits by comma and drops any segment that looks like prose rather than a
     *     location component (e.g. "eager to learn and explore the dynamic and challenging").
     *     This purges professional-objective text that PDFBox merges into the address
     *     line when extracting two-column PDFs sorted by Y-position.
     *  3. Applies trimAddressTrailer() to remove any trailing garbage after PIN/state.
     *
     * "© Santhi Nivas, Kakkayur, eager to learn…, Palakkad, Kerala, 678512"
     *  → "Santhi Nivas, Kakkayur, Palakkad, Kerala, 678512"
     */
    private String finalizeAddress(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        // Step 1: strip leading special characters and any inline label prefix that leaked through
        String addr = raw.replaceAll("^[©®™•·◦●○*#~\\-–—_=+|]+\\s*", "").trim();
        addr = stripInlineLabelPrefix(addr);
        if (addr.isBlank()) return null;
        // Step 2: drop non-location segments embedded in a comma-separated address
        String[] segments = addr.split(",");
        if (segments.length > 1) {
            List<String> kept = new ArrayList<>();
            for (String seg : segments) {
                if (isLocationSegment(seg)) kept.add(seg.trim());
            }
            if (!kept.isEmpty()) addr = String.join(", ", kept);
        }
        // Step 3: strip trailing noise after the rightmost PIN / state name
        return trimAddressTrailer(addr);
    }

    /**
     * Strips a secondary label prefix that some CV templates embed inside the address value.
     *
     * Pattern: the candidate writes "Location: From: Banswara, Raj." — our ADDRESS_LABEL_PATTERN
     * matches "Location:" and captures "From: Banswara, Raj." as the value.  This method then
     * strips the inner "From:" prefix, yielding the clean "Banswara, Raj.".
     *
     * Also handles plain-text variants: "From Banswara", "Hailing from Banswara", "Based in Pune".
     */
    private String stripInlineLabelPrefix(String addr) {
        if (addr == null || addr.isBlank()) return addr;
        return addr.replaceFirst(
                "(?i)^(?:" +
                "from\\s*[:\\-–]?|" +                       // "From:" / "From -" / "From "
                "native\\s*(?:place)?\\s*[:\\-–]?|" +       // "Native:" / "Native Place:"
                "based\\s+(?:in|at)\\s*[:\\-–]?|" +        // "Based in:" / "Based At:"
                "lives?\\s+in\\s*[:\\-–]?|" +               // "Lives in:" / "Live in:"
                "hailing\\s+from\\s*[:\\-–]?|" +            // "Hailing from:"
                "originally\\s+from\\s*[:\\-–]?|" +         // "Originally from:"
                "residing\\s+(?:at|in)\\s*[:\\-–]?|" +      // "Residing at:"
                "located\\s+(?:at|in)\\s*[:\\-–]?|" +       // "Located in:"
                "hometown\\s*[:\\-–]?|" +                   // "Hometown:"
                "home\\s+town\\s*[:\\-–]?" +                // "Home Town:"
                ")\\s*", "").trim();
    }

    /**
     * Collects continuation lines for a multi-line address.
     *
     * Stops when any of these conditions is met:
     *  - Address already has both a state name AND a 6-digit PIN (fully qualified)
     *  - Next line is blank
     *  - Next line is a section header or a new labeled personal field (DOB, Father's Name…)
     *  - Next line contains a phone or email (new contact field)
     *  - Two consecutive non-address lines have been skipped
     *
     * NOTE: Does NOT require the current address to end with a comma — many CV
     * address lines like "Flat 12, Green Nagar" don't have a trailing comma but
     * are still followed by "Mandsaur, M.P. 458001" on the next line.
     */
    private String appendAddressContinuation(String addr, String[] lines, int lineIdx) {
        if (addr == null || addr.isBlank()) return addr;
        int skipped = 0;
        for (int extra = 1; extra <= 4; extra++) {
            // Stop when address is fully qualified: has both a state name and a PIN
            if (STATE_PATTERN.matcher(addr).find() && addr.matches(".*\\b\\d{6}\\b.*")) break;

            int nextIdx = lineIdx + extra;
            if (nextIdx >= lines.length) break;
            String next = lines[nextIdx].trim();
            if (next.isBlank()) break;
            if (next.length() > 150) break;

            // Stop on major section headers (Education, Experience, Skills…)
            if (MAJOR_SECTION_HEADER.matcher(next).matches()) break;

            // Stop on address/personal label patterns that start a new labeled field
            if (next.matches("(?i)^(?:father|mother|parent|date\\s+of\\s+birth|d\\.?o\\.?b|age|"
                    + "marital|nationality|gender|sex|religion|category|caste|"
                    + "mobile|phone|contact\\s+no|linkedin|github|website|"
                    + "name|salary|ctc|notice\\s+period|languages?|hobbies?)\\b.*[:\\-].*")) break;

            // Stop when the next field is phone/email
            if (EMAIL_PATTERN.matcher(next).find() || PHONE_PATTERN.matcher(next).find()) break;

            if (!isAddressLike(next)) {
                if (++skipped > 1) break; // allow at most one non-address line (column-merge artifact)
                continue;
            }

            String sep = (addr.endsWith(",") || addr.endsWith("–") || addr.endsWith("-")) ? " " : ", ";
            addr = addr + sep + next;
        }
        return addr;
    }

    private int scoreAddress(String addr, int baseScore) {
        if (addr == null || addr.isBlank()) return 0;
        int score = baseScore;
        boolean hasKeyword = ADDRESS_KEYWORD_PATTERN.matcher(addr).find();
        boolean hasCity    = CITY_PATTERN.matcher(addr).find();
        boolean hasState   = STATE_PATTERN.matcher(addr).find();
        boolean hasPin     = addr.matches(".*\\b\\d{6}\\b.*");
        // Keyword bonus only meaningful when paired with city/state/PIN — prevents "investment
        // sector", "market analysis" etc. from outscoring a plain city+state address.
        if (hasKeyword && (hasCity || hasState || hasPin)) score += 8;
        else if (hasKeyword)                                score += 2; // keyword alone — low confidence
        if (hasPin)   score += 5;
        if (hasCity)  score += 3;
        if (hasState) score += 2;
        if (addr.length() > 30) score += 1;
        return score;
    }

    private String extractAddress(String text) {
        String[] lines = text.split("\\r?\\n");

        // Collect ALL candidates from every strategy and return the highest-scoring one.
        // This prevents a city-only match (e.g. "Thiruvananthapuram, Kerala" from a
        // "Current Location:" field) from shadowing a keyword-rich permanent address
        // (e.g. "C-48, Burhani Colony, Mandsaur (M.P.)") found later in the document.
        //
        // Base scores by source:
        //   5 → found via explicit high-confidence label (Address:, Permanent Address:, etc.)
        //   2 → found via ambiguous label (Location:, City:), icon scan, or contact proximity
        //
        // Content bonuses (added by scoreAddress):
        //   +8 address keyword (Colony, Nagar, near, Road…)  +5 PIN  +2 city  +1 state  +1 long
        List<String[]> candidates = new ArrayList<>(); // [addr, baseScoreStr]

        // Strategy 0: Personal-details section scan.
        // Indian CVs often place "Personal Details / Information" at the END of the document.
        // Walk the full text to find the section header, then search within it for any address label.
        for (int i = 0; i < lines.length; i++) {
            if (!PERSONAL_SECTION_HEADER.matcher(lines[i].trim()).matches()) continue;
            for (int j = i + 1; j < Math.min(i + 25, lines.length); j++) {
                String raw = lines[j].trim();
                if (raw.isBlank()) continue;
                if (MAJOR_SECTION_HEADER.matcher(raw).matches()) break;
                String stripped = raw.replaceAll("^[\\u2022\\u2023\\u25AA\\u25CF\\u25C6\\u25E6"
                        + "\\u2316\\u2299\\u29BF\\u27A4\\u2714\\u2756\\u2739\\u2766"
                        + "\\u2605\\u2606\\u260F\\u2709\\u279C\\u279F\\u27B3\\u27B8\\u2794"
                        + "\\u00A9\\u00AE\\uE000-\\uF8FF]\\s*", "")
                        .replaceAll("^(?:\\uD83D\\uDCCD|\\uD83C\\uDFE0|\\uD83D\\uDCCC)\\s*", "");
                Matcher lm = ADDRESS_LABEL_PATTERN.matcher(stripped);
                int baseScore;
                if (lm.find()) {
                    baseScore = 5;
                } else {
                    lm = ADDRESS_LOCATION_LABEL_PATTERN.matcher(stripped);
                    if (!lm.find()) continue;
                    baseScore = 2;
                }
                String addr = lm.group(1).trim()
                        .replaceAll("\\s*[|•·]+.*$", "")
                        .replaceAll("\\s{2,}", " ").trim();
                if (addr.length() < 3) {
                    // Label with no inline value — scan forward (skip blanks) for the real value
                    for (int look = j + 1; look < Math.min(j + 4, lines.length); look++) {
                        String nxt = lines[look].trim();
                        if (nxt.isBlank()) continue;
                        if (DECLARATION_FOOTER_PATTERN.matcher(nxt).matches()) break;
                        if (MAJOR_SECTION_HEADER.matcher(nxt).matches()) break;
                        if (nxt.matches("(?i)^[A-Za-z][A-Za-z\\s]{2,30}[:\\-]\\s*$")) break;
                        addr = nxt; j = look;
                        break;
                    }
                }
                if (addr.length() < 3) continue;
                addr = stripInlineLabelPrefix(addr);
                addr = appendAddressContinuation(addr, lines, j);
                addr = addr.replaceAll(",\\s*,", ",").replaceAll("^[,\\s]+|[,\\s]+$", "").trim();
                if (!addr.isBlank() && addr.length() <= 300)
                    candidates.add(new String[]{addr, String.valueOf(baseScore)});
            }
        }

        // Strategy 1: Explicit address-label scan — full document.
        // Previously limited to first 50 lines; now covers the entire document because many
        // Indian CVs place "Address:" inside a personal section that follows work experience.
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i].trim();
            // Strip leading icon/bullet characters (standard symbols + PUA icon fonts)
            String stripped = raw.replaceAll("^[\\u2022\\u2023\\u25AA\\u25CF\\u25C6\\u25E6"
                    + "\\u2316\\u2299\\u29BF\\u27A4\\u2714\\u2756\\u2739\\u2766"
                    + "\\u2605\\u2606\\u260F\\u2709\\u279C\\u279F\\u27B3\\u27B8\\u2794"
                    + "\\u00A9\\u00AE\\uE000-\\uF8FF]\\s*", "")
                    .replaceAll("^(?:\\uD83D\\uDCCD|\\uD83C\\uDFE0|\\uD83D\\uDCCC)\\s*", "");

            // High-confidence address labels — match regardless of document position
            Matcher lm = ADDRESS_LABEL_PATTERN.matcher(stripped);
            if (lm.find()) {
                String addr = lm.group(1).trim()
                        .replaceAll("\\s*[|•·]+.*$", "")
                        .replaceAll("\\s{2,}", " ").trim();
                if (addr.length() < 3) {
                    // Label with no inline value — scan forward (skip blanks) for the real value
                    for (int look = i + 1; look < Math.min(i + 4, lines.length); look++) {
                        String nxt = lines[look].trim();
                        if (nxt.isBlank()) continue;
                        if (DECLARATION_FOOTER_PATTERN.matcher(nxt).matches()) break;
                        if (MAJOR_SECTION_HEADER.matcher(nxt).matches()) break;
                        if (nxt.matches("(?i)^[A-Za-z][A-Za-z\\s]{2,30}[:\\-]\\s*$")) break;
                        addr = nxt; i = look;
                        break;
                    }
                }
                if (addr.length() >= 3) {
                    addr = stripInlineLabelPrefix(addr);
                    addr = appendAddressContinuation(addr, lines, i);
                    addr = addr.replaceAll(",\\s*,", ",").replaceAll("^[,\\s]+|[,\\s]+$", "").trim();
                    if (!addr.isBlank() && addr.length() <= 300)
                        candidates.add(new String[]{addr, "5"});
                }
                continue; // don't re-check location label on the same line
            }

            // Ambiguous labels (Location:, City:, Place:) — only safe outside work-experience blocks.
            // Guard: if a date range appears within ±3 lines, we are in an experience entry; skip.
            Matcher llm = ADDRESS_LOCATION_LABEL_PATTERN.matcher(stripped);
            if (llm.find()) {
                boolean inWorkExp = false;
                for (int k = Math.max(0, i - 3); k <= Math.min(lines.length - 1, i + 3); k++) {
                    if (DATE_RANGE_PATTERN.matcher(lines[k]).find()) { inWorkExp = true; break; }
                }
                if (!inWorkExp) {
                    String addr = llm.group(1).trim()
                            .replaceAll("\\s*[|•·]+.*$", "")
                            .replaceAll("\\s{2,}", " ").trim();
                    if (addr.length() < 3) {
                        for (int look = i + 1; look < Math.min(i + 4, lines.length); look++) {
                            String nxt = lines[look].trim();
                            if (nxt.isBlank()) continue;
                            if (DECLARATION_FOOTER_PATTERN.matcher(nxt).matches()) break;
                            if (MAJOR_SECTION_HEADER.matcher(nxt).matches()) break;
                            if (nxt.matches("(?i)^[A-Za-z][A-Za-z\\s]{2,30}[:\\-]\\s*$")) break;
                            addr = nxt; i = look;
                            break;
                        }
                    }
                    if (addr.length() >= 3) {
                        addr = stripInlineLabelPrefix(addr);
                        addr = appendAddressContinuation(addr, lines, i);
                        addr = addr.replaceAll(",\\s*,", ",").replaceAll("^[,\\s]+|[,\\s]+$", "").trim();
                        if (!addr.isBlank() && addr.length() <= 300)
                            candidates.add(new String[]{addr, "2"});
                    }
                }
            }
        }

        // Strategy 2: Icon-segment scan — works whether the icon is at line-start OR
        // mid-line (e.g. "✉ email@x.com  📍 Mandsaur  ☎ +919xxx" all on one line).
        // Split every line by any icon/PUA character; each resulting segment is a
        // candidate contact-field value.  Skip phone/email/URL/name; the remaining
        // non-empty segment that looks like a location is the address.
        for (int i = 0; i < Math.min(35, lines.length); i++) {
            String raw = lines[i].trim();
            if (raw.isBlank()) continue;

            // Split on any icon character (standard symbols + full PUA range used by icon fonts)
            // We keep a trailing limit=-1 so empty leading segments are included.
            String[] segments = raw.split(
                    "[\\u2022\\u2023\\u25AA\\u25AB\\u25B6\\u25C6\\u25CF\\u25E6"
                    + "\\u2316\\u2299\\u29BF\\u27A4\\u2714\\u2756\\u2739\\u2766"
                    + "\\u2605\\u2606\\u260F\\u2709\\u279C\\u279F\\u27B3\\u27B8\\u2794"
                    + "\\u00A9\\u00AE\\uE000-\\uF8FF]", -1);

            // Only bother if the line actually contained at least one icon character
            if (segments.length < 2) continue;

            for (String seg : segments) {
                String s = seg.replaceAll("^[\\s|,·•]+|[\\s|,·•]+$", "").trim();
                if (s.isBlank() || s.length() > 100) continue;
                // Strip any secondary label prefix embedded by the CV template
                // e.g., "📍 From: Banswara, Raj." → after icon split → "From: Banswara, Raj." → "Banswara, Raj."
                s = stripInlineLabelPrefix(s);
                if (s.isBlank() || s.length() > 100) continue;
                // Skip phone, email, URL, LinkedIn/GitHub
                if (PHONE_PATTERN.matcher(s).find()) continue;
                if (EMAIL_PATTERN.matcher(s).find()) continue;
                if (s.toLowerCase().contains("http") || s.toLowerCase().contains("linkedin")
                        || s.toLowerCase().contains("github")) continue;
                // Skip ALL-CAPS name (e.g. "AMAN PATHAN")
                if (s.matches("[A-Z][A-Z\\s\\.]{2,40}")) continue;
                // Skip designation-only segments
                if (DESIGNATION_PATTERN.matcher(s).find() && !s.matches(".*\\d.*")
                        && !ADDRESS_KEYWORD_PATTERN.matcher(s).find()) continue;
                // Skip education content
                if (EDUCATION_CONTENT_PATTERN.matcher(s).matches()) continue;
                if (INSTITUTION_PATTERN.matcher(s).find()
                        && !ADDRESS_KEYWORD_PATTERN.matcher(s).find()) continue;

                boolean hasAddrKeyword = ADDRESS_KEYWORD_PATTERN.matcher(s).find();
                boolean hasCity        = CITY_PATTERN.matcher(s).find();
                boolean hasState       = STATE_PATTERN.matcher(s).find();
                boolean hasPin         = s.matches(".*\\b\\d{6}\\b.*");
                boolean placelike      = s.length() <= 50
                        && s.matches("[A-Za-z0-9][A-Za-z0-9\\s,\\-\\.]{1,48}");

                if (hasAddrKeyword || hasCity || hasState || hasPin || placelike) {
                    // placelike-only: reject person names and lone section-header words
                    if (!hasAddrKeyword && !hasCity && !hasState && !hasPin) {
                        String[] nw = s.split("\\s+");
                        if (nw.length == 1) continue; // single word — too ambiguous
                        boolean isProbablyName = nw.length <= 3
                                && !s.matches(".*\\d.*")
                                && java.util.Arrays.stream(nw).allMatch(w -> w.length() >= 1 && Character.isUpperCase(w.charAt(0)))
                                && java.util.Arrays.stream(nw).noneMatch(this::isNameStopToken)
                                && !DESIGNATION_PATTERN.matcher(s).find();
                        if (isProbablyName) continue;
                    }
                    String addr = s.replaceAll(",\\s*,", ",")
                                   .replaceAll("^[,\\s]+|[,\\s]+$", "").trim();
                    if (!addr.isBlank()) candidates.add(new String[]{addr, "2"});
                }
            }
        }

        // Strategy 3: Contact-block proximity search (no icon extracted at all).
        // When the CV icon is an embedded image PDFBox can't extract, the address line
        // is just plain text near the phone/email.  Scan ±6 lines around the contact
        // anchor; education tables are never that close, so city/state matching is safe.
        int phoneIdx = -1, emailIdx = -1;
        for (int i = 0; i < Math.min(35, lines.length); i++) {
            String t = lines[i].trim();
            if (phoneIdx < 0 && PHONE_PATTERN.matcher(t).find()) phoneIdx = i;
            if (emailIdx < 0 && EMAIL_PATTERN.matcher(t).find()) emailIdx = i;
        }
        if (phoneIdx >= 0 || emailIdx >= 0) {
            int anchor = (phoneIdx >= 0 && emailIdx >= 0)
                    ? Math.max(phoneIdx, emailIdx)
                    : (phoneIdx >= 0 ? phoneIdx : emailIdx);
            int start = Math.max(0, anchor - 4);
            int end   = Math.min(lines.length - 1, anchor + 6);

            // The very first non-blank line is always the candidate's name — never the address.
            int firstNonBlank = -1;
            for (int k = 0; k < lines.length; k++) {
                if (!lines[k].trim().isBlank()) { firstNonBlank = k; break; }
            }

            for (int i = start; i <= end; i++) {
                if (i == firstNonBlank) continue;   // skip name line
                String raw = lines[i].trim();
                if (raw.isBlank()) continue;
                // Strip any leading icon character before evaluating content
                String t = raw.replaceAll(
                        "^[\\u2022\\u25CF\\u25C6\\u2316\\u2299\\u29BF\\u27A4\\u2714"
                        + "\\u2605\\u2606\\u260F\\u00A9\\u00AE\\u279C\\u2794\\uE000-\\uF8FF]\\s*", "")
                        .replaceAll("^(?:\\uD83D\\uDCCD|\\uD83C\\uDFE0|\\uD83D\\uDCCC)\\s*", "")
                        .trim();
                // Strip any secondary label prefix the CV template embedded on the same line
                // e.g. "📍 From: Banswara, Raj." → after icon strip → "From: Banswara, Raj." → "Banswara, Raj."
                t = stripInlineLabelPrefix(t);
                if (t.isBlank() || t.length() > 120) continue;
                if (PHONE_PATTERN.matcher(t).find()) continue;
                if (EMAIL_PATTERN.matcher(t).find()) continue;
                if (t.toLowerCase().contains("http") || t.toLowerCase().contains("linkedin")
                        || t.toLowerCase().contains("github")) continue;
                // Skip all-caps lines (name or section header)
                if (t.matches("[A-Z][A-Z\\s\\.]{2,40}")) continue;
                if (DESIGNATION_PATTERN.matcher(t).find() && !t.matches(".*\\d.*")
                        && !ADDRESS_KEYWORD_PATTERN.matcher(t).find()) continue;
                if (EDUCATION_CONTENT_PATTERN.matcher(t).matches()) continue;
                if (INSTITUTION_PATTERN.matcher(t).find()
                        && !ADDRESS_KEYWORD_PATTERN.matcher(t).find()) continue;

                boolean hasAddrKeyword = ADDRESS_KEYWORD_PATTERN.matcher(t).find();
                boolean hasCity        = CITY_PATTERN.matcher(t).find();
                boolean hasState       = STATE_PATTERN.matcher(t).find();
                boolean hasPin         = t.matches(".*\\b\\d{6}\\b.*");
                // shortPlaceName: short alphabetic line close to phone/email — likely a city/town
                boolean shortPlaceName = t.length() <= 50
                        && t.matches("[A-Za-z][A-Za-z\\s,\\-\\.]{1,48}");

                if (hasAddrKeyword || hasCity || hasState || hasPin || shortPlaceName) {
                    // shortPlaceName-only: reject candidate names and lone section-header words
                    // to avoid returning "Divyank Soni" or "Objective" as an address
                    if (!hasAddrKeyword && !hasCity && !hasState && !hasPin) {
                        String[] nw = t.split("\\s+");
                        if (nw.length == 1) continue; // single word — section header or ambiguous noun
                        boolean isProbablyName = nw.length <= 3
                                && !t.matches(".*\\d.*")
                                && java.util.Arrays.stream(nw).allMatch(w -> w.length() >= 1 && Character.isUpperCase(w.charAt(0)))
                                && java.util.Arrays.stream(nw).noneMatch(this::isNameStopToken)
                                && !DESIGNATION_PATTERN.matcher(t).find();
                        if (isProbablyName) continue;
                    }
                    String addr = appendAddressContinuation(t, lines, i);
                    addr = addr.replaceAll(",\\s*,", ",")
                               .replaceAll("^[,\\s]+|[,\\s]+$", "").trim();
                    if (!addr.isBlank()) candidates.add(new String[]{addr, "2"});
                }
            }
        }

        // Return the highest-scoring candidate — keyword-rich permanent addresses beat city-only matches.
        return candidates.stream()
                .max(Comparator.comparingInt(c -> scoreAddress(c[0], Integer.parseInt(c[1]))))
                .map(c -> c[0])
                .orElse(null);
    }

    private String extractExperienceYears(String text) {
        // Pattern 1: "3 years experience", "5+ years of experience"
        Matcher m = EXP_YEARS_PATTERN.matcher(text);
        if (m.find()) return m.group(1) + " years";
        // Pattern 2: "X+ years" anywhere
        Matcher m2 = Pattern.compile("(\\d+)\\s*\\+\\s*years?", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m2.find()) return m2.group(1) + "+ years";
        // Pattern 3: Indian CV duration format "1.2 Year - Company" (1-2 digit, not a 4-digit year)
        Matcher m3 = Pattern.compile("(\\d{1,2}(?:\\.\\d+)?)\\s*\\+?\\s*[Yy]ears?(?=\\s|[-–]|,|$)",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (m3.find()) return m3.group(1) + " years";
        // Pattern 4: duration in months — "6 months experience"
        Matcher m4 = Pattern.compile("(\\d+)\\s*[Mm]onths?\\s+(?:of\\s+)?experience", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m4.find()) return m4.group(1) + " months";
        return null;
    }

    // ── Education extraction ─────────────────────────────────────────────────

    // Matches a standalone education section heading on its own line.
    private static final Pattern EDUCATION_SECTION_HEADER = Pattern.compile(
            "^(?:education(?:al)?(?:\\s+(?:details?|qualifications?|background|history|summary|information|profile))?|"
            + "academic(?:s|\\s+(?:details?|background|qualifications?|profile|history|credentials?))?|"
            + "qualifications?(?:\\s+(?:details?|summary))?|"
            + "educational\\s+(?:background|profile|credentials?|history|qualifications?))\\s*[:\\-]?$",
            Pattern.CASE_INSENSITIVE);

    // Matches the heading of any major section that follows education.
    private static final Pattern EDUCATION_NEXT_SECTION = Pattern.compile(
            "^(?:experience|work(?:\\s+experience)?|employment|professional(?:\\s+experience)?|"
            + "internship|projects?|skills?|technical\\s+skills?|certifications?|"
            + "achievements?|references?|declarations?|languages?|objectives?|"
            + "summar(?:y|ies)|profiles?|publications?|awards?|training|"
            + "strengths?|interests?|hobbies?|activities|personal(?:\\s+details?|\\s+info(?:rmation)?)?)\\s*[:\\-]?$",
            Pattern.CASE_INSENSITIVE);

    // Software/tool product names that must never be treated as degrees.
    // Short degree abbreviations like "MS" (M.S.) can accidentally match "MS Office",
    // "MS Word", etc., so we reject any extracted "degree" that contains these words.
    private static final Set<String> SOFTWARE_NOT_DEGREE = new HashSet<>(Arrays.asList(
            "office", "word", "excel", "powerpoint", "access", "outlook", "teams",
            "onenote", "visio", "windows", "dos", "tally", "sap", "oracle", "erp",
            "crm", "quickbooks", "photoshop", "autocad", "corel", "illustrator"
    ));

    // Subject words and conjunctions that may appear between a degree abbreviation
    // and the institution name.  Any word NOT in this set signals where the
    // institution's proper-noun prefix begins (e.g. "Vikram" in "BCOM Vikram University").
    private static final Set<String> DEGREE_SPECIALIZATIONS = new HashSet<>(Arrays.asList(
            "in", "of", "and", "the",
            "finance", "commerce", "science", "arts", "technology", "engineering",
            "computer", "computers", "management", "marketing", "accounting", "law",
            "education", "medicine", "medical", "psychology", "sociology", "economics",
            "statistics", "mathematics", "maths", "physics", "chemistry", "biology",
            "history", "geography", "english", "literature", "business", "administration",
            "pharmacy", "design", "architecture", "agriculture", "nursing", "information",
            "communication", "human", "resources", "industrial", "mechanical", "electrical",
            "civil", "electronics", "biotechnology", "banking", "systems", "software",
            "data", "network", "security", "digital", "applied", "general",
            "it", "cs", "cse", "ece", "eee", "mba", "bca", "mca",
            "international", "applied", "pure", "corporate", "operational"
    ));

    private String extractEducation(String text) {
        String[] lines = text.split("\\r?\\n");

        // Pass 1 — section-based: find the education/academic section header and
        // extract degrees only from within that section.  This prevents false
        // positives from experience or objective paragraphs that happen to mention
        // a degree name in passing ("leverage my MBA skills…").
        List<String> sectionDegrees = extractDegreesFromSection(lines);
        if (!sectionDegrees.isEmpty()) {
            return String.join(" | ", sectionDegrees);
        }

        // Pass 2 — full-document fallback: for CVs without explicit section headers.
        // Degree keyword must appear within the first 5 characters of the line (at or
        // near line start) to avoid grabbing random mid-sentence mentions.
        List<String> fallback = new ArrayList<>();
        for (String line : lines) {
            String deg = extractDegreeFromLine(line.trim(), true);
            if (deg != null && fallback.stream().noneMatch(d -> d.equalsIgnoreCase(deg))) {
                fallback.add(deg);
            }
            if (fallback.size() == 4) break;
        }
        return fallback.isEmpty() ? null : String.join(" | ", fallback);
    }

    private List<String> extractDegreesFromSection(String[] lines) {
        List<String> degrees = new ArrayList<>();
        boolean inSection = false;

        for (String line : lines) {
            String t = line.trim();
            if (t.isBlank()) continue;

            if (EDUCATION_SECTION_HEADER.matcher(t).matches()) {
                inSection = true;
                continue;
            }
            if (inSection && EDUCATION_NEXT_SECTION.matcher(t).matches()) break;
            if (!inSection) continue;

            // Skip lines that are clearly URL fragments, not education content.
            // PDFBox splits long URLs across lines; the second fragment (e.g. "pathan-823963250")
            // doesn't start with "http" so normal URL checks miss it.
            if (t.matches(".*\\b[a-zA-Z]+-\\d{5,}\\b.*")) continue;         // LinkedIn slug fragment
            if (t.toLowerCase().contains("linkedin") || t.toLowerCase().contains("github")) continue;

            // Within the section: lenient (degree need not be at line start —
            // table-merged lines can have bullet or leading spaces)
            String deg = extractDegreeFromLine(t, false);
            if (deg != null && degrees.stream().noneMatch(d -> d.equalsIgnoreCase(deg))) {
                degrees.add(deg);
            }
            if (degrees.size() == 4) break;
        }
        return degrees;
    }

    // Extracts a clean degree string from a single line.
    // strict=true  → degree keyword must appear within first 5 chars (full-doc scan)
    // strict=false → degree may appear anywhere (inside a known education section)
    private String extractDegreeFromLine(String t, boolean strict) {
        if (t == null || t.length() < 2 || t.length() > 200) return null;

        // Strip leading bullet / arrow characters that PDFs render before list items
        // (➤ ► ▸ → • · ✓ ✗ -, etc.) so "➤ B.Com – From …" becomes "B.Com – From …"
        t = t.replaceAll("^[\\s\\u2022\\u00b7\\u25b8\\u25ba\\u25cf\\u27a4\\u2714\\u2716\\u27a2\\u279c➤►▸→•·\\-]+", "").trim();
        if (t.length() < 2) return null;

        Matcher dm = DEGREE_PATTERN.matcher(t);
        if (!dm.find()) return null;

        // Word-boundary guard: the matched degree token must not be embedded inside a
        // longer word.  Without this, "BE" matches "Be" in "Behind", and "ME" matches
        // "me" in "Resume", causing false positives in education output.
        int mStart = dm.start(), mEnd = dm.end();
        if (mStart > 0 && Character.isLetterOrDigit(t.charAt(mStart - 1))) return null;
        if (mEnd < t.length() && Character.isLetterOrDigit(t.charAt(mEnd))) return null;

        if (strict && dm.start() > 5) return null; // skip mid-sentence degree mentions

        String clean;
        Matcher instM = EDUCATION_INSTITUTION_PATTERN.matcher(t);

        if (instM.find()) {
            // Institution keyword on same line (table-merged or inline format):
            //   "BCOM Vikram University 2022 73"
            //   "B.Tech Computer Science, Pune University, 2019"
            // 1. Take text before the institution keyword.
            // 2. Strip city-name prefixes (e.g. "Pune" before "University").
            // 3. Re-locate degree within the cleaned prefix text.
            // 4. Walk forward from degree collecting only known subject words.
            String beforeInst = t.substring(0, instM.start());
            beforeInst = CITY_PATTERN.matcher(beforeInst).replaceAll("");
            beforeInst = beforeInst.replaceAll("[,;\\-–()|]+", " ")
                                   .replaceAll("\\s{2,}", " ").trim();

            Matcher dm2 = DEGREE_PATTERN.matcher(beforeInst);
            if (!dm2.find()) {
                clean = dm.group().trim();
            } else {
                // Word-boundary check on the re-matched position too
                int s2 = dm2.start(), e2 = dm2.end();
                if (s2 > 0 && Character.isLetterOrDigit(beforeInst.charAt(s2 - 1))) return null;
                if (e2 < beforeInst.length() && Character.isLetterOrDigit(beforeInst.charAt(e2))) return null;
                String afterDeg = beforeInst.substring(e2).trim();
                String spec = buildDegreeSpecialization(afterDeg);
                clean = spec.isEmpty() ? dm2.group().trim()
                                       : dm2.group().trim() + " " + spec;
            }
        } else {
            // No institution keyword on this line.
            // Start from the degree keyword itself (mStart) to strip any prefix text that
            // PDFBox merged from an adjacent column — e.g. "ENGLISH BACHELOR OF COMMERCE"
            // where "ENGLISH" is the medium-of-instruction column merged with the degree column.
            // When the degree is at position 0 this is a no-op.
            String fromDeg = t.substring(mStart);
            clean = fromDeg
                    .replaceAll("[,\\s\\-–]+(?:19|20)\\d{2}.*$", "")          // year + trailing
                    .replaceAll("[,\\s]+\\d+(?:\\.\\d+)?\\s*%.*$", "")        // percentage
                    .replaceAll("\\s*[–—]\\s+.*$", "")                        // em-dash: "B.Com – From Vikram…"
                    .replaceAll("(?i)\\s+(?:from|at|through|via)\\s+.*$", "") // "From IIT Bombay"
                    .replaceAll("[,;\\-–]+$", "")                             // trailing punctuation
                    .trim();
            if (!DEGREE_PATTERN.matcher(clean).find()) return null;
        }

        if (clean.length() < 2) return null;

        // Preserve "(DCA)" style abbreviations in parens; only strip a lone trailing ")"
        // that has no matching "(", which can occur when parentheses span a table cell boundary.
        if (clean.endsWith(")") && !clean.contains("(")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }

        // Reject software/tool names that slip through via short degree abbreviations
        // (e.g. "MS" matching "MS Office", "MS Excel", "Tally ERP")
        for (String w : clean.toLowerCase().split("\\s+")) {
            if (SOFTWARE_NOT_DEGREE.contains(w)) return null;
        }

        return clean.length() >= 2 ? clean : null;
    }

    // Walks forward word-by-word through the text after the degree abbreviation.
    // Collects only words present in DEGREE_SPECIALIZATIONS (academic subjects /
    // conjunctions).  Stops at the first unrecognised word so that university
    // proper-noun prefixes ("Vikram", "Mandsaur") are excluded.
    private String buildDegreeSpecialization(String afterDeg) {
        if (afterDeg == null || afterDeg.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String word : afterDeg.trim().split("\\s+")) {
            String key = word.replaceAll("[^a-zA-Z&]", "").toLowerCase();
            if (key.isEmpty()) continue;
            if (!DEGREE_SPECIALIZATIONS.contains(key)) break;
            if (sb.length() > 0) sb.append(" ");
            sb.append(word.replaceAll("[^a-zA-Z&]", ""));
        }
        return sb.toString().replaceAll("(?i)\\s*(in|of|and)$", "").trim();
    }

    private String extractDesignation(String text) {
        String[] lines = text.split("\\r?\\n");
        // Strategy 1: designation on its own line near top (first 50 lines)
        for (int i = 0; i < Math.min(50, lines.length); i++) {
            String line = lines[i].trim();
            if (line.length() < 5 || line.length() > 80) continue;
            Matcher dm = DESIGNATION_PATTERN.matcher(line);
            if (!dm.find()) continue;
            // Word-boundary guard: reject partial matches like "Lead" inside "Leadership"
            if (dm.end() < line.length() && Character.isLetter(line.charAt(dm.end()))) continue;
            if (EMAIL_PATTERN.matcher(line).find()) continue;
            if (line.matches(".*\\d{4}.*")) continue;
            if (line.toLowerCase().contains("university") || line.toLowerCase().contains("college")) continue;
            return line;
        }
        // Strategy 2: designation in parentheses in duration format "X Year - Company (Designation)"
        Matcher dm = DURATION_COMPANY_PATTERN.matcher(text);
        while (dm.find()) {
            String desig = dm.group(2);
            if (desig != null) {
                desig = desig.trim();
                if (desig.length() >= 3 && desig.length() <= 80) {
                    return desig;
                }
            }
        }
        return null;
    }

    private String extractCurrentCompany(String text) {
        String[] lines = text.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            if (DATE_RANGE_PATTERN.matcher(lines[i]).find()) {
                // Check if context around this date range looks like an education section.
                // If any of the 4 surrounding lines contain a degree keyword or "education" heading,
                // skip this date range entirely — it belongs to the education timeline, not work history.
                boolean educationContext = false;
                for (int k = Math.max(0, i - 4); k <= Math.min(lines.length - 1, i + 1); k++) {
                    String ctx = lines[k].trim().toLowerCase();
                    if (DEGREE_PATTERN.matcher(ctx).find()
                            || ctx.equals("education") || ctx.startsWith("education ")
                            || EDUCATION_INSTITUTION_PATTERN.matcher(ctx).find()) {
                        educationContext = true;
                        break;
                    }
                }
                if (educationContext) break;

                // Walk backwards up to 3 lines to find company name
                for (int j = i - 1; j >= Math.max(0, i - 3); j--) {
                    String candidate = lines[j].trim();
                    // Strip leading bullet markers (PDFBox may render • as @)
                    String clean = candidate.replaceFirst("^[@•*\\-]\\s*", "");
                    if (clean.length() > 3 && clean.length() < 70
                            && !DESIGNATION_PATTERN.matcher(clean).find()
                            && !EMAIL_PATTERN.matcher(clean).find()
                            && !PHONE_PATTERN.matcher(clean).find()
                            && !clean.matches("\\d.*")
                            && !DEGREE_PATTERN.matcher(clean).find()
                            && !EDUCATION_INSTITUTION_PATTERN.matcher(clean).find()) {
                        return clean;
                    }
                }
                break; // Only check the first date range (most recent role)
            }
        }
        // Strategy 2: duration format "1.2 Year - Company (Designation)"
        Matcher dm = DURATION_COMPANY_PATTERN.matcher(text);
        if (dm.find()) {
            String company = dm.group(1).trim();
            // Strip leading bullet markers (? • @ * -)
            company = company.replaceFirst("^[?•@*\\-\\s]+", "").trim();
            if (company.length() > 2 && company.length() < 70) {
                return company;
            }
        }
        return null;
    }

    private String extractNameFromText(String text) {
        if (text == null) return null;
        String[] lines = text.split("\\r?\\n");

        // Strategy A (highest confidence): Explicit "Name:" or "Full Name:" label.
        // Handles CVs that write "Name: Ashish Nalwade" in their personal-details section.
        Pattern nameLabelPat = Pattern.compile(
                "^(?:full\\s+)?name\\s*[:\\-]\\s*(.{2,60})$", Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < Math.min(25, lines.length); i++) {
            Matcher nm = nameLabelPat.matcher(lines[i].trim());
            if (!nm.find()) continue;
            String candidate = nm.group(1).trim().replaceAll("[^A-Za-z\\s.]", "").trim();
            String[] cw = candidate.split("\\s+");
            if (cw.length >= 2 && cw.length <= 4
                    && Stream.of(cw).noneMatch(this::isNameStopToken)
                    && !DESIGNATION_PATTERN.matcher(candidate).find()) {
                return toTitleCase(candidate);
            }
        }

        // Strategy B: Consecutive lines each with exactly one ALL-CAPS token → combine to first+last.
        // Handles CVs where the name is vertically split: "ASHISH\nNALWADE".
        List<String> capsTokens = new ArrayList<>();
        for (int i = 0; i < Math.min(12, lines.length); i++) {
            List<String> lineTokens = new ArrayList<>();
            for (String raw : lines[i].split("\\s+")) {
                String token = raw.replaceAll("[^A-Za-z]", "");
                if (token.length() >= 3
                        && token.equals(token.toUpperCase())
                        && !isNameStopToken(token)) {
                    lineTokens.add(token);
                }
            }
            if (lineTokens.size() == 1) {
                capsTokens.add(lineTokens.get(0));
                if (capsTokens.size() == 2) break;
            } else if (lineTokens.size() > 1) {
                capsTokens.clear(); // multi-token line — let Strategy C handle it
                break;
            }
            // Zero tokens (blank/noise line) — keep accumulating
        }
        if (capsTokens.size() == 2 && !capsTokens.get(0).equalsIgnoreCase(capsTokens.get(1))) {
            return toTitleCase(capsTokens.get(0) + " " + capsTokens.get(1));
        }

        // Strategy C: ALL-CAPS multi-word line (first 12 lines).
        // Pre-strips any embedded designation before collecting name words, so that
        // "ASHISH NALWADE SOFTWARE ENGINEER" → DESIGNATION_PATTERN trims to "ASHISH NALWADE".
        // Then collects words left-to-right, stopping at any NAME_STOP_WORD.
        for (int i = 0; i < Math.min(12, lines.length); i++) {
            String line = lines[i].trim();
            if (!line.matches("[A-Z][A-Z\\s.]{2,}")) continue;
            // Remove designation suffix (e.g. "SOFTWARE ENGINEER") from the right
            String effective = line;
            Matcher dm = DESIGNATION_PATTERN.matcher(line);
            if (dm.find()) effective = line.substring(0, dm.start()).trim();
            String[] words = effective.split("\\s+");
            List<String> nameWords = new ArrayList<>();
            for (String w : words) {
                if (w.isBlank()) continue;
                if (isNameStopToken(w) || nameWords.size() == 3) break;
                nameWords.add(w);
            }
            if (nameWords.size() >= 2) return toTitleCase(String.join(" ", nameWords));
        }

        // Strategy E: Email-hint guided full-document name search (runs BEFORE the line scan).
        //
        // The email local-part often encodes the person's name parts, even with trailing digits:
        //   "gupta.charchit456@gmail.com" → strip digits → ["gupta","charchit"]
        //   "john.doe@gmail.com"           → ["john","doe"]
        //
        // Once we have the parts, we scan the ENTIRE document for the first line that contains
        // ALL of them — this recovers the correct word order and bypasses the positional
        // limitation of Strategy D (which is fooled when a two-column PDF merges content like
        // "Consumer Durable Goods" into the header area before the actual name line).
        for (int i = 0; i < Math.min(30, lines.length); i++) {
            String ln = lines[i];
            if (!ln.contains("@")) continue;
            Matcher em = Pattern.compile("[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}").matcher(ln);
            if (!em.find()) continue;
            String fullEmail = em.group();
            String local = fullEmail.substring(0, fullEmail.indexOf('@'));
            if (!local.contains(".") && !local.contains("_") && !local.contains("-")) continue;
            List<String> parts = Arrays.stream(local.split("[._\\-]+"))
                    .map(p -> p.replaceAll("\\d+$", ""))   // strip trailing digits: "charchit456" → "charchit"
                    .filter(p -> p.matches("[a-zA-Z]{2,}")
                            && !isNameStopToken(p))
                    .collect(Collectors.toList());
            if (parts.size() < 2) continue;
            // Scan full document for the earliest line containing ALL email name parts
            for (String candidate : lines) {
                String ct = candidate.trim();
                if (ct.isBlank()) continue;
                String ctLower = ct.toLowerCase();
                if (parts.stream().noneMatch(p -> ctLower.contains(p.toLowerCase()))) continue;
                if (parts.stream().allMatch(p -> ctLower.contains(p.toLowerCase()))) {
                    String[] cw = ct.split("\\s+");
                    if (cw.length >= 2 && cw.length <= 4
                            && ct.matches("[A-Za-z][A-Za-z\\s.]{1,40}")
                            && Stream.of(cw).noneMatch(this::isNameStopToken)
                            && !DESIGNATION_PATTERN.matcher(ct).find()) {
                        return ct;
                    }
                }
            }
            // No matching line found — return name parts in email order as fallback
            return parts.stream()
                    .map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        }

        // Strategy D: First proper-name line that appears BEFORE the contact info block.
        // Key constraint: the name is always ABOVE the email/phone in the CV.
        // Scanning past contact info risks picking up experience/objective text as the name.
        int firstContactLine = Integer.MAX_VALUE;
        for (int i = 0; i < lines.length; i++) {
            String tl = lines[i].trim();
            if (EMAIL_PATTERN.matcher(tl).find() || PHONE_PATTERN.matcher(tl).find()) {
                firstContactLine = i;
                break;
            }
        }
        // Collect non-blank lines strictly before the first email/phone line (max 10)
        List<String> preContactLines = new ArrayList<>();
        for (int i = 0; i < Math.min(firstContactLine, lines.length); i++) {
            String tl = lines[i].trim();
            if (!tl.isBlank()) preContactLines.add(tl);
            if (preContactLines.size() == 10) break;
        }
        // Fallback: if no contact info found in first 35 lines, use first 8 non-blank lines
        if (preContactLines.isEmpty()) {
            for (int i = 0; i < Math.min(35, lines.length); i++) {
                String tl = lines[i].trim();
                if (!tl.isBlank()) preContactLines.add(tl);
                if (preContactLines.size() == 8) break;
            }
        }
        for (String line : preContactLines) {
            // Strip " – Designation" suffix (two-column PDFBox merge artifact)
            String stripped = line.replaceAll("(?i)\\s*[-–]\\s+.+$", "").trim();
            // Strip designation suffix matched by DESIGNATION_PATTERN
            Matcher dm = DESIGNATION_PATTERN.matcher(stripped);
            if (dm.find()) stripped = stripped.substring(0, dm.start()).trim();
            // Right-trim NAME_STOP_WORDs one by one
            List<String> wlist = new ArrayList<>(Arrays.asList(stripped.split("\\s+")));
            wlist.removeIf(String::isBlank);
            while (!wlist.isEmpty()
                    && isNameStopToken(wlist.get(wlist.size() - 1))) {
                wlist.remove(wlist.size() - 1);
            }
            // Left-trim NAME_STOP_WORDs too
            while (!wlist.isEmpty()
                    && isNameStopToken(wlist.get(0))) {
                wlist.remove(0);
            }
            stripped = String.join(" ", wlist).trim();
            if (stripped.isEmpty()) continue;
            String[] words = stripped.split("\\s+");
            // Must be 2-3 words, no digits, letters only, all words Title-Cased (proper nouns)
            if (words.length < 2 || words.length > 3) continue;
            if (!stripped.matches("[A-Za-z][A-Za-z\\s.]{1,35}")) continue;
            if (stripped.matches(".*\\d.*")) continue;
            // Every word must start with uppercase (Title Case = proper noun = name)
            boolean allTitleCase = Stream.of(words)
                    .allMatch(w -> w.length() >= 1 && Character.isUpperCase(w.charAt(0)));
            if (!allTitleCase) continue;
            if (Stream.of(words).anyMatch(this::isNameStopToken)) continue;
            if (DESIGNATION_PATTERN.matcher(stripped).find()) continue;
            return stripped;
        }

        // Strategy F: LinkedIn profile slug → name parts.
        // Handles two-column OCR splits where the URL is truncated mid-slug.
        Pattern linkedInPat = Pattern.compile(
                "linkedin\\.com/in/([a-zA-Z][a-zA-Z0-9\\-]*)", Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < lines.length; i++) {
            Matcher lm = linkedInPat.matcher(lines[i]);
            if (!lm.find()) continue;
            String slug = lm.group(1);
            String afterSlug = lines[i].substring(lm.end()).trim();
            boolean likelyCutOff = !afterSlug.isEmpty() || slug.length() < 5;
            if (likelyCutOff) {
                for (int j = i + 1; j <= Math.min(i + 3, lines.length - 1); j++) {
                    String next = lines[j].trim();
                    if (next.isEmpty()) continue;
                    if (next.matches("[a-zA-Z0-9\\-]+")) slug = slug + next;
                    break;
                }
            }
            List<String> nameParts = Arrays.stream(slug.split("-"))
                    .filter(p -> !p.isBlank() && p.matches("[a-zA-Z]+"))
                    .collect(Collectors.toList());
            if (nameParts.size() >= 2) {
                return nameParts.stream()
                        .map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1).toLowerCase())
                        .collect(Collectors.joining(" "));
            }
        }

        // Strategy G: Single-word (mononym) fallback — many Indian CVs list only a first
        // name above the contact block, with no surname anywhere in the document. Runs
        // last because a lone word is weaker evidence than any multi-word match above,
        // but it is still read from the resume body itself, never from the file name.
        for (String line : preContactLines) {
            String[] words = line.trim().split("\\s+");
            if (words.length != 1) continue;
            String word = words[0];
            if (!word.matches("[A-Z][a-zA-Z]{2,19}")) continue;
            if (isNameStopToken(word)) continue;
            if (DESIGNATION_PATTERN.matcher(word).find()) continue;
            return toTitleCase(word);
        }

        return null;
    }

    private String toTitleCase(String s) {
        return Arrays.stream(s.trim().split("\\s+"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private String normalizeStateName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toLowerCase();
        String mapped = STATE_NORMALIZE_MAP.get(key);
        if (mapped != null) return mapped;
        if (key.equals("india")) return "India";
        return toTitleCase(raw.trim());
    }

    /**
     * Parses a finalized full address string into individual components.
     * Returns a map with keys: addressStreet, addressArea, addressLandmark,
     * addressCity, addressDistrict, addressState, addressPostalCode, addressCountry.
     * Only populated keys are included — missing components are absent from the map.
     */
    private Map<String, String> parseAddressComponents(String fullAddress) {
        Map<String, String> comp = new LinkedHashMap<>();
        if (fullAddress == null || fullAddress.isBlank()) return comp;

        // 1. Extract PIN code
        Matcher pm = Pattern.compile("\\b(\\d{6})\\b").matcher(fullAddress);
        if (pm.find()) comp.put("addressPostalCode", pm.group(1));

        // 2. Extract state (take rightmost match) and normalize
        Matcher sm = STATE_PATTERN.matcher(fullAddress);
        String rawState = null;
        while (sm.find()) rawState = sm.group();
        if (rawState != null) {
            String norm = normalizeStateName(rawState);
            if ("India".equalsIgnoreCase(norm)) {
                comp.put("addressCountry", "India");
            } else {
                comp.put("addressState", norm);
                comp.put("addressCountry", "India");
            }
        }
        if (!comp.containsKey("addressCountry")
                && fullAddress.matches("(?i).*\\bIndia\\b.*")) {
            comp.put("addressCountry", "India");
        }

        // 3. Split original by comma; clean each segment of PIN, state, country, parens
        String[] rawParts = fullAddress.split(",");
        List<String> parts = new ArrayList<>();
        for (String raw : rawParts) {
            String p = STATE_PATTERN.matcher(
                    raw.replaceAll("\\b\\d{6}\\b", "")
                       .replaceAll("(?i)\\bIndia\\b", "")
                       .replaceAll("\\([^)]*\\)", "")
            ).replaceAll("")
             .replaceAll("\\s{2,}", " ")
             .replaceAll("^[\\s,;\\-–]+|[\\s,;\\-–]+$", "")
             .trim();
            if (!p.isBlank() && p.length() >= 2) parts.add(p);
        }
        if (parts.isEmpty()) return comp;

        // 4. Classify segments by keyword patterns
        String street = null, area = null, landmark = null, city = null, district = null;
        List<String> unclassified = new ArrayList<>();

        for (String part : parts) {
            String lower = part.toLowerCase();

            // Landmark: near / opp / opposite / behind / beside / next to
            if (lower.matches(".*\\b(?:near|opp\\.?|opposite|behind|beside|next\\s+to|in\\s+front|adj\\.?|adjacent)\\b.*")) {
                if (landmark == null) { landmark = part; continue; }
            }

            // City: CITY_PATTERN match
            Matcher cm = CITY_PATTERN.matcher(part);
            if (cm.find() && city == null) {
                city = cm.group();
                continue;
            }

            // District: district / tehsil / taluka keyword
            if (lower.matches(".*\\b(?:dist\\.?|district|tehsil|taluka|taluk|subdivision)\\b.*") && district == null) {
                district = part;
                continue;
            }

            // Street: flat / house / plot / floor / wing / door + digit, or leading digit
            if (part.matches(".*\\b(?:flat|house|h\\.?no\\.?|h/no|plot|floor|wing|door|room)\\b.*")
                    || part.matches("^\\d.*")
                    || part.matches("^[A-Za-z]-?\\d.*")) {
                if (street == null) { street = part; continue; }
            }

            // Area: colony / nagar / sector / phase / society / road / street / lane etc.
            if (part.matches("(?i).*\\b(?:colony|nagar|nagara|vihar|enclave|society|sector|phase|" +
                              "layout|extension|road|street|marg|chowk|lane|mohalla|locality|" +
                              "area|ward|gali|bazaar|market|garden|park|peth|wada|puram|ganj|" +
                              "complex|residency|tower|heights?)\\b.*")) {
                if (area == null) { area = part; continue; }
            }

            unclassified.add(part);
        }

        // 5. Positional assignment for unclassified segments
        for (String p : unclassified) {
            if (street == null)                              { street = p; continue; }
            if (area == null)                               { area = p; continue; }
            if (city == null && p.length() <= 35)           { city = p; }
        }

        // 6. Store non-null results (title-cased)
        if (street   != null && !street.isBlank())   comp.put("addressStreet",   toTitleCase(street.trim()));
        if (area     != null && !area.isBlank())     comp.put("addressArea",     toTitleCase(area.trim()));
        if (landmark != null && !landmark.isBlank()) comp.put("addressLandmark", toTitleCase(landmark.trim()));
        if (city     != null && !city.isBlank())     comp.put("addressCity",     toTitleCase(city.trim()));
        if (district != null && !district.isBlank()) comp.put("addressDistrict", toTitleCase(district.trim()));

        return comp;
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private AtsCandidateDTO toCandidateDTO(AtsCandidate c, boolean full) {
        AtsCandidateDTO.AtsCandidateDTOBuilder b = AtsCandidateDTO.builder()
                .id(c.getId())
                .candidateId(c.getCandidateId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .address(c.getAddress())
                .addressStreet(c.getAddressStreet())
                .addressArea(c.getAddressArea())
                .addressLandmark(c.getAddressLandmark())
                .addressCity(c.getAddressCity())
                .addressDistrict(c.getAddressDistrict())
                .addressState(c.getAddressState())
                .addressPostalCode(c.getAddressPostalCode())
                .addressCountry(c.getAddressCountry())
                .appliedProfile(c.getAppliedProfile())
                .officeLocation(c.getOfficeLocation())
                .source(c.getSource())
                .skills(c.getSkills())
                .totalExperienceYears(c.getTotalExperienceYears())
                .currentDesignation(c.getCurrentDesignation())
                .currentCompanyCv(c.getCurrentCompanyCv())
                .educationSummary(c.getEducationSummary())
                .linkedinUrl(c.getLinkedinUrl())
                .githubUrl(c.getGithubUrl())
                .resumeOriginalName(c.getResumeOriginalName())
                .resumeUploadedAt(c.getResumeUploadedAt() != null ? c.getResumeUploadedAt() : c.getCreatedAt())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt());

        if (c.getCreatedBy() != null) {
            b.createdById(c.getCreatedBy().getId())
             .createdByName(c.getCreatedBy().getFirstName() + " " + c.getCreatedBy().getLastName());
        }

        // hrScreening is included in both list and detail views — needed for the HR spreadsheet tab
        if (c.getHrScreening() != null)
            b.hrScreening(toHrScreeningDTO(c.getHrScreening()));

        if (full) {
            if (c.getTechnicalInterviews() != null)
                b.technicalInterviews(c.getTechnicalInterviews().stream()
                        .map(this::toTechInterviewDTO).collect(Collectors.toList()));
            if (c.getFinalRound() != null)
                b.finalRound(toFinalRoundDTO(c.getFinalRound()));
        }
        return b.build();
    }

    private AtsHrScreeningDTO toHrScreeningDTO(AtsHrScreening s) {
        AtsHrScreeningDTO.AtsHrScreeningDTOBuilder b = AtsHrScreeningDTO.builder()
                .id(s.getId())
                .candidateId(s.getCandidate() != null ? s.getCandidate().getId() : null)
                .communicationSkills(s.getCommunicationSkills())
                .currentCompany(s.getCurrentCompany())
                .currentCtc(s.getCurrentCtc())
                .expectedCtc(s.getExpectedCtc())
                .noticePeriod(s.getNoticePeriod())
                .totalExperience(s.getTotalExperience())
                .relevantExperience(s.getRelevantExperience())
                .preferredLocation(s.getPreferredLocation())
                .availability(s.getAvailability())
                .reasonForChange(s.getReasonForChange())
                .currentRoleResponsibilities(s.getCurrentRoleResponsibilities())
                .workBase(s.getWorkBase())
                .screeningDate(s.getScreeningDate())
                .hrComments(s.getHrComments())
                .rejectionReason(s.getRejectionReason())
                .decision(s.getDecision())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt());
        if (s.getConductedBy() != null)
            b.conductedById(s.getConductedBy().getId())
             .conductedByName(s.getConductedBy().getFirstName() + " " + s.getConductedBy().getLastName());
        return b.build();
    }

    private AtsTechnicalInterviewDTO toTechInterviewDTO(AtsTechnicalInterview t) {
        AtsCandidate cand = t.getCandidate();
        String link = t.getToken() != null ? (baseUrl + "/interview/technical/" + t.getToken()) : null;
        AtsTechnicalInterviewDTO.AtsTechnicalInterviewDTOBuilder b = AtsTechnicalInterviewDTO.builder()
                .id(t.getId())
                .candidateId(cand != null ? cand.getId() : null)
                .candidateName(cand != null ? cand.getName() : null)
                .candidateEmail(cand != null ? cand.getEmail() : null)
                .candidateAppliedProfile(cand != null ? cand.getAppliedProfile() : null)
                .candidateStatus(cand != null ? cand.getStatus() : null)
                .scheduledAt(t.getScheduledAt())
                .token(t.getToken())
                .interviewStatus(t.getInterviewStatus() != null ? t.getInterviewStatus().name() : AtsTechInterviewStatus.PENDING_LINK.name())
                .interviewLink(link)
                .interviewTechnology(t.getInterviewTechnology())
                .interviewDifficulty(t.getInterviewDifficulty() != null ? t.getInterviewDifficulty().name() : null)
                .questionCount(t.getQuestionCount())
                .durationMinutes(t.getDurationMinutes())
                .startedAt(t.getStartedAt())
                .completedAt(t.getCompletedAt())
                .evaluatedAt(t.getEvaluatedAt())
                .videoUrl(t.getVideoUrl())
                .violationCount(t.getViolationCount())
                .score(t.getScore())
                .totalMarks(t.getTotalMarks())
                .technicalSkillsRating(t.getTechnicalSkillsRating())
                .communicationRating(t.getCommunicationRating())
                .problemSolvingRating(t.getProblemSolvingRating())
                .codingAbilityRating(t.getCodingAbilityRating())
                .architectureKnowledgeRating(t.getArchitectureKnowledgeRating())
                .comments(t.getComments())
                .decision(t.getDecision())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt());
        if (t.getInterviewer() != null)
            b.interviewerId(t.getInterviewer().getId())
             .interviewerName(t.getInterviewer().getFirstName() + " " + t.getInterviewer().getLastName());
        if (t.getAssignedBy() != null)
            b.assignedById(t.getAssignedBy().getId())
             .assignedByName(t.getAssignedBy().getFirstName() + " " + t.getAssignedBy().getLastName());
        return b.build();
    }

    private AtsFinalRoundDTO toFinalRoundDTO(AtsFinalRound r) {
        AtsCandidate cand = r.getCandidate();
        String link = r.getToken() != null ? (baseUrl + "/interview/final/" + r.getToken()) : null;
        AtsFinalRoundDTO.AtsFinalRoundDTOBuilder b = AtsFinalRoundDTO.builder()
                .id(r.getId())
                .candidateId(cand != null ? cand.getId() : null)
                .candidateName(cand != null ? cand.getName() : null)
                .candidateEmail(cand != null ? cand.getEmail() : null)
                .token(r.getToken())
                .interviewStatus(r.getInterviewStatus() != null ? r.getInterviewStatus().name() : AtsFinalInterviewStatus.PENDING_LINK.name())
                .interviewLink(link)
                .scheduledAt(r.getScheduledAt())
                .startedAt(r.getStartedAt())
                .completedAt(r.getCompletedAt())
                .evaluatedAt(r.getEvaluatedAt())
                .videoUrl(r.getVideoUrl())
                .overallRating(r.getOverallRating())
                .communicationRating(r.getCommunicationRating())
                .cultureFitRating(r.getCultureFitRating())
                .salaryDiscussion(r.getSalaryDiscussion())
                .expectedCtc(r.getExpectedCtc())
                .offeredCtc(r.getOfferedCtc())
                .noticePeriod(r.getNoticePeriod())
                .directorRemarks(r.getDirectorRemarks())
                .finalInterviewDate(r.getFinalInterviewDate())
                .finalRemarks(r.getFinalRemarks())
                .salaryRecommendation(r.getSalaryRecommendation())
                .joiningDate(r.getJoiningDate())
                .directorRecommendation(r.getDirectorRecommendation() != null ? r.getDirectorRecommendation() : "PENDING")
                .directorNotesAt(r.getDirectorNotesAt())
                .finalDecision(r.getFinalDecision() != null ? r.getFinalDecision() : "PENDING")
                .decidedAt(r.getDecidedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt());
        if (r.getConductedBy() != null)
            b.conductedById(r.getConductedBy().getId())
             .conductedByName(r.getConductedBy().getFirstName() + " " + r.getConductedBy().getLastName());
        if (r.getAssignedBy() != null)
            b.assignedById(r.getAssignedBy().getId())
             .assignedByName(r.getAssignedBy().getFirstName() + " " + r.getAssignedBy().getLastName());
        if (r.getDecidedBy() != null)
            b.decidedById(r.getDecidedBy().getId())
             .decidedByName(r.getDecidedBy().getFirstName() + " " + r.getDecidedBy().getLastName());
        return b.build();
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    private void sendRejectionEmail(String to, String candidateName, String position) {
        sendRejectionEmail(to, candidateName, position, null);
    }

    private void sendRejectionEmail(String to, String candidateName, String position, String rejectionReason) {
        if (to == null || to.isBlank()) return;
        try {
            String reasonHtml = (rejectionReason != null && !rejectionReason.isBlank())
                ? "<p><strong>Feedback:</strong> " + rejectionReason + "</p>"
                : "";
            sendMail(to, "Application Status — " + position,
                html("Application Status Update",
                    "<p>Dear <strong>" + candidateName + "</strong>,</p>" +
                    "<p>Thank you for applying for the <strong>" + position + "</strong> position.</p>" +
                    "<p>After careful review, we regret to inform you that your application has not been selected to proceed at this time. " +
                    "We will keep your profile on file for future opportunities.</p>" +
                    reasonHtml +
                    "<p>We appreciate your interest and wish you the very best in your career.</p>"));
        } catch (Exception e) {
            log.warn("Rejection email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendTechAssignmentEmail(String to, String managerName, String candidateName, String position, String scheduledAt) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Technical Interview Assignment — " + candidateName,
                html("Technical Interview Assignment",
                    "<p>Dear <strong>" + managerName + "</strong>,</p>" +
                    "<p>You have been assigned to conduct a <strong>Technical Interview</strong>:</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Candidate", candidateName) + row("Position", position) + row("Scheduled At", scheduledAt) +
                    "</table>" +
                    "<p>Please log in to the portal to view candidate details and submit your evaluation after the interview.</p>"));
        } catch (Exception e) {
            log.warn("Tech assignment email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendFinalRoundNotifEmail(String to, String candidateName, String position) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Final Round Ready — " + candidateName,
                html("Candidate Ready for Final Round",
                    "<p>A candidate has cleared the technical round and is awaiting the <strong>Final Round</strong>:</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Candidate", candidateName) + row("Position", position) +
                    "</table>" +
                    "<p>Please log in to the portal to schedule and complete the final evaluation.</p>"));
        } catch (Exception e) {
            log.warn("Final round notif email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendTechClearedEmail(String to, String candidateName, String position) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Congratulations! Technical Round Cleared — " + position,
                html("Technical Round Cleared",
                    "<p>Dear <strong>" + candidateName + "</strong>,</p>" +
                    "<p>Congratulations! We are pleased to inform you that you have successfully cleared the <strong>Technical Interview</strong> for the position of <strong>" + position + "</strong>.</p>" +
                    "<p>You have been shortlisted for the <strong>Final Round</strong>. Our team will reach out to you shortly to schedule the final interview.</p>" +
                    "<p>We look forward to speaking with you again. Please keep your schedule open for the next few days.</p>"));
        } catch (Exception e) {
            log.warn("Tech cleared email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendOfferEmail(String to, String candidateName, String position, String salary, String joiningDate) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Congratulations! Offer Approved — " + position,
                html("Offer Approved 🎉",
                    "<p>Dear <strong>" + candidateName + "</strong>,</p>" +
                    "<p>We are thrilled to inform you that your application for <strong>" + position + "</strong> has been <strong>approved</strong>!</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    (salary != null && !salary.isBlank() ? row("Salary Offered", salary) : "") +
                    row("Joining Date", joiningDate) +
                    "</table>" +
                    "<p>Welcome to the team! Our HR team will reach out shortly with onboarding details.</p>"));
        } catch (Exception e) {
            log.warn("Offer email failed for {}: {}", to, e.getMessage());
        }
    }

    private String html(String title, String body) {
        return "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f0f2f5;font-family:Arial,sans-serif;'>" +
               "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f0f2f5;padding:30px 0;'><tr><td align='center'>" +
               "<table width='580' cellpadding='0' cellspacing='0' style='background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 4px 16px rgba(0,0,0,.12);max-width:580px;'>" +
               "<tr><td style='background:#1a2847;padding:22px 32px;'><p style='color:#fff;margin:0;font-size:20px;font-weight:bold;'>&#127919; " + title + "</p></td></tr>" +
               "<tr><td style='padding:28px 32px;color:#333;font-size:14px;line-height:1.7;'>" + body + "</td></tr>" +
               "<tr><td style='background:#f8f9fa;padding:14px 32px;border-top:1px solid #e8e8e8;'><p style='color:#aaa;font-size:12px;margin:0;'>EmpSAS &mdash; Interview Management Portal</p></td></tr>" +
               "</table></td></tr></table></body></html>";
    }

    private String row(String label, String value) {
        return "<tr><td style='background:#f4f6f9;border:1px solid #dde3ec;padding:10px 14px;font-weight:bold;color:#4a5568;width:38%;'>"
             + label + "</td><td style='border:1px solid #dde3ec;padding:10px 14px;color:#333;'>"
             + (value != null && !value.isBlank() ? value : "&mdash;") + "</td></tr>";
    }

    private void sendMail(String to, String subject, String htmlBody) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
        h.setTo(to);
        h.setSubject(subject);
        h.setText(htmlBody, true);
        mailSender.send(msg);
    }

    // ── Video Interview — Link Generation ─────────────────────────────────────

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private String generateUniqueToken() {
        String token;
        do {
            StringBuilder sb = new StringBuilder(20);
            for (int i = 0; i < 20; i++) sb.append(TOKEN_CHARS.charAt(SECURE_RANDOM.nextInt(TOKEN_CHARS.length())));
            token = sb.toString();
        } while (techRepo.existsByToken(token));
        return token;
    }

    @Transactional
    public AtsTechnicalInterviewDTO generateInterviewLink(Long techId, String technology, String difficulty, Integer questionCount, String callerEmail) {
        AtsTechnicalInterview tech = techRepo.findById(techId)
                .orElseThrow(() -> new ResourceNotFoundException("Technical interview not found: " + techId));

        int qCount = (questionCount != null && questionCount >= 5 && questionCount <= 40) ? questionCount : 20;
        String tech2 = (technology != null && !technology.isBlank()) ? technology : "General";
        InterviewDifficulty diff = parseInterviewDifficulty(difficulty);

        // Select a fresh, randomized set of questions every time this is called —
        // each candidate gets their own shuffle, never a fixed/deterministic list.
        List<InterviewQuestion> questions = selectRandomQuestions(tech2, diff, qCount);
        if (questions.isEmpty()) throw new com.emp.management.exception.BadRequestException(
                "No active questions found for technology: " + tech2 + ". Add questions in the Question Bank first.");

        // Clear any previous questions for this interview
        techQRepo.deleteByInterview(tech);

        // Re-shuffle in application memory too, so the question ORDER a candidate sees
        // is independent of whatever order the DB happened to return them in.
        Collections.shuffle(questions, SECURE_RANDOM);

        // Store shuffled questions
        List<String> letters = List.of("A", "B", "C", "D");
        for (int i = 0; i < questions.size(); i++) {
            InterviewQuestion q = questions.get(i);
            String optionOrder = null;
            if (q.getQuestionType() == QuestionType.MCQ) {
                List<String> shuffled = new ArrayList<>(letters);
                Collections.shuffle(shuffled, SECURE_RANDOM);
                optionOrder = String.join(",", shuffled);
            }
            techQRepo.save(AtsTechInterviewQuestion.builder()
                    .interview(tech).question(q)
                    .questionOrder(i + 1).optionOrder(optionOrder).build());
        }

        // Calculate total marks
        int totalMarks = questions.stream().mapToInt(q -> q.getMarks() != null ? q.getMarks() : 5).sum();

        // Generate token
        String token = tech.getToken() != null ? tech.getToken() : generateUniqueToken();

        // Default null fields that come from pre-existing DB rows
        if (tech.getDurationMinutes() == null) tech.setDurationMinutes(45);
        if (tech.getViolationCount()  == null) tech.setViolationCount(0);

        tech.setToken(token);
        tech.setInterviewStatus(AtsTechInterviewStatus.LINK_GENERATED);
        tech.setInterviewTechnology(tech2);
        tech.setInterviewDifficulty(diff);
        tech.setQuestionCount(questions.size());
        tech.setTotalMarks(totalMarks);
        tech.setOfferSdp(null);
        tech.setAnswerSdp(null);
        techRepo.save(tech);

        // Send email to candidate
        AtsCandidate cand = tech.getCandidate();
        String candidateEmail = cand.getEmail();
        String interviewLink  = baseUrl + "/interview/technical/" + token;
        if (candidateEmail != null && !candidateEmail.isBlank()) {
            sendInterviewLinkEmail(candidateEmail, cand.getName(), cand.getAppliedProfile(),
                    interviewLink, tech.getDurationMinutes(), questions.size());
        }

        return toTechInterviewDTO(tech);
    }

    private InterviewDifficulty parseInterviewDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) return InterviewDifficulty.MIXED;
        try {
            return InterviewDifficulty.valueOf(difficulty.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return InterviewDifficulty.MIXED;
        }
    }

    /**
     * Randomly selects up to {@code count} ACTIVE questions for the given technology,
     * honoring difficulty when specified. Falls back to MIXED (any difficulty) for the
     * same technology when too few match the requested difficulty, then — only if the
     * technology itself has no active questions at all — to a random sample across all
     * technologies, so a candidate is never left with an empty or fixed question set.
     */
    private List<InterviewQuestion> selectRandomQuestions(String technology, InterviewDifficulty difficulty, int count) {
        List<InterviewQuestion> selected;
        if (difficulty == InterviewDifficulty.MIXED) {
            selected = new ArrayList<>(questionRepo.findRandomByTechnology(technology, count));
        } else {
            selected = new ArrayList<>(questionRepo.findRandomByTechnologyAndDifficulty(technology, difficulty.name(), count));
            if (selected.size() < count) {
                List<InterviewQuestion> more = questionRepo.findRandomByTechnology(technology, count - selected.size());
                for (InterviewQuestion q : more) {
                    if (selected.stream().noneMatch(s -> s.getId().equals(q.getId()))) selected.add(q);
                }
            }
        }
        if (selected.isEmpty()) {
            selected = new ArrayList<>(questionRepo.findRandomActive(count));
        }
        return selected.stream().limit(count).collect(Collectors.toList());
    }

    // ── Video Interview — Candidate Public Endpoints ──────────────────────────

    @Transactional(readOnly = true)
    public CandidateRoomDTO getCandidateRoom(String token) {
        AtsTechnicalInterview tech = techRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid interview link."));

        AtsCandidate cand = tech.getCandidate();
        List<AtsTechInterviewQuestion> qs = techQRepo.findByInterviewOrderByQuestionOrder(tech);
        List<AtsTechInterviewAnswer>   as = techARepo.findByInterview(tech);

        Map<Long, AtsTechInterviewAnswer> answerMap = new HashMap<>();
        for (AtsTechInterviewAnswer a : as) answerMap.put(a.getQuestionId(), a);

        List<CandidateRoomDTO.CandidateQuestionDTO> questionDTOs = new ArrayList<>();
        for (AtsTechInterviewQuestion tq : qs) {
            InterviewQuestion q = tq.getQuestion();
            List<CandidateRoomDTO.McqOption> options = null;
            if (q.getQuestionType() == QuestionType.MCQ && tq.getOptionOrder() != null) {
                options = buildShuffledOptions(q, tq.getOptionOrder());
            }
            questionDTOs.add(CandidateRoomDTO.CandidateQuestionDTO.builder()
                    .questionId(q.getId()).questionNumber(tq.getQuestionOrder())
                    .questionType(q.getQuestionType().name())
                    .questionText(q.getQuestionText())
                    .marks(q.getMarks()).options(options).build());
        }

        List<CandidateRoomDTO.AnswerStatusDTO> savedAnswers = new ArrayList<>();
        for (AtsTechInterviewAnswer a : as) {
            // Convert stored original letter back to display letter
            String displayLetter = a.getSelectedOption();
            if (displayLetter != null) {
                // Find the optionOrder for this question to map back to display position
                for (AtsTechInterviewQuestion tq : qs) {
                    if (tq.getQuestion().getId().equals(a.getQuestionId()) && tq.getOptionOrder() != null) {
                        String[] order = tq.getOptionOrder().split(",");
                        String[] displayLetters = {"A", "B", "C", "D"};
                        for (int i = 0; i < order.length; i++) {
                            if (order[i].equals(a.getSelectedOption())) {
                                displayLetter = displayLetters[i];
                                break;
                            }
                        }
                    }
                }
            }
            savedAnswers.add(new CandidateRoomDTO.AnswerStatusDTO(a.getQuestionId(), a.getAnswerText(), displayLetter));
        }

        return CandidateRoomDTO.builder()
                .interviewId(tech.getId())
                .candidateName(cand.getName())
                .candidateEmail(cand.getEmail())
                .appliedProfile(cand.getAppliedProfile())
                .interviewStatus(tech.getInterviewStatus().name())
                .durationMinutes(tech.getDurationMinutes() != null ? tech.getDurationMinutes() : 45)
                .startedAt(tech.getStartedAt())
                .questions(questionDTOs)
                .savedAnswers(savedAnswers)
                .build();
    }

    @Transactional
    public void startInterview(String token) {
        AtsTechnicalInterview tech = techRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid interview link."));
        if (tech.getInterviewStatus() == AtsTechInterviewStatus.CANDIDATE_SUBMITTED ||
            tech.getInterviewStatus() == AtsTechInterviewStatus.EVALUATED) return;
        if (tech.getStartedAt() == null) tech.setStartedAt(LocalDateTime.now());
        if (tech.getViolationCount() == null) tech.setViolationCount(0);
        if (tech.getDurationMinutes() == null) tech.setDurationMinutes(45);
        tech.setInterviewStatus(AtsTechInterviewStatus.IN_PROGRESS);
        techRepo.save(tech);
    }

    @Transactional
    public void saveAnswer(String token, Long questionId, String answerText, String selectedDisplayLetter) {
        AtsTechnicalInterview tech = techRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid interview link."));

        // Convert display letter (A/B/C/D position in shuffled view) back to original letter
        String originalLetter = selectedDisplayLetter;
        if (selectedDisplayLetter != null && !selectedDisplayLetter.isBlank()) {
            List<AtsTechInterviewQuestion> qs = techQRepo.findByInterviewOrderByQuestionOrder(tech);
            String[] displayLetters = {"A", "B", "C", "D"};
            for (AtsTechInterviewQuestion tq : qs) {
                if (tq.getQuestion().getId().equals(questionId) && tq.getOptionOrder() != null) {
                    String[] order = tq.getOptionOrder().split(",");
                    for (int i = 0; i < displayLetters.length; i++) {
                        if (displayLetters[i].equals(selectedDisplayLetter) && i < order.length) {
                            originalLetter = order[i];
                            break;
                        }
                    }
                    break;
                }
            }
        }

        final String finalOriginalLetter = originalLetter;
        AtsTechInterviewAnswer answer = techARepo.findByInterviewAndQuestionId(tech, questionId)
                .orElse(AtsTechInterviewAnswer.builder().interview(tech).questionId(questionId).build());
        answer.setAnswerText(answerText);
        answer.setSelectedOption(finalOriginalLetter);
        techARepo.save(answer);
    }

    @Transactional
    public Map<String, Object> logViolation(String token, String violationType, String description, String ipAddress) {
        AtsTechnicalInterview tech = techRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid interview link."));

        int newCount = (tech.getViolationCount() == null ? 0 : tech.getViolationCount()) + 1;
        tech.setViolationCount(newCount);
        if (tech.getDurationMinutes() == null) tech.setDurationMinutes(45);

        techVRepo.save(AtsTechInterviewViolation.builder()
                .interview(tech).violationType(violationType)
                .description(description).violationNumber(newCount).ipAddress(ipAddress).build());

        boolean autoSubmitted = false;
        if (newCount >= 3 && tech.getInterviewStatus() == AtsTechInterviewStatus.IN_PROGRESS) {
            autoSubmitted = true;
            calculateAndSubmit(tech);
        } else {
            techRepo.save(tech);
        }

        return Map.of("violationCount", newCount, "autoSubmitted", autoSubmitted);
    }

    @Transactional
    public Map<String, Integer> submitInterview(String token) {
        AtsTechnicalInterview tech = techRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid interview link."));
        return calculateAndSubmit(tech);
    }

    private Map<String, Integer> calculateAndSubmit(AtsTechnicalInterview tech) {
        List<AtsTechInterviewQuestion> qs = techQRepo.findByInterviewOrderByQuestionOrder(tech);
        List<AtsTechInterviewAnswer>   as = techARepo.findByInterview(tech);
        Map<Long, AtsTechInterviewAnswer> answerMap = new HashMap<>();
        for (AtsTechInterviewAnswer a : as) answerMap.put(a.getQuestionId(), a);

        int score = 0, total = 0;
        for (AtsTechInterviewQuestion tq : qs) {
            InterviewQuestion q = tq.getQuestion();
            int marks = q.getMarks() != null ? q.getMarks() : 5;
            total += marks;
            if (q.getQuestionType() == QuestionType.MCQ) {
                AtsTechInterviewAnswer a = answerMap.get(q.getId());
                if (a != null && q.getCorrectAnswer() != null && q.getCorrectAnswer().equals(a.getSelectedOption())) {
                    score += marks;
                }
            }
        }

        tech.setScore(score);
        tech.setTotalMarks(total);
        tech.setCompletedAt(LocalDateTime.now());
        tech.setInterviewStatus(AtsTechInterviewStatus.CANDIDATE_SUBMITTED);
        techRepo.save(tech);
        return Map.of("score", score, "totalMarks", total);
    }

    @Transactional
    public void uploadRecording(String token, MultipartFile file) {
        AtsTechnicalInterview tech = techRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid interview link."));
        try {
            Path dir = Paths.get(uploadDir, "tech-recordings");
            Files.createDirectories(dir);
            String filename = token + "_" + System.currentTimeMillis() + ".webm";
            Path dest = dir.resolve(filename);
            file.transferTo(dest.toFile());
            tech.setVideoUrl("/uploads/tech-recordings/" + filename);
            techRepo.save(tech);
        } catch (IOException e) {
            log.warn("Recording upload failed for token {}: {}", token, e.getMessage());
        }
    }

    // ── Video Interview — Manager Room ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public ManagerRoomDTO getManagerRoom(Long techId) {
        AtsTechnicalInterview tech = techRepo.findById(techId)
                .orElseThrow(() -> new ResourceNotFoundException("Technical interview not found: " + techId));

        AtsCandidate cand = tech.getCandidate();
        List<AtsTechInterviewQuestion> qs = techQRepo.findByInterviewOrderByQuestionOrder(tech);
        List<AtsTechInterviewAnswer>   as = techARepo.findByInterview(tech);
        List<AtsTechInterviewViolation> vs = techVRepo.findByInterviewOrderByOccurredAtAsc(tech);

        Map<Long, AtsTechInterviewAnswer> answerMap = new HashMap<>();
        for (AtsTechInterviewAnswer a : as) answerMap.put(a.getQuestionId(), a);

        List<ManagerRoomDTO.QuestionWithAnswer> qwa = new ArrayList<>();
        for (AtsTechInterviewQuestion tq : qs) {
            InterviewQuestion q = tq.getQuestion();
            AtsTechInterviewAnswer ans = answerMap.get(q.getId());
            List<String> opts = q.getQuestionType() == QuestionType.MCQ
                    ? List.of(q.getOptionA() != null ? q.getOptionA() : "",
                              q.getOptionB() != null ? q.getOptionB() : "",
                              q.getOptionC() != null ? q.getOptionC() : "",
                              q.getOptionD() != null ? q.getOptionD() : "")
                    : null;

            Boolean correct = null;
            if (q.getQuestionType() == QuestionType.MCQ && ans != null && q.getCorrectAnswer() != null) {
                correct = q.getCorrectAnswer().equals(ans.getSelectedOption());
            }

            qwa.add(ManagerRoomDTO.QuestionWithAnswer.builder()
                    .questionId(q.getId()).questionNumber(tq.getQuestionOrder())
                    .questionText(q.getQuestionText()).questionType(q.getQuestionType().name())
                    .marks(q.getMarks()).correctAnswer(q.getCorrectAnswer()).options(opts)
                    .selectedOption(ans != null ? ans.getSelectedOption() : null)
                    .answerText(ans != null ? ans.getAnswerText() : null)
                    .correct(correct).build());
        }

        List<ManagerRoomDTO.ViolationRecord> vrs = vs.stream()
                .map(v -> new ManagerRoomDTO.ViolationRecord(
                        v.getViolationType(), v.getDescription(), v.getViolationNumber(), v.getOccurredAt()))
                .collect(Collectors.toList());

        return ManagerRoomDTO.builder()
                .interviewId(tech.getId()).token(tech.getToken())
                .candidateName(cand != null ? cand.getName() : null)
                .candidateEmail(cand != null ? cand.getEmail() : null)
                .appliedProfile(cand != null ? cand.getAppliedProfile() : null)
                .interviewStatus(tech.getInterviewStatus().name())
                .startedAt(tech.getStartedAt()).completedAt(tech.getCompletedAt())
                .durationMinutes(tech.getDurationMinutes() != null ? tech.getDurationMinutes() : 45)
                .score(tech.getScore()).totalMarks(tech.getTotalMarks())
                .violationCount(tech.getViolationCount()).videoUrl(tech.getVideoUrl())
                .offerSdp(tech.getOfferSdp())
                .questionsWithAnswers(qwa).violations(vrs).build();
    }

    @Transactional
    public AtsCandidateDTO submitManagerEvaluation(Long techId, AtsTechnicalInterviewDTO dto, String callerEmail) {
        AtsTechnicalInterview tech = techRepo.findById(techId)
                .orElseThrow(() -> new ResourceNotFoundException("Technical interview not found: " + techId));

        tech.setTechnicalSkillsRating(dto.getTechnicalSkillsRating());
        tech.setCommunicationRating(dto.getCommunicationRating());
        tech.setProblemSolvingRating(dto.getProblemSolvingRating());
        tech.setCodingAbilityRating(dto.getCodingAbilityRating());
        tech.setArchitectureKnowledgeRating(dto.getArchitectureKnowledgeRating());
        tech.setComments(dto.getComments());
        tech.setDecision(dto.getDecision());
        tech.setInterviewStatus(AtsTechInterviewStatus.EVALUATED);
        tech.setEvaluatedAt(LocalDateTime.now());
        techRepo.save(tech);

        AtsCandidate c = tech.getCandidate();

        if ("REJECT".equals(dto.getDecision())) {
            c.setStatus(AtsCandidateStatus.TECHNICAL_REJECTED);
            sendRejectionEmail(c.getEmail(), c.getName(), c.getAppliedProfile());

        } else if ("APPROVE".equals(dto.getDecision())) {
            c.setStatus(AtsCandidateStatus.FINAL_ROUND_PENDING);
            if (finalRepo.findByCandidateId(c.getId()).isEmpty()) {
                finalRepo.save(AtsFinalRound.builder().candidate(c).finalDecision("PENDING").build());
            }
            // Notify candidate that they cleared the technical round
            sendTechClearedEmail(c.getEmail(), c.getName(), c.getAppliedProfile());
            // Notify all admins to schedule the final round
            employeeRepo.findActiveAdmins().forEach(admin -> {
                String adminEmail = admin.getUser() != null ? admin.getUser().getEmail() : null;
                sendFinalRoundNotifEmail(adminEmail, c.getName(), c.getAppliedProfile());
            });
        }

        return toCandidateDTO(candidateRepo.save(c), true);
    }

    // ── WebRTC Signaling ──────────────────────────────────────────────────────

    @Transactional
    public void saveOffer(String token, String offerSdp) {
        AtsTechnicalInterview tech = techRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid interview link."));
        tech.setOfferSdp(offerSdp);
        techRepo.save(tech);
    }

    @Transactional(readOnly = true)
    public String getOffer(Long techId) {
        return techRepo.findById(techId).map(AtsTechnicalInterview::getOfferSdp).orElse(null);
    }

    @Transactional
    public void saveAnswerSdp(Long techId, String answerSdp) {
        AtsTechnicalInterview tech = techRepo.findById(techId)
                .orElseThrow(() -> new ResourceNotFoundException("Technical interview not found: " + techId));
        tech.setAnswerSdp(answerSdp);
        techRepo.save(tech);
    }

    @Transactional(readOnly = true)
    public String getAnswerSdp(String token) {
        return techRepo.findByToken(token).map(AtsTechnicalInterview::getAnswerSdp).orElse(null);
    }

    public void addCandidateIce(String token, String iceJson) {
        candidateIceMap.computeIfAbsent(token, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(iceJson);
    }

    public List<String> getCandidateIce(Long techId) {
        return techRepo.findById(techId).map(t -> candidateIceMap.getOrDefault(t.getToken(), List.of()))
                .orElse(List.of());
    }

    public void addManagerIce(Long techId, String iceJson) {
        techRepo.findById(techId).ifPresent(t ->
                managerIceMap.computeIfAbsent(t.getToken(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(iceJson));
    }

    public List<String> getManagerIce(String token) {
        return managerIceMap.getOrDefault(token, List.of());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<CandidateRoomDTO.McqOption> buildShuffledOptions(InterviewQuestion q, String optionOrder) {
        Map<String, String> orig = new LinkedHashMap<>();
        orig.put("A", q.getOptionA() != null ? q.getOptionA() : "");
        orig.put("B", q.getOptionB() != null ? q.getOptionB() : "");
        orig.put("C", q.getOptionC() != null ? q.getOptionC() : "");
        orig.put("D", q.getOptionD() != null ? q.getOptionD() : "");

        String[] order = optionOrder.split(",");
        String[] displayLetters = {"A", "B", "C", "D"};
        List<CandidateRoomDTO.McqOption> result = new ArrayList<>();
        for (int i = 0; i < order.length; i++) {
            result.add(new CandidateRoomDTO.McqOption(displayLetters[i], orig.getOrDefault(order[i], "")));
        }
        return result;
    }

    private void sendInterviewLinkEmail(String to, String candidateName, String position,
                                        String link, int duration, int qCount) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Technical Interview Invitation — " + position,
                html("Technical Interview Invitation",
                    "<p>Dear <strong>" + candidateName + "</strong>,</p>" +
                    "<p>You have been invited to complete a <strong>Technical Interview</strong> for the position of <strong>" + position + "</strong>.</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Questions", qCount + " questions") +
                    row("Duration", duration + " minutes") +
                    row("Format", "Live video interview with screen recording") +
                    "</table>" +
                    "<p style='background:#f0f4ff;border-left:4px solid #4f46e5;padding:12px 16px;border-radius:0 6px 6px 0;margin:20px 0;font-size:13px;'>" +
                    "<strong>Instructions:</strong><br/>" +
                    "• Use a laptop or desktop with a webcam and microphone<br/>" +
                    "• Ensure a stable internet connection<br/>" +
                    "• The interview must be completed in a single session<br/>" +
                    "• Switching tabs or exiting fullscreen will be flagged as a violation<br/>" +
                    "• 3 violations result in automatic submission</p>" +
                    "<p style='text-align:center;margin:24px 0;'>" +
                    "<a href='" + link + "' style='background:#1e3a5f;color:#fff;padding:14px 32px;text-decoration:none;border-radius:8px;font-size:15px;font-weight:bold;display:inline-block;'>Start Interview</a>" +
                    "</p>" +
                    "<p style='font-size:12px;color:#888;'>Or copy this link: <a href='" + link + "'>" + link + "</a></p>" +
                    "<p>Best of luck!</p>"));
        } catch (Exception e) {
            log.warn("Interview link email failed for {}: {}", to, e.getMessage());
        }
    }

    // ── Final Round Video Interview ───────────────────────────────────────────

    // ICE maps keyed by final round token
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>> finalCandidateIceMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>> finalDirectorIceMap  = new java.util.concurrent.ConcurrentHashMap<>();

    /** HR/Admin step: assigns the Director who will conduct the Final Round. No link is generated yet. */
    @Transactional
    public AtsFinalRoundDTO assignFinalRoundDirector(Long candidateId, Long directorId, String callerEmail) {
        AtsCandidate c = findCandidate(candidateId);
        if (!AtsCandidateStatus.FINAL_ROUND_PENDING.equals(c.getStatus())) {
            throw new com.emp.management.exception.BadRequestException(
                    "Candidate must have cleared the Technical Interview before assigning a Director.");
        }
        EmployeeDetails caller = findEmployee(callerEmail).orElse(null);
        EmployeeDetails director = employeeRepo.findById(directorId)
                .orElseThrow(() -> new ResourceNotFoundException("Director not found: " + directorId));

        AtsFinalRound round = finalRepo.findByCandidateId(candidateId)
                .orElseGet(() -> AtsFinalRound.builder().candidate(c).finalDecision("PENDING").build());
        round.setConductedBy(director);
        round.setAssignedBy(caller);
        // Reassigning to a different director invalidates any link already generated
        if (round.getInterviewStatus() == null) round.setInterviewStatus(AtsFinalInterviewStatus.PENDING_LINK);
        finalRepo.save(round);

        String directorEmail = director.getUser() != null ? director.getUser().getEmail() : null;
        sendFinalRoundAssignedEmail(directorEmail,
                director.getFirstName() + " " + director.getLastName(),
                c.getName(), c.getAppliedProfile());

        return toFinalRoundDTO(finalRepo.save(round));
    }

    /** Director step: schedules the Final Interview and (re)generates the secure candidate link. */
    @Transactional
    public AtsFinalRoundDTO generateFinalInterviewLink(Long candidateId, LocalDateTime scheduledAt, String callerEmail) {
        AtsCandidate c = findCandidate(candidateId);
        AtsFinalRound round = finalRepo.findByCandidateId(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Final round not found for candidate: " + candidateId));

        if (round.getConductedBy() == null) {
            throw new com.emp.management.exception.BadRequestException(
                    "Assign a Director to this candidate before scheduling the final interview.");
        }
        requireFinalRoundOwner(round, callerEmail);

        if (scheduledAt != null) round.setScheduledAt(scheduledAt);

        String token = (round.getToken() != null) ? round.getToken() : generateUniqueFinalToken();
        round.setToken(token);
        round.setInterviewStatus(AtsFinalInterviewStatus.LINK_GENERATED);
        round.setOfferSdp(null);
        round.setAnswerSdp(null);
        finalRepo.save(round);

        String link = baseUrl + "/interview/final/" + token;
        String scheduledStr = round.getScheduledAt() != null ? round.getScheduledAt().format(DT_FMT) : "TBD";
        if (c.getEmail() != null && !c.getEmail().isBlank()) {
            sendFinalInterviewLinkEmail(c.getEmail(), c.getName(), c.getAppliedProfile(), link, scheduledStr);
        }
        return toFinalRoundDTO(finalRepo.save(round));
    }

    /** Throws 403 unless the caller is the Director assigned to this Final Round (or an Admin). */
    private void requireFinalRoundOwner(AtsFinalRound round, String callerEmail) {
        EmployeeDetails caller = findEmployee(callerEmail).orElse(null);
        boolean isAdmin = caller != null && caller.getUser() != null && caller.getUser().getRole() == Role.ADMIN;
        boolean isOwner = caller != null && round.getConductedBy() != null
                && round.getConductedBy().getId().equals(caller.getId());
        if (!isAdmin && !isOwner) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the assigned Director can perform this action.");
        }
    }

    private String generateUniqueFinalToken() {
        String token;
        do {
            StringBuilder sb = new StringBuilder(20);
            for (int i = 0; i < 20; i++) sb.append(TOKEN_CHARS.charAt(SECURE_RANDOM.nextInt(TOKEN_CHARS.length())));
            token = sb.toString();
        } while (finalRepo.existsByToken(token));
        return token;
    }

    @Transactional(readOnly = true)
    public AtsFinalRoundDTO getCandidateFinalRoom(String token) {
        AtsFinalRound round = finalRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid final interview link."));
        return toFinalRoundDTO(round);
    }

    @Transactional
    public void startFinalInterview(String token) {
        AtsFinalRound round = finalRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid final interview link."));
        if (round.getInterviewStatus() == AtsFinalInterviewStatus.CANDIDATE_SUBMITTED ||
            round.getInterviewStatus() == AtsFinalInterviewStatus.EVALUATED) return;
        if (round.getStartedAt() == null) round.setStartedAt(LocalDateTime.now());
        round.setInterviewStatus(AtsFinalInterviewStatus.IN_PROGRESS);
        finalRepo.save(round);
    }

    @Transactional
    public void submitFinalInterview(String token) {
        AtsFinalRound round = finalRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid final interview link."));
        if (round.getInterviewStatus() == AtsFinalInterviewStatus.EVALUATED) return;
        round.setCompletedAt(LocalDateTime.now());
        round.setInterviewStatus(AtsFinalInterviewStatus.CANDIDATE_SUBMITTED);
        finalRepo.save(round);
    }

    @Transactional(readOnly = true)
    public DirectorRoomDTO getDirectorRoom(Long finalRoundId, String callerEmail) {
        AtsFinalRound round = finalRepo.findById(finalRoundId)
                .orElseThrow(() -> new ResourceNotFoundException("Final round not found: " + finalRoundId));
        requireFinalRoundOwner(round, callerEmail);
        AtsCandidate cand = round.getCandidate();
        return DirectorRoomDTO.builder()
                .finalRoundId(round.getId())
                .token(round.getToken())
                .candidateName(cand != null ? cand.getName() : null)
                .candidateEmail(cand != null ? cand.getEmail() : null)
                .appliedProfile(cand != null ? cand.getAppliedProfile() : null)
                .interviewStatus(round.getInterviewStatus() != null ? round.getInterviewStatus().name() : AtsFinalInterviewStatus.PENDING_LINK.name())
                .scheduledAt(round.getScheduledAt())
                .startedAt(round.getStartedAt())
                .completedAt(round.getCompletedAt())
                .evaluatedAt(round.getEvaluatedAt())
                .videoUrl(round.getVideoUrl())
                .offerSdp(round.getOfferSdp())
                .overallRating(round.getOverallRating())
                .communicationRating(round.getCommunicationRating())
                .cultureFitRating(round.getCultureFitRating())
                .salaryDiscussion(round.getSalaryDiscussion())
                .expectedCtc(round.getExpectedCtc())
                .offeredCtc(round.getOfferedCtc())
                .noticePeriod(round.getNoticePeriod())
                .directorRemarks(round.getDirectorRemarks())
                .finalDecision(round.getFinalDecision())
                .build();
    }

    @Transactional
    public AtsCandidateDTO submitDirectorEvaluation(Long finalRoundId, AtsFinalRoundDTO dto, String callerEmail) {
        AtsFinalRound round = finalRepo.findById(finalRoundId)
                .orElseThrow(() -> new ResourceNotFoundException("Final round not found: " + finalRoundId));
        requireFinalRoundOwner(round, callerEmail);
        EmployeeDetails caller = findEmployee(callerEmail).orElse(null);

        round.setOverallRating(dto.getOverallRating());
        round.setCommunicationRating(dto.getCommunicationRating());
        round.setCultureFitRating(dto.getCultureFitRating());
        round.setSalaryDiscussion(dto.getSalaryDiscussion());
        round.setExpectedCtc(dto.getExpectedCtc());
        round.setOfferedCtc(dto.getOfferedCtc());
        round.setNoticePeriod(dto.getNoticePeriod());
        round.setDirectorRemarks(dto.getDirectorRemarks());
        round.setJoiningDate(dto.getJoiningDate());
        round.setFinalDecision(dto.getFinalDecision());
        round.setInterviewStatus(AtsFinalInterviewStatus.EVALUATED);
        round.setEvaluatedAt(LocalDateTime.now());
        round.setConductedBy(caller);
        finalRepo.save(round);

        AtsCandidate c = round.getCandidate();

        if ("APPROVE".equals(dto.getFinalDecision())) {
            c.setStatus(AtsCandidateStatus.SELECTED);
            String joiningStr = dto.getJoiningDate() != null ? dto.getJoiningDate().toString() : "TBD";
            String salary = dto.getOfferedCtc() != null ? dto.getOfferedCtc() : dto.getSalaryDiscussion();
            sendOfferEmail(c.getEmail(), c.getName(), c.getAppliedProfile(), salary, joiningStr);
            // Notify HR
            employeeRepo.findActiveAdmins().forEach(admin -> {
                String adminEmail = admin.getUser() != null ? admin.getUser().getEmail() : null;
                sendFinalApprovedHrEmail(adminEmail, c.getName(), c.getAppliedProfile(), salary, joiningStr);
            });

        } else if ("REJECT".equals(dto.getFinalDecision())) {
            c.setStatus(AtsCandidateStatus.REJECTED);
            sendRejectionEmail(c.getEmail(), c.getName(), c.getAppliedProfile());

        } else if ("HOLD".equals(dto.getFinalDecision())) {
            // Status stays FINAL_ROUND_PENDING
            employeeRepo.findActiveAdmins().forEach(admin -> {
                String adminEmail = admin.getUser() != null ? admin.getUser().getEmail() : null;
                sendFinalHoldHrEmail(adminEmail, c.getName(), c.getAppliedProfile());
            });
        }

        return toCandidateDTO(candidateRepo.save(c), true);
    }

    @Transactional
    public void uploadFinalRecording(String token, MultipartFile file) {
        AtsFinalRound round = finalRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid final interview link."));
        try {
            String ext = Optional.ofNullable(file.getOriginalFilename())
                    .filter(n -> n.contains(".")).map(n -> n.substring(n.lastIndexOf('.'))).orElse(".webm");
            String filename = "final_" + token + "_" + System.currentTimeMillis() + ext;
            Path dest = Paths.get(uploadDir, "recordings", filename);
            Files.createDirectories(dest.getParent());
            file.transferTo(dest);
            round.setVideoUrl("recordings/" + filename);
            finalRepo.save(round);
        } catch (IOException e) {
            log.warn("Final recording upload failed for token {}: {}", token, e.getMessage());
        }
    }

    // WebRTC signaling — Final Round
    @Transactional
    public void saveFinalOffer(String token, String offerSdp) {
        AtsFinalRound round = finalRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid final interview link."));
        round.setOfferSdp(offerSdp);
        finalRepo.save(round);
    }

    @Transactional(readOnly = true)
    public String getFinalOffer(Long finalRoundId) {
        return finalRepo.findById(finalRoundId).map(AtsFinalRound::getOfferSdp).orElse(null);
    }

    @Transactional
    public void saveFinalAnswerSdp(Long finalRoundId, String answerSdp) {
        AtsFinalRound round = finalRepo.findById(finalRoundId)
                .orElseThrow(() -> new ResourceNotFoundException("Final round not found: " + finalRoundId));
        round.setAnswerSdp(answerSdp);
        finalRepo.save(round);
    }

    @Transactional(readOnly = true)
    public String getFinalAnswerSdp(String token) {
        return finalRepo.findByToken(token).map(AtsFinalRound::getAnswerSdp).orElse(null);
    }

    public void addCandidateFinalIce(String token, String iceJson) {
        finalCandidateIceMap.computeIfAbsent(token, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(iceJson);
    }

    public List<String> getCandidateFinalIce(Long finalRoundId) {
        return finalRepo.findById(finalRoundId)
                .map(r -> finalCandidateIceMap.getOrDefault(r.getToken(), List.of()))
                .orElse(List.of());
    }

    public void addDirectorIce(Long finalRoundId, String iceJson) {
        finalRepo.findById(finalRoundId).ifPresent(r ->
                finalDirectorIceMap.computeIfAbsent(r.getToken(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(iceJson));
    }

    public List<String> getDirectorIce(String token) {
        return finalDirectorIceMap.getOrDefault(token, List.of());
    }

    private void sendFinalRoundAssignedEmail(String to, String directorName, String candidateName, String position) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Final Round Assigned — " + candidateName,
                html("Final Round Assigned to You",
                    "<p>Dear <strong>" + directorName + "</strong>,</p>" +
                    "<p>You have been assigned to conduct the <strong>Final Round</strong> for the following candidate, who has already cleared the Technical Interview:</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Candidate", candidateName) + row("Position", position) +
                    "</table>" +
                    "<p>Please log in to the portal to review the technical evaluation, schedule the final interview and generate the candidate's secure interview link.</p>"));
        } catch (Exception e) {
            log.warn("Final round assigned email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendDirectorNotesReadyEmail(String to, String directorName, String candidateName, String position, String recommendation) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Final Round Notes Submitted — " + candidateName,
                html("Final Round Evaluation Ready for Review",
                    "<p><strong>" + directorName + "</strong> has submitted their Final Round interview notes for the following candidate:</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Candidate", candidateName) + row("Position", position) + row("Director's Recommendation", recommendation) +
                    "</table>" +
                    "<p>Please review the evaluation in the portal and record the hiring decision.</p>"));
        } catch (Exception e) {
            log.warn("Director notes ready email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendHrDecisionToDirectorEmail(String to, String candidateName, String position, String outcome) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Hiring Decision Recorded — " + candidateName,
                html("Hiring Decision Recorded",
                    "<p>HR has recorded the final hiring decision for the candidate you interviewed:</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Candidate", candidateName) + row("Position", position) + row("Outcome", outcome) +
                    "</table>" +
                    "<p>Thank you for conducting the Final Round interview.</p>"));
        } catch (Exception e) {
            log.warn("HR decision to director email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendFinalInterviewLinkEmail(String to, String candidateName, String position, String link, String scheduledAt) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Final Interview Invitation — " + position,
                html("Final Interview Invitation",
                    "<p>Dear <strong>" + candidateName + "</strong>,</p>" +
                    "<p>Congratulations on clearing the Technical Round! You have been invited to attend the <strong>Final Interview</strong> for the position of <strong>" + position + "</strong>.</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Scheduled At", scheduledAt) +
                    "</table>" +
                    "<p>The interview will be conducted via a secure video call. Please ensure you are in a quiet, professional environment.</p>" +
                    "<p style='text-align:center;margin:24px 0;'>" +
                    "<a href='" + link + "' style='background:#1e3a5f;color:#fff;padding:14px 32px;text-decoration:none;border-radius:8px;font-size:15px;font-weight:bold;display:inline-block;'>Join Final Interview</a>" +
                    "</p>" +
                    "<p style='font-size:12px;color:#888;'>Or copy this link: <a href='" + link + "'>" + link + "</a></p>" +
                    "<p>All the best!</p>"));
        } catch (Exception e) {
            log.warn("Final interview link email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendFinalApprovedHrEmail(String to, String candidateName, String position, String salary, String joiningDate) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Final Round Approved — " + candidateName,
                html("Candidate Selected",
                    "<p>The Director has <strong>approved</strong> the following candidate after the Final Interview:</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Candidate", candidateName) + row("Position", position) +
                    (salary != null && !salary.isBlank() ? row("Offered CTC", salary) : "") +
                    row("Joining Date", joiningDate) +
                    "</table>" +
                    "<p>Please proceed with offer letter generation and onboarding.</p>"));
        } catch (Exception e) {
            log.warn("Final approved HR email failed for {}: {}", to, e.getMessage());
        }
    }

    private void sendFinalHoldHrEmail(String to, String candidateName, String position) {
        if (to == null || to.isBlank()) return;
        try {
            sendMail(to, "Final Round On Hold — " + candidateName,
                html("Candidate On Hold",
                    "<p>The Director has placed the following candidate <strong>on hold</strong> after the Final Interview:</p>" +
                    "<table style='border-collapse:collapse;width:100%;margin:16px 0;font-size:14px;'>" +
                    row("Candidate", candidateName) + row("Position", position) +
                    "</table>" +
                    "<p>Please await further instructions from the Director before proceeding.</p>"));
        } catch (Exception e) {
            log.warn("Final hold HR email failed for {}: {}", to, e.getMessage());
        }
    }
}
