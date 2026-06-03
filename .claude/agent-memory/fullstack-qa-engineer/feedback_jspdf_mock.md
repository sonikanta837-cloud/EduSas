---
name: empsas-jspdf-mock-pattern
description: How to mock jsPDF in Resources.jsx tests — class constructor approach; test button visibility not internals
type: feedback
---

`Resources.jsx` uses `import jsPDF from 'jspdf'` and calls `new jsPDF({ unit: 'mm', format: 'a4' })` then chains many methods on the result.

**The mock pattern that works:**

```js
jest.mock('jspdf', () => {
  const mockDoc = {
    setFontSize: jest.fn().mockReturnThis(),
    setFont: jest.fn().mockReturnThis(),
    setTextColor: jest.fn().mockReturnThis(),
    setDrawColor: jest.fn().mockReturnThis(),
    setLineWidth: jest.fn().mockReturnThis(),
    text: jest.fn().mockReturnThis(),
    line: jest.fn().mockReturnThis(),
    splitTextToSize: jest.fn().mockImplementation(() => ['sample text']),
    getTextWidth: jest.fn().mockReturnValue(50),
    save: jest.fn(),
    addPage: jest.fn().mockReturnThis(),
  };
  class MockJsPDF {
    constructor() { return mockDoc; }
  }
  MockJsPDF._mockDoc = mockDoc;
  return MockJsPDF;
});
```

Key points:
- Must use a **class** (not a plain function) so `new MockJsPDF()` works and returns `mockDoc`
- `splitTextToSize` must return a real array (not empty string) since `generatePDF` calls `.length` on it
- Access the mock doc via `require('jspdf')._mockDoc` for assertions

**Limitation:** Asserting `save()` was called tends to be fragile due to module resolution order. Prefer testing that the Download PDF button is visible and clicking it doesn't throw, rather than asserting jsPDF internals.

**Why:** jsPDF 4.x is an ES module. React Scripts/Babel transpiles the default import but the class constructor return-override approach is the most reliable way to intercept `new jsPDF()` calls in Jest/jsdom.
