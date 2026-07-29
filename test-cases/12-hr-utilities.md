# 12 — HR Utilities: Announcements, Holidays, FAQs, Resources, Leave Bulk Upload

Covers: `AnnouncementController`, `HolidayController`, `FaqController`, `ResourceController`, `LeaveUploadController`.

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-HRU-001 | Create an announcement | ADMIN/DIRECTOR/HR | 1) POST `/api/announcements` | title, body | 201 | P2 | High |
| TC-HRU-002 | EMPLOYEE attempts to create announcement | Logged in EMPLOYEE | 1) POST `/api/announcements` | — | 403 | P2 | High |
| TC-HRU-003 | List announcements (all authenticated) | — | 1) GET `/api/announcements` | — | 200; visible to all roles | P3 | High |
| TC-HRU-004 | Mark announcement as viewed | Unviewed announcement | 1) POST `/api/announcements/{id}/view` | — | 200; view recorded, doesn't double-count on repeat calls | P3 | High |
| TC-HRU-005 | Unread count updates after viewing | 3 unread, 1 viewed | 1) GET `/api/announcements/unread-count` before/after viewing one | — | Count decreases by 1 | P3 | High |
| TC-HRU-006 | View list of who has seen an announcement | HR/Admin/Director | 1) GET `/api/announcements/{id}/viewers` | — | 200; viewer list with timestamps | P3 | High |
| TC-HRU-007 | Update/delete an announcement | HR/Admin/Director | 1) PUT then DELETE `/api/announcements/{id}` | — | 200 both | P3 | High |
| TC-HRU-008 | Create a holiday | ADMIN/DIRECTOR/HR | 1) POST `/api/holidays` | `name, date, holidayType` | 201 | P2 | High |
| TC-HRU-009 | Create holiday — blank name | — | 1) POST | `name:""` | 400 (`@NotBlank`) | P3 | High |
| TC-HRU-010 | Create holiday — missing date | — | 1) POST | `date:null` | 400 (`@NotNull`) | P3 | High |
| TC-HRU-011 | Bulk-create holidays | ADMIN/DIRECTOR/HR | 1) POST `/api/holidays/bulk` | array of holiday objects | 201; all valid entries created, invalid ones reported | P2 | Medium |
| TC-HRU-012 | List all holidays for a year | — | 1) GET `/api/holidays?year=2026` | — | 200; every role can view | P3 | High |
| TC-HRU-013 | Get "my" holidays (seating-location scoped) | Employee at "Pune" location, region-specific holidays exist | 1) GET `/api/holidays/my` | — | 200; only holidays applicable to employee's `seatingLocation` | P2 | High |
| TC-HRU-014 | Get distinct years with holidays | — | 1) GET `/api/holidays/years` | — | 200; list for year-picker dropdown | P4 | High |
| TC-HRU-015 | Update/delete a holiday | ADMIN/DIRECTOR/HR | 1) PUT then DELETE `/api/holidays/{id}` | — | 200 both | P3 | High |
| TC-HRU-016 | Create FAQ | ADMIN/DIRECTOR/HR | 1) POST `/api/faqs` | question, answer | 201 | P3 | High |
| TC-HRU-017 | List FAQs — public/unauthenticated toggle | — | 1) GET `/api/faqs?all=true` vs default | — | Behavior differs per `all` param — verify unauthenticated access is intentional and doesn't leak internal-only FAQs | P3 | Medium |
| TC-HRU-018 | Toggle FAQ visibility | HR/Admin/Director | 1) PATCH `/api/faqs/{id}/toggle` | — | 200; hidden FAQ excluded from default list | P3 | High |
| TC-HRU-019 | Delete an FAQ | — | 1) DELETE `/api/faqs/{id}` | — | 200 | P4 | High |
| TC-HRU-020 | Upload a resource file | ADMIN/DIRECTOR/HR | 1) POST `/api/resources/upload` | valid document (< 10MB) | 201; stored in `./uploads` | P2 | Medium |
| TC-HRU-021 | Upload resource exceeding size/type limits | Oversized/invalid file | 1) POST upload | 15MB file | Rejected — verify graceful error, not raw 500 (see also TC-FILE tests) | P2 | Medium |
| TC-HRU-022 | View/download a resource | Any authenticated role | 1) GET `/api/resources/view/{id}` and `/download/{id}` | — | 200; correct file streamed to all roles (no `@PreAuthorize` restriction found — confirm intentional) | P3 | High |
| TC-HRU-023 | Toggle resource visibility / delete | HR/Admin/Director | 1) PATCH toggle, then DELETE | — | 200 both | P3 | High |
| TC-HRU-024 | Bulk leave/holiday xlsx upload | ADMIN/DIRECTOR | 1) GET `/api/leave-upload/template` 2) fill it 3) POST `/api/leave-upload?location=Pune` | valid xlsx matching template | 200; rows imported; malformed rows reported without aborting the whole batch | P2 | Medium |
| TC-HRU-025 | Bulk upload with malformed rows (missing required columns) | — | 1) POST leave-upload | xlsx with 2 bad rows among 10 | Bad rows rejected/reported individually; 8 good rows still imported (verify partial-success behavior, not all-or-nothing failure) | P2 | Medium |
| TC-HRU-026 | Non-ADMIN/DIRECTOR attempts bulk upload | Logged in HR | 1) POST `/api/leave-upload` | — | 403 (restricted narrower than most HR-utility writes — HR excluded here) | P2 | High |
