import React, { useEffect, useState, useMemo, useCallback, useRef } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, IconButton, Button, CircularProgress, Chip, Paper,
  Divider, Collapse, Tabs, Tab, Table, TableBody, TableCell,
  TableHead, TableRow, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import CancelOutlinedIcon     from '@mui/icons-material/CancelOutlined';
import PendingActionsIcon     from '@mui/icons-material/PendingActions';
import HistoryIcon            from '@mui/icons-material/History';
import ChevronLeftIcon      from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon     from '@mui/icons-material/ChevronRight';
import LoginIcon            from '@mui/icons-material/Login';
import LogoutIcon           from '@mui/icons-material/Logout';
import ExpandMoreIcon       from '@mui/icons-material/ExpandMore';
import ExpandLessIcon       from '@mui/icons-material/ExpandLess';
import TimerIcon            from '@mui/icons-material/Timer';
import dayjs from 'dayjs';
import { timesheetApi }   from '../api/timesheetApi';
import { employeeApi }    from '../api/employeeApi';
import { leaveUploadApi } from '../api/leaveUploadApi';
import { correctionApi }  from '../api/correctionApi';
import { toast }          from 'react-toastify';
import CorrectionPopup    from '../components/CorrectionPopup';

// ── Helpers ───────────────────────────────────────────────────────────────────

const fmtTime = (t) => (t ? t.substring(0, 5) : null);

const fmtHours = (h) => {
  if (h == null) return '--';
  const hrs = Math.floor(h);
  const mins = Math.round((h - hrs) * 60);
  return `${hrs}h ${String(mins).padStart(2, '0')}m`;
};

// eslint-disable-next-line no-unused-vars
const parseTime = (t) => {
  if (!t) return null;
  const [h, m, s] = t.split(':').map(Number);
  return h * 3600 + m * 60 + (s || 0);
};

// seconds from loginTime (string "HH:MM:SS") to now
const liveSecondsFrom = (loginTimeStr) => {
  if (!loginTimeStr) return 0;
  const now = dayjs();
  const todayBase = now.startOf('day');
  const [h, m, s] = loginTimeStr.split(':').map(Number);
  const loginMs = todayBase.add(h, 'hour').add(m, 'minute').add(s || 0, 'second');
  return Math.max(0, now.diff(loginMs, 'second'));
};

const secToHm = (sec) => {
  if (sec == null || isNaN(sec)) return '00:00:00';
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};

// Sum completed session hours + live seconds of open session
const calcTotalSeconds = (sessions, liveSec) => {
  const completed = sessions
    .filter(s => s.sessionHours != null)
    .reduce((acc, s) => acc + s.sessionHours * 3600, 0);
  return Math.round(completed) + (liveSec || 0);
};

// ── Session row ───────────────────────────────────────────────────────────────

const SessionRow = ({ session, index, isOpen }) => (
  <Box sx={{
    display: 'flex', alignItems: 'center', gap: 2,
    px: 2, py: 0.75,
    bgcolor: isOpen ? 'rgba(20,184,166,0.05)' : 'transparent',
    borderLeft: isOpen ? '3px solid #14b8a6' : '3px solid transparent',
  }}>
    <Typography variant="caption" color="text.secondary" sx={{ width: 20 }}>#{index + 1}</Typography>
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
      <LoginIcon sx={{ fontSize: 14, color: '#16a34a' }} />
      <Typography variant="body2" fontWeight={600} color="#16a34a">{fmtTime(session.loginTime)}</Typography>
    </Box>
    <Typography variant="caption" color="text.secondary">→</Typography>
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
      <LogoutIcon sx={{ fontSize: 14, color: session.logoutTime ? '#dc2626' : '#94a3b8' }} />
      <Typography variant="body2" fontWeight={600} color={session.logoutTime ? '#dc2626' : '#94a3b8'}>
        {session.logoutTime ? fmtTime(session.logoutTime) : '--:--'}
      </Typography>
    </Box>
    {session.sessionHours != null ? (
      <Chip label={fmtHours(session.sessionHours)} size="small"
        sx={{ ml: 'auto', bgcolor: '#f0fdf4', color: '#16a34a', fontWeight: 600, fontSize: '0.72rem', height: 22 }} />
    ) : isOpen ? (
      <Chip label="Active" size="small" color="success" variant="outlined"
        sx={{ ml: 'auto', fontSize: '0.72rem', height: 22 }} />
    ) : null}
  </Box>
);

// ── Day card ──────────────────────────────────────────────────────────────────

