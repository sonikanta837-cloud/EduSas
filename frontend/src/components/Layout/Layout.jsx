import React, { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Box } from '@mui/material';
import Sidebar, { SIDEBAR_W_OPEN, SIDEBAR_W_CLOSED } from './Sidebar';
import Header, { HEADER_HEIGHT } from './Header';
import PortalSwitcherBar, { PORTAL_BAR_HEIGHT } from './PortalSwitcherBar';
import PortalWelcome from './PortalWelcome';
import PortalGuard from './PortalGuard';
import { findOwningPortal } from './portals';
import { fetchAllowedPortals } from '../../store/portalSlice';

const TOP_OFFSET = HEADER_HEIGHT + PORTAL_BAR_HEIGHT;
const SIDEBAR_TRANSITION_MS = 280;

const Layout = () => {
  const dispatch = useDispatch();
  const location = useLocation();
  const { sidebarOpen } = useSelector((s) => s.ui);
  const { activePortal } = useSelector((s) => s.portal);
  const sidebarW = activePortal ? (sidebarOpen ? SIDEBAR_W_OPEN : SIDEBAR_W_CLOSED) : 0;

  // Shared pages (Settings, Profile, ...) aren't owned by any portal, so they
  // must stay reachable from the header menu even before a portal is picked —
  // only portal-owned pages fall back to the "choose a portal" welcome screen.
  const isSharedPage = !findOwningPortal(location.pathname);

  // Re-syncs portal permissions with the backend once per app mount (i.e. on
  // every browser refresh) so grants/revokes made since the last login show up
  // without requiring the user to log out and back in.
  useEffect(() => {
    dispatch(fetchAllowedPortals());
  }, [dispatch]);

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: '#f8fafc' }}>
      <Header />
      <PortalSwitcherBar />
      <Sidebar />
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          minHeight: '100vh',
          ml: { xs: 0, md: `${sidebarW}px` },
          pt: `${TOP_OFFSET}px`,
          transition: `margin-left ${SIDEBAR_TRANSITION_MS}ms ease`,
        }}
      >
        <Box sx={{ p: 3 }}>
          {activePortal || isSharedPage ? <PortalGuard><Outlet /></PortalGuard> : <PortalWelcome />}
        </Box>
      </Box>
    </Box>
  );
};

export default Layout;
