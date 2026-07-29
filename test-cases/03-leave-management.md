# 03 — Leave Management

Covers: `LeaveController` (`/api/leaves/**`), `Leave` entity, `LeaveStatus{PENDING,APPROVED,REJECTED}`.

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-LEAVE-001 | Apply for leave — valid request | Logged-in employee | 1) POST `/api/leaves/apply/{employeeId}` | `leaveType:"Sick"`, `startDate`, `endDate`, `reason` | 201; status defaults `PENDING`; `appliedAt` auto-set; email to manager sent | P1 | High |
| TC-LEAVE-002 | Apply for leave — blank leaveType | — | 1) POST apply | `leaveType:""` | 400 (`@NotBlank`) | P2 | High |
| TC-LEAVE-003 | Apply for leave — missing startDate/endDate | — | 1) POST apply | `startDate:null` | 400 (`@NotNull`) | P2 | High |
| TC-LEAVE-004 | Apply for leave — endDate before startDate | — | 1) POST apply | `startDate:"2026-08-10"`, `endDate:"2026-08-05"` | No `@Valid` cross-field constraint exists — verify if service rejects or silently accepts an inverted range (likely bug/gap; document actual behavior) | P1 | High |
| TC-LEAVE-005 | Apply for leave — startDate in the past | — | 1) POST apply | `startDate: 30 days ago` | Verify whether past-dated leave is blocked or allowed (no explicit rule found — confirm intended behavior with product owner) | P2 | Medium |
| TC-LEAVE-006 | Apply for leave spanning a public holiday | Public holiday falls within range | 1) POST apply spanning the holiday date | date range including holiday | Verify `totalDays` calculation excludes/includes holiday per business rule | P2 | Medium |
| TC-LEAVE-007 | Apply for overlapping leave (duplicate date range) | Employee already has a `PENDING`/`APPROVED` leave for overlapping dates | 1) POST apply again for overlapping dates | overlapping range | Verify whether overlap is blocked (no explicit constraint found — likely gap; document) | P2 | Medium |
| TC-LEAVE-008 | View own leave history | Employee has 3 leave records | 1) GET `/api/leaves/employee/{employeeId}` | — | 200; all 3 returned regardless of status | P2 | High |
| TC-LEAVE-009 | Manager views pending leaves for their team | Manager has 2 direct reports with pending leaves | 1) GET `/api/leaves/manager/{managerId}/pending` | — | 200; only `PENDING` leaves for direct reports | P1 | High |
| TC-LEAVE-010 | Manager views all team leaves (any status) | — | 1) GET `/api/leaves/manager/{managerId}` | — | 200; all statuses returned | P2 | High |
| TC-LEAVE-011 | EMPLOYEE attempts manager's pending-leaves endpoint | Logged in EMPLOYEE | 1) GET `/api/leaves/manager/{id}/pending` | — | 403 (role restricted to `ADMIN,DIRECTOR,MANAGER,ASSISTANT_MANAGER,HR`) | P1 | High |
| TC-LEAVE-012 | Approve a pending leave | Manager, leave in `PENDING` | 1) PATCH `/api/leaves/{leaveId}/action` | `{"status":"APPROVED","comment":"Enjoy!"}` | 200; status→`APPROVED`; `actionDate` set; decision email sent to employee (CC HR+Admin) | P1 | High |
| TC-LEAVE-013 | Reject a pending leave | Manager, leave in `PENDING` | 1) PATCH action | `{"status":"REJECTED","comment":"Team short-staffed"}` | 200; status→`REJECTED`; email sent | P1 | High |
| TC-LEAVE-014 | Action with invalid status string | — | 1) PATCH action | `{"status":"CANCELLED"}` | 400 "Invalid status: CANCELLED" (not a valid `LeaveStatus` enum) | P2 | High |
| TC-LEAVE-015 | Action with blank status | — | 1) PATCH action | `{"status":""}` | 400 "status is required" | P2 | High |
| TC-LEAVE-016 | Action with lowercase status (case-insensitivity check) | — | 1) PATCH action | `{"status":"approved"}` | 200 — status parsed via uppercase conversion before `valueOf` | P3 | High |
| TC-LEAVE-017 | Action on an already-decided leave | Leave already `APPROVED` | 1) PATCH action again | `{"status":"REJECTED"}` | Verify whether re-action is blocked or allowed to flip status (no explicit guard found in inventory — document actual behavior; likely gap) | P2 | Medium |
| TC-LEAVE-018 | ADMIN/HR/DIRECTOR view all leaves org-wide | — | 1) GET `/api/leaves` | — | 200; all leave records across all employees | P2 | High |
| TC-LEAVE-019 | MANAGER attempts org-wide all-leaves endpoint | Logged in MANAGER | 1) GET `/api/leaves` | — | 403 (restricted to `ADMIN,HR,DIRECTOR`) | P1 | High |
| TC-LEAVE-020 | Employee edits own pending leave | Leave still `PENDING`, owner editing | 1) PUT `/api/leaves/{leaveId}?employeeId={id}` | updated reason/dates | 200 | P2 | High |
| TC-LEAVE-021 | Employee attempts to edit another employee's leave | `employeeId` param ≠ leave owner | 1) PUT `/api/leaves/{leaveId}?employeeId={otherId}` | — | Verify ownership enforced in service layer — should be rejected (IDOR risk if not, see TC-SEC-006) | P1 | High |
| TC-LEAVE-022 | Delete own leave request | Leave owned by requester | 1) DELETE `/api/leaves/{leaveId}` | — | 200; row removed/cancelled | P3 | High |
| TC-LEAVE-023 | Get public holidays list | — | 1) GET `/api/leaves/public-holidays` | — | 200; list of holidays visible to any authenticated user | P3 | High |
| TC-LEAVE-024 | Leave request email content correctness | Employee applies for leave | 1) Apply leave 2) Inspect sent email | — | Email to manager, CC HR+Admin, contains employee name/dates/reason | P2 | Low |
| TC-LEAVE-025 | Leave decision email content correctness | Manager approves/rejects | 1) Approve/reject 2) Inspect email | — | Email to employee, CC HR+Admin, contains decision + manager comment | P2 | Low |
| TC-LEAVE-026 | totalDays calculated correctly for single-day leave | — | 1) Apply with startDate=endDate | same date both fields | `totalDays = 1` | P3 | High |
| TC-LEAVE-027 | Leave request reflected in attendance/daily-summary as `LEAVE` status | Approved leave for a given date, no clock-in that day | 1) Approve leave for date D 2) GET job-summary for date D | — | `DailyAttendanceStatus = LEAVE` (not `ABSENT`) — cross-module integration check | P1 | Medium |
