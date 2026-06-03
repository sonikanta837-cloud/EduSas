import React, { useEffect, useState, useMemo, useCallback, useRef } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, IconButton, Button, CircularProgress, Chip, Paper,
  Divider, Collapse
} from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import LoginIcon from '@mui/icons-material/Login';
import LogoutIcon from '@mui/icons-material/Logout';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import TimerIcon from '@mui/icons-material/Timer';
import dayjs from 'dayjs';
import { timesheetApi } from '../api/timesheetApi';
import { employeeApi } from '../api/employeeApi';
import { leaveUploadApi } from '../api/leaveUploadApi';
import { toast } from 'react-toastify';

// ── Helpers ───────────────────────────────────────────────────────────────────

const fmtTime = (t) => (t ? t.substring(0, 5) : null);

const fmtHours = (h) => {
  if (h == null) return '--';
  const hrs = Math.floor(h);
  const mins = Math.round((h - hrs) * 60);
  return `${hrs}h ${String(mins).padStart(2, '0')}m`;
};

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
      <Typography variant="body2" fontWeight={600} color="#16a34a">
        {fmtTime(session.loginTime)}
      </Typography>
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
    ) : (
      <Chip label="Active" size="small" color="success" variant="outlined"
        sx={{ ml: 'auto', fontSize: '0.72rem', height: 22 }} />
    )}
  </Box>
);

// ── Day card ──────────────────────────────────────────────────────────────────

const DayCard = ({ date, sessions, isToday, liveSec, storedHours, holidayName }) => {
  const [expanded, setExpanded] = useState(isToday);

  const dow = date.format('ddd').toUpperCase();
  const dom = date.format('DD MMM');
  const isWeekend = date.day() === 0 || date.day() === 6;
  const isFuture = date.isAfter(dayjs(), 'day');
  const isHoliday = Boolean(holidayName);

  const hasData = sessions.length > 0;
  const openSession = sessions.find(s => !s.logoutTime);
  const totalSec = isToday ? calcTotalSeconds(sessions, liveSec) : null;

  const sessionHours = hasData
    ? sessions.filter(s => s.sessionHours != null).reduce((a, s) => a + s.sessionHours, 0)
    : null;
  const totalHours = storedHours != null ? storedHours : sessionHours;

  const isPresent = hasData || storedHours != null;
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
            <SessionRow key={s.id} session={s} index={i} isOpen={!s.logoutTime} />
          ))}
        </Box>
      </Collapse>
    </Box>
  );
};

// ── Main page ─────────────────────────────────────────────────────────────────

const AttendancePage = () => {
  const { user } = useSelector((s) => s.auth);
  const [myEmployee, setMyEmployee] = useState(null);
  const [weekStart, setWeekStart] = useState(dayjs().startOf('week'));
  const [sessions, setSessions] = useState([]);
  const [todaySessions, setTodaySessions] = useState([]);
  const [workingHoursMap, setWorkingHoursMap] = useState({});
  const [holidaysMap, setHolidaysMap] = useState({});   // "YYYY-MM-DD" → holiday name
  const [loading, setLoading] = useState(true);

  // Live counter
  const [liveSec, setLiveSec] = useState(0);
  const timerRef = useRef(null);

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
      const [weekData, todayData, weekTimesheets] = await Promise.all([
        timesheetApi.getSessionsByRange(emp.id, start, end),
        timesheetApi.getTodaySessions(emp.id),
        timesheetApi.getAttendanceByRange(emp.id, start, end),
      ]);
      setSessions(weekData);
      setTodaySessions(todayData);
      // Build date → workingHours map so manual edits by admin/manager are reflected
      const hoursMap = {};
      (Array.isArray(weekTimesheets) ? weekTimesheets : []).forEach(ts => {
        if (ts.workDate && ts.workingHours != null) {
          hoursMap[String(ts.workDate)] = ts.workingHours;
        }
      });
      setWorkingHoursMap(hoursMap);
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

  // Listen for working-hours changes made by admin/manager so the employee sees them instantly
  useEffect(() => {
    const handler = (e) => {
      const { empId, date, hours } = e.detail;
      if (myEmployee && empId === myEmployee.id) {
        setWorkingHoursMap(prev => ({ ...prev, [date]: hours }));
      }
    };
    window.addEventListener('working-hours-updated', handler);
    return () => window.removeEventListener('working-hours-updated', handler);
  }, [myEmployee]);

  const todayTotalSec = calcTotalSeconds(todaySessions, isCheckedIn ? liveSec : null);
  const todayStr = dayjs().format('YYYY-MM-DD');
  const todayHoliday = holidaysMap[todayStr] || null;
  const todayIsWeekend = dayjs().day() === 0 || dayjs().day() === 6;

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={2}>Attendance</Typography>

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
                  storedHours={workingHoursMap[dateStr] ?? null}
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
    </Box>
  );
};

export default AttendancePage;
