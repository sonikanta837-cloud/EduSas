# 08 — Interview Module (ATS Hiring Pipeline)

Covers: `InterviewController` (`/api/interviews/**`), `Interviews.jsx`. Live pipeline: **CV Bank → HR Screening → Technical Interview → Final Round**.
Status flow: `NEW → UNDER_HR_REVIEW → (HR_REJECTED | TECHNICAL_PENDING) → (TECHNICAL_REJECTED | FINAL_ROUND_PENDING) → (SELECTED | REJECTED)`.
Key design point under test: **two-step Final Round decision separation** — Director records advisory `directorRecommendation` (roles `ADMIN,DIRECTOR`), HR records the binding `finalDecision` (roles `ADMIN,HR`) only after Director notes are submitted.

## CV Bank

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-ATS-001 | Store + parse a resume (step 1 of 2-step create) | HR/Admin/Director logged in | 1) POST `/api/interviews/candidates/store-resume` (multipart PDF) | valid resume PDF | 200; returns `resumePath` + parsed fields (skills, experience, education) | P1 | Medium |
| TC-ATS-002 | Create candidate referencing stored resume (step 2) | `resumePath` from step 1 | 1) POST `/api/interviews/candidates` | `name, appliedProfile` + `resumePath` | 201; candidate created with `status=NEW`, unique `candidateId` generated | P1 | High |
| TC-ATS-003 | Create candidate — missing required `name` | — | 1) POST candidates | `name` omitted | 400 (required `@RequestParam`, no bean validation — verify it's not a raw 500/NPE since DTO has no annotations) | P1 | High |
| TC-ATS-004 | Create candidate — missing required `appliedProfile` | — | 1) POST candidates | omitted | 400 | P1 | High |
| TC-ATS-005 | Upload resume exceeding 10MB | File > 10MB | 1) POST store-resume | 12MB PDF | Spring's `MaxUploadSizeExceededException` → falls to generic handler → 500 (gap: should ideally be a clean 413/400 — flag as improvement) | P2 | Medium |
| TC-ATS-006 | Upload non-PDF/unsupported file type | .exe or .txt disguised as resume | 1) POST store-resume | invalid file type | 400 `IllegalArgumentException` ("Could not extract text...") not 500 | P1 | High |
| TC-ATS-007 | Duplicate check by email | Candidate with same email already exists | 1) POST `/api/interviews/candidates/check-duplicate` | matching email | 200 with duplicate candidate DTO returned | P1 | High |
| TC-ATS-008 | Duplicate check by phone (email differs) | Same phone, different email | 1) POST check-duplicate | matching phone only | 200 with duplicate DTO (priority: email→phone→name) | P2 | High |
| TC-ATS-009 | Duplicate check — no match | Unique candidate | 1) POST check-duplicate | unique email/phone/name | 204 No Content | P2 | High |
| TC-ATS-010 | List all candidates (paginated) | 20+ candidates | 1) GET `/api/interviews/candidates` | — | 200; correct pagination | P2 | High |
| TC-ATS-011 | Export candidate pipeline report | HR/Manager logged in | 1) GET `/api/interviews/candidates/export` | — | 200; xlsx with correct columns | P2 | Medium |
| TC-ATS-012 | View/download resume file | Candidate has resume | 1) GET `/api/interviews/candidates/{id}/resume` | — | 200; correct file streamed | P2 | Medium |
| TC-ATS-013 | Replace resume for existing candidate | Candidate exists with old resume | 1) POST `/api/interviews/candidates/{id}/replace-resume` | new resume file | 200; old resume archived, new one active | P2 | Medium |
| TC-ATS-014 | Reject candidate directly from CV Bank | `status=NEW` | 1) POST `/api/interviews/candidates/{id}/reject` | rejection reason | 200; `status→REJECTED` directly (bypasses HR screening/technical/final) | P2 | High |
| TC-ATS-015 | Open HR screening from CV Bank | `status=NEW` | 1) POST `/api/interviews/candidates/{id}/open-hr` | — | 200; `status→UNDER_HR_REVIEW`; frontend step indicator advances to step 1 | P1 | High |
| TC-ATS-016 | MANAGER attempts CV Bank upload | Logged in MANAGER | 1) POST `/api/interviews/candidates` | valid payload | 403 (CV Bank write ops are `ADMIN,DIRECTOR,HR` only; Manager can view but not create/delete) | P1 | High |
| TC-ATS-017 | Delete a candidate | ADMIN/DIRECTOR only | 1) DELETE `/api/interviews/candidates/{id}` as HR | — | 403 (delete restricted narrower than create — HR can create but not delete) | P1 | High |
| TC-ATS-018 | Update candidate details | HR/Admin/Director | 1) PUT `/api/interviews/candidates/{id}` | corrected phone number | 200 | P3 | High |

