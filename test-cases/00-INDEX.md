# EmpSAS — End-to-End QA Test Suite (Index)

Comprehensive test case suite for the Employee Management System (EmpSAS), covering every module discovered in the codebase: `backend/` (Spring Boot 3.2 / Java 17) and `frontend/` (React 18 / MUI v5), branch `new-interview-module`.

This suite is a **documentation deliverable only** — no application code was modified to produce it.

## Legend

**Priority**
- **P1 – Critical**: Core business flow, security, or data-integrity risk. Must pass before any release.
- **P2 – High**: Important functional path or common user action.
- **P3 – Medium**: Secondary/edge functionality, less frequent path.
- **P4 – Low**: Cosmetic, rare edge case, nice-to-have validation.

**Automation Feasibility**
- **High**: Pure API/unit-level, deterministic, no external timing — good fit for REST-assured/JUnit/Cypress/Playwright.
- **Medium**: UI-level, deterministic but needs stable selectors/mocking (file upload, dialogs, multi-step forms).
- **Low**: Depends on real SMTP delivery, WebRTC/video, scheduled `@Scheduled` jobs, or wall-clock timing — feasible only with heavy mocking/time-travel.
- **Manual**: Requires human judgment (visual layout, video/audio quality, proctoring UX) or one-off exploratory checks.

## Modules

| # | File | Module | Cases |
|---|---|---|---|
| 1 | [01-authentication.md](01-authentication.md) | Auth: Login, Register, Refresh, Forgot/Reset Password, JWT, Rate Limiting | 44 |
| 2 | [02-employee-management.md](02-employee-management.md) | Employee CRUD, Employee Detail, Org Chart, HR Assignment | 39 |
| 3 | [03-leave-management.md](03-leave-management.md) | Leave apply/approve/reject, Public Holidays | 27 |
| 4 | [04-attendance-timesheet.md](04-attendance-timesheet.md) | Job Sessions (clock in/out/break/switch), Job Daily Summary, Timesheet Entries | 45 |
| 5 | [05-working-hours-correction.md](05-working-hours-correction.md) | Correction request submit/approve/reject, audit trail | 23 |
| 6 | [06-performance-pip.md](06-performance-pip.md) | Performance Reviews, Performance Improvement Plans | 31 |
| 7 | [07-courses-training.md](07-courses-training.md) | Courses, Enrollment, Exam, Certificates | 28 |
| 8 | [08-interview-ats.md](08-interview-ats.md) | ATS pipeline: CV Bank → HR Screening → Technical → Final Round | 58 |
| 9 | [09-question-bank.md](09-question-bank.md) | Interview Question Bank | 14 |
| 10 | [10-reports.md](10-reports.md) | Reports.jsx: employee summary, monthly leaves, daily work, attendance | 16 |
| 11 | [11-settings-permissions.md](11-settings-permissions.md) | System Settings, Portal Permissions, Per-user Module Overrides | 20 |
| 12 | [12-hr-utilities.md](12-hr-utilities.md) | Announcements, Holidays, FAQs, Resources, Leave Bulk Upload, Master Data | 26 |
| 13 | [13-file-upload-export.md](13-file-upload-export.md) | Resume parsing, all Excel/CSV/PDF exports, upload limits | 22 |
| 14 | [14-email-notifications.md](14-email-notifications.md) | Every EmailService trigger, cross-module | 20 |
| 15 | [15-security.md](15-security.md) | AuthZ bypass, IDOR, JWT tampering, injection, info disclosure | 26 |
| 16 | [16-performance-nfr.md](16-performance-nfr.md) | Load, concurrency, response time, scheduled-job overlap | 14 |
| 17 | [17-database-integrity.md](17-database-integrity.md) | Constraints, cascades, `ddl-auto=update` enum gap, data consistency | 15 |
| 18 | [18-regression.md](18-regression.md) | Cross-module regression checklist tied to recent commits | 16 |

**Total: 484 test cases.**

## Known system gaps surfaced during test design (flag to dev team, not defects to silently work around)

1. `VideoInterviews.jsx` / `CandidateInterview.jsx` and their backend (`/api/video-interview/**`) have no registered frontend routes — API-testable only, UI unreachable.
2. `/api/settings/correction-policy` has no corresponding panel in `Settings.jsx` — backend-only, UI unreachable.
3. Legacy `InterviewRound`/`InterviewCandidate` entities and `InterviewReminderTask` are still scheduled but nothing seeds rows anymore — effectively dead code paths.
4. `ProtectedRoute` ignores its own `roles` prop at the router level; role gating is enforced by `PortalGuard` + per-page checks instead — worth confirming no route regresses this assumption.
5. Generic 500 handler leaks `ex.getMessage()` to the client — see Security suite TC-SEC-020+.
6. Two different 403 JSON shapes exist depending on whether Spring Security or `GlobalExceptionHandler` intercepts the denial — assert on the correct one per endpoint.
