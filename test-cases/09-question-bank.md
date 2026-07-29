# 09 — Interview Question Bank

Covers: `InterviewQuestionController` (`/api/interview-questions/**`), `QuestionBank.jsx`. `QuestionType{TEXT,MCQ}`, `QuestionDifficulty{EASY,MEDIUM,HARD}`, `QuestionStatus{ACTIVE,INACTIVE}`.

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-QB-001 | Create a TEXT question | ADMIN/HR/Manager logged in | 1) POST `/api/interview-questions` | `type:"TEXT"`, technology, category, difficulty | 201; question saved `status=ACTIVE` | P1 | High |
| TC-QB-002 | Create an MCQ question with options | — | 1) POST | `type:"MCQ"`, options list, correct answer index | 201; MCQ persisted with options | P1 | High |
| TC-QB-003 | Create question with invalid difficulty enum | — | 1) POST | `difficulty:"IMPOSSIBLE"` | 400 (enum deserialization failure) | P2 | High |
| TC-QB-004 | EMPLOYEE attempts to create a question | Logged in EMPLOYEE | 1) POST | — | 403 (not in `ADMIN,DIRECTOR,HR,MANAGER,ASSISTANT_MANAGER`) | P1 | High |
| TC-QB-005 | List questions with pagination/filter | 50+ questions | 1) GET `/api/interview-questions?technology=Java&difficulty=MEDIUM` | — | 200; filtered correctly | P2 | High |
| TC-QB-006 | Get single question | — | 1) GET `/api/interview-questions/{id}` | — | 200 | P3 | High |
| TC-QB-007 | Update a question | — | 1) PUT `/api/interview-questions/{id}` | updated text | 200 | P2 | High |
| TC-QB-008 | Toggle question status (active/inactive) | — | 1) PATCH `/api/interview-questions/{id}/toggle-status` | — | 200; status flips; inactive questions excluded from interview generation pool | P2 | High |
| TC-QB-009 | Delete a question | HR/Admin/Director only | 1) DELETE `/api/interview-questions/{id}` as MANAGER | — | 403 (delete narrower than create — Manager can create but not delete) | P1 | High |
| TC-QB-010 | Get distinct technologies list | — | 1) GET `/api/interview-questions/technologies` | — | 200; distinct list for dropdown | P4 | High |
| TC-QB-011 | Get distinct categories list | — | 1) GET `/api/interview-questions/categories` | — | 200 | P4 | High |
| TC-QB-012 | Question bank stats | — | 1) GET `/api/interview-questions/stats` | — | 200; counts by technology/difficulty | P3 | High |
| TC-QB-013 | Bulk import questions via xlsx | Valid template file | 1) POST `/api/interview-questions/import` | xlsx with 20 rows | 200; all valid rows imported, malformed rows reported/skipped gracefully (not a silent partial failure) | P2 | Medium |
| TC-QB-014 | Export question bank to xlsx | — | 1) GET `/api/interview-questions/export` | — | 200; xlsx with correct columns matching import template (round-trip compatibility) | P2 | Medium |
