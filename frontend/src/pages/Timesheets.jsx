import React, { useEffect, useState, useCallback } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, Table, TableBody, TableCell,
  TableHead, TableRow, Button, TextField,
  CircularProgress, Grid, Chip, Paper, Divider,
  Dialog, DialogTitle, DialogContent, DialogActions, DialogContentText,
  Tooltip,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import FreeBreakfastIcon from '@mui/icons-material/FreeBreakfast';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import WorkHistoryIcon from '@mui/icons-material/WorkHistory';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import dayjs from 'dayjs';
import { employeeApi } from '../api/employeeApi';
import { jobWorkSessionApi } from '../api/jobWorkSessionApi';
import MasterDataAutocomplete from '../components/Timesheet/MasterDataAutocomplete';
import { toast } from 'react-toastify';

const STATUS_META = {
  NOT_LOGGED_IN: { label: 'Not Logged In', color: 'default' },
  WORKING:       { label: 'Working',       color: 'success' },
  ON_BREAK:      { label: 'On Break',      color: 'warning' },
};

// hh:mm:ss — for live-ticking timers
const formatClock = (ms) => {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return [h, m, s].map((n) => String(n).padStart(2, '0')).join(':');
};

// "Xh Ym" — for historical/summary figures
const formatMinutesShort = (mins) => `${Math.floor(mins / 60)}h ${mins % 60}m`;

const EmptyState = ({ icon, text }) => (
  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, py: 4, color: '#94a3b8' }}>
    {icon}
    <Typography variant="body2">{text}</Typography>
  </Box>
);

const FieldSummary = ({ label, value }) => (
  <Grid item xs={6} sm={3}>
    <Typography variant="caption" color="text.secondary" display="block">{label}</Typography>
    <Typography variant="body2" fontWeight={600} noWrap>{value || '—'}</Typography>
  </Grid>
);

