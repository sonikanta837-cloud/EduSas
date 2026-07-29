# 04 — Attendance & Timesheet (Job Sessions / Job Daily Summary / Timesheet Entries)

Covers: `JobWorkSessionController`, `JobDailySummaryController`, `TimesheetController`, `TimesheetMasterController`. Legacy `AttendanceSession` is retired — attendance sources exclusively from `JobWorkSession`/`JobDailySummary`.

Key rules: one open session per employee (`unique(employee_id, open_flag)`); required minutes default 480 (8h); status precedence Holiday > Weekend > zero-sessions(Leave/Absent) > Under-hours > Overtime > Present; nightly rollup at 23:55 IST; break/under-hours alert emails guarded by `alertSent` flags.

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-ATT-001 | Clock in (login) — valid session start | Employee has no open session; active Client/Job/JobType/PeriodEnd master values exist | 1) POST `/api/job-sessions/login` | valid `employeeId,clientId,jobId,jobTypeId,periodEndId` | 200/201; session created with `openFlag=true`, `loginTime=now` | P1 | High |
| TC-ATT-002 | Clock in while an open session already exists | Employee already has an open session | 1) POST login again | same employee | 400 "You already have an active job session — please log out before starting a new one" | P1 | High |
| TC-ATT-003 | Clock in with inactive Client/Job master value | Client marked inactive | 1) POST login | inactive `clientId` | 400 rejection (master value must be active + correct `MasterDataType`) | P2 | High |
| TC-ATT-004 | Clock in with missing required field | — | 1) POST login | `jobTypeId:null` | 400 (`@NotNull` on `JobSessionLoginRequest`) | P2 | High |
| TC-ATT-005 | Clock out (logout) — valid | Open session exists | 1) POST `/api/job-sessions/logout/{employeeId}` | — | 200; `logoutTime` set, `sessionMinutes` computed, `status=CLOSED`, `openFlag=null` | P1 | High |
| TC-ATT-006 | Clock out auto-closes dangling open break | Session has an active (unended) break | 1) Logout | — | Break auto-closed at logout time, doesn't block logout | P2 | High |
| TC-ATT-007 | Logout with no open session | Employee has no active session | 1) POST logout | — | Error response (no session to close) — verify graceful message not 500 | P2 | High |
| TC-ATT-008 | Switch job mid-day | Open session on Job A | 1) POST `/api/job-sessions/switch` | new `jobId=B`, same employee | 200; old session closed with correct `sessionMinutes`, new session opened atomically (no unique-constraint violation) | P1 | High |
| TC-ATT-009 | Switch job — race/atomicity check | Rapid successive switch calls | 1) Fire 2 switch requests near-simultaneously | — | No duplicate open sessions / constraint violation (verifies `saveAndFlush` atomicity) | P2 | Medium |
| TC-ATT-010 | Start break | Open session, no active break | 1) POST `/api/job-sessions/break/start/{employeeId}` | — | 200; `JobSessionBreak` row created with `breakStartTime=now` | P1 | High |
| TC-ATT-011 | Start break with no open session | No active session | 1) POST break/start | — | 400 "no open session" | P2 | High |
| TC-ATT-012 | Start break when a break is already in progress | Active break exists | 1) POST break/start again | — | 400 "break already in progress" | P2 | High |
| TC-ATT-013 | End break | Active break exists | 1) POST `/api/job-sessions/break/end/{employeeId}` | — | 200; `breakEndTime` set, `breakMinutes` computed | P1 | High |
| TC-ATT-014 | End break with no active break | No open break | 1) POST break/end | — | 400 "no active break" | P2 | High |
| TC-ATT-015 | Get today's session for employee | Session exists for today | 1) GET `/api/job-sessions/today/{employeeId}` | — | 200; correct session detail | P2 | High |
| TC-ATT-016 | Get session range for employee | Multiple sessions over a date range | 1) GET `/api/job-sessions/range/{employeeId}?from&to` | date range | 200; only sessions within range returned | P2 | High |
| TC-ATT-017 | Employee attempts to view another employee's sessions (not privileged) | Logged in EMPLOYEE, target ≠ self | 1) GET `/api/job-sessions/today/{otherId}` | — | `AccessDeniedException` (403) via `requireSelfOrAdmin`/`requireSelfOrPrivileged` check | P1 | High |
| TC-ATT-018 | Manager/HR/Admin view work-report across team | Logged in MANAGER | 1) GET `/api/job-sessions/work-report` | — | 200; scoped to direct reports (or org-wide for Admin/HR/Director) | P1 | High |
| TC-ATT-019 | Break-duration alert email (>60 min post-logout gap) | Employee logged out, then gap between sessions exceeds 60 min, `monitorBreaks()` scheduled task runs | 1) Simulate/wait for `@Scheduled(fixedRate=5min)` run | gap > threshold | Email sent to manager+employee once; `breakAlertSent=true` set; `JobBreakAlertLog` row created | P2 | Low |
| TC-ATT-020 | Break-duration alert does not resend | `breakAlertSent` already true from prior run | 1) Run monitor task again | — | No duplicate email; guard flag respected | P2 | Low |
| TC-ATT-021 | In-session break alert (>60 min active break) | `monitorActiveBreaks()` @Scheduled(60s) detects long open break | 1) Leave a break open >60 min, wait for task | — | Email sent once, `JobSessionBreak.alertSent=true`, `JobBreakAlertLog` row created | P2 | Low |
| TC-ATT-022 | Daily summary computes `PRESENT` correctly | Employee worked exactly required minutes (480) | 1) GET `/api/job-summaries/my` for that date | — | `status=PRESENT`, `overtimeMinutes=0` | P1 | High |
| TC-ATT-023 | Daily summary computes `UNDER_HOURS` | Employee worked 400 min (< 480) | 1) GET job-summary | — | `status=UNDER_HOURS` | P1 | High |
| TC-ATT-024 | Daily summary computes `OVERTIME` | Employee worked 550 min (> 480) | 1) GET job-summary | — | `status=OVERTIME`, `overtimeMinutes=70` | P1 | High |
| TC-ATT-025 | Daily summary boundary — exactly required minutes | Worked exactly 480 min | 1) GET job-summary | — | `status=PRESENT` (not overtime/under-hours at exact boundary) | P2 | High |
| TC-ATT-026 | Daily summary precedence — Holiday overrides Under-hours | Date is a public holiday, employee worked 100 min | 1) GET job-summary for that date | — | `status=HOLIDAY` (holiday takes precedence over hours-based status) | P2 | High |
| TC-ATT-027 | Daily summary precedence — Weekend overrides zero-sessions | Saturday, no sessions | 1) GET job-summary | — | `status=WEEKEND` (not `ABSENT`) | P2 | High |
| TC-ATT-028 | Daily summary — zero sessions with approved leave | Approved leave covers the date, no clock-in | 1) GET job-summary | — | `status=LEAVE` | P1 | High |
| TC-ATT-029 | Daily summary — zero sessions, no leave | Weekday, no clock-in, no leave | 1) GET job-summary | — | `status=ABSENT` | P1 | High |
| TC-ATT-030 | Under-hours consolidated audit email (11:00 IST weekdays) | Yesterday was a weekday with `UNDER_HOURS` employees | 1) Trigger/wait for `runUnderHoursAuditEmail()` | — | Manager+CC HR/Admin receive consolidated email; `underHoursAlertSent=true`; `UnderHoursAlertLog` row created; excludes employees on approved leave | P2 | Low |
| TC-ATT-031 | Under-hours audit skips weekends | Scheduled task fires on Sat/Sun | 1) Verify no audit run occurs | — | No emails sent on Sat/Sun | P3 | Low |
| TC-ATT-032 | Nightly rollup generates summaries at 23:55 IST | End of day reached | 1) Wait for/trigger `generateDailySummaries()` | — | `JobDailySummary` row created/updated for every active employee for that date | P1 | Low |
| TC-ATT-033 | Export job summaries as CSV | Manager/HR logged in | 1) GET `/api/job-summaries/export` | date range | 200; CSV file with correct columns; manager-scoped to own reports unless Admin/HR/Director | P2 | Medium |
| TC-ATT-034 | Backfill summaries via `generate/range` | Missing historical summaries | 1) POST `/api/job-summaries/generate/range` as ADMIN/DIRECTOR/HR | date range | 200; summaries regenerated for the range | P2 | Medium |
| TC-ATT-035 | Timesheet entry — valid hours | Employee logging project time | 1) POST `/api/timesheets/entries` | `hours:8.5, projectName:"Alpha", date` | 201; entry saved | P1 | High |
| TC-ATT-036 | Timesheet entry — hours = 0 (boundary, exclusive min) | — | 1) POST entries | `hours:0` | 400 (`@DecimalMin(0.0, inclusive=false)`) | P2 | High |
| TC-ATT-037 | Timesheet entry — hours = 24.0 (boundary, inclusive max) | — | 1) POST entries | `hours:24.0` | 201 accepted | P2 | High |
| TC-ATT-038 | Timesheet entry — hours = 24.01 (just above max) | — | 1) POST entries | `hours:24.01` | 400 (`@DecimalMax(24.0)`) | P2 | High |
| TC-ATT-039 | Timesheet entry — blank projectName | — | 1) POST entries | `projectName:""` | 400 (`@NotBlank`) | P2 | High |
| TC-ATT-040 | Timesheet entry — negative hours | — | 1) POST entries | `hours:-1` | 400 | P2 | High |
| TC-ATT-041 | Delete single timesheet entry | Entry exists, owner or privileged | 1) DELETE `/api/timesheets/entries/{id}` | — | 200; entry removed | P3 | High |
| TC-ATT-042 | Delete all entries for a project | Multiple entries under one project | 1) DELETE `/api/timesheets/entries/project` | `projectName` | 200; all matching entries removed | P3 | High |
| TC-ATT-043 | Timesheet master data — add new Client/Job/JobType/PeriodEnd | Any authenticated role | 1) POST `/api/timesheet-master/{type}` | `type=CLIENT`, `value="Acme Corp"` | 201; new master row created (any role permitted to add) | P3 | High |
| TC-ATT-044 | Timesheet master data — update existing value | HR/Admin/Director | 1) PUT `/api/timesheet-master/{id}` | updated value | 200 (update restricted to `ADMIN,DIRECTOR,HR`) | P3 | High |
| TC-ATT-045 | EMPLOYEE attempts to update master data | Logged in EMPLOYEE | 1) PUT `/api/timesheet-master/{id}` | — | 403 | P2 | High |
