---
name: feedback_rtl_multiple_elements
description: RTL findByText fails when multiple DOM elements share the same text — always use findAllByText and assert length >= 1
metadata:
  type: feedback
---

Use `findAllByText` (not `findByText`) whenever a label or value can appear in more than one place in the rendered component tree.

**Why:** In AttendancePage, the text "Today" appears in both the summary card and the DayCard for the current day, and HH:MM:SS clock values appear in both the summary card header and inside each DayCard row. Using `findByText` with these strings throws "Found multiple elements" and fails the test, even though the assertion is logically correct.

**How to apply:** When writing RTL tests for EmpSAS pages that render list/grid/card structures:
- Default to `findAllByText` / `getAllByText` for any text that a parent component passes down to child components.
- For values that are strictly unique (e.g., a page `<h1>` title), `findByText` is safe.
- After `findAllByText`, assert `expect(els.length).toBeGreaterThanOrEqual(1)` or index into the array `els[0]`.
