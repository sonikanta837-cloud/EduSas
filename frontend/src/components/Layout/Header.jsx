import React, { useState, useEffect, useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import {
  AppBar, Toolbar, Typography, Box, Avatar, Menu,
  MenuItem, Divider, Tooltip, Badge, IconButton,
  Popover, Button, CircularProgress,
} from '@mui/material';
import NotificationsIcon  from '@mui/icons-material/Notifications';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import MenuIcon           from '@mui/icons-material/Menu';
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
import RuleIcon                from '@mui/icons-material/Rule';
import { logout }                  from '../../store/authSlice';
import { toggleMobileSidebar }      from '../../store/uiSlice';
import { announcementApi }         from '../../api/announcementApi';
import { courseNotificationApi }   from '../../api/courseNotificationApi';
import { pipNotificationApi }      from '../../api/pipNotificationApi';
import { correctionNotificationApi } from '../../api/correctionNotificationApi';

export const HEADER_HEIGHT = 56;

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
  const { user }       = useSelector((s) => s.auth);
  const { activePortal } = useSelector((s) => s.portal);

  const [anchorEl,       setAnchorEl]       = useState(null);
  const [unreadCount,    setUnreadCount]    = useState(0);   // announcements
  const [courseUnread,   setCourseUnread]   = useState(0);
  const [pipUnread,      setPipUnread]      = useState(0);
  const [correctionUnread, setCorrectionUnread] = useState(0);
  const [notifAnchor,    setNotifAnchor]    = useState(null);
  const [unreadItems,    setUnreadItems]    = useState([]);
  const [courseItems,    setCourseItems]    = useState([]);
  const [pipItems,       setPipItems]       = useState([]);
  const [correctionItems, setCorrectionItems] = useState([]);
  const [notifLoading,   setNotifLoading]   = useState(false);

  const totalUnread = unreadCount + courseUnread + pipUnread + correctionUnread;

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
    correctionNotificationApi.getUnreadCount()
      .then(data => setCorrectionUnread(data?.count ?? 0))
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

  // ── Reset correction badge ────────────────────────────────────────────────────
  useEffect(() => {
    const handleRead = () => { setCorrectionUnread(0); setCorrectionItems([]); };
    window.addEventListener('correction-notifications-read', handleRead);
    return () => window.removeEventListener('correction-notifications-read', handleRead);
  }, []);

  // ── Open notification popover and load all unread items ─────────────────────
  const openNotifications = useCallback((e) => {
    setNotifAnchor(e.currentTarget);
    setNotifLoading(true);
    Promise.all([
      announcementApi.getUnread().catch(() => []),
      courseNotificationApi.getUnread().catch(() => []),
      pipNotificationApi.getUnread().catch(() => []),
      correctionNotificationApi.getUnread().catch(() => []),
    ]).then(([ann, course, pip, correction]) => {
      setUnreadItems(Array.isArray(ann)        ? ann        : []);
      setCourseItems(Array.isArray(course)     ? course     : []);
      setPipItems(Array.isArray(pip)           ? pip        : []);
      setCorrectionItems(Array.isArray(correction) ? correction : []);
    }).finally(() => setNotifLoading(false));
  }, []);

  // ── Click single notification ────────────────────────────────────────────────
  const handleNotifClick = useCallback(async (item) => {
    try {
      await announcementApi.markAsRead(item.id);
    } catch { /* silent */ }
    setUnreadItems(prev => prev.filter(i => i.id !== item.id));
    setUnreadCount(prev => Math.max(0, prev - 1));
    document.activeElement?.blur();
    setNotifAnchor(null);
    navigate('/org-chart?tab=1');
  }, [navigate]);

  // ── Click a course notification ───────────────────────────────────────────────
  const handleCourseNotifClick = useCallback(async (item) => {
    try { await courseNotificationApi.markAsRead(item.id); } catch { /* silent */ }
    setCourseItems(prev => prev.filter(i => i.id !== item.id));
    setCourseUnread(prev => Math.max(0, prev - 1));
    document.activeElement?.blur();
    setNotifAnchor(null);
    navigate('/courses');
  }, [navigate]);

  // ── Click a PIP notification ──────────────────────────────────────────────────
  const handlePipNotifClick = useCallback(async (item) => {
    try { await pipNotificationApi.markAsRead(item.id); } catch { /* silent */ }
    setPipItems(prev => prev.filter(i => i.id !== item.id));
    setPipUnread(prev => Math.max(0, prev - 1));
    document.activeElement?.blur();
    setNotifAnchor(null);
    navigate('/performance');
  }, [navigate]);

  // ── Click a correction notification ───────────────────────────────────────────
  const handleCorrectionNotifClick = useCallback(async (item) => {
    try { await correctionNotificationApi.markAsRead(item.id); } catch { /* silent */ }
    setCorrectionItems(prev => prev.filter(i => i.id !== item.id));
    setCorrectionUnread(prev => Math.max(0, prev - 1));
    document.activeElement?.blur();
    setNotifAnchor(null);
    navigate('/working-hours-corrections');
  }, [navigate]);

  // ── Mark all read ────────────────────────────────────────────────────────────
  const markAllRead = useCallback(async () => {
    await Promise.all([
      ...unreadItems.map(i => announcementApi.markAsRead(i.id).catch(() => {})),
      courseNotificationApi.markAllRead().catch(() => {}),
      pipNotificationApi.markAllRead().catch(() => {}),
      correctionNotificationApi.markAllRead().catch(() => {}),
    ]);
    setUnreadItems([]);
    setCourseItems([]);
    setPipItems([]);
    setCorrectionItems([]);
    setUnreadCount(0);
    setCourseUnread(0);
    setPipUnread(0);
    setCorrectionUnread(0);
    window.dispatchEvent(new Event('announcements-read'));
    window.dispatchEvent(new Event('course-notifications-read'));
    window.dispatchEvent(new Event('pip-notifications-read'));
    window.dispatchEvent(new Event('correction-notifications-read'));
  }, [unreadItems]);

  const handleLogout = () => {
    dispatch(logout()); // clears localStorage + fires backend invalidation async
    navigate('/login');
  };

  const notifOpen = Boolean(notifAnchor);

  return (
    <AppBar
      position="fixed"
      elevation={0}
      sx={{
        width: '100%',
        left: 0,
        borderRadius: 0,
        background: 'linear-gradient(135deg, #0b1220 0%, #16213a 55%, #0a0e1a 100%)',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
        boxShadow: '0 4px 20px rgba(0,0,0,0.45)',
        color: '#fff',
        zIndex: (theme) => theme.zIndex.drawer + 100,
      }}
    >
      <Toolbar sx={{ gap: 1, minHeight: `${HEADER_HEIGHT}px !important`, py: 0, px: { xs: 2, sm: 3 } }}>

        {/* Mobile nav trigger — nothing to open until a portal is selected */}
        {activePortal && (
          <IconButton
            size="small"
            onClick={() => dispatch(toggleMobileSidebar())}
            aria-label="Open navigation menu"
            sx={{
              display: { xs: 'inline-flex', md: 'none' },
              color: 'rgba(255,255,255,0.8)', mr: 0.5, flexShrink: 0,
              '&:hover': { bgcolor: 'rgba(255,255,255,0.08)' },
            }}
          >
            <MenuIcon sx={{ fontSize: '1.3rem' }} />
          </IconButton>
        )}

        {/* Brand — logo + application name */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
          <img
            src="/logo.png"
            alt="EduSAS"
            style={{ height: 30, width: 30, objectFit: 'contain', flexShrink: 0 }}
          />
          <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
            <Typography sx={{ fontWeight: 800, color: '#fff', fontSize: '0.98rem', lineHeight: 1.15, letterSpacing: '0.01em' }}>
              EduSAS
            </Typography>
            <Typography sx={{ fontSize: '0.6rem', color: 'rgba(255,255,255,0.45)', letterSpacing: '0.03em', fontWeight: 500, lineHeight: 1.1 }}>
              Employee Management System
            </Typography>
          </Box>
        </Box>

        <Box sx={{ flexGrow: 1 }} />

        {/* ── Notification bell ── */}
        <Tooltip title="Notifications">
          <IconButton
            size="small"
            onClick={openNotifications}
            sx={{
              color: notifOpen ? '#2dd4bf' : 'rgba(255,255,255,0.8)',
              bgcolor: notifOpen ? 'rgba(45,212,191,0.12)' : 'transparent',
              '&:hover': { bgcolor: 'rgba(255,255,255,0.08)' },
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
          disableRestoreFocus
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
            {(unreadItems.length > 0 || courseItems.length > 0 || pipItems.length > 0 || correctionItems.length > 0) && (
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
            ) : unreadItems.length === 0 && courseItems.length === 0 && pipItems.length === 0 && correctionItems.length === 0 ? (
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

            {/* ── Working Hours Correction notification cards ── */}
            {correctionItems.length > 0 && (
              <>
                <Box sx={{ px: 2.5, py: .75, bgcolor: '#f8fafc', borderTop: '1px solid #f1f5f9', borderBottom: '1px solid #f1f5f9' }}>
                  <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px' }}>
                    Working Hours Corrections
                  </Typography>
                </Box>
                {correctionItems.map((item, idx) => (
                  <Box
                    key={item.id}
                    onClick={() => handleCorrectionNotifClick(item)}
                    sx={{
                      px: 2.5, py: 1.75, cursor: 'pointer', position: 'relative',
                      borderBottom: idx < correctionItems.length - 1 ? '1px solid #f8fafc' : 'none',
                      transition: 'background .15s',
                      '&:hover': { bgcolor: '#f8fafc' },
                    }}
                  >
                    <Box sx={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', width: 6, height: 6, borderRadius: '50%', bgcolor: '#c2410c' }} />
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, pl: 1 }}>
                      <Box sx={{ width: 38, height: 38, borderRadius: '10px', flexShrink: 0, bgcolor: '#fff7ed', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '1px solid #fdba74' }}>
                        <RuleIcon sx={{ fontSize: 19, color: '#c2410c' }} />
                      </Box>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography sx={{ fontSize: 11, fontWeight: 700, color: '#c2410c', letterSpacing: '.2px', mb: .3 }}>
                          Working Hours Correction
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
            '&:hover': { bgcolor: 'rgba(255,255,255,0.08)' }, transition: 'background 0.15s',
          }}
        >
          <Box sx={{ textAlign: 'right', display: { xs: 'none', sm: 'block' } }}>
            <Typography sx={{ fontSize: '0.78rem', fontWeight: 700, color: '#fff', lineHeight: 1.2 }}>
              {user?.fullName}
            </Typography>
            <Typography sx={{ fontSize: '0.65rem', color: 'rgba(255,255,255,0.5)', lineHeight: 1.2 }}>
              {roleLabel}
            </Typography>
          </Box>
          <Avatar sx={{
            width: 32, height: 32, bgcolor: '#14b8a6', fontSize: '0.75rem', fontWeight: 700, flexShrink: 0,
            border: '2px solid rgba(255,255,255,0.18)',
          }}>
            {userInitials}
          </Avatar>
        </Box>

        {/* Divider */}
        <Box sx={{ width: '1px', height: 22, bgcolor: 'rgba(255,255,255,0.14)', mx: 0.75, flexShrink: 0 }} />

        {/* Logout */}
        <Tooltip title="Logout">
          <IconButton
            size="small"
            onClick={handleLogout}
            sx={{
              color: '#f87171', flexShrink: 0,
              '&:hover': { bgcolor: 'rgba(248,113,113,0.14)' },
            }}
          >
            <LogoutIcon sx={{ fontSize: '1.2rem' }} />
          </IconButton>
        </Tooltip>

        {/* User dropdown menu */}
        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={() => setAnchorEl(null)}
          disableRestoreFocus
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
            onClick={() => { document.activeElement?.blur(); setAnchorEl(null); navigate('/profile'); }}
            sx={{ gap: 1.5, py: 1, fontSize: '0.875rem' }}
          >
            <PersonIcon fontSize="small" sx={{ color: '#64748b' }} /> My Profile
          </MenuItem>
          {(user?.role === 'ADMIN' || user?.role === 'DIRECTOR') && (
            <MenuItem
              onClick={() => { document.activeElement?.blur(); setAnchorEl(null); navigate('/settings'); }}
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
