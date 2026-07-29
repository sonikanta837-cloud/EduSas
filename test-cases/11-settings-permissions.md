# 11 — Settings, Portal Permissions & Per-User Module Overrides

Covers: `SystemSettingController` (`/api/settings/**`), `Settings.jsx`, `PortalPermissionController`, `PermissionController`, `PortalGuard`, `Sidebar.jsx` `allowedModules` override.

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-SET-001 | Get/update Break Alert threshold | ADMIN/DIRECTOR logged in | 1) GET `/api/settings/break-alert` 2) PUT with new threshold | `thresholdMinutes:45` | 200 both; new value affects `monitorBreaks()` going forward | P2 | High |
| TC-SET-002 | Get/update Work-Report-Email toggle | — | 1) GET/PUT `/api/settings/work-report-email` | `enabled:false` | 200; disabling stops scheduled work-report emails | P2 | High |
| TC-SET-003 | Get/update Attendance Audit toggle | — | 1) GET/PUT `/api/settings/attendance-audit` | `enabled:true` | 200 | P2 | High |
| TC-SET-004 | Get/update Correction Policy settings (backend-only, no UI panel) | — | 1) GET/PUT `/api/settings/correction-policy` directly via API | policy fields | 200 — confirms endpoint works even though `Settings.jsx` has no panel for it (documented gap; verify whether this is intentional or missing UI work) | P3 | Medium |
| TC-SET-005 | Get/update Performance Threshold | — | 1) GET/PUT `/api/settings/performance-threshold` | `threshold:3.5` (step 0.5, range 1–5) | 200; used by `belowThresholdOnly` filter in performance export | P2 | High |
| TC-SET-006 | Performance threshold — boundary values 1 and 5 | — | 1) PUT threshold=1, then threshold=5 | — | Both accepted (inclusive range) | P3 | High |
| TC-SET-007 | Performance threshold — invalid value (0.3, not a 0.5 step) | — | 1) PUT threshold=0.3 | — | Verify server-side step validation (or confirm it's UI-only enforcement — gap if backend accepts) | P3 | Medium |
| TC-SET-008 | Non-ADMIN/DIRECTOR attempts any settings endpoint | Logged in HR | 1) GET `/api/settings/break-alert` | — | 403 (all settings endpoints are `ADMIN,DIRECTOR` only, including reads) | P1 | High |
| TC-SET-009 | Settings.jsx renders exactly 4 nav panels | ADMIN logged in | 1) Load `/settings` | — | Panels: Break Alerts, Attendance Audit, Email Automation, Performance Threshold (no Correction Policy panel) | P3 | Medium |
| TC-SET-010 | Get own portal permissions | Any authenticated user | 1) GET `/api/portal-permissions/me` | — | 200; list of portals this user can access | P2 | High |
| TC-SET-011 | Check access to a specific portal | — | 1) GET `/api/portal-permissions/check/{portalId}` | portal user has access to | 200 | P2 | High |
| TC-SET-012 | Check access to a portal user lacks | EMPLOYEE checking an admin-only portal | 1) GET `/api/portal-permissions/check/{adminPortalId}` | — | 403 `AccessDeniedException` | P1 | High |
| TC-SET-013 | Update role-level portal permissions | ADMIN/DIRECTOR | 1) PUT `/api/portal-permissions/roles/{role}` | new portal list for `MANAGER` role | 200; role's default portal access updated | P2 | High |
| TC-SET-014 | Non-privileged role attempts to update role permissions | Logged in HR | 1) PUT `/api/portal-permissions/roles/{role}` | — | 403 | P1 | High |
| TC-SET-015 | PortalGuard redirects on stale client-side permission list | Server-side permission revoked after login (session still cached old list client-side) | 1) Navigate to a now-restricted route | — | Server-side `checkAccess` call on nav catches it, redirects to `/access-denied` | P1 | Medium |
| TC-SET-016 | Per-user `allowedModules` override restricts a normal user | User has `allowedModules=["/dashboard","/employees"]` set via `PermissionController` | 1) Login as that user 2) Check sidebar | — | Only Dashboard + Employees visible, all other role-default menu items hidden | P1 | Medium |
| TC-SET-017 | ADMIN/DIRECTOR bypass `allowedModules` restriction | ADMIN has an `allowedModules` override set | 1) Login as ADMIN | — | Full menu still shown — `isSuperUser` check bypasses the override entirely | P1 | Medium |
| TC-SET-018 | Update per-user permission override | ADMIN/DIRECTOR | 1) PUT `/api/permissions/{userId}` | new `allowedModules` array | 200; user's next login/refresh reflects new menu | P2 | High |
| TC-SET-019 | Get all per-user permission overrides | ADMIN/DIRECTOR | 1) GET `/api/permissions` | — | 200; list of all overrides | P3 | High |
| TC-SET-020 | Non-ADMIN/DIRECTOR attempts to modify permissions | Logged in MANAGER | 1) PUT `/api/permissions/{userId}` | — | 403 | P1 | High |
