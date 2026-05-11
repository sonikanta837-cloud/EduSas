import React, { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Grid, Card, CardContent, Typography, Box, CircularProgress,
  LinearProgress, Chip
} from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import SchoolIcon from '@mui/icons-material/School';
import EventNoteIcon from '@mui/icons-material/EventNote';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import { dashboardApi } from '../api/dashboardApi';
import { toast } from 'react-toastify';

const StatCard = ({ title, value, icon, color, subtitle }) => (
  <Card sx={{ height: '100%' }}>
    <CardContent>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="body2" color="text.secondary" gutterBottom>{title}</Typography>
          <Typography variant="h4" fontWeight={700} color={color}>{value}</Typography>
          {subtitle && <Typography variant="caption" color="text.secondary">{subtitle}</Typography>}
        </Box>
        <Box sx={{
          width: 48, height: 48, borderRadius: 2,
          bgcolor: `${color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center'
        }}>
          {React.cloneElement(icon, { sx: { color, fontSize: 28 } })}
        </Box>
      </Box>
    </CardContent>
  </Card>
);

const DashboardPage = () => {
  const { user } = useSelector((s) => s.auth);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    dashboardApi.getStats()
      .then(setStats)
      .catch(() => toast.error('Failed to load dashboard'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
      <CircularProgress />
    </Box>
  );

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={700}>
          Welcome back, {user?.fullName?.split(' ')[0] || 'User'} 👋
        </Typography>
        <Typography variant="body1" color="text.secondary" mt={0.5}>
          Here's what's happening in your organization today.
        </Typography>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard title="Total Employees" value={stats?.totalEmployees || 0}
            icon={<PeopleIcon />} color="#1976d2"
            subtitle={`${stats?.activeEmployees || 0} active`} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard title="Pending Leaves" value={stats?.pendingLeaves || 0}
            icon={<EventNoteIcon />} color="#ed6c02" subtitle="Awaiting approval" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard title="Active Courses" value={stats?.activeCourses || 0}
            icon={<SchoolIcon />} color="#2e7d32" subtitle="Available to enroll" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard title="Work Hours Today" value={`${(stats?.totalWorkHoursToday || 0).toFixed(1)}h`}
            icon={<AccessTimeIcon />} color="#9c27b0" subtitle="Across all employees" />
        </Grid>

        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Course Completion Rate</Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 2 }}>
                <Box sx={{ flex: 1 }}>
                  <LinearProgress variant="determinate"
                    value={stats?.avgCourseCompletion || 0}
                    sx={{ height: 12, borderRadius: 6 }} />
                </Box>
                <Typography variant="h6" fontWeight={700} color="primary">
                  {(stats?.avgCourseCompletion || 0).toFixed(1)}%
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary" mt={1}>
                Overall course completion across all employees
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>Quick Stats</Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, mt: 1 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="body2" color="text.secondary">Active Employees</Typography>
                  <Chip label={stats?.activeEmployees || 0} size="small" color="success" />
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="body2" color="text.secondary">Total Managers</Typography>
                  <Chip label={stats?.totalManagers || 0} size="small" color="info" />
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography variant="body2" color="text.secondary">Pending Approvals</Typography>
                  <Chip label={stats?.pendingLeaves || 0} size="small" color="warning" />
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default DashboardPage;
