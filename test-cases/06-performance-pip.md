# 06 — Performance Reviews & Performance Improvement Plans (PIP)

Covers: `PerformanceController` (`/api/performance/**`), `PipController` (`/api/pip/**`), `PipNotificationController`. Low-rating threshold configurable via Settings (default 3.0).

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-PERF-001 | Create performance review — valid | Manager logged in, reviewing a direct report | 1) POST `/api/performance` | `rating:4, reviewDate:today, reviewPeriod:"Q2-2026", comments:"Good work"` | 201; review saved | P1 | High |
| TC-PERF-002 | Create review — rating below min (0) | — | 1) POST | `rating:0` | 400 (`@Min(1)`) | P2 | High |
| TC-PERF-003 | Create review — rating above max (6) | — | 1) POST | `rating:6` | 400 (`@Max(5)`) | P2 | High |
| TC-PERF-004 | Create review — rating boundary 1 and 5 | — | 1) POST twice | `rating:1` then `rating:5` | Both 201 accepted | P3 | High |
| TC-PERF-005 | Create review — future reviewDate | — | 1) POST | `reviewDate: tomorrow` | 400 (`@PastOrPresent`) | P2 | High |
| TC-PERF-006 | Create review — blank comments | — | 1) POST | `comments:""` | 400 (`@NotBlank`) | P2 | High |
| TC-PERF-007 | Create review — missing employeeId/reviewerId | — | 1) POST | `employeeId:null` | 400 (`@NotNull`) | P2 | High |
| TC-PERF-008 | EMPLOYEE attempts to create a review | Logged in EMPLOYEE | 1) POST `/api/performance` | valid payload | 403 (create restricted to `ADMIN,DIRECTOR,MANAGER,ASSISTANT_MANAGER`) | P1 | High |
| TC-PERF-009 | View reviews for an employee | Employee has 2 reviews | 1) GET `/api/performance/employee/{id}` | — | 200; both returned | P2 | High |
| TC-PERF-010 | View reviews written by a reviewer | Manager wrote 3 reviews | 1) GET `/api/performance/reviewer/{id}` | — | 200; 3 returned | P2 | High |
| TC-PERF-011 | Get average rating for employee | Employee has ratings [3,4,5] | 1) GET `/api/performance/employee/{id}/average` | — | 200; average = 4.0 | P2 | High |
| TC-PERF-012 | Update existing review | Review exists | 1) PUT `/api/performance/{id}` | updated `rating:2` | 200; persisted | P2 | High |
| TC-PERF-013 | Delete a review | — | 1) DELETE `/api/performance/{id}` | — | 200; removed | P3 | High |
| TC-PERF-014 | ADMIN/HR views all reviews org-wide | — | 1) GET `/api/performance` | — | 200; all reviews (role includes HR here, unlike create) | P2 | High |
| TC-PERF-015 | Export performance report — CSV/Excel/PDF | HR logged in | 1) GET `/api/performance/export?reportType=excel&...filters` | various filter combos (`department, ratingMin, belowThresholdOnly, pipOnly`) | 200; file generated matching filters; `PerformanceExportAuditLog` row written | P2 | Medium |
| TC-PERF-016 | Export audit log — access restricted | MANAGER logged in | 1) GET `/api/performance/export/audit-log` | — | 403 (only `ADMIN,DIRECTOR`) | P2 | High |
| TC-PERF-017 | belowThresholdOnly filter uses configured threshold | Settings threshold = 3.0; employee avg = 2.5 | 1) GET export with `belowThresholdOnly=true` | — | Only employees averaging < 3.0 included | P2 | Medium |
| TC-PERF-018 | Create PIP for a low-performing employee | Manager/HR logged in | 1) POST `/api/pip` | employee, goals, start/end dates | 201; PIP created; `sendPipCreatedEmail` sent to employee, CC manager+admin | P1 | High |
| TC-PERF-019 | EMPLOYEE attempts to create a PIP | Logged in EMPLOYEE | 1) POST `/api/pip` | — | 403 | P1 | High |
| TC-PERF-020 | View own PIP as EMPLOYEE | Employee has an active PIP | 1) GET `/api/pip/employee/{ownId}` | — | 200 — EMPLOYEE explicitly allowed to view (unlike create) | P2 | High |
| TC-PERF-021 | Add goal to a PIP | PIP exists | 1) POST `/api/pip/{id}/goals` | goal description, target date | 201; goal linked | P2 | High |
| TC-PERF-022 | Update/delete a PIP goal | Goal exists | 1) PUT then DELETE `/api/pip/{id}/goals/{goalId}` | — | 200 both operations | P3 | High |
| TC-PERF-023 | Submit weekly review on a PIP | Active PIP | 1) POST `/api/pip/{id}/reviews` | progress notes, rating | 201; weekly review recorded | P2 | High |
| TC-PERF-024 | Employee adds a comment to their own PIP | Active PIP, EMPLOYEE logged in | 1) POST `/api/pip/{id}/comments` | comment text | 201 — all roles including EMPLOYEE can comment | P3 | High |
| TC-PERF-025 | Set PIP outcome — COMPLETED | PIP nearing end date | 1) PUT `/api/pip/{id}/outcome` | `outcome:"COMPLETED"` | 200; `sendPipOutcomeEmail` sent to employee, CC manager+admin | P1 | High |
| TC-PERF-026 | Set PIP outcome — TERMINATED | — | 1) PUT outcome | `outcome:"TERMINATED"` | 200; correct email content for termination outcome | P1 | Low |
| TC-PERF-027 | Set PIP outcome — EXTENDED | — | 1) PUT outcome | `outcome:"EXTENDED"` | 200; extended end date reflected | P2 | High |
| TC-PERF-028 | Delete a PIP | ADMIN/DIRECTOR only | 1) DELETE `/api/pip/{id}` as HR | — | 403 (delete restricted to `ADMIN,DIRECTOR`) | P2 | High |
| TC-PERF-029 | PIP unread notifications badge | New PIP-related notification exists for user | 1) GET `/api/pip-notifications/unread-count` | — | 200; correct unread count | P3 | High |
| TC-PERF-030 | Mark PIP notification as read | Unread notification exists | 1) POST `/api/pip-notifications/{id}/read` | — | 200; count decrements | P3 | High |
| TC-PERF-031 | Mark all PIP notifications read | Multiple unread | 1) POST `/api/pip-notifications/read-all` | — | 200; unread-count = 0 | P3 | High |
