# 13 — File Upload, Resume Parsing & Data Export (Cross-Module)

Covers: `ResumeController`, ATS resume endpoints, all Excel/CSV/PDF export endpoints, global multipart limits (`spring.servlet.multipart.max-file-size=10MB`).

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-FILE-001 | Standalone resume parse — valid PDF, ADMIN/DIRECTOR | Logged in ADMIN | 1) POST `/api/resume/parse` | text-based PDF resume | 200; extracted text/fields returned | P1 | Medium |
| TC-FILE-002 | Standalone resume parse — HR attempts (narrower than ATS endpoint) | Logged in HR | 1) POST `/api/resume/parse` | valid PDF | 403 — this endpoint is `ADMIN,DIRECTOR` only, unlike the ATS `store-resume`/`parse-resume` which do allow HR | P1 | High |
| TC-FILE-003 | Resume parse — empty file | — | 1) POST parse | 0-byte file | 400 | P2 | High |
| TC-FILE-004 | Resume parse — scanned/image-only PDF (no extractable text) | Scanned PDF, PDFBox text extraction returns empty | 1) POST parse | scanned PDF | Falls back to Claude vision API config path if enabled, else 400 "Could not extract text from the uploaded file" | P2 | Medium |
| TC-FILE-005 | Resume parse — corrupted/non-PDF binary | Renamed .exe as .pdf | 1) POST parse | corrupted file | 400 (not 500) — verify PDFBox failure is caught cleanly | P1 | High |
| TC-FILE-006 | Resume parse — unexpected internal error path | Simulated internal failure | 1) POST parse under fault condition | — | 500 generic message only as last resort — confirm 4xx is preferred wherever a known failure mode exists | P3 | Low |
| TC-FILE-007 | Multipart upload exactly at 10MB boundary | — | 1) Upload a 10MB file to any multipart endpoint | 10,485,760 bytes exactly | Accepted (boundary inclusive per Spring config) | P2 | Medium |
| TC-FILE-008 | Multipart upload just over 10MB | — | 1) Upload 10MB + 1 byte | — | Rejected — `MaxUploadSizeExceededException`; verify response isn't a raw uncaught 500 with stack trace exposed (currently falls to generic "unexpected error" handler — flag as improvement opportunity) | P2 | Medium |
| TC-FILE-009 | Candidate recording upload (public token endpoint) — file size limit | Valid interview token | 1) POST `.../recording` with an 11MB video | — | Rejected per same global limit | P2 | Medium |
| TC-FILE-010 | Question bank xlsx import — malformed file (wrong sheet structure) | — | 1) POST `/api/interview-questions/import` | corrupted/wrong-schema xlsx | 400, clear error message, no partial half-imported state | P2 | Medium |
| TC-FILE-011 | Question bank xlsx export/import round-trip | Export then re-import same file | 1) GET export 2) POST import of the exported file | — | Imported data matches original (schema compatibility) | P2 | Medium |
| TC-FILE-012 | Candidate pipeline export (ATS) — correct role gating | MANAGER logged in | 1) GET `/api/interviews/candidates/export` | — | 200 (Manager explicitly allowed to view/export, unlike create/delete) | P2 | High |
| TC-FILE-013 | Job-summaries CSV export — manager-scoped data only | Manager exporting | 1) GET `/api/job-summaries/export` | — | CSV contains only direct reports' rows, not org-wide | P1 | High |
| TC-FILE-014 | Performance export — all 3 formats (csv/excel/pdf) | HR logged in | 1) GET export with `reportType=csv`, then `excel`, then `pdf` | — | Each format downloads correctly with matching data | P2 | Medium |
| TC-FILE-015 | Performance export writes audit log entry every time | — | 1) Trigger 3 exports 2) GET `/export/audit-log` | — | 3 audit rows, one per export, correct user/timestamp/filters recorded | P2 | High |
| TC-FILE-016 | Leave-upload template download | ADMIN/DIRECTOR | 1) GET `/api/leave-upload/template` | — | 200; xlsx with correct header columns for the bulk-import format | P3 | High |
| TC-FILE-017 | Certificate PDF download — content correctness | Certificate exists | 1) GET `/api/certificates/download?certNo=` | valid cert no | PDF renders correct employee name, course, completion date, cert number | P2 | Manual |
| TC-FILE-018 | Resource upload — path traversal in filename | Malicious filename | 1) POST `/api/resources/upload` | filename `"../../etc/passwd"` | Filename sanitized; file stored safely within `./uploads`, no directory traversal | P1 | High |
| TC-FILE-019 | Resume filename with special characters/unicode | — | 1) Upload resume with filename `"résumé (final) v2.pdf"` | — | Stored and retrievable without corruption/error | P3 | Medium |
| TC-FILE-020 | Concurrent uploads to `./uploads` don't collide | Two uploads with identical filename, near-simultaneous | 1) Upload same filename twice quickly | — | Both stored distinctly (unique naming/versioning), neither silently overwrites the other | P2 | Medium |
| TC-FILE-021 | Export with zero matching records | Filters yield no data | 1) GET any export endpoint with narrow filters | filters matching nothing | Valid empty file returned (correct headers, no rows) — not a 500/crash | P3 | Medium |
| TC-FILE-022 | Excel export handles large dataset without timeout | 5,000+ rows | 1) GET performance/job-summary export org-wide | — | Completes within acceptable time (see NFR suite for exact threshold), file integrity intact | P2 | Low |
