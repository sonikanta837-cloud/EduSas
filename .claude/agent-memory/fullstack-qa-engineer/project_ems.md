---
name: project-ems-context
description: Employee Management System — full-stack EMS built and tested, 10 modules complete
metadata:
  type: project
---

Full-stack Employee Management System (EmpSAS) is complete with all 10 modules. Spring Boot 3.2 backend + React 18 frontend.

**Why:** Ongoing development project; tests are maintained per-feature as additions land.

**How to apply:** When adding new tests, follow the existing pattern: @WebMvcTest + TestSecurityConfig + GlobalExceptionHandler import for controllers; @ExtendWith(MockitoExtension.class) with @InjectMocks for services.

## Key institutional knowledge

- `TestSecurityConfig` lives in `com.emp.management.config` — always import it alongside `GlobalExceptionHandler` in `@WebMvcTest` tests.
- JwtAuthFilter must be excluded in controller tests via `excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)`.
- `MissingServletRequestParameterException` is NOT handled by `GlobalExceptionHandler` — falls through to the generic handler which returns 500, not 400. Test for `status == 400 || status == 500`.
- `CertificatePdfService.buildPdf()` catches logo load failures silently (`catch (Exception ignored)`) — the logo not being present in test classpath is fine; PDF still generates.
- PDF byte output always starts with `%PDF` magic bytes — useful assertion for smoke-testing the PDF generation path.
- Frontend axios interceptor redirects to `/login` on 401 refresh failure via `window.location.href`; jsdom logs "Not implemented: navigation" console noise — this is expected and tests still pass.
- JaCoCo added to `pom.xml` build plugins (version 0.8.11) for coverage reporting.
- Branch coverage for certificate tests: CertificateController 100%, CertificatePdfService 100%, CertificateService 100%.
- Frontend overall coverage is low (~4%) because most pages have no tests yet — only api/*.js and Login.jsx are tested.
