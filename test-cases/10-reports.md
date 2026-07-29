# 10 — Reports Module

Covers: `Reports.jsx` (report types: `employee-summary`, `monthly-leaves`, `daily-work-report`, `attendance-report`), consolidated per the Reports/Attendance merge. Role gates: `canSeeAllReportTypes=[ADMIN,DIRECTOR]`, `isManagerOrAbove=[ADMIN,DIRECTOR,HR,MANAGER,ASSISTANT_MANAGER]`, `canGenerateAttendance=[ADMIN,DIRECTOR,HR]`.

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-RPT-001 | ADMIN/DIRECTOR default report type is employee-summary | Login as ADMIN | 1) Open Reports page | — | `reportType` defaults to `employee-summary` | P3 | Medium |
| TC-RPT-002 | EMPLOYEE/other roles default to attendance-report | Login as EMPLOYEE | 1) Open Reports page | — | `reportType` defaults to `attendance-report` | P3 | Medium |
| TC-RPT-003 | Employee-summary report visible only to ADMIN/DIRECTOR | Logged in HR | 1) Attempt to select "Employee Summary" report type | — | Option hidden/disabled for HR (`canSeeAllReportTypes`) | P2 | Medium |
| TC-RPT-004 | Monthly-leaves report accessible to manager-or-above | Logged in MANAGER | 1) Select "Monthly Leaves" | — | 200; report scoped to team | P2 | High |
| TC-RPT-005 | Daily-work-report generation | Logged in HR | 1) Select "Daily Work Report", pick date | — | 200; matches JobDailySummary data for the date | P2 | High |
| TC-RPT-006 | Attendance report generation restricted to ADMIN/DIRECTOR/HR | Logged in MANAGER | 1) Attempt attendance-report generation | — | Blocked/disabled per `canGenerateAttendance` (Manager can view team attendance elsewhere but not generate this consolidated report) | P2 | Medium |
| TC-RPT-007 | Attendance report shows correct status chips | Employees with varied statuses on the date | 1) Generate attendance report for a date with PRESENT/UNDER_HOURS/OVERTIME/ABSENT/LEAVE/HOLIDAY/WEEKEND employees | — | Each row's chip color/label matches its `DailyAttendanceStatus` | P2 | Medium |
| TC-RPT-008 | `PENDING_LOGOUT` frontend-only status displayed | Employee has an open session (forgot to log out), report run mid-day | 1) Generate attendance report for today | — | Row shows `PENDING_LOGOUT` (derived client-side, not a backend enum value) — confirm this derivation logic is correct and doesn't crash on edge session states | P2 | Medium |
| TC-RPT-009 | Export report to Excel/CSV/PDF | Report generated | 1) Click Export | format selection | Correct file downloaded matching on-screen data | P2 | Medium |
| TC-RPT-010 | Report with no matching data (empty state) | Date range with zero records | 1) Generate report | far-future date range | Empty-state UI shown, no error/crash | P3 | Medium |
| TC-RPT-011 | Report date-range filter — invalid range (end < start) | — | 1) Set end date before start date | inverted range | Client-side validation blocks submission or shows friendly error | P3 | Medium |
| TC-RPT-012 | Report pagination on large datasets | 500+ rows | 1) Generate org-wide report | — | Pagination controls work, no timeout/performance degradation | P2 | Medium |
| TC-RPT-013 | Manager sees only own team's data across all report types | Logged in MANAGER | 1) Generate each report type | — | No cross-team data leakage in any report | P1 | High |
| TC-RPT-014 | HR sees org-wide data (not team-scoped) | Logged in HR | 1) Generate reports | — | Full org data returned where role permits | P2 | High |
| TC-RPT-015 | Report reflects real-time leave approval | Leave just approved | 1) Generate monthly-leaves report | — | Newly approved leave appears immediately, no caching lag | P3 | Medium |
| TC-RPT-016 | Reports nav item visible to all roles (post role-gating change) | Any of the 6 roles logged in | 1) Check sidebar | — | "Reports" visible to everyone; internal report-type options still respect role gates | P2 | Medium |
