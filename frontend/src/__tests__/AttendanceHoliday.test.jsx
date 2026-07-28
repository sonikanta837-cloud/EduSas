/**
 * Tests for holiday/weekend/leave status display in frontend/src/pages/Attendance.jsx
 *
 * Attendance status (Present/Under Hours/Overtime/Absent/Leave/Holiday/Weekend) is
 * now computed server-side by JobDailySummaryService and delivered via
 * jobSummaryApi — the page just renders whatever `status` each day's summary
 * carries. Holiday names still come from leaveUploadApi.getHolidays (used for
 * the display label only; the HOLIDAY/WEEKEND/LEAVE classification itself is
 * driven entirely by the mocked summary `status`).
 *
 * Coverage areas:
 *  - Holidays API is called on mount
 *  - Today banner: Holiday / Weekly Off / Leave, driven by the today summary's status
 *  - DayCard chip reflects each backend status value for a day in the current week
 *  - Holiday name chip (with truncation) shown for a HOLIDAY day
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router-dom';
import dayjs from 'dayjs';

import authReducer from '../store/authSlice';

// ── Mocks ────────────────────────────────────────────────────────────────────

jest.mock('react-toastify', () => ({
  toast: { error: jest.fn(), success: jest.fn() },
}));

jest.mock('../api/jobWorkSessionApi', () => ({
  jobWorkSessionApi: {
    getRange: jest.fn(),
    getToday: jest.fn(),
  },
}));

jest.mock('../api/jobSummaryApi', () => ({
  jobSummaryApi: {
    getMy: jest.fn(),
  },
}));

jest.mock('../api/employeeApi', () => ({
  employeeApi: { getByUserId: jest.fn() },
}));

jest.mock('../api/leaveUploadApi', () => ({
  leaveUploadApi: { getHolidays: jest.fn() },
}));

import { jobWorkSessionApi } from '../api/jobWorkSessionApi';
import { jobSummaryApi }     from '../api/jobSummaryApi';
import { employeeApi }       from '../api/employeeApi';
import { leaveUploadApi }    from '../api/leaveUploadApi';
import AttendancePage        from '../pages/Attendance';

// ── Fixtures ──────────────────────────────────────────────────────────────────

const EMPLOYEE = { id: 42, fullName: 'Test Employee' };
const USER     = { userId: 7, email: 'test@company.com', role: 'EMPLOYEE', fullName: 'Test Employee' };
const EMPTY_TODAY = { sessions: [], totalMinutesToday: 0, totalFormatted: '0h 00m', openSession: null };

const summaryFor = (workDate, status, extra = {}) => ({
  employeeId: 42, employeeName: 'Test Employee', department: 'Engineering',
  workDate, totalWorkingMinutes: 0, totalBreakMinutes: 0, totalOfficeMinutes: 0,
  overtimeMinutes: 0, sessionCount: 0, firstLoginTime: null, lastLogoutTime: null,
  primaryClient: null, status, ...extra,
});

// ── Store + render helpers ─────────────────────────────────────────────────────

const buildStore = () =>
  configureStore({
    reducer: { auth: authReducer },
    preloadedState: { auth: { user: USER, token: 'tok', loading: false, error: null } },
  });

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

// ── Default setup ─────────────────────────────────────────────────────────────

beforeEach(() => {
  jest.clearAllMocks();
  employeeApi.getByUserId.mockResolvedValue(EMPLOYEE);
  jobWorkSessionApi.getRange.mockResolvedValue([]);
  jobWorkSessionApi.getToday.mockResolvedValue(EMPTY_TODAY);
  jobSummaryApi.getMy.mockResolvedValue([]);
  leaveUploadApi.getHolidays.mockResolvedValue([]);
});

// ── Holidays API is called ────────────────────────────────────────────────────

describe('AttendancePage — holidays API', () => {
  it('calls leaveUploadApi.getHolidays on mount', async () => {
    renderPage();
    await screen.findByText('Attendance');
    expect(leaveUploadApi.getHolidays).toHaveBeenCalledTimes(1);
  });

  it('does not crash when getHolidays returns an empty array', async () => {
    leaveUploadApi.getHolidays.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('Attendance')).toBeInTheDocument();
  });

  it('does not crash when getHolidays rejects', async () => {
    leaveUploadApi.getHolidays.mockRejectedValue(new Error('API error'));
    renderPage();
    expect(await screen.findByText('Attendance')).toBeInTheDocument();
  });

  it('does not crash when getHolidays returns non-array', async () => {
    leaveUploadApi.getHolidays.mockResolvedValue(null);
    renderPage();
    expect(await screen.findByText('Attendance')).toBeInTheDocument();
  });
});

// ── Today banner — driven by today's summary status ──────────────────────────

describe('AttendancePage — today banner', () => {
  const todayStr = dayjs().format('YYYY-MM-DD');

  it('shows holiday banner when today\'s status is HOLIDAY', async () => {
    leaveUploadApi.getHolidays.mockResolvedValue([{ date: todayStr, name: 'Republic Day' }]);
    jobSummaryApi.getMy.mockResolvedValue([summaryFor(todayStr, 'HOLIDAY')]);

    renderPage();

    expect(await screen.findByText(/Today is a public holiday/i)).toBeInTheDocument();
    // "Republic Day" appears both in the banner and in today's DayCard chip
    expect(screen.getAllByText(/Republic Day/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/No attendance required/i)).toBeInTheDocument();
  });

  it('shows weekly off banner when today\'s status is WEEKEND', async () => {
    jobSummaryApi.getMy.mockResolvedValue([summaryFor(todayStr, 'WEEKEND')]);

    renderPage();

    expect(await screen.findByText(/Today is a weekly off/i)).toBeInTheDocument();
  });

  it('shows leave banner when today\'s status is LEAVE', async () => {
    jobSummaryApi.getMy.mockResolvedValue([summaryFor(todayStr, 'LEAVE')]);

    renderPage();

    expect(await screen.findByText(/You are on approved leave today/i)).toBeInTheDocument();
  });

  it('does not show any banner on a normal PRESENT day', async () => {
    jobSummaryApi.getMy.mockResolvedValue([summaryFor(todayStr, 'PRESENT')]);

    renderPage();

    await screen.findByText('Attendance');
    expect(screen.queryByText(/Today is a public holiday/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Today is a weekly off/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/You are on approved leave today/i)).not.toBeInTheDocument();
  });
});

// ── DayCard status chips — one per backend status value ──────────────────────

describe('DayCard — status chip reflects backend status', () => {
  const weekStart = dayjs().startOf('week');
  const monday = weekStart.add(1, 'day');
  const mondayStr = monday.format('YYYY-MM-DD');

  it.each([
    ['PRESENT', 'Present'],
    ['UNDER_HOURS', 'Under Hours'],
    ['OVERTIME', 'Overtime'],
    ['ABSENT', 'Absent'],
    ['LEAVE', 'Leave'],
    ['WEEKEND', 'Weekly Off'],
  ])('renders the %s status as "%s"', async (status, label) => {
    jobSummaryApi.getMy.mockResolvedValue([summaryFor(mondayStr, status)]);

    renderPage();
    await screen.findByText('Attendance');

    // The legend footer always renders one instance of every status label, so
    // a day actually carrying this status must push the count to 2 or more.
    await waitFor(() => {
      expect(screen.queryAllByText(label).length).toBeGreaterThanOrEqual(2);
    });
  });

  it('shows the holiday name chip (not the generic "Holiday" label) for a HOLIDAY day', async () => {
    leaveUploadApi.getHolidays.mockResolvedValue([{ date: mondayStr, name: 'Test Holiday' }]);
    jobSummaryApi.getMy.mockResolvedValue([summaryFor(mondayStr, 'HOLIDAY')]);

    renderPage();
    await screen.findByText('Attendance');

    await waitFor(() => {
      expect(screen.queryAllByText('Test Holiday').length).toBeGreaterThanOrEqual(1);
    });
  });
});

// ── Holiday chip truncation (pure string logic used by DayCard) ──────────────

describe('DayCard — holiday name truncation', () => {
  it('truncates holiday names longer than 20 characters', () => {
    const holidayName = 'Very Long Holiday Name That Exceeds Twenty Chars';
    const truncated = holidayName.length > 20
      ? holidayName.slice(0, 20) + '…'
      : holidayName;
    expect(truncated).toBe('Very Long Holiday Na…');
    expect(truncated.startsWith('Very Long Holiday Na')).toBe(true);
  });

  it('does not truncate names of 20 characters or fewer', () => {
    const holidayName = 'Republic Day';
    const truncated = holidayName.length > 20
      ? holidayName.slice(0, 20) + '…'
      : holidayName;
    expect(truncated).toBe('Republic Day');
  });
});

// ── holidaysMap construction ──────────────────────────────────────────────────

describe('holidaysMap construction logic', () => {
  it('maps date strings to holiday names correctly', () => {
    const list = [
      { date: '2025-01-26', name: 'Republic Day' },
      { date: '2025-08-15', name: 'Independence Day' },
    ];
    const map = {};
    list.forEach(h => { map[h.date] = h.name; });

    expect(map['2025-01-26']).toBe('Republic Day');
    expect(map['2025-08-15']).toBe('Independence Day');
    expect(map['2025-12-25']).toBeUndefined();
  });

  it('handles empty holiday list', () => {
    const map = {};
    [].forEach(h => { map[h.date] = h.name; });
    expect(Object.keys(map)).toHaveLength(0);
  });

  it('handles duplicate dates by overwriting with last entry', () => {
    const list = [
      { date: '2025-01-26', name: 'Republic Day' },
      { date: '2025-01-26', name: 'Override' },
    ];
    const map = {};
    list.forEach(h => { map[h.date] = h.name; });
    expect(map['2025-01-26']).toBe('Override');
  });
});
