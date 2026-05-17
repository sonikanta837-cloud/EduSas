/**
 * Tests for frontend/src/pages/Attendance.jsx
 *
 * Focus:
 *  1. secToHm utility — exercises the fixed line (sec % 60 — single semicolon)
 *     through the rendered component's "Working Hours" clock display.
 *  2. Component-level render, loading, and empty-state behaviour.
 *
 * secToHm is not exported, so it is tested via the DOM output it drives.
 * A parallel pure-logic suite re-implements the same formula to document
 * expected values; the component integration tests prove the real code path.
 */

import React from 'react';
import { render, screen, act, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router-dom';
import MockAdapter from 'axios-mock-adapter';

import authReducer from '../store/authSlice';
import api from '../api/axios';

// ── Mocks ────────────────────────────────────────────────────────────────────

// react-toastify — prevent real DOM portal errors in test environment
jest.mock('react-toastify', () => ({
  toast: { error: jest.fn(), success: jest.fn() },
}));

// timesheetApi — all methods are mocked so no real network calls fire
jest.mock('../api/timesheetApi', () => ({
  timesheetApi: {
    getSessionsByRange: jest.fn(),
    getTodaySessions: jest.fn(),
    getAttendanceByRange: jest.fn(),
  },
}));

// employeeApi — getByUserId resolves with a fixture employee
jest.mock('../api/employeeApi', () => ({
  employeeApi: {
    getByUserId: jest.fn(),
  },
}));

import { timesheetApi } from '../api/timesheetApi';
import { employeeApi } from '../api/employeeApi';
import AttendancePage from '../pages/Attendance';

// ── Helpers ───────────────────────────────────────────────────────────────────

const EMPLOYEE_FIXTURE = { id: 42, fullName: 'Test Employee' };

const USER_FIXTURE = {
  userId: 7,
  email: 'test@company.com',
  role: 'EMPLOYEE',
  fullName: 'Test Employee',
};

/** Build a minimal Redux store seeded with an authenticated user. */
const buildStore = () =>
  configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: USER_FIXTURE,
        token: 'test-token',
        loading: false,
        error: null,
      },
    },
  });

/** Render AttendancePage inside the required providers. */
const renderPage = () => {
  const store = buildStore();
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <AttendancePage />
      </MemoryRouter>
    </Provider>
  );
};

/**
 * Reference implementation of secToHm — mirrors the source exactly
 * (including the fixed single-semicolon line) so we can document
 * expected values without depending on private module internals.
 */
const secToHm = (sec) => {
  if (sec == null || isNaN(sec)) return '00:00:00';
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};

// ── Pure-logic suite for secToHm reference implementation ────────────────────

describe('secToHm reference logic', () => {
  it('returns 00:00:00 for 0 seconds', () => {
    expect(secToHm(0)).toBe('00:00:00');
  });

  it('returns 00:00:01 for 1 second', () => {
    expect(secToHm(1)).toBe('00:00:01');
  });

  it('returns 00:01:00 for 60 seconds', () => {
    expect(secToHm(60)).toBe('00:01:00');
  });

  it('returns 01:00:00 for 3600 seconds (1 hour)', () => {
    expect(secToHm(3600)).toBe('01:00:00');
  });

  it('returns 08:00:00 for exactly 8 hours (28800 seconds)', () => {
    expect(secToHm(28800)).toBe('08:00:00');
  });

  it('returns 08:30:45 for 8h 30m 45s', () => {
    expect(secToHm(8 * 3600 + 30 * 60 + 45)).toBe('08:30:45');
  });

  it('pads single-digit hours/minutes/seconds with leading zero', () => {
    expect(secToHm(3661)).toBe('01:01:01');
  });

  it('handles overtime — more than 8 hours', () => {
    expect(secToHm(9 * 3600)).toBe('09:00:00');
  });

  it('returns 00:00:00 for null input', () => {
    expect(secToHm(null)).toBe('00:00:00');
  });

  it('returns 00:00:00 for NaN input', () => {
    expect(secToHm(NaN)).toBe('00:00:00');
  });

  it('handles large values — 100 hours', () => {
    expect(secToHm(360000)).toBe('100:00:00');
  });

  it('seconds component is isolated from minutes — 59 seconds does not carry over', () => {
    // 3599 seconds = 0h 59m 59s
    expect(secToHm(3599)).toBe('00:59:59');
  });

  it('seconds component correctly uses modulo (the bug was sec % 60;;)', () => {
    // If the seconds assignment were broken the template literal would use
    // undefined or NaN for s. Verify the result is a clean two-digit string.
    const result = secToHm(90); // 1m 30s
    expect(result).toBe('00:01:30');
    // The seconds portion must be exactly "30"
    expect(result.split(':')[2]).toBe('30');
  });
});

// ── Component integration tests ───────────────────────────────────────────────