## HR Screening

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-ATS-019 | Save HR screening questionnaire | `status=UNDER_HR_REVIEW` | 1) POST `/api/interviews/candidates/{id}/hr-screening` | CTC, notice period, comms rating, etc. | 200; `AtsHrScreening` row saved, `decision=PENDING` | P1 | High |
| TC-ATS-020 | Submit HR decision — SUITABLE | screening filled | 1) POST `/api/interviews/candidates/{id}/hr-screening/decision` | `decision:"SUITABLE"` | 200; `status→TECHNICAL_PENDING` | P1 | High |
| TC-ATS-021 | Submit HR decision — NOT_SUITABLE with rejection reason | — | 1) POST decision | `decision:"NOT_SUITABLE", rejectionReason:"Salary mismatch"` | 200; `status→HR_REJECTED` | P1 | High |
| TC-ATS-022 | HR Screening tab pagination (5/page) | 12 candidates in HR-screening stage | 1) Load HR Screening tab | — | Frontend paginates 5 per page correctly | P3 | Medium |
| TC-ATS-023 | MANAGER attempts to save HR screening | Logged in MANAGER | 1) POST hr-screening | — | 403 (`ADMIN,DIRECTOR,HR` only) | P1 | High |

## Technical Interview

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-ATS-024 | Assign a technical interviewer | `status=TECHNICAL_PENDING` | 1) POST `/api/interviews/candidates/{id}/technical` | `interviewer` employee id | 201; `AtsTechnicalInterview` created, `interviewStatus=PENDING_LINK` | P1 | High |
| TC-ATS-025 | Generate interview video link | Assigned interview exists | 1) POST `/api/interviews/technical/{id}/generate-link` | `interviewTechnology, interviewDifficulty, questionCount, durationMinutes` | 200; unique 24-char `token` generated; `interviewStatus→LINK_GENERATED`; `sendVideoInterviewInvitation` emailed to candidate | P1 | High |
| TC-ATS-026 | Candidate validates token and starts interview (public, no JWT) | Valid, unexpired token | 1) GET `/api/interviews/technical/candidate/{token}` 2) POST `.../start` | valid token | 200; `interviewStatus→IN_PROGRESS`, `startedAt` set | P1 | Medium |
| TC-ATS-027 | Candidate accesses with invalid/expired token | — | 1) GET candidate/{bogusToken} | garbage token | 404/400, no data leaked | P1 | High |
| TC-ATS-028 | Candidate submits an answer | Interview `IN_PROGRESS` | 1) POST `.../answer` | question id + answer text | 200; answer recorded | P1 | Medium |
| TC-ATS-029 | Candidate logs a proctoring violation | Interview in progress | 1) POST `.../violation` | violation type (tab-switch, multiple faces, etc.) | 200; `violationCount` increments | P2 | Medium |
| TC-ATS-030 | Violation count accumulates across multiple events | 3 violations logged | 1) POST violation x3 | — | `violationCount=3` | P2 | Medium |
| TC-ATS-031 | Candidate submits interview | All questions answered | 1) POST `.../submit` | — | 200; `interviewStatus→CANDIDATE_SUBMITTED`, `completedAt` set | P1 | Medium |
| TC-ATS-032 | Candidate uploads recording | Interview submitted | 1) POST `.../recording` (multipart video) | video file | 200; `videoUrl` stored | P2 | Medium |
| TC-ATS-033 | Candidate re-uses token after already submitting | Interview already `CANDIDATE_SUBMITTED`/`EVALUATED` | 1) POST `.../start` again with same token | — | Rejected/blocked — one-time-use enforcement (verify actual behavior; security-relevant) | P1 | High |
| TC-ATS-034 | Reviewer views their assigned interviews | Interviewer logged in | 1) GET `/api/interviews/my-assignments` | — | 200; only their own assignments | P2 | High |
| TC-ATS-035 | Reviewer views their assigned rounds | — | 1) GET `/api/interviews/my-rounds` | — | 200 | P3 | High |
| TC-ATS-036 | Interviewer joins evaluation room | Candidate submitted | 1) GET `/api/interviews/technical/{id}/room` | — | 200; candidate's answers/recording visible for review | P1 | Medium |
| TC-ATS-037 | Interviewer submits evaluation with decision APPROVE | Room reviewed | 1) POST `/api/interviews/technical/{id}/evaluate` | 5 rating dimensions + `decision:"APPROVE"` | 200; `interviewStatus→EVALUATED`, `evaluatedAt` set; `sendInterviewFeedbackEmail` sent | P1 | High |
| TC-ATS-038 | Interviewer submits evaluation with decision REJECT | — | 1) POST evaluate | `decision:"REJECT"` | 200; downstream candidate status should move to `TECHNICAL_REJECTED` on subsequent status-sync | P1 | High |
| TC-ATS-039 | Rating dimensions out of expected 1-5 range | — | 1) POST evaluate | `technicalSkillsRating:10` | Verify server-side bound check exists (no `@Min/@Max` found on entity — likely gap; document) | P2 | Medium |
| TC-ATS-040 | WebRTC signaling exchange (offer/answer via DB polling) | Room open both sides | 1) POST `.../signal/offer` 2) GET `.../signal/answer` | SDP payloads | 200; signaling completes without external STUN/TURN service (DB-polling design) | P2 | Manual |
| TC-ATS-041 | EMPLOYEE (non-interviewer) attempts to submit technical feedback | Logged in EMPLOYEE not assigned as interviewer | 1) POST `/api/interviews/technical/{id}/feedback` | — | Endpoint is `isAuthenticated()` only — verify service-level check restricts to the actual assigned interviewer (IDOR risk if absent) | P1 | High |

