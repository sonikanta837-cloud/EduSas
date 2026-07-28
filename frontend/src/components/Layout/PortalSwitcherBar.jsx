import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Box, Typography } from '@mui/material';
import { setActivePortal } from '../../store/portalSlice';
import { portalRegistry } from './portals';
import { HEADER_HEIGHT } from './Header';

export const PORTAL_BAR_HEIGHT = 52;

// Full-width strip directly under the header — spans edge-to-edge like the
// header itself, rather than being confined to the sidebar's column width.
// Adding a portal only requires an entry in `portalRegistry`; this renders generically.
const PortalSwitcherBar = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { activePortal, allowedPortals } = useSelector((s) => s.portal);
  const visiblePortals = portalRegistry.filter((p) => allowedPortals.includes(p.id));

  const handleSwitch = (portal) => {
    dispatch(setActivePortal(portal.id));
    if (portal.id !== activePortal) navigate(portal.dashboardPath);
  };

  const handleKeyDown = (e, portal) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleSwitch(portal);
    }
  };

  return (
    <Box
      role="tablist"
      aria-label="Portal switcher"
      sx={{
        position: 'fixed',
        top: HEADER_HEIGHT,
        left: 0,
        right: 0,
        height: PORTAL_BAR_HEIGHT,
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        px: { xs: 2, sm: 3 },
        background: 'linear-gradient(180deg, #1e293b 0%, #16213a 100%)',
        borderTop: '1px solid rgba(255,255,255,0.05)',
        borderBottom: '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 2px 8px rgba(0,0,0,0.18)',
        zIndex: (theme) => theme.zIndex.drawer + 50,
        overflowX: 'auto',
        overflowY: 'hidden',
      }}
    >
      {visiblePortals.map((portal) => {
        const active = portal.id === activePortal;
        return (
          <Box
            key={portal.id}
            role="tab"
            aria-selected={active}
            tabIndex={0}
            onClick={() => handleSwitch(portal)}
            onKeyDown={(e) => handleKeyDown(e, portal)}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.85,
              py: 0.85,
              px: 1.75,
              borderRadius: '10px',
              cursor: 'pointer',
              outline: 'none',
              flexShrink: 0,
              color: active ? '#14b8a6' : 'rgba(255,255,255,0.55)',
              bgcolor: active ? 'rgba(20,184,166,0.15)' : 'transparent',
              border: active ? '1px solid rgba(20,184,166,0.4)' : '1px solid transparent',
              transition: 'all 0.15s ease',
              '&:hover': {
                bgcolor: active ? 'rgba(20,184,166,0.2)' : 'rgba(255,255,255,0.06)',
                color: active ? '#14b8a6' : 'rgba(255,255,255,0.9)',
              },
              '&:focus-visible': {
                boxShadow: 'inset 0 0 0 2px rgba(20,184,166,0.6)',
              },
            }}
          >
            <Box sx={{ '& svg': { fontSize: 19, display: 'block' } }}>{portal.icon}</Box>
            <Typography
              sx={{
                fontSize: '0.82rem',
                fontWeight: active ? 700 : 500,
                whiteSpace: 'nowrap',
                color: 'inherit',
              }}
            >
              {portal.label}
            </Typography>
          </Box>
        );
      })}
    </Box>
  );
};

export default PortalSwitcherBar;
