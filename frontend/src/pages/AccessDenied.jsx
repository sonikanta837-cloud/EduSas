import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { Box, Typography, Paper, Button } from '@mui/material';
import BlockIcon from '@mui/icons-material/Block';
import { setActivePortal } from '../store/portalSlice';

const AccessDeniedPage = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();

  const backToPortals = () => {
    dispatch(setActivePortal(null));
    navigate('/dashboard');
  };

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', pt: { xs: 4, md: 8 } }}>
      <Paper sx={{ p: { xs: 3, sm: 5 }, textAlign: 'center', maxWidth: 460 }}>
        <BlockIcon sx={{ fontSize: 48, color: '#ef4444', mb: 2 }} />
        <Typography variant="h6" fontWeight={700} gutterBottom>
          403 — Access Denied
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Your role doesn't have permission to access this portal. Contact your administrator
          if you believe this is a mistake.
        </Typography>
        <Button variant="contained" onClick={backToPortals}>
          Back to Portal Selection
        </Button>
      </Paper>
    </Box>
  );
};

export default AccessDeniedPage;