// ─── Job Time Tracking panel ────────────────────────────────────────────────
const JobTimeTrackingPanel = ({ employeeId }) => {
  const [clientId,   setClientId]   = useState(null);
  const [jobId,       setJobId]      = useState(null);
  const [jobTypeId,   setJobTypeId]  = useState(null);
  const [periodEndId, setPeriodEndId] = useState(null);
  const [jobTask,     setJobTask]    = useState('');
  const [today,       setToday]      = useState(null);
  const [loading,     setLoading]    = useState(true);
  const [submitting,  setSubmitting] = useState(false);
  const [breakSubmitting, setBreakSubmitting] = useState(false);
  const [now, setNow] = useState(() => dayjs());

  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);

  const [switchOpen,       setSwitchOpen]       = useState(false);
  const [switching,        setSwitching]        = useState(false);
  const [switchClientId,   setSwitchClientId]   = useState(null);
  const [switchJobId,      setSwitchJobId]      = useState(null);
  const [switchJobTypeId,  setSwitchJobTypeId]  = useState(null);
  const [switchPeriodEndId, setSwitchPeriodEndId] = useState(null);
  const [switchJobTask,    setSwitchJobTask]    = useState('');

  const load = useCallback(async () => {
    if (!employeeId) return;
    setLoading(true);
    try {
      const data = await jobWorkSessionApi.getToday(employeeId);
      setToday(data);
      const open = data?.openSession;
      if (open) {
        setClientId(open.client?.id ?? null);
        setJobId(open.job?.id ?? null);
        setJobTypeId(open.jobType?.id ?? null);
        setPeriodEndId(open.periodEnd?.id ?? null);
        setJobTask(open.description || '');
      }
    } catch {
      toast.error('Failed to load job time tracking status');
    } finally {
      setLoading(false);
    }
  }, [employeeId]);

  useEffect(() => { load(); }, [load]);

  const openSession = today?.openSession || null;
  const hasOpenSession = !!openSession;
  const onBreak = !!openSession?.onBreak;
  const openBreakRecord = (openSession?.breaks || []).find((b) => !b.breakEndTime) || null;

  const status = !hasOpenSession ? 'NOT_LOGGED_IN' : (onBreak ? 'ON_BREAK' : 'WORKING');
  const statusMeta = STATUS_META[status];

  // Live-ticking clock while a session is open — drives the working/break timers.
  useEffect(() => {
    if (!hasOpenSession) return undefined;
    const id = setInterval(() => setNow(dayjs()), 1000);
    return () => clearInterval(id);
  }, [hasOpenSession]);

  const completedBreakMinutes = (openSession?.breaks || [])
    .filter((b) => b.breakEndTime)
    .reduce((sum, b) => sum + (b.breakMinutes || 0), 0);

  const workingElapsedMs = !openSession ? 0
    : onBreak
      ? dayjs(openBreakRecord.breakStartTime).diff(dayjs(openSession.loginTime)) - completedBreakMinutes * 60000
      : now.diff(dayjs(openSession.loginTime)) - completedBreakMinutes * 60000;

  const breakElapsedMs = onBreak && openBreakRecord ? now.diff(dayjs(openBreakRecord.breakStartTime)) : 0;

  const fieldsReady = !!(clientId && jobId && jobTypeId && periodEndId && jobTask.trim());
  const loginDisabled  = loading || submitting || hasOpenSession  || !fieldsReady;
  const logoutDisabled = loading || submitting || !hasOpenSession || !fieldsReady;
  const switchDisabled = loading || submitting || switching || !hasOpenSession;

  // Break controls are independent of the Job Task login/logout controls above —
  // they only depend on whether a job session is open and whether a break is
  // already in progress, not on the Job Task fields/validation.
  const breakStartDisabled = loading || breakSubmitting || !hasOpenSession || onBreak;
  const breakEndDisabled   = loading || breakSubmitting || !hasOpenSession || !onBreak;

  // Per-session break total (minutes), counting a still-open break live.
  const sessionBreakMinutes = useCallback((s) => {
    const completed = (s.breaks || []).filter((b) => b.breakEndTime)
      .reduce((sum, b) => sum + (b.breakMinutes || 0), 0);
    const open = (s.breaks || []).find((b) => !b.breakEndTime);
    if (!open) return completed;
    return completed + Math.max(0, Math.floor(now.diff(dayjs(open.breakStartTime)) / 60000));
  }, [now]);

  const allBreaksToday = (today?.sessions || [])
    .flatMap((s) => (s.breaks || []).map((b) => ({ ...b, jobValue: s.job?.value, jobTask: s.description })));

  const totalBreakMinutesToday = allBreaksToday.reduce((sum, b) => {
    if (b.breakMinutes != null) return sum + b.breakMinutes;
    return sum + Math.max(0, Math.floor(now.diff(dayjs(b.breakStartTime)) / 60000));
  }, 0);

  const handleLogin = async () => {
    setSubmitting(true);
    try {
      await jobWorkSessionApi.login({
        employeeId, clientId, jobId, jobTypeId, periodEndId, description: jobTask.trim(),
      });
      toast.success('Logged in — job timer started');
      await load();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to log in');
    } finally {
      setSubmitting(false);
    }
  };

  const handleLogout = async () => {
    setLogoutConfirmOpen(false);
    setSubmitting(true);
    try {
      await jobWorkSessionApi.logout(employeeId);
      toast.success('Logged out — job timer stopped');
      await load();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to log out');
    } finally {
      setSubmitting(false);
    }
  };

  const handleBreakStart = async () => {
    setBreakSubmitting(true);
    try {
      await jobWorkSessionApi.startBreak(employeeId);
      toast.success('Break started');
      await load();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to start break');
    } finally {
      setBreakSubmitting(false);
    }
  };

  const handleBreakEnd = async () => {
    setBreakSubmitting(true);
    try {
      await jobWorkSessionApi.endBreak(employeeId);
      toast.success('Break ended');
      await load();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to end break');
    } finally {
      setBreakSubmitting(false);
    }
  };

  const openSwitchDialog = () => {
    setSwitchClientId(null);
    setSwitchJobId(null);
    setSwitchJobTypeId(null);
    setSwitchPeriodEndId(null);
    setSwitchJobTask('');
    setSwitchOpen(true);
  };
  const closeSwitchDialog = () => setSwitchOpen(false);

  const switchFieldsReady = !!(switchClientId && switchJobId && switchJobTypeId && switchPeriodEndId && switchJobTask.trim());

  const handleSwitchJob = async () => {
    if (!switchFieldsReady) return;
    setSwitching(true);
    try {
      await jobWorkSessionApi.switchJob({
        employeeId, clientId: switchClientId, jobId: switchJobId, jobTypeId: switchJobTypeId,
        periodEndId: switchPeriodEndId, description: switchJobTask.trim(),
      });
      toast.success('Switched job — previous session closed, new one started');
      closeSwitchDialog();
      await load();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to switch job');
    } finally {
      setSwitching(false);
    }
  };

  return (
    <Box sx={{
      bgcolor: 'white', p: 2, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.08)', mb: 2,
    }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
        <Typography variant="subtitle1" fontWeight={700}>
          Job Time Tracking
        </Typography>
        <Chip size="small" color={statusMeta.color} label={statusMeta.label}
          sx={{ fontWeight: 700 }} />
      </Box>

      {/* ── Job Session controls ─────────────────────────────────────────── */}
      <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
          <WorkHistoryIcon fontSize="small" color="action" />
          <Typography variant="body2" fontWeight={700} color="text.secondary">JOB SESSION</Typography>
        </Box>

        <Grid container spacing={1.5}>
          <Grid item xs={12} sm={6} md={3}>
            <MasterDataAutocomplete type="CLIENT" label="Client Name" value={clientId}
              onChange={(id) => setClientId(id)} disabled={hasOpenSession || loading} sx={{ width: '100%' }} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <MasterDataAutocomplete type="JOB" label="Job Name" value={jobId}
              onChange={(id) => setJobId(id)} disabled={hasOpenSession || loading} sx={{ width: '100%' }} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <MasterDataAutocomplete type="PERIOD_END" label="Period End" value={periodEndId}
              onChange={(id) => setPeriodEndId(id)} disabled={hasOpenSession || loading} sx={{ width: '100%' }} />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <MasterDataAutocomplete type="JOB_TYPE" label="Job Type" value={jobTypeId}
              onChange={(id) => setJobTypeId(id)} disabled={hasOpenSession || loading} sx={{ width: '100%' }} />
          </Grid>
        </Grid>

        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, mt: 1.5, flexWrap: 'wrap' }}>
          <TextField
            label="Job Task" required placeholder="What are you working on?" size="small"
            value={jobTask} onChange={(e) => setJobTask(e.target.value)}
            disabled={hasOpenSession || loading} sx={{ flex: 1, minWidth: 220 }}
          />
          <Button variant="contained" color="success" startIcon={<PlayArrowIcon />}
            disabled={loginDisabled} onClick={handleLogin}>
            Login
          </Button>
          <Button variant="contained" color="error" startIcon={<StopIcon />}
            disabled={logoutDisabled} onClick={() => setLogoutConfirmOpen(true)}>
            Logout
          </Button>
          <Tooltip title={hasOpenSession ? '' : 'Log in to a job before switching'}>
            <span>
              <Button variant="outlined" startIcon={<SwapHorizIcon />}
                disabled={switchDisabled} onClick={openSwitchDialog}>
                Switch Job
              </Button>
            </span>
          </Tooltip>
          {submitting && <CircularProgress size={18} sx={{ mt: 1 }} />}
        </Box>

        {hasOpenSession && (
          <Box sx={{ mt: 2, p: 1.5, borderRadius: 1.5, bgcolor: '#f0fdf4', border: '1px solid #bbf7d0' }}>
            <Typography variant="caption" fontWeight={700} color="text.secondary">CURRENT JOB</Typography>
            <Grid container spacing={1} sx={{ mt: 0.5 }}>
              <FieldSummary label="Client" value={openSession.client?.value} />
              <FieldSummary label="Job" value={openSession.job?.value} />
              <FieldSummary label="Job Task" value={openSession.description} />
              <FieldSummary label="Start Time" value={dayjs(openSession.loginTime).format('hh:mm A')} />
            </Grid>
            <Divider sx={{ my: 1.5 }} />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <AccessTimeIcon fontSize="small" color="success" />
              <Typography variant="body2" color="text.secondary">Working Time:</Typography>
              <Typography variant="h6" fontWeight={700} sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatClock(workingElapsedMs)}
              </Typography>
            </Box>
          </Box>
        )}
      </Paper>

      {/* ── Break controls — independent of the Job Task login/logout controls ── */}
      <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, mt: 2, bgcolor: onBreak ? '#fffbeb' : 'transparent' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
          <FreeBreakfastIcon fontSize="small" color="action" />
          <Typography variant="body2" fontWeight={700} color="text.secondary">BREAK</Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
          <Tooltip title={!hasOpenSession ? 'Log in to a job before taking a break' : (onBreak ? 'A break is already in progress' : '')}>
            <span>
              <Button variant="outlined" color="warning" size="small" startIcon={<FreeBreakfastIcon />}
                disabled={breakStartDisabled} onClick={handleBreakStart}>
                Break Start
              </Button>
            </span>
          </Tooltip>
          <Tooltip title={!hasOpenSession || !onBreak ? 'No break in progress' : ''}>
            <span>
              <Button variant="outlined" color="primary" size="small" startIcon={<PlayArrowIcon />}
                disabled={breakEndDisabled} onClick={handleBreakEnd}>
                Break End
              </Button>
            </span>
          </Tooltip>
          {breakSubmitting && <CircularProgress size={16} />}

          <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 1 }}>
            <AccessTimeIcon fontSize="small" color={onBreak ? 'warning' : 'disabled'} />
            <Typography variant="body2" color="text.secondary">Break Time:</Typography>
            <Typography variant="h6" fontWeight={700}
              sx={{ fontVariantNumeric: 'tabular-nums', color: onBreak ? '#b45309' : 'text.disabled' }}>
              {onBreak ? formatClock(breakElapsedMs) : '00:00:00'}
            </Typography>
          </Box>
        </Box>
      </Paper>

      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2.5, mt: 1.5 }}>
        <Box>
          <Typography variant="caption" color="text.secondary">Working Hours&nbsp;</Typography>
          <Typography variant="caption" fontWeight={700}>{today?.totalFormatted || '0h 0m'}</Typography>
        </Box>
        <Box>
          <Typography variant="caption" color="text.secondary">Break Time&nbsp;</Typography>
          <Typography variant="caption" fontWeight={700}>{formatMinutesShort(totalBreakMinutesToday)}</Typography>
        </Box>
      </Box>

      {/* ── Today's Sessions ─────────────────────────────────────────────── */}
      <Box sx={{ mt: 2 }}>
        <Typography variant="caption" color="text.secondary" fontWeight={600}>Today's Sessions</Typography>
        {today?.sessions?.length > 0 ? (
          <Table size="small" sx={{ mt: 0.5 }}>
            <TableHead>
              <TableRow>
                <TableCell>Job</TableCell>
                <TableCell>Client</TableCell>
                <TableCell>Job Task</TableCell>
                <TableCell>Login</TableCell>
                <TableCell>Logout</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Break Duration</TableCell>
                <TableCell align="right">Duration</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {today.sessions.map((s) => (
                <TableRow key={s.id}>
                  <TableCell>{s.job?.value}</TableCell>
                  <TableCell>{s.client?.value}</TableCell>
                  <TableCell>{s.description || <em>—</em>}</TableCell>
                  <TableCell>{dayjs(s.loginTime).format('hh:mm A')}</TableCell>
                  <TableCell>{s.logoutTime ? dayjs(s.logoutTime).format('hh:mm A') : <em>—</em>}</TableCell>
                  <TableCell>
                    <Chip size="small" label={s.status === 'OPEN' ? 'Active' : 'Completed'}
                      color={s.status === 'OPEN' ? 'success' : 'default'} />
                  </TableCell>
                  <TableCell align="right">{formatMinutesShort(sessionBreakMinutes(s))}</TableCell>
                  <TableCell align="right">
                    {s.sessionMinutes != null ? formatMinutesShort(s.sessionMinutes) : '—'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <EmptyState icon={<WorkHistoryIcon sx={{ fontSize: 32 }} />} text="No job sessions recorded today. Log in to start tracking your work." />
        )}
      </Box>

      {/* ── Today's Breaks ───────────────────────────────────────────────── */}
      <Box sx={{ mt: 2 }}>
        <Typography variant="caption" color="text.secondary" fontWeight={600}>Today's Breaks</Typography>
        {allBreaksToday.length > 0 ? (
          <Table size="small" sx={{ mt: 0.5 }}>
            <TableHead>
              <TableRow>
                <TableCell>Job</TableCell>
                <TableCell>Job Task</TableCell>
                <TableCell>Break Start</TableCell>
                <TableCell>Break End</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Break Duration</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {allBreaksToday.map((b) => (
                <TableRow key={b.id}>
                  <TableCell>{b.jobValue}</TableCell>
                  <TableCell>{b.jobTask || <em>—</em>}</TableCell>
                  <TableCell>{dayjs(b.breakStartTime).format('hh:mm A')}</TableCell>
                  <TableCell>{b.breakEndTime ? dayjs(b.breakEndTime).format('hh:mm A') : <em>—</em>}</TableCell>
                  <TableCell>
                    <Chip size="small" label={b.breakEndTime ? 'Completed' : 'In Progress'}
                      color={b.breakEndTime ? 'default' : 'warning'} />
                  </TableCell>
                  <TableCell align="right">
                    {b.breakMinutes != null ? formatMinutesShort(b.breakMinutes)
                      : formatMinutesShort(Math.max(0, Math.floor(now.diff(dayjs(b.breakStartTime)) / 60000)))}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <EmptyState icon={<EventBusyIcon sx={{ fontSize: 32 }} />} text="No breaks taken today." />
        )}
      </Box>

      {/* ── Logout confirmation ──────────────────────────────────────────── */}
      <Dialog open={logoutConfirmOpen} onClose={() => setLogoutConfirmOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <WarningAmberIcon color="warning" /> Confirm Job Logout
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            This will end your current job session
            {openSession ? ` for "${openSession.job?.value}" (${openSession.client?.value})` : ''} and stop the timer.
            {onBreak ? ' Your current break will also be closed.' : ''}
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setLogoutConfirmOpen(false)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleLogout}>Logout</Button>
        </DialogActions>
      </Dialog>

      {/* ── Switch Job ────────────────────────────────────────────────────── */}
      <Dialog open={switchOpen} onClose={closeSwitchDialog} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>Switch Job</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            Ends your current job session and immediately starts a new one. Your attendance is unaffected —
            only the job task timer switches.
          </DialogContentText>
          <Grid container spacing={1.5}>
            <Grid item xs={12} sm={6}>
              <MasterDataAutocomplete type="CLIENT" label="Client Name" value={switchClientId}
                onChange={(id) => setSwitchClientId(id)} disabled={switching} sx={{ width: '100%' }} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <MasterDataAutocomplete type="JOB" label="Job Name" value={switchJobId}
                onChange={(id) => setSwitchJobId(id)} disabled={switching} sx={{ width: '100%' }} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <MasterDataAutocomplete type="PERIOD_END" label="Period End" value={switchPeriodEndId}
                onChange={(id) => setSwitchPeriodEndId(id)} disabled={switching} sx={{ width: '100%' }} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <MasterDataAutocomplete type="JOB_TYPE" label="Job Type" value={switchJobTypeId}
                onChange={(id) => setSwitchJobTypeId(id)} disabled={switching} sx={{ width: '100%' }} />
            </Grid>
            <Grid item xs={12}>
              <TextField
                label="Job Task" required placeholder="What are you working on?" fullWidth size="small"
                value={switchJobTask} onChange={(e) => setSwitchJobTask(e.target.value)} disabled={switching}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={closeSwitchDialog} disabled={switching}>Cancel</Button>
          <Button variant="contained" startIcon={<SwapHorizIcon />}
            disabled={!switchFieldsReady || switching} onClick={handleSwitchJob}>
            {switching ? <CircularProgress size={18} /> : 'Switch Job'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// ─── Main component ──────────────────────────────────────────────────────────
const TimesheetsPage = () => {
  const { user } = useSelector((s) => s.auth);

  const [myEmployee, setMyEmployee] = useState(null);
  const [loading,     setLoading]   = useState(true);

  useEffect(() => {
    employeeApi.getByUserId(user.userId)
      .then(setMyEmployee)
      .catch(() => toast.error('Could not load your employee profile'))
      .finally(() => setLoading(false));
  }, [user]);

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', minWidth: 0 }}>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={700}>Timesheets</Typography>
      </Box>
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}><CircularProgress /></Box>
      ) : myEmployee ? (
        <JobTimeTrackingPanel employeeId={myEmployee.id} />
      ) : null}
    </Box>
  );
};

export default TimesheetsPage;
