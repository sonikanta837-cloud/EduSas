import React, { useEffect, useState, useMemo, useCallback, useRef } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, IconButton, Button, CircularProgress, Chip, Paper,
  Divider, Collapse,
} from '@mui/material';
import ChevronLeftIcon      from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon     from '@mui/icons-material/ChevronRight';
import LoginIcon            from '@mui/icons-material/Login';
import LogoutIcon           from '@mui/icons-material/Logout';
import ExpandMoreIcon       from '@mui/icons-material/ExpandMore';
import ExpandLessIcon       from '@mui/icons-material/ExpandLess';
import TimerIcon            from '@mui/icons-material/Timer';
import dayjs from 'dayjs';
import { jobWorkSessionApi } from '../api/jobWorkSessionApi';
import { jobSummaryApi }     from '../api/jobSummaryApi';
import { employeeApi }       from '../api/employeeApi';
import { leaveUploadApi }    from '../api/leaveUploadApi';
import { toast }             from 'react-toastify';

// ── Helpers ───────────────────────────────────────────────────────────────────

const fmtTime = (t) => (t ? dayjs(t).format('HH:mm') : null);

const fmtMinutes = (mins) => {
  if (mins == null) return '--';
  const h = Math.floor(mins / 60);
  const m = Math.round(mins % 60);
  return `${h}h ${String(m).padStart(2, '0')}m`;
};

const secToHm = (sec) => {
  if (sec == null || isNaN(sec)) return '00:00:00';
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};

// Sum of completed session minutes (as seconds) + live seconds of the open session
const calcTotalSeconds = (sessions, liveSec) => {
  const completed = sessions
    .filter((s) => s.sessionMinutes != null)
    .reduce((acc, s) => acc + s.sessionMinutes * 60, 0);
  return Math.round(completed) + (liveSec || 0);
};

const STATUS_STYLES = {
  PRESENT:     { label: 'Present',     color: '#16a34a', bg: '#dcfce7' },
  UNDER_HOURS: { label: 'Under Hours', color: '#c2410c', bg: '#ffedd5' },
  OVERTIME:    { label: 'Overtime',    color: '#7c3aed', bg: '#ede9fe' },
  ABSENT:      { label: 'Absent',      color: '#dc2626', bg: '#fee2e2' },
  LEAVE:       { label: 'Leave',       color: '#2563eb', bg: '#dbeafe' },
  HOLIDAY:     { label: 'Holiday',     color: '#7e22ce', bg: '#f3e8ff' },
  WEEKEND:     { label: 'Weekly Off',  color: '#b45309', bg: '#fef3c7' },
};

const StatusChip = ({ status }) => {
  const style = STATUS_STYLES[status];
  if (!style) return null;
  return (
    <Chip label={style.label} size="small"
      sx={{ bgcolor: style.bg, color: style.color, height: 20, fontSize: '0.65rem', fontWeight: 600 }} />
  );
};

// ── Session row ───────────────────────────────────────────────────────────────

const SessionRow = ({ session, index, isOpen }) => (
  <Box sx={{
    display: 'flex', alignItems: 'center', gap: 2,
    px: 2, py: 0.75,
    bgcolor: isOpen ? 'rgba(20,184,166,0.05)' : 'transparent',
    borderLeft: isOpen ? '3px solid #14b8a6' : '3px solid transparent',
  }}>
    <Typography variant="caption" color="text.secondary" sx={{ width: 20 }}>{index + 1}.</Typography>
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
    {session.job?.value && (
      <Typography variant="caption" color="text.secondary">{session.job.value}</Typography>
    )}
    {session.sessionMinutes != null ? (
      <Chip label={fmtMinutes(session.sessionMinutes)} size="small"
        sx={{ ml: 'auto', bgcolor: '#f0fdf4', color: '#16a34a', fontWeight: 600, fontSize: '0.72rem', height: 22 }} />
    ) : isOpen ? (
      <Chip label="Active" size="small" color="success" variant="outlined"
        sx={{ ml: 'auto', fontSize: '0.72rem', height: 22 }} />
    ) : null}
  </Box>
);

// ── Day card ──────────────────────────────────────────────────────────────────

