import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Box, Typography, Paper } from '@mui/material';
import { setActivePortal } from '../../store/portalSlice';
import { portalRegistry } from './portals';

// Shown in place of the routed page whenever no portal is selected — prompts
// the user to pick one. Selecting a card here (or a button in the switcher
// bar above) activates that portal, slides its sidebar in, and navigates to
// its dashboard.
const PortalWelcome = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { user } = useSelector((s) => s.auth);
  const { allowedPortals } = useSelector((s) => s.portal);
  const firstName = user?.fullName?.split(' ')[0] || 'there';
  const visiblePortals = portalRegistry.filter((p) => allowedPortals.includes(p.id));

  const selectPortal = (portal) => {
    dispatch(setActivePortal(portal.id));
    navigate(portal.dashboardPath);
  };

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', pt: { xs: 3, md: 6 } }}>
      <Box sx={{ maxWidth: 900, width: '100%', textAlign: 'center' }}>
        <Typography variant="h5" fontWeight={700} gutterBottom>
          Welcome, {firstName} 👋
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
          Choose a portal to get started.
        </Typography>

        {visiblePortals.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No portals are currently enabled for your role. Contact your administrator.
          </Typography>
        ) : (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: `repeat(${visiblePortals.length}, 1fr)` },
            gap: 2.5,
          }}
        >
          {visiblePortals.map((portal) => (
            <Paper
              key={portal.id}
              role="button"
              tabIndex={0}
              onClick={() => selectPortal(portal)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  selectPortal(portal);
                }
              }}
              elevation={0}
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                textAlign: 'center',
                gap: 1,
                py: 4,
                px: 2.5,
                cursor: 'pointer',
                border: '1px solid #e2e8f0',
                transition: 'all 0.18s ease',
                outline: 'none',
                '&:hover': {
                  borderColor: '#14b8a6',
                  bgcolor: '#f0fdfa',
                  transform: 'translateY(-3px)',
                  boxShadow: '0 8px 20px rgba(20,184,166,0.15)',
                },
                '&:focus-visible': {
                  boxShadow: '0 0 0 2px rgba(20,184,166,0.6)',
                },
              }}
            >
              <Box
                sx={{
                  width: 52,
                  height: 52,
                  borderRadius: '14px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  bgcolor: '#f0fdfa',
                  color: '#14b8a6',
                  mb: 0.5,
                  '& svg': { fontSize: 26 },
                }}
              >
                {portal.icon}
              </Box>
              <Typography sx={{ fontSize: '0.9375rem', fontWeight: 700, color: '#0f172a' }}>
                {portal.label}
              </Typography>
              <Typography sx={{ fontSize: '0.75rem', color: '#64748b', lineHeight: 1.5 }}>
                {portal.description}
              </Typography>
            </Paper>
          ))}
        </Box>
        )}
      </Box>
    </Box>
  );
};

export default PortalWelcome;
