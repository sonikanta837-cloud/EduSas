import React, { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  AppBar, Toolbar, Typography, Box, Avatar, Menu,
  MenuItem, Divider, Tooltip, Badge, IconButton
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import ArrowBackIcon     from '@mui/icons-material/ArrowBack';
import LogoutIcon        from '@mui/icons-material/Logout';
import PersonIcon        from '@mui/icons-material/Person';
import { logout }       from '../../store/authSlice';
import { timesheetApi } from '../../api/timesheetApi';
import { SIDEBAR_W_OPEN, SIDEBAR_W_CLOSED } from './Sidebar';

const pageTitles = {
  '/dashboard':   'Dashboard',
  '/employees':   'Employee Dashboard',
  '/org-chart':   'Org Chart',
  '/courses':     'Courses',
  '/timesheets':  'Timesheets',
  '/attendance':  'Attendance',
  '/leaves':        'Leave Management',
  '/leave-upload':  'Leave Upload',
  '/performance':   'Performance',
  '/reports':     'Reports',
  '/resources':   'Resources',
  '/profile':     'My Profile',
};

const Header = () => {
  const dispatch  = useDispatch();
  const navigate  = useNavigate();
  const location  = useLocation();
  const { user }       = useSelector((s) => s.auth);
  const { sidebarOpen } = useSelector((s) => s.ui);
  const [anchorEl, setAnchorEl] = useState(null);

  const sidebarW = sidebarOpen ? SIDEBAR_W_OPEN : SIDEBAR_W_CLOSED;

  const pageTitle =
    pageTitles[location.pathname] ||
    Object.entries(pageTitles).find(([k]) => location.pathname.startsWith(k + '/'))?.[1] ||
    'EduSAS';

  const userInitials = user?.fullName
    ?.split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase() || 'U';

  const roleLabel = user?.role
    ? user.role.charAt(0) + user.role.slice(1).toLowerCase()
    : '';

  const handleLogout = async () => {
    if (user?.employeeId) {
      try { await timesheetApi.checkOut(user.employeeId); } catch { /* silent */ }
    }
    await dispatch(logout());
    navigate('/login');
  };

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        width: `calc(100% - ${sidebarW}px)`,
        ml: `${sidebarW}px`,
        transition: 'width 0.25s ease, margin-left 0.25s ease',
        bgcolor: 'white',
        borderBottom: '1px solid #e2e8f0',
        color: 'text.primary',
        zIndex: (theme) => theme.zIndex.appBar,
      }}
    >
      <Toolbar sx={{ gap: 1, minHeight: '64px !important', px: { xs: 2, sm: 3 } }}>

        {/* Back button — shown on all pages except dashboard */}
        {location.pathname !== '/dashboard' && (
          <Tooltip title="Go back">
            <IconButton
              size="small"
              onClick={() => navigate(-1)}
              sx={{ color: '#64748b', mr: 0.5, flexShrink: 0, '&:hover': { bgcolor: '#f1f5f9' } }}
            >
              <ArrowBackIcon sx={{ fontSize: '1.2rem' }} />
            </IconButton>
          </Tooltip>
        )}

        {/* Page title */}
        {pageTitle && (
          <Typography
            variant="h6"
            sx={{ fontWeight: 700, color: '#0f172a', fontSize: '1.35rem', whiteSpace: 'nowrap', flexShrink: 0 }}
          >
            {pageTitle}
          </Typography>
        )}

        <Box sx={{ flexGrow: 1 }} />

        {/* Notifications */}
        <Tooltip title="Notifications">
          <IconButton
            size="small"
            sx={{ color: '#64748b', '&:hover': { bgcolor: '#f1f5f9' }, mr: 0.5, flexShrink: 0 }}
          >
            <Badge badgeContent={0} color="error">
              <NotificationsIcon sx={{ fontSize: '1.3rem' }} />
            </Badge>
          </IconButton>
        </Tooltip>

        {/* User info + avatar */}
        <Box
          onClick={(e) => setAnchorEl(e.currentTarget)}
          sx={{
            display: 'flex', alignItems: 'center', gap: 1.2,
            cursor: 'pointer', px: 1, py: 0.5, borderRadius: 2, flexShrink: 0,
            '&:hover': { bgcolor: '#f1f5f9' }, transition: 'background 0.15s',
          }}
        >
          <Box sx={{ textAlign: 'right', display: { xs: 'none', sm: 'block' } }}>
            <Typography sx={{ fontSize: '0.82rem', fontWeight: 700, color: '#0f172a', lineHeight: 1.25 }}>
              {user?.fullName}
            </Typography>
            <Typography sx={{ fontSize: '0.7rem', color: '#64748b', lineHeight: 1.25 }}>
              {roleLabel}
            </Typography>
          </Box>
          <Avatar
            sx={{
              width: 36, height: 36,
              bgcolor: '#14b8a6',
              fontSize: '0.82rem', fontWeight: 700,
              flexShrink: 0,
            }}
          >
            {userInitials}
          </Avatar>
        </Box>

        {/* Explicit logout button — always visible */}
        <Tooltip title="Logout">
          <IconButton
            size="small"
            onClick={handleLogout}
            sx={{ color: '#ef4444', ml: 0.5, flexShrink: 0, '&:hover': { bgcolor: '#fef2f2' } }}
          >
            <LogoutIcon sx={{ fontSize: '1.2rem' }} />
          </IconButton>
        </Tooltip>

        {/* Dropdown menu */}
        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={() => setAnchorEl(null)}
          PaperProps={{
            sx: {
              minWidth: 210, mt: 1, borderRadius: 2,
              boxShadow: '0 4px 24px rgba(0,0,0,0.12)',
              border: '1px solid #f1f5f9',
            },
          }}
        >
          <Box sx={{ px: 2, py: 1.5 }}>
            <Typography variant="subtitle2" fontWeight={700}>{user?.fullName}</Typography>
            <Typography variant="caption" color="text.secondary">{user?.email}</Typography>
          </Box>
          <Divider />
          <MenuItem
            onClick={() => { navigate('/profile'); setAnchorEl(null); }}
            sx={{ gap: 1.5, py: 1, fontSize: '0.875rem' }}
          >
            <PersonIcon fontSize="small" sx={{ color: '#64748b' }} /> My Profile
          </MenuItem>
          <MenuItem
            onClick={handleLogout}
            sx={{ color: 'error.main', gap: 1.5, py: 1, fontSize: '0.875rem' }}
          >
            <LogoutIcon fontSize="small" /> Logout
          </MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
};

export default Header;