## Final Round

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-ATS-042 | Assign a Director for final round | `status=FINAL_ROUND_PENDING` | 1) POST `/api/interviews/candidates/{id}/final/assign-director` as HR/Admin | director id | 200; `AtsFinalRound.assignedBy` set | P1 | High |
| TC-ATS-043 | List available directors | — | 1) GET `/api/interviews/directors` | — | 200; users with role `DIRECTOR` | P3 | High |
| TC-ATS-044 | Director generates final-round link | Director assigned | 1) POST `/api/interviews/candidates/{id}/final/generate-link` as DIRECTOR | — | 200; token generated, `interviewStatus→LINK_GENERATED`, candidate emailed | P1 | High |
| TC-ATS-045 | Candidate completes final round (public token flow) | Valid token | 1) start → answer → submit → recording via `/final/candidate/{token}/**` | — | Mirrors technical flow; `interviewStatus→CANDIDATE_SUBMITTED` | P1 | Medium |
| TC-ATS-046 | Director submits interview notes + advisory recommendation | Candidate submitted final round | 1) POST `/api/interviews/candidates/{id}/final` | `directorRemarks, directorRecommendation:"APPROVE"` | 200; `directorRecommendation` + `directorNotesAt` set; `finalDecision` still `PENDING` (advisory only, not binding) | P1 | High |
| TC-ATS-047 | HR attempts to record final decision BEFORE director notes submitted | `directorNotesSubmitted=false` | 1) POST `/api/interviews/final/{id}/decision` as HR | `finalDecision:"APPROVE"` | Frontend hides this action (`canRecordHrDecision` false); backend should also reject — verify server-side guard exists independent of UI (critical: don't rely on frontend-only enforcement) | P1 | High |
| TC-ATS-048 | HR records binding final decision after director notes exist | `directorNotesSubmitted=true` | 1) POST `/api/interviews/final/{id}/decision` | `finalDecision:"APPROVE", offeredCtc, joiningDate` | 200; `decidedBy/decidedAt` set; candidate `status→SELECTED` | P1 | High |
| TC-ATS-049 | DIRECTOR (not HR) attempts to record the binding final decision | Logged in DIRECTOR | 1) POST `/final/{id}/decision` | — | 403 (`ADMIN,HR` only — Director's role here is advisory notes, not the decision endpoint) | P1 | High |
| TC-ATS-050 | HR attempts to submit director notes | Logged in HR | 1) POST `/api/interviews/candidates/{id}/final` | — | 403 (`ADMIN,DIRECTOR` only) | P1 | High |
| TC-ATS-051 | HR final decision — REJECT | — | 1) POST `/final/{id}/decision` | `finalDecision:"REJECT"` | 200; candidate `status→REJECTED` | P1 | High |
| TC-ATS-052 | HR final decision — HOLD | — | 1) POST decision | `finalDecision:"HOLD"` | 200; status remains at final stage, no terminal transition | P2 | High |
| TC-ATS-053 | Director's "Join Room" hidden from HR in UI | Logged in HR | 1) Open Final Round tab for a candidate | — | Director evaluation/room view not rendered for HR (`!isHR && ...` guard) | P2 | Manual |
| TC-ATS-054 | HR decision section hidden from Admin/Director in UI | Logged in DIRECTOR | 1) Open Final Round tab | — | HR-decision UI section not shown to Director (frontend role gate, `Interviews.jsx:1859-1864`) | P2 | Manual |

## Stats & Cross-cutting

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-ATS-055 | Pipeline stats endpoint | — | 1) GET `/api/interviews/stats` | — | 200; counts per stage/status accurate | P2 | High |
| TC-ATS-056 | Candidate status→step mapping (frontend) | Candidates in each status | 1) Load Interviews.jsx for candidates at NEW, UNDER_HR_REVIEW, TECHNICAL_PENDING, SELECTED | — | Stepper shows step 0/1/2/3 respectively per `statusToStep()` mapping | P3 | Manual |
| TC-ATS-057 | Legacy `/api/video-interview/**` endpoints reachable only via API (no UI route) | — | 1) Attempt to navigate to `VideoInterviews.jsx`/`CandidateInterview.jsx` routes in browser | — | 404/no route — confirms these pages are orphaned; API itself should still be tested at the HTTP layer for regressions since the entities remain live | P3 | Manual |
| TC-ATS-058 | Legacy `InterviewReminderTask` doesn't fire spuriously | No `InterviewRound` rows are ever created by current flows | 1) Monitor scheduled task logs over time | — | No reminder emails sent (dead code path confirmed, not silently misfiring) | P4 | Low |
