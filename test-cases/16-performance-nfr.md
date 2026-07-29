# 16 — Performance & Non-Functional Requirements

Covers load, concurrency, response-time, and scheduled-job behavior across the app. These are largely exploratory/benchmark-style and should have agreed SLAs confirmed with the team before treating a result as pass/fail.

| ID | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Automation |
|---|---|---|---|---|---|---|---|
| TC-NFR-001 | Login response time under normal load | — | 1) Measure `/api/auth/login` latency, 100 sequential requests | — | p95 < agreed SLA (e.g. 300ms) — confirm target with team | P2 | Medium |
| TC-NFR-002 | Employee list response time with 1,000+ employees | Large dataset seeded | 1) GET `/api/employees?page=0&size=25` | — | Response within acceptable latency; pagination prevents full-table scans on the client | P2 | Medium |
| TC-NFR-003 | Concurrent clock-in/out from many employees at shift start | 200 employees clock in within a 1-minute window | 1) Fire concurrent `/api/job-sessions/login` requests | — | No unique-constraint violations, no dropped sessions, correct `sessionMinutes` on all | P1 | Medium |
| TC-NFR-004 | Concurrent switch-job calls don't corrupt session state | Same employee, rapid double-click on "Switch Job" in UI | 1) Fire 2 near-simultaneous `switch` calls | — | Exactly one consistent final state (no duplicate open sessions, per `saveAndFlush` atomicity) | P1 | Medium |
| TC-NFR-005 | Nightly rollup (`generateDailySummaries`, 23:55 IST) completes within its window | Full org (500+ employees) | 1) Trigger/observe the scheduled job | — | Completes well before next business day, no timeout/OOM | P1 | Low |
| TC-NFR-006 | Under-hours audit (11:00 IST) and break-monitors don't overlap/contend | Both scheduled tasks configured | 1) Observe execution during simultaneous trigger windows | — | No deadlock/contention on `JobDailySummary`/`JobWorkSession` tables | P2 | Low |
| TC-NFR-007 | Large Excel export (5,000+ rows) memory/time behavior | Big dataset | 1) GET performance/job-summary export org-wide | — | Completes without OOM, streamed rather than fully buffered if possible | P2 | Low |
| TC-NFR-008 | Resume parsing (PDFBox) time for a large multi-page PDF | 20-page resume | 1) POST resume parse | large PDF | Completes within acceptable time, doesn't block other requests (async/thread-pool behavior) | P3 | Medium |
| TC-NFR-009 | WebRTC signaling via DB polling doesn't degrade under concurrent interviews | 10 simultaneous technical interviews in progress | 1) Run concurrent signaling exchanges | — | No noticeable lag increase; DB polling frequency scales acceptably | P2 | Manual |
| TC-NFR-010 | Org chart render time for deep/wide hierarchy | 500+ employees, 5 levels deep | 1) Load `/org-chart`, expand several branches | — | Lazy-loading keeps initial render fast; no full-tree fetch stalls the UI | P2 | Manual |
| TC-NFR-011 | JWT validation overhead under high request rate | Sustained load test | 1) Fire 1,000 authenticated requests/min | — | `JwtAuthFilter` overhead remains negligible relative to overall request time | P3 | Low |
| TC-NFR-012 | Rate-limiter memory doesn't grow unbounded | Sustained traffic over hours | 1) Observe `LoginRateLimitFilter`'s in-memory tracking structure over time | — | Old entries expire/get cleaned up (sliding 15-min window), no memory leak | P2 | Low |
| TC-NFR-013 | Database connection pool under peak concurrent users | Simulated peak (e.g. 9AM login rush) | 1) Load-test concurrent logins + dashboard loads | — | No connection-pool exhaustion errors; graceful queueing/backpressure | P2 | Low |
| TC-NFR-014 | Frontend bundle load time / Time-to-Interactive on `npm run build` output | Production build | 1) Serve build, measure via Lighthouse | — | Meets agreed performance budget (document target with team) | P3 | Manual |
