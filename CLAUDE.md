# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Full-stack Employee Management System (EmpSAS). Spring Boot 3.2 (Java 17) backend + React 18 / MUI v5 frontend.

## Commands

### Backend (`backend/`)
```bash
mvn clean package          # build JAR
mvn spring-boot:run        # run dev server (port 8080)
mvn clean package -DskipTests  # build without tests
```

### Frontend (`frontend/`)
```bash
npm install    # install dependencies
npm start      # dev server on port 3000 (proxies /api → localhost:8080)
npm run build  # production build
```

Both must be running simultaneously during development. The frontend proxy in `package.json` forwards all `/api` calls to `http://localhost:8080`.

## Architecture

### Backend package layout (`com.emp.management`)

```
controller/   REST endpoints — thin, delegate to service
service/      Business logic, email notifications, scheduled tasks
repository/   Spring Data JPA interfaces
entity/       JPA entities (MySQL, ddl-auto=update)
dto/          Request/response objects (Lombok @Builder pattern)
security/     JWT filter chain, UserDetailsServiceImpl
exception/    GlobalExceptionHandler, ResourceNotFoundException, BadRequestException
```

### Key entities and relationships

- **User** ↔ **EmployeeDetails** (1:1) — every employee has a User login account; role lives on User
- **EmployeeDetails** → **EmployeeDetails** (self-referential `manager_id`) — org hierarchy
- **EmployeeDetails** → **Leave**, **Timesheet**, **AttendanceSession**, **TimesheetEntry**, **PerformanceReview**, **Enrollment**
- **Course** → **Enrollment** (1:N), **Exam** (1:1), **Certificate** (1:N)
- **Timesheet** = daily summary (one row per employee per day); **AttendanceSession** = individual login/logout sessions (many per day)

Roles enum: `ADMIN`, `MANAGER`, `HR`, `EMPLOYEE`

### Auth flow

1. `POST /api/auth/login` returns `accessToken` + `refreshToken`
2. Tokens stored in `localStorage`; Axios request interceptor adds `Authorization: Bearer <token>`
3. On 401, Axios response interceptor calls `/api/auth/refresh`, swaps tokens, retries — on failure redirects to `/login`
4. Backend: stateless JWT via `JwtAuthenticationFilter`; roles enforced via `@PreAuthorize` on controller methods

Redux store slices: `auth` (user, tokens, loading/error), `ui`

### Frontend page structure

Most feature pages (`Employees.jsx`, `EmployeeDetail.jsx`, `OrgChart.jsx`, etc.) are large single-file components. Key patterns:

- **`Employees.jsx`** — tabbed dashboard (Employees / Timesheet / Performance / Courses / Ex-Employees); each tab is a separate sub-component in the same file; pagination via `TablePagination` on all tabs; role-gated columns and actions via `user.role` from Redux
- **`EmployeeDetail.jsx`** — full profile view + edit form; access control derived from role + manager relationship
- **`OrgChart.jsx`** — recursive lazy-loaded tree; `subtreeWidth()` computes layout; `Branch` + `NodeCard` components; filter drawer with location/employment-type; `visibleSet` / `matchedSet` control which nodes render
- **`api/*.js`** — thin wrappers over the shared Axios instance; response interceptor unwraps `response.data` on success, rejects with raw error on failure

### Business rules to remember

- Deactivating an employee (`toggleStatus`) automatically transfers their subordinates to the first active ADMIN
- `subordinateCount` and `managerId`/`managerName` on EmployeeDTO only include **active** employees
- Overtime alert emails (>8 hours/day) fire on logout **and** via a `@Scheduled` task every 15 minutes; guarded by `alertSent` flag on the Timesheet row to prevent duplicates; sent to employee's manager (fallback: first ADMIN)
- Employee code is auto-generated as an incrementing integer starting from 10001

### Email / SMTP

Configured via `application.properties` (`spring.mail.*`). `EmailService` sends: password reset, leave request (to manager), leave decision (to employee), overtime alert (to manager/admin).

### File uploads

`./uploads` directory; 10 MB limit. Resume parsing uses Apache PDFBox. Excel/CSV export uses Apache POI + OpenCSV.

## Database

MySQL at `localhost:3306/employee_management`. Credentials in `application.properties` (root/root default). `ddl-auto=update` — schema evolves automatically for new columns, but **does not alter existing ENUM columns** when new enum values are added; those require a manual `ALTER TABLE` statement.