const DayCard = ({ date, sessions, summary, isToday, liveSec, holidayName }) => {
  const [expanded, setExpanded] = useState(isToday);

  const dow = date.format('ddd').toUpperCase();
  const isFuture = date.isAfter(dayjs(), 'day');

  const hasData = sessions.length > 0;
  const openSession = sessions.find((s) => !s.logoutTime);
  const totalSec = isToday ? calcTotalSeconds(sessions, liveSec) : null;

  const status = summary?.status;
  const firstLogin = summary?.firstLoginTime ? fmtTime(summary.firstLoginTime) : null;
  const lastLogout = summary?.lastLogoutTime ? fmtTime(summary.lastLogoutTime) : null;

  const cardBorder = isToday ? '2px solid #14b8a6'
    : status === 'HOLIDAY' ? '1px solid #d8b4fe'
    : status === 'WEEKEND' ? '1px solid #fde68a'
    : '1px solid #e2e8f0';
  const cardBg = isToday ? 'rgba(20,184,166,0.02)'
    : status === 'HOLIDAY' ? '#faf5ff'
    : status === 'WEEKEND' ? '#fffbeb'
    : 'white';

  return (
    <Box sx={{ border: cardBorder, borderRadius: 2, overflow: 'hidden', mb: 1.5, bgcolor: cardBg }}>
      {/* Summary row */}
      <Box sx={{
        display: 'flex', alignItems: 'center', gap: 1.5, px: 2, py: 1.5,
        cursor: hasData ? 'pointer' : 'default',
        '&:hover': hasData ? { bgcolor: '#f8fafc' } : {},
      }}
        onClick={() => hasData && setExpanded((e) => !e)}
      >
        {/* Day label */}
        <Box sx={{ width: 80, flexShrink: 0 }}>
          <Typography variant="caption" color="text.secondary" display="block">{dow}</Typography>
          {isToday ? (
            <Box sx={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              width: 32, height: 32, borderRadius: '50%', bgcolor: '#14b8a6' }}>
              <Typography variant="body2" fontWeight={700} color="white">{date.format('DD')}</Typography>
            </Box>
          ) : (
            <Typography variant="body1" fontWeight={600} color={status === 'WEEKEND' ? '#94a3b8' : '#1e293b'}>
              {date.format('DD')}
            </Typography>
          )}
          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.65rem' }}>{date.format('MMM')}</Typography>
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
        <Box sx={{ width: 55, flexShrink: 0 }}>
          {hasData && (
            <>
              <Typography variant="caption" color="text.secondary" display="block">Sessions</Typography>
              <Typography variant="body2" fontWeight={600}>{sessions.length}</Typography>
            </>
          )}
        </Box>

        {/* Working hours */}
        <Box sx={{ width: 110, flexShrink: 0 }}>
          {isToday && openSession ? (
            <>
              <Typography variant="caption" color="text.secondary" display="block">Today's Hours</Typography>
              <Typography variant="body2" fontWeight={700} color={status === 'OVERTIME' ? '#7c3aed' : '#1e293b'}
                sx={{ fontFamily: 'monospace', fontSize: '0.9rem' }}>
                {secToHm(totalSec)}
              </Typography>
            </>
          ) : summary?.totalWorkingMinutes != null ? (
            <>
              <Typography variant="caption" color="text.secondary" display="block">Working Hours</Typography>
              <Typography variant="body2" fontWeight={700} color={status === 'OVERTIME' ? '#7c3aed' : '#1e293b'}>
                {fmtMinutes(summary.totalWorkingMinutes)}
              </Typography>
            </>
          ) : null}
        </Box>

        {/* Break / Office / Overtime */}
        {summary && (summary.totalBreakMinutes > 0 || summary.overtimeMinutes > 0) && (
          <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap' }}>
            {summary.totalBreakMinutes > 0 && (
              <Chip label={`Break ${fmtMinutes(summary.totalBreakMinutes)}`} size="small"
                sx={{ bgcolor: '#fff7ed', color: '#c2410c', height: 20, fontSize: '0.62rem', fontWeight: 600 }} />
            )}
            {summary.overtimeMinutes > 0 && (
              <Chip label={`+${fmtMinutes(summary.overtimeMinutes)} OT`} size="small"
                sx={{ bgcolor: '#ede9fe', color: '#7c3aed', height: 20, fontSize: '0.62rem', fontWeight: 600 }} />
            )}
          </Box>
        )}

        {/* Status + expand */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, ml: 'auto' }}>
          {holidayName && status === 'HOLIDAY' ? (
            <Chip
              label={holidayName.length > 20 ? holidayName.slice(0, 20) + '…' : holidayName}
              size="small" title={holidayName}
              sx={{ bgcolor: '#f3e8ff', color: '#7e22ce', height: 20, fontSize: '0.65rem', fontWeight: 600 }}
            />
          ) : !isFuture && status ? <StatusChip status={status} /> : null}
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
            <SessionRow key={s.id} session={s} index={i} isOpen={!s.logoutTime && isToday} />
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
  const [summaries, setSummaries] = useState([]);
  const [todaySessions, setTodaySessions] = useState([]);
  const [todaySummary, setTodaySummary] = useState(null);
  const [holidaysMap, setHolidaysMap] = useState({});   // "YYYY-MM-DD" → holiday name
  const [loading, setLoading] = useState(true);

  // Live counter
  const [liveSec, setLiveSec] = useState(0);
  const timerRef = useRef(null);

  const weekEnd = useMemo(() => weekStart.add(6, 'day'), [weekStart]);

  const sessionsByDate = useMemo(() => {
    const map = {};
    sessions.forEach((s) => {
      const key = s.workDate;
      if (!map[key]) map[key] = [];
      map[key].push(s);
    });
    return map;
  }, [sessions]);

  const summariesByDate = useMemo(() => {
    const map = {};
    summaries.forEach((s) => { map[s.workDate] = s; });
    return map;
  }, [summaries]);

  const days = useMemo(() =>
    Array.from({ length: 7 }, (_, i) => weekStart.add(i, 'day')),
    [weekStart]
  );

  const isCheckedIn = todaySessions.some((s) => !s.logoutTime);

  // Restart live timer whenever today's sessions change
  useEffect(() => {
    clearInterval(timerRef.current);
    const openSession = todaySessions.find((s) => !s.logoutTime);
    if (openSession) {
      const tick = () => setLiveSec(dayjs().diff(dayjs(openSession.loginTime), 'second'));
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
      const today = dayjs().format('YYYY-MM-DD');
      const [weekSessions, weekSummaries, todayData, todaySummaryList] = await Promise.all([
        jobWorkSessionApi.getRange(emp.id, start, end),
        jobSummaryApi.getMy(start, end),
        jobWorkSessionApi.getToday(emp.id),
        jobSummaryApi.getMy(today, today),
      ]);
      setSessions(weekSessions);
      setSummaries(weekSummaries);
      setTodaySessions(todayData.sessions || []);
      setTodaySummary(Array.isArray(todaySummaryList) ? todaySummaryList[0] : null);
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
      (Array.isArray(list) ? list : []).forEach((h) => { map[h.date] = h.name; });
      setHolidaysMap(map);
    }).catch(() => {});
  }, [user.userId]);

  useEffect(() => {
    if (myEmployee) loadData(myEmployee);
  }, [myEmployee, loadData]);

  const todayTotalSec = calcTotalSeconds(todaySessions, isCheckedIn ? liveSec : null);
  const todayStr = dayjs().format('YYYY-MM-DD');
  const todayHoliday = holidaysMap[todayStr] || null;
  const todayStatus = todaySummary?.status;

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} mb={3}>Attendance</Typography>

      {/* ── Holiday / Weekly Off / Leave banner for today ── */}
      {(todayStatus === 'HOLIDAY' || todayStatus === 'WEEKEND' || todayStatus === 'LEAVE') && (
        <Box sx={{
          display: 'flex', alignItems: 'center', gap: 1.5, px: 2.5, py: 1.5, mb: 2,
          bgcolor: todayStatus === 'HOLIDAY' ? '#faf5ff' : todayStatus === 'LEAVE' ? '#eff6ff' : '#fffbeb',
          border: `1px solid ${todayStatus === 'HOLIDAY' ? '#d8b4fe' : todayStatus === 'LEAVE' ? '#bfdbfe' : '#fde68a'}`,
          borderRadius: 2,
        }}>
          <Typography sx={{ fontSize: '1.25rem' }}>
            {todayStatus === 'HOLIDAY' ? '🎉' : todayStatus === 'LEAVE' ? '🌴' : '🏖️'}
          </Typography>
          <Box>
            <Typography sx={{ fontWeight: 700, fontSize: '0.9rem', color: todayStatus === 'HOLIDAY' ? '#7e22ce' : todayStatus === 'LEAVE' ? '#2563eb' : '#b45309' }}>
              {todayStatus === 'HOLIDAY' ? `Today is a public holiday${todayHoliday ? ` — ${todayHoliday}` : ''}`
                : todayStatus === 'LEAVE' ? 'You are on approved leave today'
                : 'Today is a weekly off'}
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
                color={todayStatus === 'OVERTIME' ? '#7c3aed' : todaySessions.length > 0 ? '#1e293b' : 'text.disabled'}
                sx={{ fontFamily: 'monospace', minWidth: 100 }}>
                {todaySessions.length > 0 ? secToHm(todayTotalSec) : '--:--:--'}
              </Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Break Time</Typography>
              <Typography variant="h6" fontWeight={700} color={todaySummary?.totalBreakMinutes ? '#c2410c' : 'text.disabled'}>
                {todaySummary?.totalBreakMinutes != null ? fmtMinutes(todaySummary.totalBreakMinutes) : '--'}
              </Typography>
            </Box>

            <Box>
              <Typography variant="caption" color="text.secondary">Office Time</Typography>
              <Typography variant="h6" fontWeight={700} color={todaySummary?.totalOfficeMinutes ? '#1d4ed8' : 'text.disabled'}>
                {todaySummary?.totalOfficeMinutes != null ? fmtMinutes(todaySummary.totalOfficeMinutes) : '--'}
              </Typography>
            </Box>

            {todaySummary?.overtimeMinutes > 0 && (
              <Box>
                <Typography variant="caption" color="text.secondary">Overtime</Typography>
                <Typography variant="h6" fontWeight={700} color="#7c3aed">
                  +{fmtMinutes(todaySummary.overtimeMinutes)}
                </Typography>
              </Box>
            )}

            <Box>
              <Typography variant="caption" color="text.secondary">Sessions</Typography>
              <Typography variant="h6" fontWeight={700}>{todaySessions.length}</Typography>
            </Box>
          </Box>

          <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 1 }}>
            {todayStatus && <StatusChip status={todayStatus} />}
            {isCheckedIn && <Chip label="Currently logged in" color="success" size="small" />}
          </Box>
        </Box>

        <Typography variant="caption" color="text.disabled" display="block" mt={1}>
          Attendance reflects Job Time Tracking clock-in/out — log in and out from the Timesheets page
        </Typography>

        {/* Today session list */}
        {todaySessions.length > 0 && (
          <Box sx={{ mt: 2, pt: 2, borderTop: '1px solid #f1f5f9' }}>
            <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" mb={1}>
              TODAY'S SESSIONS
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {todaySessions.map((s) => (
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
                    {s.logoutTime ? fmtTime(s.logoutTime) : '...'}
                  </Typography>
                  {s.sessionMinutes != null && (
                    <Typography variant="caption" color="text.secondary">({fmtMinutes(s.sessionMinutes)})</Typography>
                  )}
                  {!s.logoutTime && (
                    <Chip label="Active" size="small" color="success" sx={{ height: 18, fontSize: '0.6rem' }} />
                  )}
                </Box>
              ))}
            </Box>
          </Box>
        )}
      </Paper>

      {/* ── Week View ── */}
      <Paper sx={{ overflow: 'hidden', border: '1px solid #e2e8f0', borderRadius: 2 }}>
        <Box sx={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          bgcolor: '#f8fafc', borderBottom: '1px solid #e2e8f0', py: 1.5, px: 2
        }}>
          <Typography variant="body1" fontWeight={600} color="text.secondary">Weekly View</Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <IconButton size="small" onClick={() => setWeekStart((w) => w.subtract(7, 'day'))}>
              <ChevronLeftIcon />
            </IconButton>
            <Typography variant="body2" fontWeight={600}>
              {weekStart.format('DD MMM')} – {weekEnd.format('DD MMM YYYY')}
            </Typography>
            <IconButton size="small" onClick={() => setWeekStart((w) => w.add(7, 'day'))}>
              <ChevronRightIcon />
            </IconButton>
            <Button size="small" variant="outlined" onClick={() => setWeekStart(dayjs().startOf('week'))}
              sx={{ borderColor: '#1e3a5f', color: '#1e3a5f', bgcolor: '#ffffff', fontWeight: 600, '&:hover': { bgcolor: 'rgba(30,58,95,0.06)', borderColor: '#152d4a' } }}>
              This Week
            </Button>
          </Box>
        </Box>

        <Box sx={{ p: 2 }}>
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress sx={{ color: '#14b8a6' }} />
            </Box>
          ) : (
            days.map((d) => {
              const dateStr = d.format('YYYY-MM-DD');
              const isToday = dateStr === dayjs().format('YYYY-MM-DD');
              const daySessions = isToday ? todaySessions : (sessionsByDate[dateStr] || []);
              const daySummary = isToday ? todaySummary : summariesByDate[dateStr];
              return (
                <DayCard
                  key={dateStr}
                  date={d}
                  sessions={daySessions}
                  summary={daySummary}
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
          {Object.values(STATUS_STYLES).map(({ color, label }) => (
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
