import React from 'react';
import { Outlet } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { Box, Toolbar } from '@mui/material';
import Sidebar, { SIDEBAR_W_OPEN, SIDEBAR_W_CLOSED } from './Sidebar';
import Header from './Header';

const Layout = () => {
  const { sidebarOpen } = useSelector((s) => s.ui);
  const sidebarW = sidebarOpen ? SIDEBAR_W_OPEN : SIDEBAR_W_CLOSED;

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: '#f8fafc' }}>
      <Sidebar />
      <Header />
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          minHeight: '100vh',
          ml: { xs: 0, md: `${sidebarW}px` },
          transition: 'margin-left 0.25s ease',
        }}
      >
        <Toolbar />
        <Box sx={{ p: 3 }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
};

export default Layout;
