import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { Box, Tooltip, Typography, IconButton } from '@mui/material';
import ChevronLeftIcon  from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import DashboardIcon    from '@mui/icons-material/Dashboard';
import PeopleIcon       from '@mui/icons-material/People';
import SchoolIcon       from '@mui/icons-material/School';
import AccessTimeIcon   from '@mui/icons-material/AccessTime';
import EventNoteIcon    from '@mui/icons-material/EventNote';
import HowToRegIcon     from '@mui/icons-material/HowToReg';
import StarIcon         from '@mui/icons-material/Star';
import BarChartIcon     from '@mui/icons-material/BarChart';
import AccountTreeIcon  from '@mui/icons-material/AccountTree';
import FolderIcon       from '@mui/icons-material/Folder';
import PersonIcon       from '@mui/icons-material/Person';
import FileUploadIcon   from '@mui/icons-material/FileUpload';
import { toggleSidebar } from '../../store/uiSlice';

export const SIDEBAR_W_OPEN   = 240;
export const SIDEBAR_W_CLOSED = 72;

const allNavItems = [
  { label: 'Dashboard',    path: '/dashboard',    icon: <DashboardIcon />,    roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { label: 'Employees',    path: '/employees',    icon: <PeopleIcon />,       roles: ['ADMIN', 'MANAGER'] },
  { label: 'Org Chart',    path: '/org-chart',    icon: <AccountTreeIcon />,  roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { label: 'Courses',      path: '/courses',      icon: <SchoolIcon />,       roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { label: 'Timesheets',   path: '/timesheets',   icon: <AccessTimeIcon />,   roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { label: 'Attendance',   path: '/attendance',   icon: <HowToRegIcon />,     roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { label: 'Leaves',       path: '/leaves',       icon: <EventNoteIcon />,    roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { label: 'Leave Upload', path: '/leave-upload', icon: <FileUploadIcon />,   roles: ['ADMIN'] },
  { label: 'Performance',  path: '/performance',  icon: <StarIcon />,         roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { label: 'Reports',      path: '/reports',      icon: <BarChartIcon />,     roles: ['ADMIN', 'MANAGER'] },
  { label: 'Resources',    path: '/resources',    icon: <FolderIcon />,       roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
  { label: 'My Profile',   path: '/profile',      icon: <PersonIcon />,       roles: ['ADMIN', 'MANAGER', 'EMPLOYEE'] },
];

const Sidebar = () => {
  const dispatch  = useDispatch();
  const navigate  = useNavigate();
  const location  = useLocation();
  const { user }        = useSelector((s) => s.auth);
  const { sidebarOpen } = useSelector((s) => s.ui);
  const role     = user?.role || 'EMPLOYEE';
  const navItems = allNavItems.filter((item) => item.roles.includes(role));
  const width    = sidebarOpen ? SIDEBAR_W_OPEN : SIDEBAR_W_CLOSED;

  return (
    <>
      {/* Toggle button — separate fixed element so overflow:hidden doesn't clip it */}
      <IconButton
        onClick={() => dispatch(toggleSidebar())}
        size="small"
        sx={{
          position: 'fixed',
          top: 18,
          left: width - 13,
          zIndex: 1300,
          width: 26,
          height: 26,
          bgcolor: '#1e293b',
          border: '2px solid rgba(255,255,255,0.2)',
          color: '#14b8a6',
          transition: 'left 0.25s ease',
          '&:hover': { bgcolor: 'rgba(20,184,166,0.25)', borderColor: '#14b8a6' },
          boxShadow: '0 2px 8px rgba(0,0,0,0.4)',
        }}
      >
        {sidebarOpen ? <ChevronLeftIcon sx={{ fontSize: 16 }} /> : <ChevronRightIcon sx={{ fontSize: 16 }} />}
      </IconButton>

    <Box
      sx={{
        position: 'fixed',
        top: 0,
        left: 0,
        width,
        height: '100vh',
        bgcolor: '#0f172a',
        display: 'flex',
        flexDirection: 'column',
        alignItems: sidebarOpen ? 'flex-start' : 'center',
        zIndex: 1200,
        boxShadow: '2px 0 8px rgba(0,0,0,0.18)',
        overflowY: 'auto',
        overflowX: 'hidden',
        transition: 'width 0.25s ease',
        '&::-webkit-scrollbar': { display: 'none' },
      }}
    >
      {/* Logo row */}
      <Box
        sx={{
          width: '100%',
          height: 64,
          display: 'flex',
          alignItems: 'center',
          justifyContent: sidebarOpen ? 'flex-start' : 'center',
          flexShrink: 0,
          borderBottom: '1px solid rgba(255,255,255,0.07)',
          mb: 1,
          px: sidebarOpen ? 2 : 0,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <img
            src="/logo.png"
            alt="EduSAS"
            style={{ height: 34, width: 34, objectFit: 'contain', flexShrink: 0 }}
          />
          {sidebarOpen && (
            <Typography sx={{ fontSize: '1rem', fontWeight: 700, color: '#fff', whiteSpace: 'nowrap' }}>
              EduSAS
            </Typography>
          )}
        </Box>
      </Box>

      {/* Nav items */}
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: sidebarOpen ? 'flex-start' : 'center',
          width: '100%',
          px: sidebarOpen ? 1.5 : 0.5,
          gap: 0.5,
          flex: 1,
        }}
      >
        {navItems.map((item) => {
          const active =
            location.pathname === item.path ||
            (item.path !== '/dashboard' && location.pathname.startsWith(item.path + '/'));

          const itemContent = (
            <Box
              onClick={() => navigate(item.path)}
              sx={{
                width: '100%',
                display: 'flex',
                flexDirection: sidebarOpen ? 'row' : 'column',
                alignItems: 'center',
                justifyContent: sidebarOpen ? 'flex-start' : 'center',
                py: sidebarOpen ? 0.9 : 1,
                px: sidebarOpen ? 1.5 : 0.5,
                borderRadius: '10px',
                cursor: 'pointer',
                position: 'relative',
                color: active ? '#14b8a6' : 'rgba(255,255,255,0.55)',
                bgcolor: active ? 'rgba(20,184,166,0.15)' : 'transparent',
                transition: 'all 0.15s ease',
                '&:hover': {
                  bgcolor: active ? 'rgba(20,184,166,0.2)' : 'rgba(255,255,255,0.06)',
                  color: active ? '#14b8a6' : 'rgba(255,255,255,0.9)',
                },
                '&::before': active ? {
                  content: '""',
                  position: 'absolute',
                  left: 0,
                  top: '20%',
                  height: '60%',
                  width: 3,
                  borderRadius: '0 3px 3px 0',
                  bgcolor: '#14b8a6',
                } : {},
              }}
            >
              <Box sx={{ '& svg': { fontSize: 22, display: 'block' }, flexShrink: 0, mr: sidebarOpen ? 1.5 : 0 }}>
                {item.icon}
              </Box>

              {sidebarOpen ? (
                <Typography sx={{ fontSize: '0.875rem', fontWeight: active ? 600 : 400, whiteSpace: 'nowrap', color: 'inherit' }}>
                  {item.label}
                </Typography>
              ) : (
                <Typography sx={{ fontSize: 9.5, fontWeight: active ? 700 : 400, lineHeight: 1.2, textAlign: 'center', mt: 0.5, wordBreak: 'break-word', maxWidth: 62, color: 'inherit' }}>
                  {item.label}
                </Typography>
              )}
            </Box>
          );

          return sidebarOpen ? (
            <Box key={item.path} sx={{ width: '100%' }}>{itemContent}</Box>
          ) : (
            <Tooltip key={item.path} title={item.label} placement="right" arrow>
              <Box sx={{ width: '100%' }}>{itemContent}</Box>
            </Tooltip>
          );
        })}
      </Box>

      {/* Version */}
      <Box sx={{ py: 1.5, flexShrink: 0, width: '100%', textAlign: 'center' }}>
        <Typography sx={{ fontSize: 8.5, color: 'rgba(255,255,255,0.18)', letterSpacing: '0.04em' }}>
          v1.0
        </Typography>
      </Box>
    </Box>
    </>
  );
};

export default Sidebar;
