import React, { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import { Box, Typography, CircularProgress } from '@mui/material';
import { employeeApi } from '../api/employeeApi';
import { toast } from 'react-toastify';
import PerformancePipTab from './PerformancePipTab';

const PerformancePipPage = () => {
  const { user } = useSelector((s) => s.auth);
  const [myEmployee, setMyEmployee] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    employeeApi.getByUserId(user.userId)
      .then(setMyEmployee)
      .catch(() => toast.error('Failed to load employee data'))
      .finally(() => setLoading(false));
  }, [user]);

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 360 }}>
      <CircularProgress sx={{ color: '#14b8a6' }} />
    </Box>
  );

  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh' }}>
      <Box sx={{ mb: 2 }}>
        <Typography sx={{ fontSize: 26, fontWeight: 700, color: '#0f172a', lineHeight: 1.2 }}>
          Performance Improvement
        </Typography>
        <Typography sx={{ fontSize: 13.5, color: '#64748b', mt: .4 }}>
          Track and manage improvement plans, goals, and progress reviews.
        </Typography>
      </Box>

      <PerformancePipTab myEmployee={myEmployee} />
    </Box>
  );
};

export default PerformancePipPage;
