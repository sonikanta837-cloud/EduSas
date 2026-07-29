# 07 — Courses, Enrollment, Exam & Certificates

Covers: `CourseController` (`/api/courses/**`), `CertificateController` (`/api/certificates/**`). Course → Enrollment(1:N), Exam(1:1), Certificate(1:N).

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-CRS-001 | Create course with auto-generated quiz | ADMIN/DIRECTOR/HR logged in | 1) POST `/api/courses` | title, content/video URL, description | 201; course created; quiz questions auto-generated | P1 | High |
| TC-CRS-002 | EMPLOYEE attempts to create a course | Logged in EMPLOYEE | 1) POST `/api/courses` | valid payload | 403 | P1 | High |
| TC-CRS-003 | List all courses | Courses exist | 1) GET `/api/courses` | — | 200; any authenticated role | P2 | High |
| TC-CRS-004 | Get single course detail | — | 1) GET `/api/courses/{id}` | — | 200; correct detail | P2 | High |
| TC-CRS-005 | Update course | ADMIN/HR | 1) PUT `/api/courses/{id}` | updated title | 200 | P2 | High |
| TC-CRS-006 | Delete course | ADMIN/HR | 1) DELETE `/api/courses/{id}` | — | 200; cascades to/handles existing enrollments appropriately | P2 | High |
| TC-CRS-007 | Enroll employee in a course | Course exists, employee not yet enrolled | 1) POST `/api/courses/{courseId}/enroll/{employeeId}` | — | 201; enrollment created; `sendCourseAssignmentEmail` sent | P1 | High |
| TC-CRS-008 | Enroll same employee twice | Already enrolled | 1) POST enroll again | — | Verify duplicate-enrollment guard (expect 400/409, or document if silently allowed — gap risk) | P2 | Medium |
| TC-CRS-009 | Assign course to employee(s) by manager/HR | Manager/HR logged in | 1) POST `/api/courses/{courseId}/assign` | employee ID list | 200; assignment email(s) sent | P2 | High |
| TC-CRS-010 | Mark course as watched | Employee enrolled | 1) POST `/api/courses/{courseId}/watch/{employeeId}` | — | 200; progress recorded (enables exam access) | P2 | High |
| TC-CRS-011 | Attempt exam before watching course | Employee enrolled but not watched | 1) POST `/api/courses/{courseId}/exam/{employeeId}` | exam answers | Verify whether exam is gated behind "watched" status (business rule to confirm) | P2 | Medium |
| TC-CRS-012 | Submit exam — passing score | Employee watched course | 1) GET exam questions 2) POST exam with correct answers | answers meeting pass threshold | 200; passed; certificate auto-generated | P1 | High |
| TC-CRS-013 | Submit exam — failing score | — | 1) POST exam with mostly wrong answers | — | 200; recorded as failed; no certificate issued; retry allowed if policy permits | P1 | High |
| TC-CRS-014 | Submit exam twice (retry) | Failed first attempt | 1) POST exam again | improved answers | Verify retry policy (attempts limit, if any) | P2 | Medium |
| TC-CRS-015 | Get exam questions for a course | — | 1) GET `/api/courses/{courseId}/exam-questions` | — | 200; questions returned without exposing correct-answer flags to non-admin roles | P2 | Medium |
| TC-CRS-016 | Admin views admin-only exam questions (with answers) | ADMIN/HR | 1) GET `/api/courses/{courseId}/admin-exam-questions` | — | 200; includes correct answers (privileged view) | P2 | High |
| TC-CRS-017 | Regenerate exam questions for a course | ADMIN/HR | 1) GET `/api/courses/{courseId}/generate-questions` | — | 200; new question set generated | P3 | Medium |
| TC-CRS-018 | View all enrollments (org-wide) | ADMIN/HR/DIRECTOR | 1) GET `/api/courses/all-enrollments` | — | 200 | P2 | High |
| TC-CRS-019 | Manager views team enrollments | MANAGER logged in | 1) GET `/api/courses/manager/{managerId}` | — | 200; scoped to direct reports | P2 | High |
| TC-CRS-020 | View employee's enrollments — hierarchy check | Logged in as a non-manager, non-admin, non-HR peer | 1) GET `/api/courses/employee/{employeeId}` (not self/report) | — | `AccessDeniedException` (self/direct-report/admin-hr only) | P1 | High |
| TC-CRS-021 | View own enrollments | Self | 1) GET `/api/courses/employee/{ownId}` | — | 200 | P2 | High |
| TC-CRS-022 | Training report generation | ADMIN/HR/DIRECTOR | 1) GET `/api/courses/training-report` | — | 200; org-wide completion/compliance data | P2 | High |
| TC-CRS-023 | View enrolled-employee-ids for a course | Privileged roles | 1) GET `/api/courses/{courseId}/enrolled-employee-ids` | — | 200; id list only | P3 | High |
| TC-CRS-024 | Get learners for a course | Privileged roles | 1) GET `/api/courses/{id}/learners` | — | 200; learner detail list | P3 | High |
| TC-CRS-025 | Employee views own certificates | Employee passed 2 exams | 1) GET `/api/certificates/my` | — | 200; 2 certificates listed | P2 | High |
| TC-CRS-026 | ADMIN/HR view all certificates | — | 1) GET `/api/certificates` | — | 200; org-wide list | P2 | High |
| TC-CRS-027 | Download certificate PDF | Certificate exists | 1) GET `/api/certificates/download?certNo={no}` | valid cert number | 200; PDF file returned, correct employee name/course/date | P1 | Medium |
| TC-CRS-028 | Download certificate with invalid certNo | — | 1) GET download | bogus certNo | 404 | P2 | High |
