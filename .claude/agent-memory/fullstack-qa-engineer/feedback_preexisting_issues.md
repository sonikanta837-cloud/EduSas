---
name: empsas-preexisting-test-issues
description: Known pre-existing test failures in EmpSAS that needed fixing — signatures, missing mocks, role mismatches, label selectors
type: feedback
---

Pre-existing test failures discovered during the Resource/FAQ/Resume test addition cycle (June 2026):

1. **LeaveServiceTest** — `sendLeaveDecisionEmail` and `sendLeaveRequestEmail` both gained a `String[] cc` second parameter. Tests used `anyString()` — changed to `any(String[].class)`.
   - **Why:** Email signature was updated to support CC recipients but the test mocks were not updated.
   - **How to apply:** Always check EmailService method signatures when adding/fixing LeaveService tests.

2. **CourseServiceFilterTest.enrollEmployee_notYetEnrolled_savesEnrollment** — `CourseService` now depends on `CourseNotificationService` but the test was missing `@Mock CourseNotificationService courseNotificationService`. Also add `doNothing().when(courseNotificationService).createNotification(any(), any())`.
   - **Why:** CourseNotificationService was added as a dependency to CourseService.

3. **PerformanceControllerTest.getAllReviews_asManager_returns403** — GET /api/performance is authorized for `ADMIN`, `MANAGER`, `ASSISTANT_MANAGER` — test was asserting 403 for MANAGER but should be 200.
   - **Why:** Test assertion was wrong about the endpoint's authorization.

4. **Login.test.jsx** — Login page uses `<Typography>Email address</Typography>` as the label (no `htmlFor`), NOT an `<label>` element. Tests using `getByLabelText(/email address/i)` fail. Fix: use `container.querySelector('input[type="email"]')`.
   - **How to apply:** Use `getEmailInput(container)` helper pattern for Login page tests.

5. **Performance.test.jsx** — For EMPLOYEE role, the page title is "My Performance Reviews", not "Performance Reviews". Tests awaiting `findByText('Performance Reviews')` fail for EMPLOYEE. Use `findByText(/performance reviews/i)` regex instead.
   - Performance subtitle for ADMIN is "Track and manage all employee performance reviews." — use `/Track and manage/i` pattern not the exact text.

6. **AttendanceHoliday.test.jsx** — The Attendance component shows "Completed" (not "Present") for past weekdays with ≤8 hours. The test was asserting for "Present" chip. Fix: assert for either "Completed" or "Present".
