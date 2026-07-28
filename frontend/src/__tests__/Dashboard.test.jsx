/**
 * Tests for frontend/src/pages/Dashboard.jsx
 *
 * Verified changes (reverted redesign):
 *  - All 4 dashboard roles render without crashing when no holidays prop is given
 *  - No holidays prop accepted by any sub-component
 *  - No attendance status strip in EmployeeDashboard
 *  - No UpcomingHolidays / QuickActionsRow components
 *  - Each role renders its expected KPI cards
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router-dom';

import authReducer from '../store/authSlice';

// ── Mocks ────────────────────────────────────────────────────────────────────

jest.mock('react-toastify', () => ({
  toast: { error: jest.fn(), success: jest.fn() },
}));

// Mock chart.js to avoid canvas errors in Jest
jest.mock('react-chartjs-2', () => ({
  Line:     () => <div data-testid="line-chart" />,
  Bar:      () => <div data-testid="bar-chart" />,
  Doughnut: () => <div data-testid="doughnut-chart" />,
}));

jest.mock('../api/dashboardApi', () => ({
  dashboardApi: { getStats: jest.fn() },
}));

jest.mock('../api/employeeApi', () => ({
  employeeApi: {
    getByUserId: jest.fn(),
    getAll: jest.fn(),
    getTeam: jest.fn(),
  },
}));

jest.mock('../api/leaveApi', () => ({
  leaveApi: {
    getAll: jest.fn(),
    getMyLeaves: jest.fn(),
    getForManager: jest.fn(),
  },
}));

jest.mock('../api/courseApi', () => ({
  courseApi: {
    getForEmployee: jest.fn(),
    getForManager: jest.fn(),
    getMyCertificates: jest.fn(),
  },
}));

jest.mock('../api/jobSummaryApi', () => ({
  jobSummaryApi: {
    getMy: jest.fn(),
  },
}));

jest.mock('../api/performanceApi', () => ({
  performanceApi: {
    getByReviewer: jest.fn(),
  },
}));

import { dashboardApi }  from '../api/dashboardApi';
import { employeeApi }   from '../api/employeeApi';
import { leaveApi }      from '../api/leaveApi';
import { courseApi }     from '../api/courseApi';
import { jobSummaryApi } from '../api/jobSummaryApi';
import { performanceApi } from '../api/performanceApi';
import DashboardPage     from '../pages/Dashboard';

// ── Helpers ───────────────────────────────────────────────────────────────────

const buildStore = (role) =>
  configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: { userId: 1, email: `${role.toLowerCase()}@company.com`, role, fullName: `Test ${role}` },
        token: 'test-token',
        loading: false,
        error: null,
      },
    },
  });

const renderDashboard = (role) => {
  const store = buildStore(role);
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </Provider>
  );
};

const STATS = {
  activeEmployees: 45, totalEmployees: 50, avgCourseCompletion: 72,
  totalWorkHoursToday: 320, activeCourses: 8, totalManagers: 5,
};

const EMPLOYEES = [
  { id: 1, firstName: 'Alice', lastName: 'Smith', active: true, role: 'EMPLOYEE', department: 'Engineering', employmentType: 'Full-time' },
  { id: 2, firstName: 'Bob',   lastName: 'Jones', active: true, role: 'MANAGER',  department: 'HR',          employmentType: 'Part-time' },
];

const LEAVES = [
  { id: 1, employeeName: 'Alice Smith', leaveType: 'Annual', status: 'PENDING',  startDate: '2025-06-01', endDate: '2025-06-03' },
  { id: 2, employeeName: 'Bob Jones',   leaveType: 'Sick',   status: 'APPROVED', startDate: '2025-05-20', endDate: '2025-05-21' },
];

// ── Default mock reset ────────────────────────────────────────────────────────

beforeEach(() => {
  jest.clearAllMocks();

  // Admin/HR defaults
  dashboardApi.getStats.mockResolvedValue(STATS);
  leaveApi.getAll.mockResolvedValue(LEAVES);
  employeeApi.getAll.mockResolvedValue(EMPLOYEES);

  // Manager defaults
  employeeApi.getByUserId.mockResolvedValue({ id: 99, firstName: 'Test', lastName: 'Manager', active: true, role: 'MANAGER' });
  employeeApi.getTeam.mockResolvedValue([]);
  leaveApi.getForManager.mockResolvedValue([]);
  courseApi.getForManager.mockResolvedValue([]);
  performanceApi.getByReviewer.mockResolvedValue([]);

  // Employee defaults
  leaveApi.getMyLeaves.mockResolvedValue([]);
  courseApi.getForEmployee.mockResolvedValue([]);
  jobSummaryApi.getMy.mockResolvedValue([]);
  courseApi.getMyCertificates.mockResolvedValue([]);
});

// ── AdminDashboard — renders without holidays prop ────────────────────────────

describe('AdminDashboard', () => {
  it('renders without crashing when no holidays prop is given', async () => {
    renderDashboard('ADMIN');
    // Wait for data load to complete — Director Dashboard badge appears after loading
    expect(await screen.findByText(/Director Dashboard/i)).toBeInTheDocument();
  });

  it('renders Total Workforce KPI card', async () => {
    renderDashboard('ADMIN');
    const els = await screen.findAllByText(/Total Workforce/i);
    expect(els.length).toBeGreaterThanOrEqual(1);
  });

  it('renders Pending Approvals KPI card', async () => {
    renderDashboard('ADMIN');
    const els = await screen.findAllByText(/Pending Approvals/i);
    expect(els.length).toBeGreaterThanOrEqual(1);
  });

  it('renders Learning Progress KPI card', async () => {
    renderDashboard('ADMIN');
    const els = await screen.findAllByText(/Learning Progress/i);
    expect(els.length).toBeGreaterThanOrEqual(1);
  });

  it('renders Attendance Rate KPI card', async () => {
    renderDashboard('ADMIN');
    const els = await screen.findAllByText(/Attendance Rate/i);
    expect(els.length).toBeGreaterThanOrEqual(1);
  });

  it('does NOT show holidays component', async () => {
    renderDashboard('ADMIN');
    await screen.findByText(/Director Dashboard/i);
    // No UpcomingHolidays section
    expect(screen.queryByText(/Upcoming Holidays/i)).not.toBeInTheDocument();
  });

  it('does NOT show Quick Actions section', async () => {
    renderDashboard('ADMIN');
    await screen.findByText(/Director Dashboard/i);
    expect(screen.queryByText(/Quick Actions/i)).not.toBeInTheDocument();
  });

  it('renders Director Dashboard badge', async () => {
    renderDashboard('ADMIN');
    expect(await screen.findByText(/Director Dashboard/i)).toBeInTheDocument();
  });

  it('renders Workforce Activity chart heading', async () => {
    renderDashboard('ADMIN');
    expect(await screen.findByText(/Workforce Activity/i)).toBeInTheDocument();
  });
});

// ── ManagerDashboard — renders without holidays prop ─────────────────────────

describe('ManagerDashboard', () => {
  it('renders without crashing when no holidays prop is given', async () => {
    renderDashboard('MANAGER');
    await waitFor(() => {
      expect(screen.getByText(/Good/i)).toBeInTheDocument();
    });
  });

  it('renders My Team KPI card', async () => {
    renderDashboard('MANAGER');
    const els = await screen.findAllByText(/My Team/i);
    expect(els.length).toBeGreaterThanOrEqual(1);
  });

  it('renders Pending Approvals KPI card', async () => {
    renderDashboard('MANAGER');
    const els = await screen.findAllByText(/Pending Approvals/i);
    expect(els.length).toBeGreaterThanOrEqual(1);
  });

  it('renders Team Enrollments KPI card', async () => {
    renderDashboard('MANAGER');
    const els = await screen.findAllByText(/Team Enrollments/i);
    expect(els.length).toBeGreaterThanOrEqual(1);
  });

  it('renders Reviews Given KPI card', async () => {
    renderDashboard('MANAGER');
    const els = await screen.findAllByText(/Reviews Given/i);
    expect(els.length).toBeGreaterThanOrEqual(1);
  });

  it('does NOT show holidays component', async () => {
    renderDashboard('MANAGER');
    // Wait for load - KPI cards appear after data resolves
    await screen.findAllByText(/My Team/i);
    expect(screen.queryByText(/Upcoming Holidays/i)).not.toBeInTheDocument();
  });
});

// ── HRDashboard — renders without holidays prop ───────────────────────────────

describe('HRDashboard', () => {
  it('renders without crashing when no holidays prop is given', async () => {
    renderDashboard('HR');
    await waitFor(() => {
      expect(screen.getByText(/Good/i)).toBeInTheDocument();
    });
  });

  it('renders Total Employees KPI card', async () => {
    renderDashboard('HR');
    expect(await screen.findByText(/Total Employees/i)).toBeInTheDocument();
  });

  it('renders Pending Leaves KPI card', async () => {
    renderDashboard('HR');
    expect(await screen.findByText(/Pending Leaves/i)).toBeInTheDocument();
  });

  it('renders Active Courses KPI card', async () => {
    renderDashboard('HR');
    expect(await screen.findByText(/Active Courses/i)).toBeInTheDocument();
  });

  it('does NOT show holidays component', async () => {
    renderDashboard('HR');
    await screen.findByText(/Total Employees/i);
    expect(screen.queryByText(/Upcoming Holidays/i)).not.toBeInTheDocument();
  });

  it('does NOT show Quick Actions row', async () => {
    renderDashboard('HR');
    await screen.findByText(/Total Employees/i);
    expect(screen.queryByText(/Quick Actions/i)).not.toBeInTheDocument();
  });
});

// ── EmployeeDashboard — renders without holidays prop ────────────────────────

describe('EmployeeDashboard', () => {
  beforeEach(() => {
    employeeApi.getByUserId.mockResolvedValue({
      id: 99, firstName: 'John', lastName: 'Doe', active: true, role: 'EMPLOYEE',
    });
  });

  it('renders without crashing when no holidays prop is given', async () => {
    renderDashboard('EMPLOYEE');
    await waitFor(() => {
      expect(screen.getByText(/Good/i)).toBeInTheDocument();
    });
  });

  it('renders Leave Balance KPI card', async () => {
    renderDashboard('EMPLOYEE');
    expect(await screen.findByText(/Leave Balance/i)).toBeInTheDocument();
  });

  it('renders My Courses KPI card', async () => {
    renderDashboard('EMPLOYEE');
    expect(await screen.findByText(/My Courses/i)).toBeInTheDocument();
  });

  it('renders Hours Today KPI card', async () => {
    renderDashboard('EMPLOYEE');
    expect(await screen.findByText(/Hours Today/i)).toBeInTheDocument();
  });

  it('renders Certificates KPI card', async () => {
    renderDashboard('EMPLOYEE');
    expect(await screen.findByText(/Certificates/i)).toBeInTheDocument();
  });

  it('does NOT show attendance status strip', async () => {
    renderDashboard('EMPLOYEE');
    await screen.findByText(/Leave Balance/i);
    // No attendance status chip like "Checked In" / "Not Checked In"
    expect(screen.queryByText(/Checked In/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Not Checked In/i)).not.toBeInTheDocument();
  });

  it('does NOT show holidays component', async () => {
    renderDashboard('EMPLOYEE');
    await screen.findByText(/Leave Balance/i);
    expect(screen.queryByText(/Upcoming Holidays/i)).not.toBeInTheDocument();
  });

  it('shows enrolled courses section', async () => {
    renderDashboard('EMPLOYEE');
    expect(await screen.findByText(/My Enrolled Courses/i)).toBeInTheDocument();
  });

  it('shows empty courses message when no enrollments', async () => {
    courseApi.getForEmployee.mockResolvedValue([]);
    renderDashboard('EMPLOYEE');
    expect(await screen.findByText(/No courses enrolled yet/i)).toBeInTheDocument();
  });

  it('shows Leave Summary section', async () => {
    renderDashboard('EMPLOYEE');
    expect(await screen.findByText(/Leave Summary/i)).toBeInTheDocument();
  });
});

// ── Loading state ─────────────────────────────────────────────────────────────

describe('DashboardPage — loading state', () => {
  it('shows loading spinner while data is fetched', () => {
    employeeApi.getByUserId.mockReturnValue(new Promise(() => {}));
    renderDashboard('EMPLOYEE');
    expect(document.querySelector('.MuiCircularProgress-root')).toBeInTheDocument();
  });
});

// ── API failure graceful handling ─────────────────────────────────────────────

describe('DashboardPage — API failure', () => {
  it('Admin dashboard renders with empty data when API fails', async () => {
    dashboardApi.getStats.mockRejectedValue(new Error('Network error'));
    leaveApi.getAll.mockRejectedValue(new Error('Network error'));
    employeeApi.getAll.mockRejectedValue(new Error('Network error'));

    renderDashboard('ADMIN');

    await waitFor(() => {
      // Should not crash — greeting still rendered
      expect(screen.getByText(/Good/i)).toBeInTheDocument();
    });
  });
});
