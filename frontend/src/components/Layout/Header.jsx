import React, { useState, useEffect, useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  AppBar, Toolbar, Typography, Box, Avatar, Menu,
  MenuItem, Divider, Tooltip, Badge, IconButton,
  Popover, Button, CircularProgress,
} from '@mui/material';
import NotificationsIcon  from '@mui/icons-material/Notifications';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import ArrowBackIcon      from '@mui/icons-material/ArrowBack';
import LogoutIcon         from '@mui/icons-material/Logout';
import PersonIcon         from '@mui/icons-material/Person';
import SettingsIcon      from '@mui/icons-material/Settings';
import DoneAllIcon        from '@mui/icons-material/DoneAll';
import ArrowForwardIcon   from '@mui/icons-material/ArrowForward';
import CampaignIcon           from '@mui/icons-material/Campaign';
import SchoolIcon             from '@mui/icons-material/School';
import BeachAccessIcon        from '@mui/icons-material/BeachAccess';
import MenuBookIcon           from '@mui/icons-material/MenuBook';
import EmojiEventsIcon        from '@mui/icons-material/EmojiEvents';
import GavelIcon              from '@mui/icons-material/Gavel';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import AssignmentLateIcon      from '@mui/icons-material/AssignmentLate';
import { logout }                  from '../../store/authSlice';
import { timesheetApi }            from '../../api/timesheetApi';
import { announcementApi }         from '../../api/announcementApi';
import { courseNotificationApi }   from '../../api/courseNotificationApi';
import { pipNotificationApi }      from '../../api/pipNotificationApi';
import { SIDEBAR_W_OPEN, SIDEBAR_W_CLOSED } from './Sidebar';

const pageTitles = {
  '/dashboard':   'Dashboard',
  '/employees':   'Employee Dashboard',
  '/org-chart':   'Organisation',
  '/courses':     'Courses',
  '/timesheets':  'Timesheets',
  '/attendance':  'Attendance',
  '/leaves':      'Leave Management',
  '/leave-upload':'Leave Management',
  '/performance': 'Performance',
  '/reports':     'Reports',
  '/resources':   'Resources',
  '/profile':            'My Profile',
  '/roles-permissions':  'Roles & Permissions',
  '/settings':           'Settings',
};

const fallbackRoutes = {
  '/employees':   '/employees',
  '/org-chart':   '/dashboard',
  '/courses':     '/courses',
  '/timesheets':  '/timesheets',
  '/attendance':  '/attendance',
  '/leaves':      '/leaves',
  '/leave-upload':'/leave-upload',
  '/performance': '/performance',
  '/reports':     '/reports',
  '/resources':   '/resources',
  '/profile':           '/dashboard',
  '/roles-permissions': '/dashboard',
};

// Category colours (kept local — no shared module needed)
const CAT_MAP = {
  General:     { color: '#475569', dot: '#94a3b8', bg: '#f1f5f9', IconComp: CampaignIcon          },
  Holiday:     { color: '#ea580c', dot: '#f97316', bg: '#fff7ed', IconComp: BeachAccessIcon       },
  Training:    { color: '#2563eb', dot: '#3b82f6', bg: '#eff6ff', IconComp: MenuBookIcon          },
  Recognition: { color: '#7c3aed', dot: '#8b5cf6', bg: '#f5f3ff', IconComp: EmojiEventsIcon      },
  Policy:      { color: '#dc2626', dot: '#ef4444', bg: '#fef2f2', IconComp: GavelIcon             },
  Important:   { color: '#b45309', dot: '#f59e0b', bg: '#fffbeb', IconComp: NotificationsActiveIcon },
};
const catCfg = (cat) => CAT_MAP[cat] || CAT_MAP.General;

const fmtNotif = (dt) => {
  if (!dt) return '';
  const d    = new Date(dt);
  const diff = Math.floor((Date.now() - d.getTime()) / 1000);
  if (diff < 60)     return 'Just now';
  if (diff < 3600)   return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400)  return `${Math.floor(diff / 3600)}h ago`;
  if (diff < 604800) return `${Math.floor(diff / 86400)}d ago`;
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
};