const DayCard = ({ date, sessions, isToday, liveSec, holidayName }) => {
  const [expanded, setExpanded] = useState(isToday);

  const dow = date.format('ddd').toUpperCase();
  const isWeekend = date.day() === 0 || date.day() === 6;
  const isFuture = date.isAfter(dayjs(), 'day');
  const isHoliday = Boolean(holidayName);

  const hasData = sessions.length > 0;
  const openSession = sessions.find(s => !s.logoutTime);
  const totalSec = isToday ? calcTotalSeconds(sessions, liveSec) : null;

  const sessionHours = hasData
    ? sessions.filter(s => s.sessionHours != null).reduce((a, s) => a + s.sessionHours, 0)
    : null;
  const totalHours = sessionHours;

  const isPresent = hasData;
  const firstLogin = hasData ? fmtTime(sessions[0].loginTime) : null;
  const lastLogout = hasData ? fmtTime(sessions[sessions.length - 1].logoutTime) : null;

  let statusChip = null;
  if (isHoliday) {
    statusChip = (
      <Chip
        label={holidayName.length > 20 ? holidayName.slice(0, 20) + '…' : holidayName}
        size="small"
        title={holidayName}
        sx={{ bgcolor: '#f3e8ff', color: '#7e22ce', height: 20, fontSize: '0.65rem', fontWeight: 600 }}
      />
    );
  } else if (isWeekend) {
    statusChip = <Chip label="Weekly Off" size="small" sx={{ bgcolor: '#fef3c7', color: '#b45309', height: 20, fontSize: '0.65rem' }} />;
  } else if (isFuture) {
    statusChip = null;
  } else if (!isPresent) {
    statusChip = <Chip label="Absent" size="small" sx={{ bgcolor: '#fee2e2', color: '#dc2626', height: 20, fontSize: '0.65rem' }} />;
  } else if (openSession && isToday) {
    statusChip = <Chip label="In Progress" size="small" color="success" sx={{ height: 20, fontSize: '0.65rem' }} />;
  } else if (openSession) {
    // Past date with a session that was never closed
    statusChip = <Chip label="Pending Logout" size="small" sx={{ bgcolor: '#fff7ed', color: '#c2410c', height: 20, fontSize: '0.65rem', fontWeight: 600 }} />;
  } else {
    const hrs = totalHours || 0;
    const isPast = !isToday && !isFuture;
    const label = hrs > 8 ? 'Overtime' : isPast ? 'Completed' : 'Present';
    statusChip = (
      <Chip label={label} size="small"
        sx={{ bgcolor: hrs > 8 ? '#fee2e2' : '#dcfce7', color: hrs > 8 ? '#dc2626' : '#16a34a', height: 20, fontSize: '0.65rem' }} />
    );
  }

  const cardBorder = isToday ? '2px solid #14b8a6'
    : isHoliday ? '1px solid #d8b4fe'
    : isWeekend ? '1px solid #fde68a'
    : '1px solid #e2e8f0';
  const cardBg = isToday ? 'rgba(20,184,166,0.02)'
    : isHoliday ? '#faf5ff'
    : isWeekend ? '#fffbeb'
    : 'white';

  return (
    <Box sx={{
      border: cardBorder,
      borderRadius: 2, overflow: 'hidden', mb: 1.5,
      bgcolor: cardBg,
    }}>
      {/* Summary row */}
      <Box sx={{
        display: 'flex', alignItems: 'center', gap: 1.5, px: 2, py: 1.5,
        cursor: hasData ? 'pointer' : 'default',
        '&:hover': hasData ? { bgcolor: '#f8fafc' } : {},
      }}
        onClick={() => hasData && setExpanded(e => !e)}
      >
        {/* Day label */}
        <Box sx={{ width: 80, flexShrink: 0 }}>
          <Typography variant="caption" color="text.secondary" display="block">{dow}</Typography>
          {isToday ? (
            <Box sx={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              width: 32, height: 32, borderRadius: '50%', bgcolor: '#14b8a6' }}>
              <Typography variant="body2" fontWeight={700} color="white">
                {date.format('DD')}
              </Typography>
            </Box>
          ) : (
            <Typography variant="body1" fontWeight={600} color={isWeekend ? '#94a3b8' : '#1e293b'}>
              {date.format('DD')}
            </Typography>
          )}
          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.65rem' }}>
            {date.format('MMM')}
          </Typography>
        </Box>

        {/* Login time */}
        <Box sx={{ width: 70, flexShrink: 0 }}>
          {firstLogin && (
            <>
              <Typography variant="caption" color="text.secondary" display="block">Login</Typography>
              <Typography variant="body2" fontWeight={700} color="#16a34a">{firstLogin}</Typography>
            </>
          )}
        </Box>

        {/* Logout time */}
        <Box sx={{ width: 70, flexShrink: 0 }}>
          {lastLogout && (
            <>
              <Typography variant="caption" color="text.secondary" display="block">Logout</Typography>
              <Typography variant="body2" fontWeight={700} color="#dc2626">{lastLogout}</Typography>
            </>
          )}
        </Box>

        {/* Sessions count */}
        <Box sx={{ width: 60, flexShrink: 0 }}>
          {hasData && (
            <>
              <Typography variant="caption" color="text.secondary" display="block">Sessions</Typography>
              <Typography variant="body2" fontWeight={600}>{sessions.length}</Typography>
            </>
          )}
        </Box>

        {/* Hours */}
        <Box sx={{ flex: 1 }}>
          {isToday && openSession ? (
            <>
              <Typography variant="caption" color="text.secondary" display="block">Today's Hours</Typography>
              <Typography variant="body2" fontWeight={700} color={totalSec / 3600 > 8 ? '#dc2626' : '#1e293b'}
                sx={{ fontFamily: 'monospace', fontSize: '0.95rem' }}>
                {secToHm(totalSec)}
              </Typography>
            </>
          ) : totalHours != null ? (
            <>
              <Typography variant="caption" color="text.secondary" display="block">Hours</Typography>
              <Typography variant="body2" fontWeight={700} color={totalHours > 8 ? '#dc2626' : '#1e293b'}>
                {fmtHours(totalHours)}
              </Typography>
            </>
          ) : null}
        </Box>

        {/* Status + expand */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {statusChip}
          {hasData && (
            <IconButton size="small" sx={{ p: 0.25 }}>
              {expanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
            </IconButton>
          )}
        </Box>
      </Box>

      {/* Expanded sessions */}
      <Collapse in={expanded && hasData}>
        <Divider />
        <Box sx={{ py: 0.5 }}>
          {sessions.map((s, i) => (
            <SessionRow
              key={s.id} session={s} index={i}
              isOpen={!s.logoutTime && isToday}
            />
          ))}
        </Box>
      </Collapse>
    </Box>
  );
};

// ── Main page ─────────────────────────────────────────────────────────────────

const AttendancePage = () => {
  const { user } = useSelector((s) => s.auth);
  const [activeTab,   setActiveTab]   = useState(0);
  const [myEmployee, setMyEmployee] = useState(null);
  const [weekStart, setWeekStart] = useState(dayjs().startOf('week'));
  const [sessions, setSessions] = useState([]);
  const [todaySessions, setTodaySessions] = useState([]);
  const [holidaysMap, setHolidaysMap] = useState({});   // "YYYY-MM-DD" → holiday name
  const [loading, setLoading] = useState(true);

  // Live counter
  const [liveSec, setLiveSec] = useState(0);
  const timerRef = useRef(null);

  // Correction popup for missing logouts
  const [pendingSessions,     setPendingSessions]     = useState([]);
  const [showCorrectionPopup, setShowCorrectionPopup] = useState(false);

  // Correction history (for this employee)
  const [myCorrections,    setMyCorrections]    = useState([]);
  const [reviewOpen,       setReviewOpen]       = useState(false);
  const [reviewTarget,     setReviewTarget]     = useState(null);
  const [reviewAction,     setReviewAction]     = useState('approve');
  const [reviewForm,       setReviewForm]       = useState({ logoutTime: '', comment: '' });
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [showAudit,        setShowAudit]        = useState(false);
  const [auditLogs,        setAuditLogs]        = useState([]);
  const isManagerOrAbove = ['ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER'].includes(user?.role);

  const weekEnd = useMemo(() => weekStart.add(6, 'day'), [weekStart]);

  // Group sessions by date string
  const sessionsByDate = useMemo(() => {
    const map = {};
    sessions.forEach(s => {
      const key = s.workDate;
      if (!map[key]) map[key] = [];
      map[key].push(s);
    });
    return map;
  }, [sessions]);

  const days = useMemo(() =>
    Array.from({ length: 7 }, (_, i) => weekStart.add(i, 'day')),
    [weekStart]
  );

  const isCheckedIn = todaySessions.some(s => !s.logoutTime);

  // Restart live timer whenever today's sessions change
  useEffect(() => {
    clearInterval(timerRef.current);
    const openSession = todaySessions.find(s => !s.logoutTime);
    if (openSession) {
      const tick = () => setLiveSec(liveSecondsFrom(openSession.loginTime));
      tick();
      timerRef.current = setInterval(tick, 1000);
    } else {
      setLiveSec(0);
    }
    return () => clearInterval(timerRef.current);
  }, [todaySessions]);

  const loadData = useCallback(async (emp) => {
    if (!emp) return;
    setLoading(true);
    try {
      const start = weekStart.format('YYYY-MM-DD');
      const end = weekStart.add(6, 'day').format('YYYY-MM-DD');
      const [weekData, todayData] = await Promise.all([
        timesheetApi.getSessionsByRange(emp.id, start, end),
        timesheetApi.getTodaySessions(emp.id),
      ]);
      setSessions(weekData);
      setTodaySessions(todayData);
    } catch {
      toast.error('Failed to load attendance');
    } finally {
      setLoading(false);
    }
  }, [weekStart]);

  useEffect(() => {
    employeeApi.getByUserId(user.userId).then(setMyEmployee).catch(() => {});
    leaveUploadApi.getHolidays().then((list) => {
      const map = {};
      (Array.isArray(list) ? list : []).forEach(h => { map[h.date] = h.name; });
      setHolidaysMap(map);
    }).catch(() => {});
  }, [user.userId]);

  useEffect(() => {
    if (myEmployee) loadData(myEmployee);
  }, [myEmployee, loadData]);

  useEffect(() => {
    if (!myEmployee) return;
    if (['ADMIN', 'DIRECTOR'].includes(user?.role)) return;
    correctionApi.getPendingSessions().then(data => {
      const sessions = Array.isArray(data) ? data : [];
      setPendingSessions(sessions);
      if (sessions.length > 0) setShowCorrectionPopup(true);
    }).catch(() => {});
  }, [myEmployee, user?.role]);

  useEffect(() => {
    if (!isManagerOrAbove || !showAudit) return;
    correctionApi.getAuditLogs().then(setAuditLogs).catch(() => {});
  }, [isManagerOrAbove, showAudit]);

  useEffect(() => {
    if (activeTab !== 1) return;
    const loadFn = isManagerOrAbove
      ? (user?.role === 'MANAGER' || user?.role === 'ASSISTANT_MANAGER'
          ? correctionApi.getPendingTeam
          : correctionApi.getPending)
      : correctionApi.getMy;
    loadFn().then(data => setMyCorrections(Array.isArray(data) ? data : [])).catch(() => {});
  }, [activeTab, isManagerOrAbove, user?.role]);

  const openReview = (req, action) => {
    setReviewTarget(req);
    setReviewAction(action);
    setReviewForm({ logoutTime: action === 'approve' ? (req.requestedLogoutTime?.slice(0,5) || '') : '', comment: '' });
    setReviewOpen(true);
  };

  const handleReview = async () => {
    setReviewSubmitting(true);
    try {
      if (reviewAction === 'approve') {
        if (!reviewForm.logoutTime) { toast.error('Logout time is required'); setReviewSubmitting(false); return; }
        await correctionApi.approve(reviewTarget.id, reviewForm.logoutTime, reviewForm.comment);
        toast.success('Correction approved');
      } else {
        await correctionApi.reject(reviewTarget.id, reviewForm.comment);
        toast.success('Correction rejected');
      }
      setMyCorrections(prev => prev.filter(r => r.id !== reviewTarget.id));
      setReviewOpen(false);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to process request');
    } finally {
      setReviewSubmitting(false);
    }
  };


  const todayTotalSec = calcTotalSeconds(todaySessions, isCheckedIn ? liveSec : null);
  const todayStr = dayjs().format('YYYY-MM-DD');
  const todayHoliday = holidaysMap[todayStr] || null;
  const todayIsWeekend = dayjs().day() === 0 || dayjs().day() === 6;

  const STATUS_CFG = {
    PENDING_MANAGER_APPROVAL: { label: 'Pending',  bg: '#fff7ed', color: '#c2410c' },
    APPROVED:                  { label: 'Approved', bg: '#dcfce7', color: '#16a34a' },
    REJECTED:                  { label: 'Rejected', bg: '#fee2e2', color: '#dc2626' },
  };

  return (
    <Box>
      {showCorrectionPopup && pendingSessions.length > 0 && (
        <CorrectionPopup
          sessions={pendingSessions}
          onAllDone={() => setShowCorrectionPopup(false)}
        />
      )}

      {/* ── Tabs ── */}
      <Box sx={{ borderBottom: '1px solid #e2e8f0', mb: 2.5 }}>
        <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}
          sx={{ '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, fontSize: 14 } }}>
          <Tab label="Attendance" />
          <Tab label="Correction History" icon={<PendingActionsIcon sx={{ fontSize: 16 }} />}
            iconPosition="start" />
        </Tabs>
      </Box>

      {/* ── Tab 0: Attendance ── */}
      {activeTab === 0 && <>

      {/* ── Holiday / Weekly Off banner for today ── */}
      {(todayHoliday || todayIsWeekend) && (
        <Box sx={{
          display: 'flex', alignItems: 'center', gap: 1.5, px: 2.5, py: 1.5, mb: 2,
          bgcolor: todayHoliday ? '#faf5ff' : '#fffbeb',
          border: `1px solid ${todayHoliday ? '#d8b4fe' : '#fde68a'}`,
          borderRadius: 2,
        }}>
          <Typography sx={{ fontSize: '1.25rem' }}>{todayHoliday ? '🎉' : '🏖️'}</Typography>
          <Box>
            <Typography sx={{ fontWeight: 700, fontSize: '0.9rem', color: todayHoliday ? '#7e22ce' : '#b45309' }}>
              {todayHoliday ? `Today is a public holiday — ${todayHoliday}` : 'Today is a weekly off'}
            </Typography>
            <Typography sx={{ fontSize: '0.78rem', color: '#94a3b8' }}>
              No attendance required. This will not affect your leave balance.
            </Typography>
          </Box>
        </Box>
      )}

      {/* ── Today Summary Card ── */}
      <Paper sx={{ p: 2.5, mb: 3, border: '1px solid #e2e8f0', borderRadius: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
          {/* Today info */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, flexWrap: 'wrap' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Box sx={{ width: 40, height: 40, borderRadius: '50%', bgcolor: '#14b8a6',
                display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <TimerIcon sx={{ color: 'white', fontSize: 20 }} />
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">Today</Typography>
                <Typography variant="body2" fontWeight={600}>{dayjs().format('ddd, DD MMM YYYY')}</Typography>
              </Box>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">First Login</Typography>
              <Typography variant="h6" fontWeight={700} color={todaySessions[0] ? '#16a34a' : 'text.disabled'}>
                {todaySessions[0] ? fmtTime(todaySessions[0].loginTime) : '--:--'}
              </Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Last Logout</Typography>
              <Typography variant="h6" fontWeight={700} color={!isCheckedIn && todaySessions.length > 0 ? '#dc2626' : 'text.disabled'}>
                {!isCheckedIn && todaySessions.length > 0
                  ? fmtTime(todaySessions[todaySessions.length - 1].logoutTime)
                  : '--:--'}
              </Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Working Hours</Typography>
              <Typography variant="h6" fontWeight={700}
                color={todayTotalSec / 3600 > 8 ? '#dc2626' : todaySessions.length > 0 ? '#1e293b' : 'text.disabled'}
                sx={{ fontFamily: 'monospace', minWidth: 100 }}>
                {todaySessions.length > 0 ? secToHm(todayTotalSec) : '--:--:--'}
              </Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Sessions</Typography>
              <Typography variant="h6" fontWeight={700}>{todaySessions.length}</Typography>
            </Box>
          </Box>

          {isCheckedIn && (
            <Chip label="Currently logged in" color="success" size="small" sx={{ ml: 'auto' }} />
          )}
        </Box>

        <Typography variant="caption" color="text.disabled" display="block" mt={1}>
          Attendance is recorded automatically on login and logout
        </Typography>

        {/* Today session list */}
        {todaySessions.length > 0 && (
          <Box sx={{ mt: 2, pt: 2, borderTop: '1px solid #f1f5f9' }}>
            <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" mb={1}>
              TODAY'S SESSIONS
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {todaySessions.map((s, i) => (
                <Box key={s.id} sx={{
                  display: 'flex', alignItems: 'center', gap: 1,
                  px: 1.5, py: 0.75, borderRadius: 1.5,
                  bgcolor: !s.logoutTime ? 'rgba(20,184,166,0.08)' : '#f8fafc',
                  border: !s.logoutTime ? '1px solid #14b8a6' : '1px solid #e2e8f0',
                }}>
                  <LoginIcon sx={{ fontSize: 13, color: '#16a34a' }} />
                  <Typography variant="body2" fontWeight={600} color="#16a34a">{fmtTime(s.loginTime)}</Typography>
                  <Typography variant="caption" color="text.secondary">→</Typography>
                  <LogoutIcon sx={{ fontSize: 13, color: s.logoutTime ? '#dc2626' : '#94a3b8' }} />
                  <Typography variant="body2" fontWeight={600} color={s.logoutTime ? '#dc2626' : '#94a3b8'}>
                    {s.logoutTime ? fmtTime(s.logoutTime) : '...' }
                  </Typography>
                  {s.sessionHours != null && (
                    <Typography variant="caption" color="text.secondary">
                      ({fmtHours(s.sessionHours)})
                    </Typography>
                  )}
                  {!s.logoutTime && (
                    <Chip label="Active" size="small" color="success"
                      sx={{ height: 18, fontSize: '0.6rem' }} />
                  )}
                </Box>
              ))}
            </Box>
          </Box>
        )}
      </Paper>

      {/* ── Week View ── */}
      <Paper sx={{ overflow: 'hidden', border: '1px solid #e2e8f0', borderRadius: 2 }}>
        {/* Week navigation */}
        <Box sx={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          bgcolor: '#f8fafc', borderBottom: '1px solid #e2e8f0', py: 1.5, px: 2
        }}>
          <Typography variant="body1" fontWeight={600} color="text.secondary">
            Weekly View
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <IconButton size="small" onClick={() => setWeekStart(w => w.subtract(7, 'day'))}>
              <ChevronLeftIcon />
            </IconButton>
            <Typography variant="body2" fontWeight={600}>
              {weekStart.format('DD MMM')} – {weekEnd.format('DD MMM YYYY')}
            </Typography>
            <IconButton size="small" onClick={() => setWeekStart(w => w.add(7, 'day'))}>
              <ChevronRightIcon />
            </IconButton>
            <Button size="small" variant="outlined" onClick={() => setWeekStart(dayjs().startOf('week'))}
              sx={{ borderColor: '#1e3a5f', color: '#1e3a5f', bgcolor: '#ffffff', fontWeight: 600, '&:hover': { bgcolor: 'rgba(30,58,95,0.06)', borderColor: '#152d4a' } }}>
              This Week
            </Button>
          </Box>
        </Box>

        {/* Day rows */}
        <Box sx={{ p: 2 }}>
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress sx={{ color: '#14b8a6' }} />
            </Box>
          ) : (
            days.map(d => {
              const dateStr = d.format('YYYY-MM-DD');
              const isToday = dateStr === dayjs().format('YYYY-MM-DD');
              const daySessions = isToday ? todaySessions : (sessionsByDate[dateStr] || []);
              return (
                <DayCard
                  key={dateStr}
                  date={d}
                  sessions={daySessions}
                  isToday={isToday}
                  liveSec={isToday ? (isCheckedIn ? liveSec : 0) : 0}
                  holidayName={holidaysMap[dateStr] || null}
                />
              );
            })
          )}
        </Box>

        {/* Legend */}
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', px: 2, py: 1.5,
          borderTop: '1px solid #f1f5f9', bgcolor: '#fafafa' }}>
          {[
            { color: '#16a34a', label: 'Login' },
            { color: '#dc2626', label: 'Logout' },
            { color: '#14b8a6', label: 'Today' },
            { color: '#a78bfa', label: 'Holiday' },
            { color: '#f59e0b', label: 'Weekly Off' },
            { color: '#94a3b8', label: 'Active session' },
          ].map(({ color, label }) => (
            <Box key={label} sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: color }} />
              <Typography variant="caption" color="text.secondary">{label}</Typography>
            </Box>
          ))}
        </Box>
      </Paper>

      </>}

      {/* ── Tab 1: Correction History ── */}
      {activeTab === 1 && (
        <Box>
          {/* Correction requests + audit log */}
          <Paper sx={{ mb: 3, overflow: 'hidden', border: '1px solid #e2e8f0', borderRadius: 2 }}>
            <Box sx={{ px: 2.5, py: 1.75, bgcolor: '#f8fafc', borderBottom: '1px solid #e2e8f0',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <Typography fontWeight={700} fontSize={14} color="#0f172a">
                  {isManagerOrAbove ? 'Pending Correction Requests' : 'My Correction Requests'}
                </Typography>
                {isManagerOrAbove && myCorrections.length > 0 && (
                  <Chip label={myCorrections.length} size="small"
                    sx={{ bgcolor: '#fff7ed', color: '#c2410c', fontWeight: 700, fontSize: 11, height: 20 }} />
                )}
              </Box>
              {isManagerOrAbove && (
                <Button size="small" variant="outlined" startIcon={<HistoryIcon sx={{ fontSize: 14 }} />}
                  onClick={() => setShowAudit(v => !v)}
                  sx={{ textTransform: 'none', fontSize: 12, borderRadius: '8px' }}>
                  {showAudit ? 'Hide Audit Log' : 'View Audit Log'}
                </Button>
              )}
            </Box>

            {myCorrections.length === 0 ? (
              <Box sx={{ py: 5, textAlign: 'center', color: '#94a3b8', fontSize: 13 }}>
                {isManagerOrAbove ? 'No pending correction requests' : 'No correction requests submitted yet'}
              </Box>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: '#f8fafc' }}>
                    {(isManagerOrAbove ? ['Employee'] : []).concat(
                      ['Date', 'First Login', 'Requested Logout', 'Reason', 'Submitted', 'Status',
                       ...(isManagerOrAbove ? ['Actions'] : ['Resolved By', 'Remarks'])]
                    ).map(h => (
                      <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12, color: '#374151', py: 1.25 }}>{h}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {myCorrections.map(r => {
                    const cfg = STATUS_CFG[r.status] || STATUS_CFG.PENDING_MANAGER_APPROVAL;
                    return (
                      <TableRow key={r.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                        {isManagerOrAbove && (
                          <TableCell>
                            <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{r.employeeName}</Typography>
                            <Typography sx={{ fontSize: 11, color: '#94a3b8' }}>{r.department}</Typography>
                          </TableCell>
                        )}
                        <TableCell sx={{ fontSize: 13, fontWeight: 600 }}>{dayjs(r.workDate).format('DD MMM YYYY')}</TableCell>
                        <TableCell sx={{ fontSize: 13 }}>{r.firstLoginTime?.slice(0,5) || r.loginTime?.slice(0,5) || '—'}</TableCell>
                        <TableCell sx={{ fontSize: 13 }}>{r.requestedLogoutTime?.slice(0,5) || '—'}</TableCell>
                        <TableCell sx={{ maxWidth: 160 }}>
                          <Typography noWrap sx={{ fontSize: 12, color: '#64748b', maxWidth: 160 }}>{r.reason}</Typography>
                        </TableCell>
                        <TableCell sx={{ fontSize: 12, color: '#94a3b8', whiteSpace: 'nowrap' }}>
                          {r.createdAt ? dayjs(r.createdAt).format('DD MMM, HH:mm') : '—'}
                        </TableCell>
                        <TableCell>
                          <Chip label={cfg.label} size="small"
                            sx={{ fontSize: 11, fontWeight: 700, bgcolor: cfg.bg, color: cfg.color }} />
                        </TableCell>
                        {isManagerOrAbove ? (
                          <TableCell>
                            <Box sx={{ display: 'flex', gap: 0.75 }}>
                              <CheckCircleOutlineIcon
                                onClick={() => openReview(r, 'approve')}
                                sx={{ fontSize: 20, color: '#16a34a', cursor: 'pointer',
                                  '&:hover': { color: '#15803d' } }} />
                              <CancelOutlinedIcon
                                onClick={() => openReview(r, 'reject')}
                                sx={{ fontSize: 20, color: '#dc2626', cursor: 'pointer',
                                  '&:hover': { color: '#b91c1c' } }} />
                            </Box>
                          </TableCell>
                        ) : (
                          <>
                            <TableCell sx={{ fontSize: 12, color: '#64748b' }}>{r.resolvedBy || '—'}</TableCell>
                            <TableCell sx={{ fontSize: 12, color: '#64748b', maxWidth: 160 }}>
                              <Typography noWrap sx={{ fontSize: 12, maxWidth: 160 }}>{r.resolverComment || '—'}</Typography>
                            </TableCell>
                          </>
                        )}
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            )}

            {/* ── Audit Log (Manager / HR / Admin) ── */}
            {isManagerOrAbove && showAudit && (
              <>
                <Divider />
                <Box sx={{ px: 2.5, py: 1.5, bgcolor: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                  <Typography fontWeight={700} fontSize={13} color="#374151">Audit Log</Typography>
                </Box>
                {auditLogs.length === 0 ? (
                  <Box sx={{ py: 3, textAlign: 'center', color: '#94a3b8', fontSize: 13 }}>No audit entries</Box>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: '#f8fafc' }}>
                        {['Employee', 'Date', 'Action', 'Req. Logout', 'Approved Logout', 'Performed By', 'Remarks', 'Time'].map(h => (
                          <TableCell key={h} sx={{ fontWeight: 700, fontSize: 11.5, color: '#374151', py: 1 }}>{h}</TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {auditLogs.slice(0, 100).map(log => (
                        <TableRow key={log.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                          <TableCell sx={{ fontSize: 12 }}>{log.employeeName}</TableCell>
                          <TableCell sx={{ fontSize: 12 }}>{log.workDate}</TableCell>
                          <TableCell>
                            <Chip label={log.action} size="small" sx={{
                              fontSize: 10, fontWeight: 700,
                              bgcolor: log.action === 'APPROVED' ? '#dcfce7' : log.action === 'REJECTED' ? '#fee2e2' : '#fff7ed',
                              color:   log.action === 'APPROVED' ? '#16a34a' : log.action === 'REJECTED' ? '#dc2626' : '#c2410c',
                            }} />
                          </TableCell>
                          <TableCell sx={{ fontSize: 12, color: '#64748b' }}>{log.requestedLogoutTime?.slice(0,5) || '—'}</TableCell>
                          <TableCell sx={{ fontSize: 12, color: '#16a34a', fontWeight: 600 }}>
                            {log.approvedLogoutTime ? log.approvedLogoutTime.slice(0,5) : '—'}
                          </TableCell>
                          <TableCell sx={{ fontSize: 12, color: '#64748b' }}>{log.approverName || '—'}</TableCell>
                          <TableCell sx={{ maxWidth: 160 }}>
                            <Typography noWrap sx={{ fontSize: 11.5, color: '#64748b', maxWidth: 160 }}>{log.remarks || '—'}</Typography>
                          </TableCell>
                          <TableCell sx={{ fontSize: 11.5, color: '#94a3b8', whiteSpace: 'nowrap' }}>
                            {log.actionDateTime ? dayjs(log.actionDateTime).format('DD MMM, HH:mm') : '—'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </>
            )}
          </Paper>

          {/* Review dialog (manager/HR/admin) */}
          {isManagerOrAbove && (
            <Dialog open={reviewOpen} onClose={() => !reviewSubmitting && setReviewOpen(false)}
              maxWidth="xs" fullWidth PaperProps={{ sx: { borderRadius: '16px' } }}>
              <DialogTitle sx={{ fontWeight: 700, fontSize: 16, pb: 0 }}>
                {reviewAction === 'approve' ? 'Approve Correction' : 'Reject Correction'}
              </DialogTitle>
              <Divider sx={{ mt: 1.5 }} />
              <DialogContent sx={{ pt: 2.5 }}>
                {reviewTarget && (
                  <Box sx={{ bgcolor: '#f8fafc', borderRadius: '10px', p: 1.5, mb: 2 }}>
                    <Typography sx={{ fontSize: 12.5, color: '#374151' }}>
                      <strong>{reviewTarget.employeeName}</strong> — {dayjs(reviewTarget.workDate).format('DD MMM YYYY')}
                    </Typography>
                    <Typography sx={{ fontSize: 12, color: '#64748b', mt: 0.25 }}>
                      Login: {reviewTarget.firstLoginTime?.slice(0,5) || reviewTarget.loginTime?.slice(0,5)} &nbsp;|&nbsp;
                      Requested: {reviewTarget.requestedLogoutTime?.slice(0,5)}
                    </Typography>
                  </Box>
                )}
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {reviewAction === 'approve' && (
                    <TextField label="Approved Logout Time *" type="time" size="small" fullWidth
                      InputLabelProps={{ shrink: true }}
                      value={reviewForm.logoutTime}
                      onChange={e => setReviewForm(f => ({ ...f, logoutTime: e.target.value }))}
                      helperText="Working hours will be recalculated automatically" />
                  )}
                  <TextField
                    label={reviewAction === 'approve' ? 'Comment (optional)' : 'Reason for Rejection *'}
                    size="small" fullWidth multiline rows={2}
                    value={reviewForm.comment}
                    onChange={e => setReviewForm(f => ({ ...f, comment: e.target.value }))} />
                </Box>
              </DialogContent>
              <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
                <Button onClick={() => setReviewOpen(false)} disabled={reviewSubmitting}
                  sx={{ textTransform: 'none', borderRadius: '8px' }}>Cancel</Button>
                <Button variant="contained" onClick={handleReview} disabled={reviewSubmitting}
                  sx={{
                    textTransform: 'none', borderRadius: '8px', minWidth: 120,
                    bgcolor: reviewAction === 'approve' ? '#16a34a' : '#dc2626',
                    '&:hover': { bgcolor: reviewAction === 'approve' ? '#15803d' : '#b91c1c' },
                  }}>
                  {reviewSubmitting ? <CircularProgress size={18} color="inherit" />
                    : reviewAction === 'approve' ? 'Approve & Update' : 'Reject'}
                </Button>
              </DialogActions>
            </Dialog>
          )}
        </Box>
      )}

    </Box>
  );
};

export default AttendancePage;
