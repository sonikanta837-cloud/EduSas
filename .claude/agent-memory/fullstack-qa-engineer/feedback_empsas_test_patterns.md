---
name: feedback-empsas-test-patterns
description: Key RTL and MUI test patterns specific to EmpSAS that prevent duplicate-element failures
metadata:
  type: feedback
---

## MUI Duplicate Text in Dashboard KPI Cards

KPI card labels in AdminDashboard appear in BOTH the KPI card and the AI insights section (e.g. "Pending Approvals" appears as a KPI label and inside the insights panel). Similarly ManagerDashboard shows "My Team" in both the KPI card and the Team Leave Requests table header.

**Why:** The Admin dashboard renders an insights section below KPI cards that summarizes the same metrics, causing two elements with identical text.

**How to apply:** Always use `findAllByText(/Label/i)` for these Dashboard KPI assertions; never `findByText`. Same issue applies to Top Performer name in Performance page — the name appears in both the stat card and the ReviewCard.

## AssetCondition Enum Values

AssetCondition only has: `NEW`, `GOOD`, `FAIR`, `POOR`. There is NO `DAMAGED` value.
AssetType does NOT have `MOBILE` — use `PHONE` instead.

**Why:** ddl-auto=update does not alter existing ENUM columns, so always verify enum values in source before writing assertions.

## Performance Page — Review Name Duplication

`employeeName` appears in both the Top Performer stat card AND the ReviewCard. Use `findAllByText(name)` and assert `length >= 1`.

## Holiday Truncation

DayCard truncates holiday names > 20 chars with `holidayName.slice(0, 20) + '…'`. The `…` character is a single unicode character (U+2026). When counting characters: `slice(0, 20)` gives exactly 20 characters from the original string.

## Dashboard Admin Loading Wait

Admin dashboard loading completes only after all three API calls settle. Wait on `findByText(/Director Dashboard/i)` to confirm the page has fully rendered before asserting other elements.
