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
import HolidaysPage from './pages/Holidays';
import RolesPermissionsPage   from './pages/RolesPermissions';
import DailyWorkReportsPage   from './pages/DailyWorkReports';
import SettingsPage           from './pages/Settings';
import InterviewsPage             from './pages/Interviews';
import InterviewFeedbackPage      from './pages/InterviewFeedback';
import QuestionBankPage           from './pages/QuestionBank';
import TechnicalInterviewRoomPage from './pages/TechnicalInterviewRoom';
import ManagerInterviewRoomPage   from './pages/ManagerInterviewRoom';
import CandidateFinalRoomPage     from './pages/CandidateFinalRoom';
import DirectorInterviewRoomPage  from './pages/DirectorInterviewRoom';

const theme = createTheme({
  palette: {
    primary: { main: '#1e3a5f', light: '#2d5492', dark: '#152d4a', contrastText: '#ffffff' },
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
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { borderRadius: 8, textTransform: 'none', fontWeight: 600 },
        containedPrimary: {
          backgroundColor: '#1e3a5f',
          color: '#ffffff',
          '&:hover': { backgroundColor: '#152d4a' },
          '&.Mui-disabled': { backgroundColor: '#8fa8c8', color: '#ffffff' },
        },
        outlinedPrimary: {
          borderColor: '#1e3a5f',
          color: '#1e3a5f',
          '&:hover': { backgroundColor: 'rgba(30,58,95,0.06)', borderColor: '#152d4a' },
        },
        textPrimary: {
          color: '#1e3a5f',
          '&:hover': { backgroundColor: 'rgba(30,58,95,0.06)' },
        },
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
            <Route path="/holidays" element={<HolidaysPage />} />
            <Route path="/roles-permissions" element={<RolesPermissionsPage />} />
            <Route path="/work-reports" element={<DailyWorkReportsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/interviews" element={<InterviewsPage />} />
            <Route path="/interviews/feedback/:roundId" element={<InterviewFeedbackPage />} />
            <Route path="/question-bank" element={<QuestionBankPage />} />
            <Route path="/interview/room/:id"       element={<ManagerInterviewRoomPage />} />
            <Route path="/interview/final-room/:id" element={<DirectorInterviewRoomPage />} />
          </Route>
        </Route>
        {/* Public candidate interview — no auth required */}
        <Route path="/interview/technical/:token" element={<TechnicalInterviewRoomPage />} />
        <Route path="/interview/final/:token"     element={<CandidateFinalRoomPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </ThemeProvider>
  );
}

export default App;
