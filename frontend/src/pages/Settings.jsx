import React, { useState, useEffect } from 'react';
import {
  Box, Typography, Paper, Switch, Slider, Checkbox,
  Button, Divider, CircularProgress, Alert, Chip, Stack,
  Tooltip, TextField, InputAdornment, Select, MenuItem,
  FormControl, InputLabel,
} from '@mui/material';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import AccessTimeIcon           from '@mui/icons-material/AccessTime';
import CheckCircleIcon          from '@mui/icons-material/CheckCircle';
import MarkEmailReadIcon        from '@mui/icons-material/MarkEmailRead';
import ScheduleIcon             from '@mui/icons-material/Schedule';
import AssignmentLateIcon       from '@mui/icons-material/AssignmentLate';
import { settingsApi }          from '../api/settingsApi';

const FREQUENCY_OPTIONS = [
  { value: 'BOTH',      label: 'Both (on logout & scheduled)', desc: 'Alert fires on logout AND in the evening scheduled job' },
  { value: 'ON_LOGOUT', label: 'On logout only',               desc: 'Alert fires immediately when employee logs out' },
  { value: 'SCHEDULED', label: 'Scheduled (end of day) only',  desc: 'Alert fires once during the evening scheduled audit' },
];

const HOURS   = Array.from({ length: 24 }, (_, i) => i);
const MINUTES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55];

