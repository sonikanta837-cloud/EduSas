import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { createTheme, ThemeProvider, CssBaseline } from '@mui/material';
import Layout from './components/Layout/Layout';
import ProtectedRoute from './components/Auth/ProtectedRoute';
import HomePage from './pages/Home';
import LoginPage from './pages/Login';
import ResetPasswordPage from './pages/ResetPassword';
import DashboardPage from './pages/Dashboard';
import EmployeesPage from './pages/Employees';
import EmployeeDetailPage from './pages/EmployeeDetail';
import CoursesPage from './pages/Courses';
import ProfilePage from './pages/Profile';
import TimesheetPage from './pages/Timesheets';
import AttendancePage from './pages/Attendance';
import LeavesPage from './pages/Leaves';
import PerformancePage from './pages/Performance';
import ReportsPage from './pages/Reports';
import OrgChartPage from './pages/OrgChart';
import ResourcesPage from './pages/Resources';
import LeaveUploadPage from './pages/LeaveUpload';

const theme = createTheme({
  palette: {
    primary: { main: '#14b8a6', contrastText: '#ffffff' },
    secondary: { main: '#6366f1' },
    background: { default: '#f8fafc' },
  },
  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    h4: { fontWeight: 700 },
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
  },
  components: {
    MuiCard: {
      styleOverrides: {
        root: { borderRadius: 12, boxShadow: '0 1px 8px rgba(0,0,0,0.07)', border: '1px solid #f1f5f9' },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: { borderRadius: 8, textTransform: 'none', fontWeight: 600 },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { borderRadius: 12 },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 500 },
      },
    },
  },
});

function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/home" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/employees" element={<EmployeesPage />} />
            <Route path="/employees/:id" element={<EmployeeDetailPage />} />
            <Route path="/courses" element={<CoursesPage />} />
            <Route path="/timesheets" element={<TimesheetPage />} />
            <Route path="/attendance" element={<AttendancePage />} />
            <Route path="/leaves" element={<LeavesPage />} />
            <Route path="/performance" element={<PerformancePage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/org-chart" element={<OrgChartPage />} />
            <Route path="/resources" element={<ResourcesPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/leave-upload" element={<LeaveUploadPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </ThemeProvider>
  );
}

export default App;
