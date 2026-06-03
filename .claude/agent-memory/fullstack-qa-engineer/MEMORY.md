# Memory Index

- [Employee Management System Project](project_ems.md) — Full-stack EMS: Spring Boot backend + React frontend, all 10 modules built; key test patterns and institutional knowledge recorded
- [RTL findByText multiple elements](feedback_rtl_multiple_elements.md) — Use findAllByText when labels or values repeat across child components (e.g. "Today", HH:MM:SS clocks in Attendance page)
- [EmpSAS Test Patterns — MUI Duplicate Text](feedback_empsas_test_patterns.md) — KPI card labels ("Pending Approvals", "My Team") appear twice in Admin/Manager dashboards; always use findAllByText for these
- [EmpSAS Pre-existing Test Issues](feedback_preexisting_issues.md) — Known pre-existing failures: LeaveServiceTest email signature mismatch, CourseServiceFilterTest missing mock, PerformanceControllerTest MANAGER role, Login Typography label issue
- [EmpSAS jsPDF Mock Pattern](feedback_jspdf_mock.md) — jsPDF in Resources.jsx uses 'new jsPDF()'; mock requires class constructor; test PDF button visibility not save() internals to avoid jsPDF internal call failures