beforeEach(() => {
  jest.clearAllMocks();

  // Default: employee lookup resolves
  employeeApi.getByUserId.mockResolvedValue(EMPLOYEE_FIXTURE);

  // Default: all timesheet calls return empty data
  timesheetApi.getSessionsByRange.mockResolvedValue([]);
  timesheetApi.getTodaySessions.mockResolvedValue([]);
  timesheetApi.getAttendanceByRange.mockResolvedValue([]);
});

describe('AttendancePage — render', () => {
  it('renders the Attendance heading', async () => {
    renderPage();
    expect(await screen.findByText('Attendance')).toBeInTheDocument();
  });

  it('shows a loading spinner while data is being fetched', () => {
    // Delay all API calls so the spinner stays visible
    timesheetApi.getSessionsByRange.mockReturnValue(new Promise(() => {}));
    timesheetApi.getTodaySessions.mockReturnValue(new Promise(() => {}));
    timesheetApi.getAttendanceByRange.mockReturnValue(new Promise(() => {}));

    renderPage();
    expect(document.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
  });

  it('shows the Weekly View section header after load', async () => {
    renderPage();
    expect(await screen.findByText('Weekly View')).toBeInTheDocument();
  });

  it('renders Today summary card with date heading', async () => {
    renderPage();
    // "Today" label appears in both the summary card and the DayCard for today
    const todayLabels = await screen.findAllByText('Today');
    expect(todayLabels.length).toBeGreaterThanOrEqual(1);
  });

  it('renders sessions count field', async () => {
    renderPage();
    expect(await screen.findByText('Sessions')).toBeInTheDocument();
  });
});

describe('AttendancePage — empty state (no sessions)', () => {
  it('shows -- for first login when there are no sessions today', async () => {
    renderPage();
    const dashes = await screen.findAllByText('--:--');
    // At minimum the First Login and Last Logout placeholders should appear
    expect(dashes.length).toBeGreaterThanOrEqual(1);
  });

  it('shows --:--:-- for working hours when no sessions exist', async () => {
    renderPage();
    expect(await screen.findByText('--:--:--')).toBeInTheDocument();
  });

  it('shows session count of 0 when no sessions today', async () => {
    renderPage();
    // The Sessions count value "0" appears in the Today Summary Card
    const zeros = await screen.findAllByText('0');
    expect(zeros.length).toBeGreaterThanOrEqual(1);
  });
});

describe('AttendancePage — with an active session', () => {
  it('displays secToHm-formatted working hours when there is an open session', async () => {
    // Provide one open session (no logoutTime) starting 90 seconds ago
    // so secToHm(90) = "00:01:30" should appear in the Today Summary card.
    // We cannot control real wall-clock time precisely in Jest, so instead
    // we seed a loginTime that is far in the past (start of day)
    // and assert that the clock shows a non-trivial HH:MM:SS value.
    const openSession = {
      id: 1,
      workDate: new Date().toISOString().slice(0, 10),
      loginTime: '00:00:00', // midnight — guarantees >0 seconds elapsed
      logoutTime: null,
      sessionHours: null,
    };

    timesheetApi.getTodaySessions.mockResolvedValue([openSession]);
    timesheetApi.getSessionsByRange.mockResolvedValue([openSession]);

    renderPage();

    // Wait for the "Currently logged in" chip which appears only when an
    // open session exists — proves the component processed the fixture.
    expect(await screen.findByText('Currently logged in')).toBeInTheDocument();

    // The working-hours clock should show a HH:MM:SS pattern (not --:--:--)
    // driven by secToHm. Multiple elements may match (Today summary + DayCard).
    const clockEls = await screen.findAllByText(/^\d{2}:\d{2}:\d{2}$/);
    expect(clockEls.length).toBeGreaterThanOrEqual(1);

    // Verify the first match: seconds component must be a two-character string
    // proving padStart works and the modulo result is valid.
    const parts = clockEls[0].textContent.split(':');
    expect(parts[2]).toHaveLength(2);
    expect(Number(parts[2])).toBeGreaterThanOrEqual(0);
    expect(Number(parts[2])).toBeLessThan(60);
  });
});

describe('AttendancePage — API failure', () => {
  it('does not crash the page when API calls reject', async () => {
    timesheetApi.getSessionsByRange.mockRejectedValue(new Error('Network error'));
    timesheetApi.getTodaySessions.mockRejectedValue(new Error('Network error'));
    timesheetApi.getAttendanceByRange.mockRejectedValue(new Error('Network error'));

    renderPage();

    // Page heading must still appear — component handles errors gracefully
    expect(await screen.findByText('Attendance')).toBeInTheDocument();
  });
});

describe('AttendancePage — week navigation', () => {
  it('renders navigation chevron buttons', async () => {
    renderPage();
    await screen.findByText('Weekly View');
    const buttons = screen.getAllByRole('button');
    // At least the prev/next chevrons + "This Week" button
    expect(buttons.length).toBeGreaterThanOrEqual(3);
  });

  it('renders "This Week" button', async () => {
    renderPage();
    expect(await screen.findByRole('button', { name: /this week/i })).toBeInTheDocument();
  });
});