const Header = () => {
  const dispatch  = useDispatch();
  const navigate  = useNavigate();
  const location  = useLocation();
  const { user }       = useSelector((s) => s.auth);
  const { sidebarOpen } = useSelector((s) => s.ui);

  const [anchorEl,       setAnchorEl]       = useState(null);
  const [unreadCount,    setUnreadCount]    = useState(0);   // announcements
  const [courseUnread,   setCourseUnread]   = useState(0);
  const [pipUnread,      setPipUnread]      = useState(0);
  const [notifAnchor,    setNotifAnchor]    = useState(null);
  const [unreadItems,    setUnreadItems]    = useState([]);
  const [courseItems,    setCourseItems]    = useState([]);
  const [pipItems,       setPipItems]       = useState([]);
  const [notifLoading,   setNotifLoading]   = useState(false);

  const totalUnread = unreadCount + courseUnread + pipUnread;

  const sidebarW  = sidebarOpen ? SIDEBAR_W_OPEN : SIDEBAR_W_CLOSED;
  const pageTitle =
    pageTitles[location.pathname] ||
    Object.entries(pageTitles).find(([k]) => location.pathname.startsWith(k + '/'))?.[1] ||
    'EduSAS';
  const userInitials = user?.fullName
    ?.split(' ').map((n) => n[0]).join('').slice(0, 2).toUpperCase() || 'U';
  const roleLabel = user?.role
    ? user.role.charAt(0) + user.role.slice(1).toLowerCase()
    : '';

  // ── Fetch unread counts on login ─────────────────────────────────────────────
  useEffect(() => {
    if (!user) return;
    announcementApi.getUnreadCount()
      .then(data => setUnreadCount(data?.count ?? 0))
      .catch(() => {});
    courseNotificationApi.getUnreadCount()
      .then(data => setCourseUnread(data?.count ?? 0))
      .catch(() => {});
    pipNotificationApi.getUnreadCount()
      .then(data => setPipUnread(data?.count ?? 0))
      .catch(() => {});
  }, [user]);

  // ── Reset badge when AnnouncementsTab marks everything read ──────────────────
  useEffect(() => {
    const handleRead = () => { setUnreadCount(0); setUnreadItems([]); };
    window.addEventListener('announcements-read', handleRead);
    return () => window.removeEventListener('announcements-read', handleRead);
  }, []);

  // ── Reset course badge when courses tab marks all read ───────────────────────
  useEffect(() => {
    const handleRead = () => { setCourseUnread(0); setCourseItems([]); };
    window.addEventListener('course-notifications-read', handleRead);
    return () => window.removeEventListener('course-notifications-read', handleRead);
  }, []);

  // ── Reset PIP badge ───────────────────────────────────────────────────────────
  useEffect(() => {
    const handleRead = () => { setPipUnread(0); setPipItems([]); };
    window.addEventListener('pip-notifications-read', handleRead);
    return () => window.removeEventListener('pip-notifications-read', handleRead);
  }, []);

  // ── Open notification popover and load all unread items ─────────────────────
  const openNotifications = useCallback((e) => {
    setNotifAnchor(e.currentTarget);
    setNotifLoading(true);
    Promise.all([
      announcementApi.getUnread().catch(() => []),
      courseNotificationApi.getUnread().catch(() => []),
      pipNotificationApi.getUnread().catch(() => []),
    ]).then(([ann, course, pip]) => {
      setUnreadItems(Array.isArray(ann)    ? ann    : []);
      setCourseItems(Array.isArray(course) ? course : []);
      setPipItems(Array.isArray(pip)       ? pip    : []);
    }).finally(() => setNotifLoading(false));
  }, []);

  // ── Click single notification ────────────────────────────────────────────────
  const handleNotifClick = useCallback(async (item) => {
    try {
      await announcementApi.markAsRead(item.id);
    } catch { /* silent */ }
    setUnreadItems(prev => prev.filter(i => i.id !== item.id));
    setUnreadCount(prev => Math.max(0, prev - 1));
    setNotifAnchor(null);
    navigate('/org-chart?tab=1');
  }, [navigate]);

  // ── Click a course notification ───────────────────────────────────────────────
  const handleCourseNotifClick = useCallback(async (item) => {
    try { await courseNotificationApi.markAsRead(item.id); } catch { /* silent */ }
    setCourseItems(prev => prev.filter(i => i.id !== item.id));
    setCourseUnread(prev => Math.max(0, prev - 1));
    setNotifAnchor(null);
    navigate('/courses');
  }, [navigate]);

  // ── Click a PIP notification ──────────────────────────────────────────────────
  const handlePipNotifClick = useCallback(async (item) => {
    try { await pipNotificationApi.markAsRead(item.id); } catch { /* silent */ }
    setPipItems(prev => prev.filter(i => i.id !== item.id));
    setPipUnread(prev => Math.max(0, prev - 1));
    setNotifAnchor(null);
    navigate('/performance');
  }, [navigate]);

  // ── Mark all read ────────────────────────────────────────────────────────────
  const markAllRead = useCallback(async () => {
    await Promise.all([
      ...unreadItems.map(i => announcementApi.markAsRead(i.id).catch(() => {})),
      courseNotificationApi.markAllRead().catch(() => {}),
      pipNotificationApi.markAllRead().catch(() => {}),
    ]);
    setUnreadItems([]);
    setCourseItems([]);
    setPipItems([]);
    setUnreadCount(0);
    setCourseUnread(0);
    setPipUnread(0);
    window.dispatchEvent(new Event('announcements-read'));
    window.dispatchEvent(new Event('course-notifications-read'));
    window.dispatchEvent(new Event('pip-notifications-read'));
  }, [unreadItems]);

  const handleLogout = async () => {
    if (user?.employeeId) {
      try { await timesheetApi.checkOut(user.employeeId); } catch { /* silent */ }
    }
    await dispatch(logout());
    navigate('/login');
  };

  const notifOpen = Boolean(notifAnchor);

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

        {/* Back button */}
        {location.pathname !== '/dashboard' && (
          <Tooltip title="Go back">
            <IconButton
              size="small"
              onClick={() => {
                if (window.history.state?.idx > 0) {
                  navigate(-1);
                } else {
                  const fallback = Object.entries(fallbackRoutes)
                    .find(([k]) => location.pathname.startsWith(k))?.[1] || '/dashboard';
                  navigate(fallback);
                }
              }}
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

        {/* ── Notification bell ── */}
        <Tooltip title="Notifications">
          <IconButton
            size="small"
            onClick={openNotifications}
            sx={{
              color: notifOpen ? '#14b8a6' : '#64748b',
              bgcolor: notifOpen ? '#f0fdfa' : 'transparent',
              '&:hover': { bgcolor: '#f1f5f9' },
              mr: 0.5, flexShrink: 0,
            }}
          >
            <Badge
              badgeContent={totalUnread > 0 ? totalUnread : null}
              color="error"
              max={99}
              sx={{ '& .MuiBadge-badge': { fontSize: 10, minWidth: 17, height: 17, px: .5 } }}
            >
              {totalUnread > 0
                ? <NotificationsIcon     sx={{ fontSize: '1.3rem' }} />
                : <NotificationsNoneIcon sx={{ fontSize: '1.3rem' }} />
              }
            </Badge>
          </IconButton>
        </Tooltip>

        {/* ── Notification Popover ── */}
        <Popover
          anchorEl={notifAnchor}
          open={notifOpen}
          onClose={() => setNotifAnchor(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          PaperProps={{
            sx: {
              width: 390, maxHeight: 540, borderRadius: '16px',
              boxShadow: '0 8px 40px rgba(0,0,0,0.14)',
              border: '1px solid #f1f5f9', mt: 1, overflow: 'hidden',
              display: 'flex', flexDirection: 'column',
            },
          }}
        >
          {/* Popover header */}
          <Box sx={{
            px: 2.5, py: 1.75, display: 'flex', alignItems: 'center',
            justifyContent: 'space-between', flexShrink: 0,
            borderBottom: '1px solid #f1f5f9', bgcolor: '#fff',
          }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography sx={{ fontWeight: 700, fontSize: 15.5, color: '#0f172a' }}>
                Notifications
              </Typography>
              {totalUnread > 0 && (
                <Box sx={{ px: 1, py: .2, borderRadius: '20px', bgcolor: '#14b8a6', minWidth: 22, textAlign: 'center' }}>
                  <Typography sx={{ fontSize: 11, fontWeight: 800, color: '#fff', lineHeight: 1.6 }}>
                    {totalUnread > 99 ? '99+' : totalUnread}
                  </Typography>
                </Box>
              )}
            </Box>
            {(unreadItems.length > 0 || courseItems.length > 0 || pipItems.length > 0) && (
              <Button
                size="small" startIcon={<DoneAllIcon sx={{ fontSize: 14 }} />}
                onClick={markAllRead}
                sx={{
                  fontSize: 12, color: '#14b8a6', fontWeight: 600,
                  textTransform: 'none', py: .4, px: 1.25, borderRadius: '8px',
                  '&:hover': { bgcolor: '#f0fdfa' },
                }}
              >
                Mark all read
              </Button>
            )}
          </Box>

          {/* Popover body */}
          <Box sx={{ flex: 1, overflow: 'auto' }}>
            {notifLoading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 5 }}>
                <CircularProgress size={26} sx={{ color: '#14b8a6' }} />
              </Box>
            ) : unreadItems.length === 0 && courseItems.length === 0 && pipItems.length === 0 ? (
              <Box sx={{ textAlign: 'center', py: 6, px: 3 }}>
                <Box sx={{
                  width: 56, height: 56, borderRadius: '50%', bgcolor: '#f0fdfa',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', mx: 'auto', mb: 1.5,
                }}>
                  <CampaignIcon sx={{ fontSize: 28, color: '#14b8a6' }} />
                </Box>
                <Typography sx={{ color: '#0f172a', fontSize: 14.5, fontWeight: 700 }}>
                  All caught up!
                </Typography>
                <Typography sx={{ color: '#94a3b8', fontSize: 13, mt: .5 }}>
                  No new notifications
                </Typography>
              </Box>
            ) : (
              <>
                {/* Section header for announcements when course notifs also exist */}
                {courseItems.length > 0 && unreadItems.length > 0 && (
                  <Box sx={{ px: 2.5, py: .75, bgcolor: '#f8fafc', borderBottom: '1px solid #f1f5f9' }}>
                    <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px' }}>
                      Announcements
                    </Typography>
                  </Box>
                )}
                {unreadItems.map((item, idx) => {
                const cc = catCfg(item.category || 'General');
                return (
                  <Box
                    key={item.id}
                    onClick={() => handleNotifClick(item)}
                    sx={{
                      px: 2.5, py: 1.75, cursor: 'pointer', position: 'relative',
                      borderBottom: idx < unreadItems.length - 1 ? '1px solid #f8fafc' : 'none',
                      transition: 'background .15s',
                      '&:hover': { bgcolor: '#f8fafc' },
                    }}
                  >
                    {/* Unread dot */}
                    <Box sx={{
                      position: 'absolute', left: 10, top: '50%',
                      transform: 'translateY(-50%)',
                      width: 6, height: 6, borderRadius: '50%', bgcolor: '#14b8a6',
                    }} />

                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, pl: 1 }}>
                      {/* Category icon */}
                      <Box sx={{
                        width: 38, height: 38, borderRadius: '10px', flexShrink: 0,
                        bgcolor: cc.dot,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        boxShadow: `0 2px 8px ${cc.dot}55`,
                      }}>
                        <cc.IconComp sx={{ fontSize: 19, color: '#fff' }} />
                      </Box>

                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        {/* Category + Priority row */}
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: .75, mb: .3 }}>
                          <Typography sx={{ fontSize: 11, fontWeight: 700, color: cc.color, letterSpacing: '.2px' }}>
                            {item.category || 'General'}
                          </Typography>
                          {(item.priority || 'Normal') !== 'Normal' && (
                            <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: item.priority === 'Urgent' ? '#dc2626' : '#d97706' }}>
                              {item.priority === 'Urgent' ? '🔴 Urgent' : '⚠ High'}
                            </Typography>
                          )}
                        </Box>
                        {/* Title */}
                        <Typography sx={{
                          fontSize: 13.5, fontWeight: 700, color: '#0f172a',
                          lineHeight: 1.35, mb: .35,
                          display: '-webkit-box', WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical', overflow: 'hidden',
                        }}>
                          {item.title}
                        </Typography>
                        {/* Date */}
                        <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>
                          {fmtNotif(item.createdAt)}
                        </Typography>
                      </Box>

                      {/* Arrow */}
                      <ArrowForwardIcon sx={{ fontSize: 14, color: '#cbd5e1', mt: .75, flexShrink: 0 }} />
                    </Box>
                  </Box>
                );
              })}
              </>
            )}

            {/* ── Course notification cards ── */}
            {courseItems.length > 0 && (
              <>
                {/* Section divider when both types are present */}
                {unreadItems.length > 0 && (
                  <Box sx={{ px: 2.5, py: .75, bgcolor: '#f8fafc', borderTop: '1px solid #f1f5f9', borderBottom: '1px solid #f1f5f9' }}>
                    <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px' }}>
                      Course Assignments
                    </Typography>
                  </Box>
                )}
                {courseItems.map((item, idx) => (
                  <Box
                    key={item.id}
                    onClick={() => handleCourseNotifClick(item)}
                    sx={{
                      px: 2.5, py: 1.75, cursor: 'pointer', position: 'relative',
                      borderBottom: idx < courseItems.length - 1 ? '1px solid #f8fafc' : 'none',
                      transition: 'background .15s',
                      '&:hover': { bgcolor: '#f8fafc' },
                    }}
                  >
                    {/* Unread dot */}
                    <Box sx={{
                      position: 'absolute', left: 10, top: '50%',
                      transform: 'translateY(-50%)',
                      width: 6, height: 6, borderRadius: '50%', bgcolor: '#6366f1',
                    }} />

                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, pl: 1 }}>
                      {/* Course icon */}
                      <Box sx={{
                        width: 38, height: 38, borderRadius: '10px', flexShrink: 0,
                        bgcolor: '#eef2ff', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        border: '1px solid #c7d2fe',
                      }}>
                        <SchoolIcon sx={{ fontSize: 19, color: '#4f46e5' }} />
                      </Box>

                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography sx={{ fontSize: 11, fontWeight: 700, color: '#4f46e5', letterSpacing: '.2px', mb: .3 }}>
                          New Course Assigned
                        </Typography>
                        <Typography sx={{
                          fontSize: 13.5, fontWeight: 700, color: '#0f172a',
                          lineHeight: 1.35, mb: .35,
                          display: '-webkit-box', WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical', overflow: 'hidden',
                        }}>
                          {item.courseTitle}
                        </Typography>
                        <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>
                          {fmtNotif(item.createdAt)}
                        </Typography>
                      </Box>

                      <ArrowForwardIcon sx={{ fontSize: 14, color: '#cbd5e1', mt: .75, flexShrink: 0 }} />
                    </Box>
                  </Box>
                ))}
              </>
            )}

            {/* ── PIP comment notification cards ── */}
            {pipItems.length > 0 && (
              <>
                <Box sx={{ px: 2.5, py: .75, bgcolor: '#f8fafc', borderTop: '1px solid #f1f5f9', borderBottom: '1px solid #f1f5f9' }}>
                  <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px' }}>
                    PIP Comments
                  </Typography>
                </Box>
                {pipItems.map((item, idx) => (
                  <Box
                    key={item.id}
                    onClick={() => handlePipNotifClick(item)}
                    sx={{
                      px: 2.5, py: 1.75, cursor: 'pointer', position: 'relative',
                      borderBottom: idx < pipItems.length - 1 ? '1px solid #f8fafc' : 'none',
                      transition: 'background .15s',
                      '&:hover': { bgcolor: '#f8fafc' },
                    }}
                  >
                    <Box sx={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', width: 6, height: 6, borderRadius: '50%', bgcolor: '#1e3a5f' }} />
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, pl: 1 }}>
                      <Box sx={{ width: 38, height: 38, borderRadius: '10px', flexShrink: 0, bgcolor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '1px solid #bfdbfe' }}>
                        <AssignmentLateIcon sx={{ fontSize: 19, color: '#1e3a5f' }} />
                      </Box>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography sx={{ fontSize: 11, fontWeight: 700, color: '#1e3a5f', letterSpacing: '.2px', mb: .3 }}>
                          PIP · {item.pipTitle}
                        </Typography>
                        <Typography sx={{
                          fontSize: 13, fontWeight: 600, color: '#0f172a',
                          lineHeight: 1.35, mb: .35,
                          display: '-webkit-box', WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical', overflow: 'hidden',
                        }}>
                          {item.message}
                        </Typography>
                        <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>{fmtNotif(item.createdAt)}</Typography>
                      </Box>
                      <ArrowForwardIcon sx={{ fontSize: 14, color: '#cbd5e1', mt: .75, flexShrink: 0 }} />
                    </Box>
                  </Box>
                ))}
              </>
            )}

          </Box>
        </Popover>

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
          <Avatar sx={{ width: 36, height: 36, bgcolor: '#14b8a6', fontSize: '0.82rem', fontWeight: 700, flexShrink: 0 }}>
            {userInitials}
          </Avatar>
        </Box>

        {/* Logout */}
        <Tooltip title="Logout">
          <IconButton
            size="small"
            onClick={handleLogout}
            sx={{ color: '#ef4444', ml: 0.5, flexShrink: 0, '&:hover': { bgcolor: '#fef2f2' } }}
          >
            <LogoutIcon sx={{ fontSize: '1.2rem' }} />
          </IconButton>
        </Tooltip>

        {/* User dropdown menu */}
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
          {user?.role === 'ADMIN' && (
            <MenuItem
              onClick={() => { navigate('/settings'); setAnchorEl(null); }}
              sx={{ gap: 1.5, py: 1, fontSize: '0.875rem' }}
            >
              <SettingsIcon fontSize="small" sx={{ color: '#64748b' }} /> Settings
            </MenuItem>
          )}
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
