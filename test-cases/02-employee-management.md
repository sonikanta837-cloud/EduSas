# 02 — Employee Management, Employee Detail, Org Chart

Covers: `EmployeeController` (`/api/employees/**`), `Employees.jsx`, `EmployeeDetail.jsx`, `OrgChart.jsx`.

Key business rules: employee code auto-increments from 10001; deactivating an employee transfers subordinates to first active ADMIN; `subordinateCount`/`managerId`/`managerName` only count active employees.

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-EMP-001 | Create employee as ADMIN | Logged in ADMIN | 1) POST `/api/employees` with `RegisterRequest` | valid unique email, role `EMPLOYEE` | 201; new `employeeCode` = last code + 1 (starts 10001 for first-ever employee) | P1 | High |
| TC-EMP-002 | Create employee as unauthorized role (MANAGER) | Logged in MANAGER | 1) POST `/api/employees` | valid payload | 403 | P1 | High |
| TC-EMP-003 | Employee code increments correctly across concurrent creates | 2 employees created back-to-back | 1) Create emp A 2) Create emp B immediately after | — | Codes are sequential and unique, no collision under near-concurrent creation | P2 | Medium |
| TC-EMP-004 | List employees — pagination | 50+ employees exist | 1) GET `/api/employees?page=0&size=10` | — | 200, 10 rows returned, correct total count metadata | P2 | High |
| TC-EMP-005 | List employees — search filter | Employee "John Doe" exists | 1) GET `/api/employees?search=John` | — | 200, only matching employees returned | P2 | High |
| TC-EMP-006 | List employees visible to EMPLOYEE role (read access) | Logged in EMPLOYEE | 1) GET `/api/employees` | — | 200 — all 6 roles allowed to list per `@PreAuthorize` | P2 | High |
| TC-EMP-007 | Get single employee by ID — not found | — | 1) GET `/api/employees/999999` | non-existent ID | 404 `ResourceNotFoundException` "Employee not found" | P2 | High |
| TC-EMP-008 | Update employee profile — valid data | Existing employee | 1) PUT `/api/employees/{id}` | valid `EmployeeDTO`, changed `position` | 200, field persisted | P1 | High |
| TC-EMP-009 | Update employee — invalid email format | — | 1) PUT `/api/employees/{id}` | `email:"bad-format"` | 400 field error | P2 | High |
| TC-EMP-010 | Update employee — negative salary | — | 1) PUT `/api/employees/{id}` | `salary:-500` | 400 (`@DecimalMin("0.0")`) | P2 | High |
| TC-EMP-011 | Update employee — salary exactly 0 (boundary) | — | 1) PUT | `salary:0` | 200 accepted (boundary is inclusive) | P3 | High |
| TC-EMP-012 | Update employee — invalid phone pattern | — | 1) PUT | `phone:"123"` (6 digits, below 7-digit min) | 400 (`@Pattern` requires 7–15 digits) | P3 | High |
| TC-EMP-013 | Update employee — phone boundary 7 digits | — | 1) PUT | `phone:"1234567"` | 200 accepted | P4 | High |
| TC-EMP-014 | Update employee — phone boundary 15 digits | — | 1) PUT | `phone:"123456789012345"` | 200 accepted | P4 | High |
| TC-EMP-015 | Update employee — blank first/last name | — | 1) PUT | `firstName:""` | 400 (`@NotBlank`) | P2 | High |
| TC-EMP-016 | EMPLOYEE updates own profile (self-service) | Logged in EMPLOYEE, editing own record | 1) PUT `/api/employees/{id}` (own id) | valid subset of editable fields | 200 — role allowed per `@PreAuthorize` on PUT | P2 | High |
| TC-EMP-017 | EMPLOYEE attempts to update another employee's profile | Logged in EMPLOYEE, target id ≠ own | 1) PUT `/api/employees/{otherId}` | valid payload | Verify service-level ownership check exists — if absent, this is a potential IDOR (see Security suite TC-SEC-005) | P1 | High |
| TC-EMP-018 | Deactivate (toggle-status) an employee who has subordinates | Manager M has 2 active direct reports; at least 1 active ADMIN exists | 1) PATCH `/api/employees/{M.id}/toggle-status` | — | 200; M.active=false; both subordinates' `managerId` reassigned to first active ADMIN | P1 | High |
| TC-EMP-019 | Deactivate employee with no active ADMIN in system | All ADMIN accounts deactivated (edge setup) | 1) Toggle-status a manager with subordinates | — | Verify graceful handling — document actual behavior (null manager vs. error) since fallback ADMIN doesn't exist | P2 | Medium |
| TC-EMP-020 | Reactivate a deactivated employee | Employee currently inactive | 1) PATCH toggle-status again | — | 200; `active=true` again; subordinates NOT automatically reassigned back | P2 | High |
| TC-EMP-021 | Deactivate as HR (unauthorized role for this action) | Logged in HR | 1) PATCH toggle-status | — | 403 — only `ADMIN,DIRECTOR` allowed | P1 | High |
| TC-EMP-022 | subordinateCount excludes inactive subordinates | Manager has 3 subordinates, 1 deactivated | 1) GET employee detail for manager | — | `subordinateCount = 2` (only active counted) | P2 | High |
| TC-EMP-023 | managerName/managerId null when manager is inactive | Employee's manager was deactivated (not reassigned in this edge) | 1) GET employee detail | — | `managerId`/`managerName` reflect only-active rule — verify UI shows appropriate fallback | P2 | Medium |
| TC-EMP-024 | Clear manager explicitly | Employee has a manager assigned | 1) PATCH `/api/employees/{id}/clear-manager` | — | 200; `managerId` set to null | P2 | High |
| TC-EMP-025 | Delete (soft-delete) employee | Active employee, ADMIN logged in | 1) DELETE `/api/employees/{id}` | — | 200 "Employee deactivated" (soft delete, not row removal) — DB row still present, `active=false` | P1 | High |
| TC-EMP-026 | Ex-employees tab — GET `/api/employees/ex` | Some employees have `dateOfExit` set / `active=false` | 1) GET `/api/employees/ex` as HR | — | 200; list restricted to `ADMIN,DIRECTOR,HR`; shows `dateOfExit` column | P2 | High |
| TC-EMP-027 | Ex-employees tab access denied for MANAGER | Logged in MANAGER | 1) GET `/api/employees/ex` | — | 403 | P2 | High |
| TC-EMP-028 | Assign HR to employee | Logged in ADMIN | 1) PATCH `/api/employees/{id}/assign-hr` | `hrUserId` | 200; `assignedHr` set | P2 | High |
| TC-EMP-029 | Get HR users list | Logged in ADMIN/DIRECTOR/HR | 1) GET `/api/employees/hr-users` | — | 200; only users with role `HR` returned | P3 | High |
| TC-EMP-030 | Get manager's team | Manager has 3 direct reports | 1) GET `/api/employees/manager/{managerId}/team` | — | 200; exactly the 3 direct reports returned | P2 | High |
| TC-EMP-031 | Get distinct locations for filter dropdown | Employees across 3 seating locations | 1) GET `/api/employees/locations` | — | 200; distinct, non-null location list | P4 | High |
| TC-EMP-032 | Employees list — role/location filter combo (frontend) | Employees list loaded | 1) Apply role filter = MANAGER 2) Apply location filter = "Pune" | — | Table shows only Managers based in Pune; pagination resets to page 1 | P3 | Medium |
| TC-EMP-033 | Add-employee / upload-resume actions hidden for EMPLOYEE role | Logged in as EMPLOYEE | 1) Open Employees tab | — | "Add Employee" and "Upload Resume" buttons not rendered (`canAddEmployee`/`canUploadResume` false) | P2 | Medium |
| TC-EMP-034 | Employee form — all 6 dialog tabs save correctly | ADMIN editing an employee | 1) Fill Basic Info, Employment, Work Details, Identity, Address tabs 2) Save | full valid EmployeeDTO across all sections | 200; all fields persisted; `System` tab shows updated `modifiedBy`/`updatedAt` | P2 | Medium |
| TC-EMP-035 | Employment type dropdown restricted to defined options | Employee form open | 1) Open Employment Type dropdown | — | Only `Full-time, Part-time, Contract, Intern, Consultant` selectable | P4 | Medium |
| TC-EMP-036 | Org Chart renders full hierarchy | Org with Admin→Managers→Employees | 1) GET `/api/employees/org-chart` 2) Load `/org-chart` page | — | 200; tree structure correct; any authenticated role can view (no `@PreAuthorize`) | P1 | Medium |
| TC-EMP-037 | Org Chart lazy-loads subtree on expand | Deep hierarchy (4+ levels) | 1) Click expand on a mid-level node | — | Child nodes fetched/rendered only on demand, no full-tree preload | P3 | Medium |
| TC-EMP-038 | Org Chart filter drawer — location + employment-type | Chart loaded | 1) Open filter drawer 2) Select location "Mumbai" + type "Full-time" | — | `visibleSet`/`matchedSet` correctly narrow rendered nodes; non-matching branches collapse/hide | P3 | Medium |
| TC-EMP-039 | Org Chart excludes/marks inactive employees appropriately | One employee deactivated | 1) Load org chart | — | Verify inactive employees are visually distinguished or excluded per intended design | P3 | Manual |