function fmt12(h, m) {
  const period = h >= 12 ? 'PM' : 'AM';
  const h12    = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${String(m).padStart(2, '0')} ${period}`;
}

function SectionHeader({ icon, title, desc, enabled }) {
  return (
    <Box sx={{ px: 3, py: 2.5, bgcolor: '#f8fafc', borderBottom: '1px solid #e2e8f0', display: 'flex', alignItems: 'center', gap: 1.5 }}>
      {icon}
      <Box>
        <Typography fontWeight={700} color="#1e293b">{title}</Typography>
        <Typography variant="caption" color="text.secondary">{desc}</Typography>
      </Box>
      <Chip
        label={enabled ? 'Active' : 'Disabled'}
        size="small"
        sx={{
          ml: 'auto',
          bgcolor: enabled ? '#dcfce7' : '#f1f5f9',
          color:   enabled ? '#16a34a' : '#94a3b8',
          fontWeight: 600,
        }}
      />
    </Box>
  );
}

function SaveBar({ onSave, saving, saved, error }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, pt: 1 }}>
      <Button
        variant="contained"
        onClick={onSave}
        disabled={saving}
        sx={{ px: 4, bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}
      >
        {saving && <CircularProgress size={16} sx={{ color: '#fff', mr: 1 }} />}
        {saving ? 'Saving…' : 'Save Settings'}
      </Button>
      {saved && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: '#16a34a' }}>
          <CheckCircleIcon fontSize="small" />
          <Typography variant="body2" fontWeight={600}>Saved successfully</Typography>
        </Box>
      )}
      {error && <Alert severity="error" sx={{ py: 0 }}>{error}</Alert>}
    </Box>
  );
}

function RecipientRow({ field, label, desc, value, onChange, disabled }) {
  return (
    <Tooltip title={desc} placement="top" arrow>
      <Box
        onClick={() => !disabled && onChange(field, !value)}
        sx={{
          display: 'flex', alignItems: 'center', gap: 1, px: 2, py: 1.5,
          border: '1.5px solid', borderRadius: 2, cursor: disabled ? 'default' : 'pointer',
          borderColor: value ? '#14b8a6' : '#e2e8f0',
          bgcolor:     value ? 'rgba(20,184,166,0.07)' : '#fafafa',
          transition: 'all 0.15s',
          '&:hover': !disabled ? { borderColor: '#14b8a6', bgcolor: 'rgba(20,184,166,0.07)' } : {},
        }}
      >
        <Checkbox
          checked={value}
          size="small"
          disableRipple
          sx={{ p: 0, color: '#94a3b8', '&.Mui-checked': { color: '#14b8a6' } }}
        />
        <Typography variant="body2" fontWeight={value ? 600 : 400} color={value ? '#0f766e' : '#64748b'}>
          {label}
        </Typography>
      </Box>
    </Tooltip>
  );
}

// ── Break Alert Settings panel ──────────────────────────────────────────────

function BreakAlertPanel() {
  const [settings, setSettings] = useState(null);
  const [loading, setLoading]   = useState(true);
  const [saving, setSaving]     = useState(false);
  const [saved, setSaved]       = useState(false);
  const [error, setError]       = useState('');

  useEffect(() => {
    settingsApi.getBreakAlertSettings()
      .then(setSettings)
      .catch(() => setError('Failed to load break alert settings.'))
      .finally(() => setLoading(false));
  }, []);

  const update = (f, v) => { setSaved(false); setSettings(p => ({ ...p, [f]: v })); };

  const handleSave = async () => {
    setSaving(true); setError('');
    try {
      setSettings(await settingsApi.saveBreakAlertSettings(settings));
      setSaved(true); setTimeout(() => setSaved(false), 3000);
    } catch { setError('Failed to save. Please try again.'); }
    finally { setSaving(false); }
  };

  const fmtMin = v => v >= 60 ? `${Math.floor(v/60)}h ${v%60>0?`${v%60}m`:''}`.trim() : `${v}m`;

  if (loading) return <Box sx={{ display:'flex', justifyContent:'center', py:6 }}><CircularProgress /></Box>;
  if (!settings) return <Alert severity="error">{error}</Alert>;

  return (
    <Paper sx={{ borderRadius: 2, overflow: 'hidden' }}>
      <SectionHeader
        icon={<AccessTimeIcon sx={{ color: '#14b8a6' }} />}
        title="Break Alert Settings"
        desc="Control when and how break time violations are reported via email"
        enabled={settings.enabled}
      />
      <Box sx={{ px: 3, py: 3, display: 'flex', flexDirection: 'column', gap: 3.5 }}>

        {/* Enable toggle */}
        <Box sx={{ display:'flex', alignItems:'center', justifyContent:'space-between', p:2, borderRadius:2, bgcolor: settings.enabled ? 'rgba(20,184,166,0.06)' : '#f8fafc', border:'1px solid', borderColor: settings.enabled ? 'rgba(20,184,166,0.3)' : '#e2e8f0' }}>
          <Box>
            <Typography fontWeight={600} color="#1e293b">Enable Break Alert Emails</Typography>
            <Typography variant="body2" color="text.secondary">Send email notifications when an employee's break exceeds the limit</Typography>
          </Box>
          <Switch checked={settings.enabled} onChange={e => update('enabled', e.target.checked)}
            sx={{ '& .MuiSwitch-switchBase.Mui-checked': { color:'#14b8a6' }, '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { bgcolor:'#14b8a6' } }} />
        </Box>

        {/* Threshold */}
        <Box sx={{ opacity: settings.enabled ? 1 : 0.45, pointerEvents: settings.enabled ? 'auto' : 'none' }}>
          <Box sx={{ display:'flex', alignItems:'center', justifyContent:'space-between', mb:2 }}>
            <Box>
              <Typography fontWeight={600} color="#1e293b">Maximum Break Duration</Typography>
              <Typography variant="body2" color="text.secondary">Alert fires when break time exceeds this limit</Typography>
            </Box>
            <TextField
              type="number" size="small"
              value={settings.thresholdMinutes}
              onChange={e => update('thresholdMinutes', Math.max(1, Math.min(480, parseInt(e.target.value)||1)))}
              inputProps={{ min:1, max:480 }}
              InputProps={{ endAdornment: <InputAdornment position="end"><Typography variant="caption" color="text.secondary">min</Typography></InputAdornment> }}
              sx={{ width:110, '& .MuiOutlinedInput-root': { fontWeight:700, color:'#b45309', bgcolor:'#fef3c7', '& fieldset':{ borderColor:'#fcd34d' }, '&:hover fieldset':{ borderColor:'#f59e0b' }, '&.Mui-focused fieldset':{ borderColor:'#f59e0b' } } }}
            />
          </Box>
          <Slider
            value={Math.min(settings.thresholdMinutes, 180)} min={15} max={180} step={5}
            marks={[{value:30,label:'30m'},{value:60,label:'1h'},{value:90,label:'1.5h'},{value:120,label:'2h'},{value:180,label:'3h'}]}
            onChange={(_, v) => update('thresholdMinutes', v)}
            sx={{ color:'#14b8a6', '& .MuiSlider-markLabel':{ fontSize:'0.75rem', color:'#94a3b8' }, '& .MuiSlider-thumb':{ bgcolor:'#14b8a6' } }}
          />
        </Box>

        <Divider />

        {/* Recipients */}
        <Box sx={{ opacity: settings.enabled ? 1 : 0.45, pointerEvents: settings.enabled ? 'auto' : 'none' }}>
          <Typography fontWeight={600} color="#1e293b" sx={{ mb:0.5 }}>Notification Recipients</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb:2 }}>Choose who receives the break alert email</Typography>
          <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
            <RecipientRow field="notifyManager" label="Employee's Manager" desc="Direct manager receives the alert" value={settings.notifyManager} onChange={update} />
            <RecipientRow field="notifyHr"      label="HR (CC)"            desc="All HR users are copied"           value={settings.notifyHr}      onChange={update} />
            <RecipientRow field="notifyAdmin"   label="Admin / Director (CC)" desc="Admin is copied on every alert" value={settings.notifyAdmin}   onChange={update} />
          </Stack>
        </Box>

        <Divider />

        {/* Frequency */}
        <Box sx={{ opacity: settings.enabled ? 1 : 0.45, pointerEvents: settings.enabled ? 'auto' : 'none' }}>
          <Typography fontWeight={600} color="#1e293b" sx={{ mb:0.5 }}>Email Frequency</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb:2 }}>When should the break alert be triggered?</Typography>
          <Stack spacing={1}>
            {FREQUENCY_OPTIONS.map(opt => (
              <Box key={opt.value} onClick={() => update('frequency', opt.value)}
                sx={{ display:'flex', alignItems:'flex-start', gap:1.5, px:2, py:1.5, border:'1.5px solid', borderRadius:2, cursor:'pointer', borderColor: settings.frequency===opt.value ? '#14b8a6' : '#e2e8f0', bgcolor: settings.frequency===opt.value ? 'rgba(20,184,166,0.07)' : '#fafafa', transition:'all 0.15s', '&:hover':{ borderColor:'#14b8a6' } }}>
                <Box sx={{ width:18, height:18, borderRadius:'50%', border:'2px solid', flexShrink:0, mt:0.2, borderColor: settings.frequency===opt.value ? '#14b8a6' : '#cbd5e1', bgcolor: settings.frequency===opt.value ? '#14b8a6' : 'transparent', display:'flex', alignItems:'center', justifyContent:'center' }}>
                  {settings.frequency===opt.value && <Box sx={{ width:6, height:6, borderRadius:'50%', bgcolor:'#fff' }} />}
                </Box>
                <Box>
                  <Typography variant="body2" fontWeight={600} color={settings.frequency===opt.value ? '#0f766e' : '#1e293b'}>{opt.label}</Typography>
                  <Typography variant="caption" color="text.secondary">{opt.desc}</Typography>
                </Box>
              </Box>
            ))}
          </Stack>
        </Box>

        <Divider />
        <SaveBar onSave={handleSave} saving={saving} saved={saved} error={error} />
      </Box>
    </Paper>
  );
}

// ── Daily Work Report Email panel ───────────────────────────────────────────

function WorkReportEmailPanel() {
  const [settings, setSettings] = useState(null);
  const [loading, setLoading]   = useState(true);
  const [saving, setSaving]     = useState(false);
  const [saved, setSaved]       = useState(false);
  const [error, setError]       = useState('');

  useEffect(() => {
    settingsApi.getWorkReportEmailSettings()
      .then(setSettings)
      .catch(() => setError('Failed to load work report email settings.'))
      .finally(() => setLoading(false));
  }, []);

  const update = (f, v) => { setSaved(false); setSettings(p => ({ ...p, [f]: v })); };

  const handleSave = async () => {
    setSaving(true); setError('');
    try {
      setSettings(await settingsApi.saveWorkReportEmailSettings(settings));
      setSaved(true); setTimeout(() => setSaved(false), 3000);
    } catch { setError('Failed to save. Please try again.'); }
    finally { setSaving(false); }
  };

  if (loading) return <Box sx={{ display:'flex', justifyContent:'center', py:6 }}><CircularProgress /></Box>;
  if (!settings) return <Alert severity="error">{error}</Alert>;

  return (
    <Paper sx={{ borderRadius: 2, overflow: 'hidden' }}>
      <SectionHeader
        icon={<MarkEmailReadIcon sx={{ color: '#6366f1' }} />}
        title="Daily Work Report Email"
        desc="Automatically send daily work report emails to employees and directors"
        enabled={settings.enabled}
      />
      <Box sx={{ px: 3, py: 3, display: 'flex', flexDirection: 'column', gap: 3.5 }}>

        {/* Enable toggle */}
        <Box sx={{ display:'flex', alignItems:'center', justifyContent:'space-between', p:2, borderRadius:2, bgcolor: settings.enabled ? 'rgba(99,102,241,0.06)' : '#f8fafc', border:'1px solid', borderColor: settings.enabled ? 'rgba(99,102,241,0.3)' : '#e2e8f0' }}>
          <Box>
            <Typography fontWeight={600} color="#1e293b">Enable Daily Work Report Emails</Typography>
            <Typography variant="body2" color="text.secondary">
              When enabled, work report emails are automatically sent at the configured time every weekday
            </Typography>
          </Box>
          <Switch checked={settings.enabled} onChange={e => update('enabled', e.target.checked)}
            sx={{ '& .MuiSwitch-switchBase.Mui-checked': { color:'#6366f1' }, '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { bgcolor:'#6366f1' } }} />
        </Box>

        {/* Send time */}
        <Box sx={{ opacity: settings.enabled ? 1 : 0.45, pointerEvents: settings.enabled ? 'auto' : 'none' }}>
          <Box sx={{ display:'flex', alignItems:'center', gap:1, mb:2 }}>
            <ScheduleIcon sx={{ color:'#6366f1', fontSize:20 }} />
            <Typography fontWeight={600} color="#1e293b">Send Time</Typography>
            <Chip
              label={fmt12(settings.sendHour, settings.sendMinute)}
              size="small"
              sx={{ ml:'auto', bgcolor:'#ede9fe', color:'#7c3aed', fontWeight:700, fontSize:'0.85rem' }}
            />
          </Box>
          <Box sx={{ display:'flex', gap:2 }}>
            <FormControl size="small" sx={{ minWidth: 130 }}>
              <InputLabel>Hour</InputLabel>
              <Select value={settings.sendHour} label="Hour" onChange={e => update('sendHour', e.target.value)}>
                {HOURS.map(h => (
                  <MenuItem key={h} value={h}>
                    {h === 0 ? '12 AM (midnight)' : h < 12 ? `${h} AM` : h === 12 ? '12 PM (noon)' : `${h-12} PM`}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 130 }}>
              <InputLabel>Minute</InputLabel>
              <Select value={settings.sendMinute} label="Minute" onChange={e => update('sendMinute', e.target.value)}>
                {MINUTES.map(m => (
                  <MenuItem key={m} value={m}>{String(m).padStart(2,'0')}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ mt:1, display:'block' }}>
            Emails are sent every weekday (Mon–Fri). Changes take effect within 1 minute — no restart required.
          </Typography>
        </Box>

        <Divider />

        {/* Recipients */}
        <Box sx={{ opacity: settings.enabled ? 1 : 0.45, pointerEvents: settings.enabled ? 'auto' : 'none' }}>
          <Typography fontWeight={600} color="#1e293b" sx={{ mb:0.5 }}>Recipients</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb:2 }}>Choose who receives the daily work report email</Typography>
          <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
            <RecipientRow
              field="notifyEmployees"
              label="Employees (individual report)"
              desc="Each employee receives their own daily work summary"
              value={settings.notifyEmployees}
              onChange={update}
            />
            <RecipientRow
              field="notifyAdmin"
              label="Admin / Director (consolidated)"
              desc="Director receives a full consolidated report of all employees"
              value={settings.notifyAdmin}
              onChange={update}
            />
          </Stack>
        </Box>

        <Divider />
        <SaveBar onSave={handleSave} saving={saving} saved={saved} error={error} />
      </Box>
    </Paper>
  );
}

// ── Attendance Audit Alert panel ─────────────────────────────────────────────

function AttendanceAuditPanel() {
  const [settings, setSettings] = useState(null);
  const [loading, setLoading]   = useState(true);
  const [saving, setSaving]     = useState(false);
  const [saved, setSaved]       = useState(false);
  const [error, setError]       = useState('');

  useEffect(() => {
    settingsApi.getAttendanceAuditSettings()
      .then(setSettings)
      .catch(() => setError('Failed to load attendance audit settings.'))
      .finally(() => setLoading(false));
  }, []);

  const update = (f, v) => { setSaved(false); setSettings(p => ({ ...p, [f]: v })); };

  const handleSave = async () => {
    setSaving(true); setError('');
    try {
      setSettings(await settingsApi.saveAttendanceAuditSettings(settings));
      setSaved(true); setTimeout(() => setSaved(false), 3000);
    } catch { setError('Failed to save. Please try again.'); }
    finally { setSaving(false); }
  };

  if (loading) return <Box sx={{ display:'flex', justifyContent:'center', py:6 }}><CircularProgress /></Box>;
  if (!settings) return <Alert severity="error">{error}</Alert>;

  return (
    <Paper sx={{ borderRadius: 2, overflow: 'hidden' }}>
      <SectionHeader
        icon={<AssignmentLateIcon sx={{ color: '#f59e0b' }} />}
        title="Attendance Audit Alert"
        desc="Automatically flag employees who worked below required hours and notify the right people"
        enabled={settings.enabled}
      />
      <Box sx={{ px: 3, py: 3, display: 'flex', flexDirection: 'column', gap: 3.5 }}>

        {/* Enable toggle */}
        <Box sx={{ display:'flex', alignItems:'center', justifyContent:'space-between', p:2, borderRadius:2, bgcolor: settings.enabled ? 'rgba(245,158,11,0.06)' : '#f8fafc', border:'1px solid', borderColor: settings.enabled ? 'rgba(245,158,11,0.3)' : '#e2e8f0' }}>
          <Box>
            <Typography fontWeight={600} color="#1e293b">Enable Attendance Audit Alerts</Typography>
            <Typography variant="body2" color="text.secondary">
              Send email alerts when an employee's daily hours fall below the required minimum
            </Typography>
          </Box>
          <Switch checked={settings.enabled} onChange={e => update('enabled', e.target.checked)}
            sx={{ '& .MuiSwitch-switchBase.Mui-checked': { color:'#f59e0b' }, '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { bgcolor:'#f59e0b' } }} />
        </Box>

        {/* Trigger time */}
        <Box sx={{ opacity: settings.enabled ? 1 : 0.45, pointerEvents: settings.enabled ? 'auto' : 'none' }}>
          <Box sx={{ display:'flex', alignItems:'center', gap:1, mb:2 }}>
            <ScheduleIcon sx={{ color:'#f59e0b', fontSize:20 }} />
            <Typography fontWeight={600} color="#1e293b">Audit Trigger Time</Typography>
            <Chip
              label={fmt12(settings.triggerHour, settings.triggerMinute)}
              size="small"
              sx={{ ml:'auto', bgcolor:'#fef3c7', color:'#b45309', fontWeight:700, fontSize:'0.85rem' }}
            />
          </Box>
          <Box sx={{ display:'flex', gap:2 }}>
            <FormControl size="small" sx={{ minWidth: 130 }}>
              <InputLabel>Hour</InputLabel>
              <Select value={settings.triggerHour} label="Hour" onChange={e => update('triggerHour', e.target.value)}>
                {HOURS.map(h => (
                  <MenuItem key={h} value={h}>
                    {h === 0 ? '12 AM (midnight)' : h < 12 ? `${h} AM` : h === 12 ? '12 PM (noon)' : `${h-12} PM`}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 130 }}>
              <InputLabel>Minute</InputLabel>
              <Select value={settings.triggerMinute} label="Minute" onChange={e => update('triggerMinute', e.target.value)}>
                {MINUTES.map(m => (
                  <MenuItem key={m} value={m}>{String(m).padStart(2,'0')}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ mt:1, display:'block' }}>
            Audit runs automatically at this time every weekday. Changes take effect within 1 minute — no restart required.
          </Typography>
        </Box>

        <Divider />

        {/* Minimum work hours */}
        <Box sx={{ opacity: settings.enabled ? 1 : 0.45, pointerEvents: settings.enabled ? 'auto' : 'none' }}>
          <Typography fontWeight={600} color="#1e293b" sx={{ mb:0.5 }}>Minimum Required Hours</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb:2 }}>
            Employees working fewer than this many hours per day will trigger an alert
          </Typography>
          <Box sx={{ display:'flex', alignItems:'center', gap:2 }}>
            <TextField
              type="number" size="small"
              value={settings.minWorkHours}
              onChange={e => update('minWorkHours', Math.max(1, Math.min(12, parseInt(e.target.value) || 1)))}
              inputProps={{ min:1, max:12 }}
              InputProps={{ endAdornment: <InputAdornment position="end"><Typography variant="caption" color="text.secondary">hrs / day</Typography></InputAdornment> }}
              sx={{ width:130, '& .MuiOutlinedInput-root': { fontWeight:700, color:'#b45309', bgcolor:'#fef3c7', '& fieldset':{ borderColor:'#fcd34d' }, '&:hover fieldset':{ borderColor:'#f59e0b' }, '&.Mui-focused fieldset':{ borderColor:'#f59e0b' } } }}
            />
            <Typography variant="body2" color="text.secondary">
              Typical values: 8 hrs (full-time), 4 hrs (part-time)
            </Typography>
          </Box>
        </Box>

        <Divider />

        {/* Recipients */}
        <Box sx={{ opacity: settings.enabled ? 1 : 0.45, pointerEvents: settings.enabled ? 'auto' : 'none' }}>
          <Typography fontWeight={600} color="#1e293b" sx={{ mb:0.5 }}>Notification Recipients</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb:2 }}>
            Choose who receives audit alert emails when under-hours are detected
          </Typography>
          <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
            <RecipientRow field="notifyManager"  label="Employee's Manager"      desc="Direct manager receives the audit alert"             value={settings.notifyManager}  onChange={update} />
            <RecipientRow field="notifyAdmin"    label="Admin / Director (CC)"   desc="Admin is copied on every audit alert"               value={settings.notifyAdmin}    onChange={update} />
            <RecipientRow field="notifyHr"       label="HR (CC)"                 desc="All HR users are copied on audit alerts"            value={settings.notifyHr}       onChange={update} />
            <RecipientRow field="notifyEmployee" label="Employee (CC)"           desc="Employee is CC'd on their own audit alert email"    value={settings.notifyEmployee} onChange={update} />
          </Stack>
        </Box>

        <Divider />
        <SaveBar onSave={handleSave} saving={saving} saved={saved} error={error} />
      </Box>
    </Paper>
  );
}

// ── Main Settings page ───────────────────────────────────────────────────────

const NAV = [
  { id: 'break',      label: 'Break Alerts',         icon: <AccessTimeIcon fontSize="small" />,      color: '#0f766e', bg: 'rgba(20,184,166,0.1)',  hover: 'rgba(20,184,166,0.07)'  },
  { id: 'audit',      label: 'Attendance Audit',      icon: <AssignmentLateIcon fontSize="small" />,  color: '#b45309', bg: 'rgba(245,158,11,0.1)',  hover: 'rgba(245,158,11,0.07)'  },
  { id: 'workReport', label: 'Email Automation',      icon: <MarkEmailReadIcon fontSize="small" />,   color: '#4f46e5', bg: 'rgba(99,102,241,0.1)',  hover: 'rgba(99,102,241,0.07)'  },
];

export default function Settings() {
  const [active, setActive] = useState('break');

  return (
    <Box sx={{ maxWidth: 960, mx: 'auto', py: 3, px: { xs: 2, md: 3 } }}>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight={700} color="#1e293b">Settings</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Configure system behaviour. Changes take effect immediately — no restart required.
        </Typography>
      </Box>

      <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>
        {/* Left nav */}
        <Paper sx={{ width: 210, flexShrink: 0, p: 1.5, borderRadius: 2 }}>
          <Typography variant="caption" color="text.disabled" sx={{ px: 1, fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
            Categories
          </Typography>
          <Box sx={{ mt: 1, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            {NAV.map(n => (
              <Box
                key={n.id}
                onClick={() => setActive(n.id)}
                sx={{
                  px: 1.5, py: 1, borderRadius: 1.5, cursor: 'pointer',
                  bgcolor: active === n.id ? n.bg : 'transparent',
                  color:   active === n.id ? n.color : '#64748b',
                  fontWeight: active === n.id ? 600 : 400,
                  fontSize: '0.875rem',
                  display: 'flex', alignItems: 'center', gap: 1,
                  transition: 'all 0.15s',
                  '&:hover': { bgcolor: n.hover, color: n.color },
                }}
              >
                {n.icon} {n.label}
              </Box>
            ))}
          </Box>
        </Paper>

        {/* Panel */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          {active === 'break'      && <BreakAlertPanel />}
          {active === 'audit'      && <AttendanceAuditPanel />}
          {active === 'workReport' && <WorkReportEmailPanel />}
        </Box>
      </Box>
    </Box>
  );
}
