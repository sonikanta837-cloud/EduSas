import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, Button, Grid, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField, CircularProgress, Avatar, Autocomplete,
  Chip, IconButton, Divider, Tabs, Tab, LinearProgress, Select,
  MenuItem, FormControl, InputLabel, Slider, OutlinedInput,
  InputAdornment, Tooltip, Alert, Collapse,
} from '@mui/material';
import AddIcon              from '@mui/icons-material/Add';
import SearchIcon           from '@mui/icons-material/Search';
import CloseIcon            from '@mui/icons-material/Close';
import EditOutlinedIcon     from '@mui/icons-material/EditOutlined';
import DeleteOutlineIcon    from '@mui/icons-material/DeleteOutline';
import FlagIcon             from '@mui/icons-material/Flag';
import CheckCircleIcon      from '@mui/icons-material/CheckCircle';
import WarningAmberIcon     from '@mui/icons-material/WarningAmber';
import EventNoteIcon        from '@mui/icons-material/EventNote';
import CommentIcon          from '@mui/icons-material/Comment';
import HistoryIcon          from '@mui/icons-material/History';
import TrackChangesIcon     from '@mui/icons-material/TrackChanges';
import AssignmentLateIcon   from '@mui/icons-material/AssignmentLate';
import CalendarTodayIcon    from '@mui/icons-material/CalendarToday';
import PersonIcon           from '@mui/icons-material/Person';
import SendIcon             from '@mui/icons-material/Send';
import { pipApi }           from '../api/pipApi';
import { employeeApi }      from '../api/employeeApi';
import { toast }            from 'react-toastify';

// ── constants ──────────────────────────────────────────────────────────────

const STATUS_META = {
  ACTIVE:     { label: 'Active',     color: '#1d4ed8', bg: '#eff6ff', border: '#bfdbfe' },
  COMPLETED:  { label: 'Completed',  color: '#16a34a', bg: '#f0fdf4', border: '#86efac' },
  EXTENDED:   { label: 'Extended',   color: '#d97706', bg: '#fffbeb', border: '#fde68a' },
  TERMINATED: { label: 'Terminated', color: '#dc2626', bg: '#fef2f2', border: '#fca5a5' },
};

const GOAL_STATUS_META = {
  NOT_STARTED:  { label: 'Not Started',  color: '#64748b', bg: '#f8fafc' },
  IN_PROGRESS:  { label: 'In Progress',  color: '#1d4ed8', bg: '#eff6ff' },
  ACHIEVED:     { label: 'Achieved',     color: '#16a34a', bg: '#f0fdf4' },
  NOT_ACHIEVED: { label: 'Not Achieved', color: '#dc2626', bg: '#fef2f2' },
};

const AVATAR_PALETTE = [
  { bg: '#ede9fe', color: '#7c3aed' }, { bg: '#fce7f3', color: '#be185d' },
  { bg: '#d1fae5', color: '#065f46' }, { bg: '#fef3c7', color: '#92400e' },
  { bg: '#dbeafe', color: '#1e40af' }, { bg: '#ffedd5', color: '#9a3412' },
];

// ── helpers ────────────────────────────────────────────────────────────────

const avatarColor = (name = '') => {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = name.charCodeAt(i) + ((h << 5) - h);
  return AVATAR_PALETTE[Math.abs(h) % AVATAR_PALETTE.length];
};
const initials = (name = '') =>
  name.split(' ').filter(Boolean).map(w => w[0]).join('').slice(0, 2).toUpperCase();
const fmtDate = d =>
  d ? new Date(d).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

const URL_SPLIT = /(https?:\/\/[^\s]+)/g;
const isSafeUrl = (s) => /^https?:\/\//i.test(s);
const TextWithLinks = ({ text, sx }) => {
  if (!text) return null;
  const parts = text.split(URL_SPLIT);
  return (
    <Typography sx={{ fontSize: 14, color: '#374151', lineHeight: 1.6, wordBreak: 'break-all', ...sx }}>
      {parts.map((part, i) =>
        isSafeUrl(part)
          ? <a key={i} href={part} target="_blank" rel="noopener noreferrer"
              style={{ color: '#1d4ed8', textDecoration: 'underline', wordBreak: 'break-all' }}>{part}</a>
          : part
      )}
    </Typography>
  );
};
const fmtDateTime = d =>
  d ? new Date(d).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
const daysLeft = endDate => {
  if (!endDate) return null;
  const diff = Math.ceil((new Date(endDate) - new Date()) / 86400000);
  return diff;
};

// ── sub-components ────────────────────────────────────────────────────────

const StatusChip = ({ status }) => {
  const m = STATUS_META[status] || STATUS_META.ACTIVE;
  return (
    <Chip label={m.label} size="small" sx={{
      bgcolor: m.bg, color: m.color, border: `1px solid ${m.border}`,
      fontWeight: 700, fontSize: '0.72rem', height: 22,
    }} />
  );
};

const GoalStatusChip = ({ status }) => {
  const m = GOAL_STATUS_META[status] || GOAL_STATUS_META.NOT_STARTED;
  return (
    <Chip label={m.label} size="small" sx={{
      bgcolor: m.bg, color: m.color, fontWeight: 600, fontSize: '0.7rem', height: 20,
    }} />
  );
};

// ── PIP card ──────────────────────────────────────────────────────────────

const PipCard = ({ pip, onClick, canManage }) => {
  const av = avatarColor(pip.employeeName || '');
  const days = daysLeft(pip.endDate);
  const isOverdue = pip.overdue;

  return (
    <Box onClick={onClick} sx={{
      bgcolor: '#fff', border: `1px solid ${isOverdue && pip.status === 'ACTIVE' ? '#fca5a5' : '#f1f5f9'}`,
      borderRadius: '14px', p: 2.5, cursor: 'pointer',
      boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
      transition: 'all .2s',
      '&:hover': { boxShadow: '0 6px 20px rgba(0,0,0,0.1)', transform: 'translateY(-2px)' },
    }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, mb: 1.5 }}>
        <Avatar sx={{ width: 42, height: 42, bgcolor: av.bg, color: av.color, fontSize: '0.85rem', fontWeight: 700, flexShrink: 0 }}>
          {initials(pip.employeeName || '')}
        </Avatar>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography sx={{ fontSize: 14.5, fontWeight: 700, color: '#0f172a' }} noWrap>{pip.employeeName}</Typography>
          <Typography sx={{ fontSize: 12, color: '#64748b' }} noWrap>{pip.employeePosition || pip.employeeDepartment || ''}</Typography>
        </Box>
        <StatusChip status={pip.status} />
      </Box>

      {/* Title */}
      <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: '#1e293b', mb: 1, lineHeight: 1.4 }}>
        {pip.title}
      </Typography>

      {/* Progress bar */}
      {pip.totalGoals > 0 && (
        <Box sx={{ mb: 1.5 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
            <Typography sx={{ fontSize: 11.5, color: '#64748b' }}>
              Goals: {pip.achievedGoals}/{pip.totalGoals} achieved
            </Typography>
            <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#1e3a5f' }}>
              {pip.overallProgressPercent}%
            </Typography>
          </Box>
          <LinearProgress
            variant="determinate" value={pip.overallProgressPercent}
            sx={{
              height: 6, borderRadius: 3, bgcolor: '#f1f5f9',
              '& .MuiLinearProgress-bar': {
                bgcolor: pip.overallProgressPercent >= 80 ? '#16a34a'
                       : pip.overallProgressPercent >= 50 ? '#f59e0b' : '#ef4444',
                borderRadius: 3,
              },
            }}
          />
        </Box>
      )}

      {/* Footer meta */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <CalendarTodayIcon sx={{ fontSize: 12, color: '#94a3b8' }} />
          <Typography sx={{ fontSize: 11.5, color: '#64748b' }}>{fmtDate(pip.startDate)} → {fmtDate(pip.endDate)}</Typography>
        </Box>
        {pip.status === 'ACTIVE' && days !== null && (
          <Chip
            label={days < 0 ? `${Math.abs(days)}d overdue` : `${days}d left`}
            size="small"
            sx={{
              height: 18, fontSize: '0.68rem', fontWeight: 700,
              bgcolor: days < 0 ? '#fef2f2' : days <= 7 ? '#fffbeb' : '#f0fdf4',
              color: days < 0 ? '#dc2626' : days <= 7 ? '#d97706' : '#16a34a',
            }}
          />
        )}
        <Box sx={{ ml: 'auto', display: 'flex', gap: 1.5 }}>
          {pip.weeklyReviewCount > 0 && (
            <Tooltip title={`${pip.weeklyReviewCount} weekly review${pip.weeklyReviewCount !== 1 ? 's' : ''}`}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.4 }}>
                <EventNoteIcon sx={{ fontSize: 13, color: '#94a3b8' }} />
                <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>{pip.weeklyReviewCount}</Typography>
              </Box>
            </Tooltip>
          )}
          {pip.commentCount > 0 && (
            <Tooltip title={`${pip.commentCount} comment${pip.commentCount !== 1 ? 's' : ''}`}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.4 }}>
                <CommentIcon sx={{ fontSize: 13, color: '#94a3b8' }} />
                <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>{pip.commentCount}</Typography>
              </Box>
            </Tooltip>
          )}
        </Box>
      </Box>
    </Box>
  );
};

// ── Create PIP dialog ─────────────────────────────────────────────────────

const BLANK_GOAL = { title: '', description: '', successCriteria: '', targetDate: '' };
const BLANK_PIP  = {
  employeeId: null, title: '', startDate: '', endDate: '',
  reason: '', improvementAreas: '', supportProvided: '', consequences: '',
};

const CreatePipDialog = ({ open, onClose, onCreated, myEmployee, isAdmin, isHR }) => {
  const [form, setForm]         = useState(BLANK_PIP);
  const [goals, setGoals]       = useState([{ ...BLANK_GOAL }]);
  const [empOptions, setEmpOptions] = useState([]);
  const [empLoading, setEmpLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [step, setStep]         = useState(0); // 0 = details, 1 = goals

  useEffect(() => {
    if (!open) { setForm(BLANK_PIP); setGoals([{ ...BLANK_GOAL }]); setStep(0); return; }
    if (!isAdmin && !isHR && !myEmployee?.id) { return; }
    setEmpLoading(true);
    // Admin and HR see all employees; Managers see only their team
    const loader = (isAdmin || isHR) ? employeeApi.getAll() : employeeApi.getTeam(myEmployee.id);
    loader
      .then(list => setEmpOptions((list || []).filter(e => e.active !== false && e.id !== myEmployee?.id && e.role !== 'ADMIN' && e.role !== 'DIRECTOR')))
      .catch(() => toast.error('Failed to load employees'))
      .finally(() => setEmpLoading(false));
  }, [open]); // eslint-disable-line

  const addGoal    = () => setGoals(g => [...g, { ...BLANK_GOAL }]);
  const removeGoal = i  => setGoals(g => g.filter((_, idx) => idx !== i));
  const updateGoal = (i, f, v) => setGoals(g => g.map((x, idx) => idx === i ? { ...x, [f]: v } : x));

  const handleSubmit = async () => {
    if (!form.employeeId) { toast.error('Select an employee'); return; }
    if (!form.title.trim()) { toast.error('Enter a PIP title'); return; }
    if (!form.startDate || !form.endDate) { toast.error('Start and end dates are required'); return; }
    if (new Date(form.endDate) <= new Date(form.startDate)) { toast.error('End date must be after start date'); return; }
    const validGoals = goals.filter(g => g.title.trim());
    setSubmitting(true);
    try {
      const created = await pipApi.create({ ...form, goals: validGoals });
      toast.success('PIP created successfully');
      onCreated(created);
      onClose();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to create PIP');
    } finally { setSubmitting(false); }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperProps={{ sx: { borderRadius: '16px', maxHeight: '92vh' } }}>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pb: 1 }}>
        <AssignmentLateIcon sx={{ color: '#1e3a5f' }} />
        <Typography fontWeight={700} fontSize={17}>Create Performance Improvement Plan</Typography>
        <IconButton onClick={onClose} sx={{ ml: 'auto' }}><CloseIcon /></IconButton>
      </DialogTitle>
      <Divider />

      {/* Step indicators */}
      <Box sx={{ px: 3, pt: 2, display: 'flex', gap: 1 }}>
        {['Plan Details', 'Goals'].map((label, i) => (
          <Box key={i} onClick={() => setStep(i)} sx={{
            px: 2, py: 0.6, borderRadius: '20px', cursor: 'pointer', fontSize: 13, fontWeight: 600,
            bgcolor: step === i ? '#1e3a5f' : '#f1f5f9',
            color: step === i ? '#fff' : '#64748b',
          }}>{i + 1}. {label}</Box>
        ))}
      </Box>

      <DialogContent sx={{ px: 3, py: 2 }}>

        {/* ── Step 0: Plan details ── */}
        {step === 0 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Autocomplete
              options={empOptions} loading={empLoading}
              getOptionLabel={e => e.fullName || ''}
              value={empOptions.find(e => e.id === form.employeeId) || null}
              onChange={(_, e) => setForm(f => ({ ...f, employeeId: e ? e.id : null }))}
              renderOption={(props, e) => (
                <Box component="li" {...props} key={e.id}>
                  <Box>
                    <Typography variant="body2" fontWeight={600}>{e.fullName}</Typography>
                    <Typography variant="caption" color="text.secondary">{e.position || e.role} · {e.department}</Typography>
                  </Box>
                </Box>
              )}
              renderInput={p => <TextField {...p} label="Employee *" size="small"
                InputProps={{ ...p.InputProps, endAdornment: <>{empLoading && <CircularProgress size={14} />}{p.InputProps.endAdornment}</> }} />}
            />
            <TextField label="PIP Title *" size="small" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} fullWidth placeholder="e.g. Q3 Performance Improvement Plan" />
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Start Date *" type="date" size="small" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} InputLabelProps={{ shrink: true }} fullWidth />
              <TextField label="End Date *" type="date" size="small" value={form.endDate} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} InputLabelProps={{ shrink: true }} fullWidth />
            </Box>
            <TextField label="Reason for PIP" size="small" multiline rows={2} value={form.reason} onChange={e => setForm(f => ({ ...f, reason: e.target.value }))} fullWidth placeholder="Why is this PIP being initiated?" />
            <TextField label="Areas of Improvement" size="small" multiline rows={2} value={form.improvementAreas} onChange={e => setForm(f => ({ ...f, improvementAreas: e.target.value }))} fullWidth placeholder="Specific skills, behaviours, or performance areas to improve" />
            <TextField label="Support Provided" size="small" multiline rows={2} value={form.supportProvided} onChange={e => setForm(f => ({ ...f, supportProvided: e.target.value }))} fullWidth placeholder="Training, mentoring, resources that will be provided" />
            <TextField label="Consequences" size="small" multiline rows={2} value={form.consequences} onChange={e => setForm(f => ({ ...f, consequences: e.target.value }))} fullWidth placeholder="Consequences if improvement targets are not met" />
          </Box>
        )}

        {/* ── Step 1: Goals ── */}
        {step === 1 && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Typography fontWeight={600} color="#1e293b">Define Improvement Goals</Typography>
              <Button size="small" startIcon={<AddIcon />} onClick={addGoal}
                sx={{ fontSize: 12.5, fontWeight: 600, color: '#1e3a5f' }}>Add Goal</Button>
            </Box>
            {goals.map((g, i) => (
              <Box key={i} sx={{ p: 2, border: '1px solid #e2e8f0', borderRadius: '12px', position: 'relative' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
                  <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#64748b' }}>Goal {i + 1}</Typography>
                  {goals.length > 1 && (
                    <IconButton size="small" onClick={() => removeGoal(i)} sx={{ color: '#dc2626' }}>
                      <DeleteOutlineIcon sx={{ fontSize: 16 }} />
                    </IconButton>
                  )}
                </Box>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                  <TextField label="Goal Title *" size="small" value={g.title} onChange={e => updateGoal(i, 'title', e.target.value)} fullWidth />
                  <TextField label="Description" size="small" multiline rows={1} value={g.description} onChange={e => updateGoal(i, 'description', e.target.value)} fullWidth />
                  <TextField label="Success Criteria" size="small" multiline rows={1} value={g.successCriteria} onChange={e => updateGoal(i, 'successCriteria', e.target.value)} fullWidth placeholder="How will we know this goal is achieved?" />
                  <TextField label="Target Date" type="date" size="small" value={g.targetDate} onChange={e => updateGoal(i, 'targetDate', e.target.value)} InputLabelProps={{ shrink: true }} sx={{ width: 200 }} />
                </Box>
              </Box>
            ))}
            {goals.length === 0 && (
              <Box sx={{ textAlign: 'center', py: 4, color: '#94a3b8' }}>
                <FlagIcon sx={{ fontSize: 36, mb: 1, display: 'block', mx: 'auto' }} />
                <Typography fontSize={13}>No goals added yet. Click "Add Goal" to start.</Typography>
              </Box>
            )}
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
        {step === 0 ? (
          <>
            <Button onClick={onClose} sx={{ color: '#64748b', fontWeight: 600 }}>Cancel</Button>
            <Button variant="contained" onClick={() => setStep(1)}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' }, fontWeight: 700 }}>
              Next: Goals →
            </Button>
          </>
        ) : (
          <>
            <Button onClick={() => setStep(0)} sx={{ color: '#64748b', fontWeight: 600 }}>← Back</Button>
            <Button onClick={onClose} sx={{ color: '#64748b', fontWeight: 600 }}>Cancel</Button>
            <Button variant="contained" onClick={handleSubmit} disabled={submitting}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' }, fontWeight: 700 }}>
              {submitting ? <CircularProgress size={16} sx={{ color: '#fff', mr: 1 }} /> : null}
              Create PIP
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
};

// ── Goal item (in detail view) ────────────────────────────────────────────

const GoalItem = ({ goal, canManage, onUpdate, onDelete }) => {
  const [editing, setEditing]   = useState(false);
  const [saving, setSaving]     = useState(false);
  const [form, setForm]         = useState({ ...goal });
  const m = GOAL_STATUS_META[goal.status] || GOAL_STATUS_META.NOT_STARTED;

  const handleSave = async () => {
    setSaving(true);
    try {
      const updated = await pipApi.updateGoal(goal.pipId, goal.id, form);
      onUpdate(updated);
      setEditing(false);
      toast.success('Goal updated');
    } catch { toast.error('Failed to update goal'); }
    finally { setSaving(false); }
  };

  return (
    <Box sx={{ p: 2, border: '1px solid #e2e8f0', borderRadius: '12px', bgcolor: '#fafafa' }}>
      {!editing ? (
        <>
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, mb: 1 }}>
            <FlagIcon sx={{ fontSize: 16, color: m.color, mt: 0.25, flexShrink: 0 }} />
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                <Typography sx={{ fontSize: 13.5, fontWeight: 700, color: '#1e293b' }}>{goal.title}</Typography>
                <GoalStatusChip status={goal.status} />
              </Box>
              {goal.description && <Typography sx={{ fontSize: 12.5, color: '#64748b', mt: 0.4 }}>{goal.description}</Typography>}
              {goal.successCriteria && (
                <Typography sx={{ fontSize: 12, color: '#94a3b8', mt: 0.3, fontStyle: 'italic' }}>
                  ✓ {goal.successCriteria}
                </Typography>
              )}
            </Box>
            {canManage && (
              <Box sx={{ display: 'flex', gap: 0.5, flexShrink: 0 }}>
                <IconButton size="small" onClick={() => { setForm({ ...goal }); setEditing(true); }} sx={{ color: '#64748b' }}>
                  <EditOutlinedIcon sx={{ fontSize: 15 }} />
                </IconButton>
                <IconButton size="small" onClick={() => onDelete(goal.id)} sx={{ color: '#dc2626' }}>
                  <DeleteOutlineIcon sx={{ fontSize: 15 }} />
                </IconButton>
              </Box>
            )}
          </Box>
          {/* Progress bar */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 1.5 }}>
            <LinearProgress variant="determinate" value={goal.progressPercent}
              sx={{ flex: 1, height: 7, borderRadius: 4, bgcolor: '#e2e8f0',
                '& .MuiLinearProgress-bar': {
                  bgcolor: goal.progressPercent >= 80 ? '#16a34a' : goal.progressPercent >= 50 ? '#f59e0b' : '#ef4444',
                  borderRadius: 4,
                },
              }} />
            <Typography sx={{ fontSize: 12, fontWeight: 700, color: '#1e3a5f', minWidth: 32 }}>{goal.progressPercent}%</Typography>
            {goal.targetDate && (
              <Typography sx={{ fontSize: 11, color: '#94a3b8' }}>Target: {fmtDate(goal.targetDate)}</Typography>
            )}
          </Box>
        </>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <TextField label="Title" size="small" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} fullWidth />
          <TextField label="Description" size="small" multiline rows={1} value={form.description || ''} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} fullWidth />
          <TextField label="Success Criteria" size="small" multiline rows={1} value={form.successCriteria || ''} onChange={e => setForm(f => ({ ...f, successCriteria: e.target.value }))} fullWidth />
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
            <FormControl size="small" sx={{ minWidth: 160 }}>
              <InputLabel>Status</InputLabel>
              <Select value={form.status} label="Status" onChange={e => setForm(f => ({ ...f, status: e.target.value }))}>
                {Object.entries(GOAL_STATUS_META).map(([k, v]) => (
                  <MenuItem key={k} value={k}><Typography sx={{ fontSize: 13 }}>{v.label}</Typography></MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField label="Target Date" type="date" size="small" value={form.targetDate || ''} onChange={e => setForm(f => ({ ...f, targetDate: e.target.value }))} InputLabelProps={{ shrink: true }} sx={{ width: 180 }} />
          </Box>
          <Box>
            <Typography sx={{ fontSize: 12, color: '#64748b', mb: 0.5 }}>Progress: {form.progressPercent}%</Typography>
            <Slider value={form.progressPercent} min={0} max={100} step={5}
              onChange={(_, v) => setForm(f => ({ ...f, progressPercent: v }))}
              sx={{ color: '#1e3a5f' }} />
          </Box>
          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
            <Button size="small" onClick={() => setEditing(false)} sx={{ color: '#64748b' }}>Cancel</Button>
            <Button size="small" variant="contained" onClick={handleSave} disabled={saving}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' }, fontSize: 12 }}>
              {saving ? <CircularProgress size={12} sx={{ color: '#fff', mr: 0.5 }} /> : null} Save
            </Button>
          </Box>
        </Box>
      )}
    </Box>
  );
};

// ── PIP Detail dialog ─────────────────────────────────────────────────────

const PipDetailDialog = ({ open, pipId, onClose, onUpdated, myEmployee, canManage, isAdmin }) => {
  const [pip, setPip]           = useState(null);
  const [loading, setLoading]   = useState(false);
  const [tab, setTab]           = useState(0);
  // True when the logged-in user IS the PIP target — they cannot manage their own PIP
  const isMyPip = pip ? pip.employeeId === myEmployee?.id : false;
  const canAct  = canManage && !isMyPip;
  const [comment, setComment]   = useState('');
  const [posting, setPosting]   = useState(false);
  const [outcomeOpen, setOutcomeOpen] = useState(false);
  const [reviewOpen, setReviewOpen]   = useState(false);
  const [addGoalOpen, setAddGoalOpen] = useState(false);
  const [goalForm, setGoalForm] = useState({ ...BLANK_GOAL });
  const [savingGoal, setSavingGoal] = useState(false);

  const load = useCallback(async () => {
    if (!pipId) return;
    setLoading(true);
    try {
      const data = await pipApi.getDetail(pipId);
      setPip(data);
    } catch { toast.error('Failed to load PIP details'); }
    finally { setLoading(false); }
  }, [pipId]);

  useEffect(() => { if (open) { setTab(0); load(); } }, [open, load]);

  const handleAddComment = async () => {
    if (!comment.trim()) return;
    setPosting(true);
    try {
      const c = await pipApi.addComment(pipId, comment.trim());
      setPip(prev => ({ ...prev, comments: [...(prev.comments || []), c], commentCount: (prev.commentCount || 0) + 1 }));
      setComment('');
    } catch { toast.error('Failed to add comment'); }
    finally { setPosting(false); }
  };

  const handleGoalUpdate = updated => {
    setPip(prev => ({
      ...prev,
      goals: prev.goals.map(g => g.id === updated.id ? updated : g),
    }));
    onUpdated?.();
  };

  const handleGoalDelete = async goalId => {
    try {
      await pipApi.deleteGoal(pipId, goalId);
      setPip(prev => ({ ...prev, goals: prev.goals.filter(g => g.id !== goalId) }));
      toast.success('Goal removed');
      onUpdated?.();
    } catch { toast.error('Failed to delete goal'); }
  };

  const handleAddGoal = async () => {
    if (!goalForm.title.trim()) { toast.error('Goal title is required'); return; }
    setSavingGoal(true);
    try {
      const g = await pipApi.addGoal(pipId, goalForm);
      setPip(prev => ({ ...prev, goals: [...(prev.goals || []), g] }));
      setGoalForm({ ...BLANK_GOAL });
      setAddGoalOpen(false);
      toast.success('Goal added');
      onUpdated?.();
    } catch { toast.error('Failed to add goal'); }
    finally { setSavingGoal(false); }
  };

  if (!open) return null;

  const av = pip ? avatarColor(pip.employeeName || '') : { bg: '#f1f5f9', color: '#64748b' };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth PaperProps={{ sx: { borderRadius: '16px', height: '90vh' } }}>
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
          <CircularProgress sx={{ color: '#1e3a5f' }} />
        </Box>
      ) : pip ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Header */}
          <Box sx={{ px: 3, py: 2, borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', gap: 2, flexShrink: 0 }}>
            <Avatar sx={{ width: 46, height: 46, bgcolor: av.bg, color: av.color, fontWeight: 700, fontSize: '0.9rem' }}>
              {initials(pip.employeeName || '')}
            </Avatar>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <Typography sx={{ fontSize: 16, fontWeight: 800, color: '#0f172a' }} noWrap>{pip.title}</Typography>
                <StatusChip status={pip.status} />
                {pip.overdue && pip.status === 'ACTIVE' && (
                  <Chip label="Overdue" size="small" sx={{ bgcolor: '#fef2f2', color: '#dc2626', fontWeight: 700, fontSize: '0.7rem', height: 20 }} />
                )}
              </Box>
              <Box sx={{ display: 'flex', gap: 2, mt: 0.3, flexWrap: 'wrap' }}>
                <Typography sx={{ fontSize: 12.5, color: '#64748b' }}>
                  <PersonIcon sx={{ fontSize: 12, mr: 0.4, verticalAlign: 'middle' }} />{pip.employeeName}
                </Typography>
                <Typography sx={{ fontSize: 12.5, color: '#94a3b8' }}>
                  <CalendarTodayIcon sx={{ fontSize: 12, mr: 0.4, verticalAlign: 'middle' }} />
                  {fmtDate(pip.startDate)} → {fmtDate(pip.endDate)}
                </Typography>
                <Typography sx={{ fontSize: 12.5, color: '#94a3b8' }}>By: {pip.createdByName}</Typography>
              </Box>
            </Box>
            <Box sx={{ display: 'flex', gap: 1, flexShrink: 0 }}>
              {canAct && pip.status === 'ACTIVE' && (
                <Button size="small" variant="outlined" onClick={() => setOutcomeOpen(true)}
                  sx={{ fontSize: 12, fontWeight: 700, borderColor: '#1e3a5f', color: '#1e3a5f', borderRadius: '8px' }}>
                  Set Outcome
                </Button>
              )}
              <IconButton onClick={onClose} size="small"><CloseIcon /></IconButton>
            </Box>
          </Box>

          {/* Progress bar */}
          {pip.totalGoals > 0 && (
            <Box sx={{ px: 3, py: 1, bgcolor: '#f8fafc', borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', gap: 2, flexShrink: 0 }}>
              <Typography sx={{ fontSize: 12.5, color: '#64748b', whiteSpace: 'nowrap' }}>
                Overall: {pip.achievedGoals}/{pip.totalGoals} goals
              </Typography>
              <LinearProgress variant="determinate" value={pip.overallProgressPercent}
                sx={{ flex: 1, height: 8, borderRadius: 4, bgcolor: '#e2e8f0',
                  '& .MuiLinearProgress-bar': {
                    bgcolor: pip.overallProgressPercent >= 80 ? '#16a34a' : pip.overallProgressPercent >= 50 ? '#f59e0b' : '#ef4444',
                  },
                }} />
              <Typography sx={{ fontSize: 12.5, fontWeight: 700, color: '#1e3a5f', minWidth: 36 }}>
                {pip.overallProgressPercent}%
              </Typography>
            </Box>
          )}

          {/* Tabs */}
          <Tabs value={tab} onChange={(_, v) => setTab(v)}
            TabIndicatorProps={{ style: { backgroundColor: '#1e3a5f', height: 2.5 } }}
            sx={{ px: 2, borderBottom: '1px solid #f1f5f9', flexShrink: 0, minHeight: 46,
              '& .MuiTab-root': { fontSize: 13, fontWeight: 600, textTransform: 'none', color: '#64748b', minHeight: 46, px: 2, '&.Mui-selected': { color: '#1e3a5f' } } }}
          >
            <Tab label="Overview" icon={<AssignmentLateIcon sx={{ fontSize: 15 }} />} iconPosition="start" />
            <Tab label={`Goals (${pip.goals?.length || 0})`} icon={<FlagIcon sx={{ fontSize: 15 }} />} iconPosition="start" />
            <Tab label={`Reviews (${pip.weeklyReviews?.length || 0})`} icon={<EventNoteIcon sx={{ fontSize: 15 }} />} iconPosition="start" />
            <Tab label={`Comments (${pip.comments?.length || 0})`} icon={<CommentIcon sx={{ fontSize: 15 }} />} iconPosition="start" />
            <Tab label="Audit Log" icon={<HistoryIcon sx={{ fontSize: 15 }} />} iconPosition="start" />
          </Tabs>

          {/* Tab content */}
          <Box sx={{ flex: 1, overflow: 'auto', p: 3 }}>

            {/* ── Overview ── */}
            {tab === 0 && (
              <Grid container spacing={3}>
                <Grid item xs={12} md={6}>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    {[
                      { label: 'Reason for PIP',        value: pip.reason,           links: false },
                      { label: 'Areas of Improvement',  value: pip.improvementAreas, links: false },
                      { label: 'Support Provided',       value: pip.supportProvided,  links: true  },
                      { label: 'Consequences',           value: pip.consequences,     links: false },
                    ].map(({ label, value, links }) => value ? (
                      <Box key={label}>
                        <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.4px', mb: 0.5 }}>{label}</Typography>
                        {links
                          ? <TextWithLinks text={value} />
                          : <Typography sx={{ fontSize: 14, color: '#374151', lineHeight: 1.6 }}>{value}</Typography>
                        }
                      </Box>
                    ) : null)}
                  </Box>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    {pip.status !== 'ACTIVE' && (pip.finalNotes || pip.outcomeDate) && (
                      <Box sx={{ p: 2, borderRadius: '12px', border: '1px solid #e2e8f0', bgcolor: STATUS_META[pip.status]?.bg || '#f8fafc' }}>
                        <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.4px', mb: 1 }}>Final Outcome</Typography>
                        <StatusChip status={pip.status} />
                        {pip.outcomeDate && <Typography sx={{ fontSize: 13, color: '#64748b', mt: 0.75 }}>Date: {fmtDate(pip.outcomeDate)}</Typography>}
                        {pip.finalNotes && <Typography sx={{ fontSize: 13.5, color: '#374151', mt: 0.75, lineHeight: 1.6 }}>{pip.finalNotes}</Typography>}
                      </Box>
                    )}
                    <Box sx={{ p: 2, borderRadius: '12px', border: '1px solid #e2e8f0', bgcolor: '#f8fafc' }}>
                      <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.4px', mb: 1.5 }}>Summary</Typography>
                      {[
                        ['Duration', `${fmtDate(pip.startDate)} → ${fmtDate(pip.endDate)}`],
                        ['Created by', pip.createdByName],
                        ['Created on', fmtDateTime(pip.createdAt)],
                        ['Goals', `${pip.totalGoals} total · ${pip.achievedGoals} achieved`],
                        ['Weekly Reviews', String(pip.weeklyReviewCount)],
                        ['Comments', String(pip.commentCount)],
                      ].map(([k, v]) => (
                        <Box key={k} sx={{ display: 'flex', gap: 2, mb: 0.75 }}>
                          <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#64748b', minWidth: 120 }}>{k}</Typography>
                          <Typography sx={{ fontSize: 13, color: '#374151' }}>{v}</Typography>
                        </Box>
                      ))}
                    </Box>
                  </Box>
                </Grid>
              </Grid>
            )}

            {/* ── Goals ── */}
            {tab === 1 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {canAct && pip.status === 'ACTIVE' && (
                  <>
                    <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <Button size="small" startIcon={<AddIcon />} variant="outlined" onClick={() => setAddGoalOpen(true)}
                        sx={{ fontWeight: 700, fontSize: 13, borderColor: '#1e3a5f', color: '#1e3a5f', borderRadius: '8px' }}>
                        Add Goal
                      </Button>
                    </Box>
                    <Collapse in={addGoalOpen}>
                      <Box sx={{ p: 2, border: '1px dashed #1e3a5f', borderRadius: '12px', bgcolor: '#f8fafc' }}>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#1e3a5f', mb: 1.5 }}>New Goal</Typography>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                          <TextField label="Goal Title *" size="small" value={goalForm.title} onChange={e => setGoalForm(f => ({ ...f, title: e.target.value }))} fullWidth />
                          <TextField label="Description" size="small" multiline rows={1} value={goalForm.description} onChange={e => setGoalForm(f => ({ ...f, description: e.target.value }))} fullWidth />
                          <TextField label="Success Criteria" size="small" value={goalForm.successCriteria} onChange={e => setGoalForm(f => ({ ...f, successCriteria: e.target.value }))} fullWidth />
                          <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-end' }}>
                            <Button size="small" onClick={() => setAddGoalOpen(false)} sx={{ color: '#64748b' }}>Cancel</Button>
                            <Button size="small" variant="contained" onClick={handleAddGoal} disabled={savingGoal}
                              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' }, fontSize: 12 }}>
                              {savingGoal ? <CircularProgress size={12} sx={{ color: '#fff', mr: 0.5 }} /> : null} Add
                            </Button>
                          </Box>
                        </Box>
                      </Box>
                    </Collapse>
                  </>
                )}
                {(pip.goals || []).length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 6, color: '#94a3b8' }}>
                    <FlagIcon sx={{ fontSize: 40, mb: 1, display: 'block', mx: 'auto', opacity: 0.4 }} />
                    <Typography fontSize={13}>No goals defined yet</Typography>
                  </Box>
                ) : (
                  (pip.goals || []).map(g => (
                    <GoalItem key={g.id} goal={g} canManage={canAct && pip.status === 'ACTIVE'} onUpdate={handleGoalUpdate} onDelete={handleGoalDelete} />
                  ))
                )}
              </Box>
            )}

            {/* ── Weekly Reviews ── */}
            {tab === 2 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                {canAct && pip.status === 'ACTIVE' && (
                  <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button size="small" startIcon={<AddIcon />} variant="outlined" onClick={() => setReviewOpen(true)}
                      sx={{ fontWeight: 700, fontSize: 13, borderColor: '#1e3a5f', color: '#1e3a5f', borderRadius: '8px' }}>
                      Add Weekly Review
                    </Button>
                  </Box>
                )}
                {(pip.weeklyReviews || []).length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 6, color: '#94a3b8' }}>
                    <EventNoteIcon sx={{ fontSize: 40, mb: 1, display: 'block', mx: 'auto', opacity: 0.4 }} />
                    <Typography fontSize={13}>No weekly reviews yet</Typography>
                  </Box>
                ) : (
                  (pip.weeklyReviews || []).map(r => (
                    <Box key={r.id} sx={{ p: 2.5, border: '1px solid #e2e8f0', borderRadius: '12px', bgcolor: '#fff' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1.5 }}>
                        <Box sx={{ width: 36, height: 36, borderRadius: '50%', bgcolor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          <Typography sx={{ fontSize: 13, fontWeight: 800, color: '#1d4ed8' }}>W{r.weekNumber}</Typography>
                        </Box>
                        <Box sx={{ flex: 1 }}>
                          <Typography sx={{ fontSize: 13.5, fontWeight: 700, color: '#1e293b' }}>Week {r.weekNumber} Review</Typography>
                          <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>{fmtDate(r.reviewDate)} · by {r.conductedByName}</Typography>
                        </Box>
                        <Box sx={{ display: 'flex', gap: 0.5 }}>
                          {[1,2,3,4,5].map(s => (
                            <Box key={s} sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: s <= r.progressRating ? '#1e3a5f' : '#e2e8f0' }} />
                          ))}
                          <Typography sx={{ fontSize: 11.5, color: '#64748b', ml: 0.5 }}>{r.progressRating}/5</Typography>
                        </Box>
                      </Box>
                      {r.overallProgress && <Box sx={{ mb: 1 }}>
                        <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.3px', mb: 0.4 }}>Overall Progress</Typography>
                        <Typography sx={{ fontSize: 13.5, color: '#374151' }}>{r.overallProgress}</Typography>
                      </Box>}
                      {r.achievements && <Box sx={{ mb: 1 }}>
                        <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#16a34a', textTransform: 'uppercase', letterSpacing: '.3px', mb: 0.4 }}>Achievements</Typography>
                        <Typography sx={{ fontSize: 13.5, color: '#374151' }}>{r.achievements}</Typography>
                      </Box>}
                      {r.challenges && <Box sx={{ mb: 1 }}>
                        <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#d97706', textTransform: 'uppercase', letterSpacing: '.3px', mb: 0.4 }}>Challenges</Typography>
                        <Typography sx={{ fontSize: 13.5, color: '#374151' }}>{r.challenges}</Typography>
                      </Box>}
                      {r.actionItems && <Box>
                        <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#6366f1', textTransform: 'uppercase', letterSpacing: '.3px', mb: 0.4 }}>Action Items</Typography>
                        <Typography sx={{ fontSize: 13.5, color: '#374151' }}>{r.actionItems}</Typography>
                      </Box>}
                    </Box>
                  ))
                )}
              </Box>
            )}

            {/* ── Comments ── */}
            {tab === 3 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {(pip.comments || []).length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 4, color: '#94a3b8' }}>
                    <CommentIcon sx={{ fontSize: 36, mb: 1, display: 'block', mx: 'auto', opacity: 0.4 }} />
                    <Typography fontSize={13}>No comments yet. Start the conversation.</Typography>
                  </Box>
                ) : (
                  (pip.comments || []).map(c => {
                    const av2 = avatarColor(c.authorName || '');
                    return (
                      <Box key={c.id} sx={{ display: 'flex', gap: 1.5 }}>
                        <Avatar sx={{ width: 34, height: 34, bgcolor: av2.bg, color: av2.color, fontSize: '0.75rem', fontWeight: 700, flexShrink: 0, mt: 0.25 }}>
                          {initials(c.authorName || '')}
                        </Avatar>
                        <Box sx={{ flex: 1 }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.4 }}>
                            <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#1e293b' }}>{c.authorName}</Typography>
                            {c.authorRole && (
                              <Chip label={c.authorRole} size="small" sx={{ height: 17, fontSize: '0.65rem', fontWeight: 600, bgcolor: '#f1f5f9', color: '#64748b' }} />
                            )}
                            <Typography sx={{ fontSize: 11.5, color: '#94a3b8', ml: 'auto' }}>{fmtDateTime(c.createdAt)}</Typography>
                          </Box>
                          <Box sx={{ bgcolor: '#f8fafc', borderRadius: '10px', px: 2, py: 1.25, border: '1px solid #f1f5f9' }}>
                            <Typography sx={{ fontSize: 13.5, color: '#374151', lineHeight: 1.6 }}>{c.content}</Typography>
                          </Box>
                        </Box>
                      </Box>
                    );
                  })
                )}
                {/* Add comment */}
                <Box sx={{ display: 'flex', gap: 1.5, mt: 1, pt: 2, borderTop: '1px solid #f1f5f9' }}>
                  <Avatar sx={{ width: 34, height: 34, bgcolor: avatarColor(myEmployee?.fullName || '').bg, color: avatarColor(myEmployee?.fullName || '').color, fontSize: '0.75rem', fontWeight: 700, flexShrink: 0, mt: 0.25 }}>
                    {initials(myEmployee?.fullName || '')}
                  </Avatar>
                  <Box sx={{ flex: 1, display: 'flex', gap: 1 }}>
                    <TextField
                      size="small" placeholder="Write a comment…" multiline maxRows={3}
                      value={comment} onChange={e => setComment(e.target.value)}
                      onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleAddComment(); } }}
                      sx={{ flex: 1, '& .MuiOutlinedInput-root': { borderRadius: '10px' } }}
                    />
                    <IconButton onClick={handleAddComment} disabled={posting || !comment.trim()}
                      sx={{ bgcolor: '#1e3a5f', color: '#fff', width: 38, height: 38, borderRadius: '10px', flexShrink: 0, '&:hover': { bgcolor: '#152d4a' }, '&.Mui-disabled': { bgcolor: '#f1f5f9', color: '#94a3b8' } }}>
                      {posting ? <CircularProgress size={14} sx={{ color: '#fff' }} /> : <SendIcon sx={{ fontSize: 16 }} />}
                    </IconButton>
                  </Box>
                </Box>
              </Box>
            )}

            {/* ── Audit Log ── */}
            {tab === 4 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
                {(pip.auditLogs || []).length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 6, color: '#94a3b8' }}>
                    <HistoryIcon sx={{ fontSize: 40, mb: 1, display: 'block', mx: 'auto', opacity: 0.4 }} />
                    <Typography fontSize={13}>No audit history</Typography>
                  </Box>
                ) : (
                  (pip.auditLogs || []).map((a, i) => (
                    <Box key={a.id} sx={{ display: 'flex', gap: 2, position: 'relative' }}>
                      {/* Timeline line */}
                      {i < pip.auditLogs.length - 1 && (
                        <Box sx={{ position: 'absolute', left: 15, top: 32, width: 2, height: 'calc(100% - 8px)', bgcolor: '#f1f5f9' }} />
                      )}
                      <Box sx={{ width: 30, height: 30, borderRadius: '50%', bgcolor: '#eff6ff', border: '2px solid #bfdbfe', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, zIndex: 1 }}>
                        <HistoryIcon sx={{ fontSize: 13, color: '#1d4ed8' }} />
                      </Box>
                      <Box sx={{ flex: 1, pb: 2.5, pt: 0.25 }}>
                        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, flexWrap: 'wrap' }}>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#1e293b' }}>{a.action?.replace(/_/g, ' ')}</Typography>
                          {a.performedByName && (
                            <Typography sx={{ fontSize: 12, color: '#64748b' }}>by {a.performedByName}</Typography>
                          )}
                          <Typography sx={{ fontSize: 11.5, color: '#94a3b8', ml: 'auto' }}>{fmtDateTime(a.createdAt)}</Typography>
                        </Box>
                        {a.details && (
                          <Typography sx={{ fontSize: 12.5, color: '#64748b', mt: 0.25, lineHeight: 1.5 }}>{a.details}</Typography>
                        )}
                      </Box>
                    </Box>
                  ))
                )}
              </Box>
            )}
          </Box>
        </Box>
      ) : null}

      {/* Add Weekly Review dialog */}
      <AddReviewDialog
        open={reviewOpen}
        onClose={() => setReviewOpen(false)}
        onAdded={r => {
          setPip(prev => ({ ...prev, weeklyReviews: [...(prev.weeklyReviews || []), r], weeklyReviewCount: (prev.weeklyReviewCount || 0) + 1 }));
          setReviewOpen(false);
          onUpdated?.();
        }}
        pipId={pipId}
        weekNumber={(pip?.weeklyReviews?.length || 0) + 1}
      />

      {/* Set Outcome dialog */}
      <OutcomeDialog
        open={outcomeOpen}
        onClose={() => setOutcomeOpen(false)}
        pip={pip}
        onSet={updated => {
          setPip(prev => ({ ...prev, ...updated }));
          setOutcomeOpen(false);
          onUpdated?.();
          toast.success('Outcome set successfully');
        }}
      />
    </Dialog>
  );
};

// ── Add Weekly Review dialog ──────────────────────────────────────────────

const BLANK_REVIEW = { overallProgress: '', progressRating: 3, achievements: '', challenges: '', actionItems: '', reviewDate: '' };

const AddReviewDialog = ({ open, onClose, onAdded, pipId, weekNumber }) => {
  const [form, setForm]     = useState({ ...BLANK_REVIEW, reviewDate: new Date().toISOString().split('T')[0] });
  const [saving, setSaving] = useState(false);

  useEffect(() => { if (open) setForm({ ...BLANK_REVIEW, reviewDate: new Date().toISOString().split('T')[0] }); }, [open]);

  const handleSave = async () => {
    if (!form.overallProgress.trim()) { toast.error('Overall progress summary is required'); return; }
    setSaving(true);
    try {
      const r = await pipApi.addReview(pipId, form);
      onAdded(r);
    } catch { toast.error('Failed to add review'); }
    finally { setSaving(false); }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: '14px' } }}>
      <DialogTitle sx={{ fontWeight: 700, fontSize: 16 }}>Week {weekNumber} Review</DialogTitle>
      <Divider />
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
        <TextField label="Review Date" type="date" size="small" value={form.reviewDate} onChange={e => setForm(f => ({ ...f, reviewDate: e.target.value }))} InputLabelProps={{ shrink: true }} sx={{ width: 200 }} />
        <Box>
          <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#374151', mb: 1 }}>Progress Rating: {form.progressRating}/5</Typography>
          <Slider value={form.progressRating} min={1} max={5} step={1} marks={[{value:1,label:'1'},{value:2,label:'2'},{value:3,label:'3'},{value:4,label:'4'},{value:5,label:'5'}]}
            onChange={(_, v) => setForm(f => ({ ...f, progressRating: v }))} sx={{ color: '#1e3a5f', width: '80%' }} />
        </Box>
        <TextField label="Overall Progress *" size="small" multiline rows={2} value={form.overallProgress} onChange={e => setForm(f => ({ ...f, overallProgress: e.target.value }))} fullWidth placeholder="Summarise progress this week" />
        <TextField label="Achievements" size="small" multiline rows={2} value={form.achievements} onChange={e => setForm(f => ({ ...f, achievements: e.target.value }))} fullWidth placeholder="What was accomplished?" />
        <TextField label="Challenges" size="small" multiline rows={2} value={form.challenges} onChange={e => setForm(f => ({ ...f, challenges: e.target.value }))} fullWidth placeholder="Any blockers or issues?" />
        <TextField label="Action Items" size="small" multiline rows={2} value={form.actionItems} onChange={e => setForm(f => ({ ...f, actionItems: e.target.value }))} fullWidth placeholder="Next steps / follow-up actions" />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} sx={{ color: '#64748b', fontWeight: 600 }}>Cancel</Button>
        <Button variant="contained" onClick={handleSave} disabled={saving}
          sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' }, fontWeight: 700 }}>
          {saving ? <CircularProgress size={16} sx={{ color: '#fff', mr: 1 }} /> : null} Save Review
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Set Outcome dialog ────────────────────────────────────────────────────

const OutcomeDialog = ({ open, onClose, pip, onSet }) => {
  const [form, setForm]     = useState({ status: 'COMPLETED', finalNotes: '', outcomeDate: new Date().toISOString().split('T')[0], newEndDate: '' });
  const [saving, setSaving] = useState(false);

  useEffect(() => { if (open) setForm({ status: 'COMPLETED', finalNotes: '', outcomeDate: new Date().toISOString().split('T')[0], newEndDate: '' }); }, [open]);

  const handleSet = async () => {
    if (!form.status) return;
    if (form.status === 'EXTENDED' && !form.newEndDate) { toast.error('New end date is required for extension'); return; }
    setSaving(true);
    try {
      const updated = await pipApi.setOutcome(pip.id, form);
      onSet(updated);
    } catch (err) { toast.error(err?.response?.data?.message || 'Failed to set outcome'); }
    finally { setSaving(false); }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: '14px' } }}>
      <DialogTitle sx={{ fontWeight: 700, fontSize: 16 }}>Set PIP Outcome</DialogTitle>
      <Divider />
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
        <Alert severity="info" sx={{ fontSize: 13 }}>This action will close the PIP and notify the employee via email.</Alert>
        <FormControl size="small" fullWidth>
          <InputLabel>Outcome</InputLabel>
          <Select value={form.status} label="Outcome" onChange={e => setForm(f => ({ ...f, status: e.target.value }))}>
            <MenuItem value="COMPLETED">
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <CheckCircleIcon sx={{ fontSize: 16, color: '#16a34a' }} /> Completed — employee met all requirements
              </Box>
            </MenuItem>
            <MenuItem value="EXTENDED">
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <EventNoteIcon sx={{ fontSize: 16, color: '#d97706' }} /> Extended — more time granted
              </Box>
            </MenuItem>
            <MenuItem value="TERMINATED">
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <WarningAmberIcon sx={{ fontSize: 16, color: '#dc2626' }} /> Terminated — plan closed without completion
              </Box>
            </MenuItem>
          </Select>
        </FormControl>
        {form.status === 'EXTENDED' && (
          <TextField label="New End Date *" type="date" size="small" value={form.newEndDate} onChange={e => setForm(f => ({ ...f, newEndDate: e.target.value }))} InputLabelProps={{ shrink: true }} fullWidth />
        )}
        <TextField label="Outcome Date" type="date" size="small" value={form.outcomeDate} onChange={e => setForm(f => ({ ...f, outcomeDate: e.target.value }))} InputLabelProps={{ shrink: true }} fullWidth />
        <TextField label="Final Notes" size="small" multiline rows={3} value={form.finalNotes} onChange={e => setForm(f => ({ ...f, finalNotes: e.target.value }))} fullWidth placeholder="Summary of final outcome, key observations, next steps…" />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} sx={{ color: '#64748b', fontWeight: 600 }}>Cancel</Button>
        <Button variant="contained" onClick={handleSet} disabled={saving}
          sx={{ bgcolor: form.status === 'TERMINATED' ? '#dc2626' : '#1e3a5f', '&:hover': { bgcolor: form.status === 'TERMINATED' ? '#b91c1c' : '#152d4a' }, fontWeight: 700 }}>
          {saving ? <CircularProgress size={16} sx={{ color: '#fff', mr: 1 }} /> : null}
          Confirm Outcome
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Main PIP tab ──────────────────────────────────────────────────────────

const PerformancePipTab = ({ myEmployee }) => {
  const { user } = useSelector(s => s.auth);
  const isAdmin   = user?.role === 'ADMIN' || user?.role === 'DIRECTOR';
  const isHR      = user?.role === 'HR';
  const isManager = user?.role === 'MANAGER' || user?.role === 'ASSISTANT_MANAGER';
  const isEmp     = !isAdmin && !isHR && !isManager;
  const canManage = isAdmin || isHR || isManager;

  const [pips, setPips]           = useState([]);
  const [loading, setLoading]     = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [detailPipId, setDetailPipId] = useState(null);
  const [search, setSearch]       = useState('');
  const [statusFlt, setStatusFlt] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await pipApi.getAll();
      setPips(Array.isArray(data) ? data : []);
    } catch { toast.error('Failed to load improvement plans'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const stats = useMemo(() => ({
    active:     pips.filter(p => p.status === 'ACTIVE').length,
    overdue:    pips.filter(p => p.overdue && p.status === 'ACTIVE').length,
    completed:  pips.filter(p => p.status === 'COMPLETED').length,
    total:      pips.length,
  }), [pips]);

  const filtered = useMemo(() =>
    pips.filter(p => {
      if (statusFlt && p.status !== statusFlt) return false;
      if (search) {
        const q = search.toLowerCase();
        return (p.employeeName || '').toLowerCase().includes(q) ||
               (p.title || '').toLowerCase().includes(q) ||
               (p.employeeDepartment || '').toLowerCase().includes(q);
      }
      return true;
    }),
  [pips, search, statusFlt]);

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', py: 10 }}>
      <CircularProgress sx={{ color: '#1e3a5f' }} />
    </Box>
  );

  return (
    <Box>
      {/* Stats row */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {[
          { label: 'Active PIPs',  value: stats.active,    color: '#1d4ed8', bg: '#eff6ff', icon: <TrackChangesIcon /> },
          { label: 'Overdue',      value: stats.overdue,   color: '#dc2626', bg: '#fef2f2', icon: <WarningAmberIcon /> },
          { label: 'Completed',    value: stats.completed, color: '#16a34a', bg: '#f0fdf4', icon: <CheckCircleIcon /> },
          { label: 'Total PIPs',   value: stats.total,     color: '#6366f1', bg: '#eef2ff', icon: <AssignmentLateIcon /> },
        ].map(s => (
          <Grid item xs={6} md={3} key={s.label}>
            <Box sx={{ bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px', p: 2.5, display: 'flex', alignItems: 'center', gap: 2, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
              <Box sx={{ width: 46, height: 46, borderRadius: '12px', bgcolor: s.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                {React.cloneElement(s.icon, { sx: { fontSize: 22, color: s.color } })}
              </Box>
              <Box>
                <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.5px' }}>{s.label}</Typography>
                <Typography sx={{ fontSize: 26, fontWeight: 800, color: '#0f172a', lineHeight: 1.1 }}>{s.value}</Typography>
              </Box>
            </Box>
          </Grid>
        ))}
      </Grid>

      {/* Filter bar */}
      <Box sx={{ bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px', p: 1.75, mb: 3,
        display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}>
        <OutlinedInput
          placeholder={isEmp ? 'Search your plans…' : 'Search by employee or plan title…'}
          value={search} onChange={e => setSearch(e.target.value)}
          startAdornment={<InputAdornment position="start"><SearchIcon sx={{ fontSize: 18, color: '#94a3b8' }} /></InputAdornment>}
          sx={{ height: 40, fontSize: 13.5, borderRadius: '10px', minWidth: 220, flex: '1 1 180px', '& fieldset': { borderColor: '#e2e8f0' } }}
        />
        <Select value={statusFlt} onChange={e => setStatusFlt(e.target.value)} displayEmpty renderValue={v => v || 'All Statuses'}
          sx={{ height: 40, fontSize: 13.5, borderRadius: '10px', minWidth: 145, '& fieldset': { borderColor: '#e2e8f0' } }}>
          <MenuItem value="">All Statuses</MenuItem>
          {Object.entries(STATUS_META).map(([k, m]) => (
            <MenuItem key={k} value={k} sx={{ fontSize: 13.5 }}>{m.label}</MenuItem>
          ))}
        </Select>
        {(search || statusFlt) && (
          <Button onClick={() => { setSearch(''); setStatusFlt(''); }} sx={{ height: 40, color: '#64748b', fontSize: 13, fontWeight: 600 }}>Clear</Button>
        )}
        {canManage && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}
            sx={{ height: 40, ml: 'auto', bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' }, fontWeight: 700, fontSize: 13.5, borderRadius: '10px', boxShadow: '0 4px 12px rgba(30,58,95,.3)' }}>
            Create PIP
          </Button>
        )}
      </Box>

      {/* PIP list */}
      {filtered.length === 0 ? (
        <Box sx={{ bgcolor: '#fff', border: '1px dashed #e2e8f0', borderRadius: '14px', py: 10, textAlign: 'center' }}>
          <AssignmentLateIcon sx={{ fontSize: 52, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1.5 }} />
          <Typography sx={{ color: '#94a3b8', fontSize: 15, fontWeight: 600 }}>
            {pips.length === 0 ? (isEmp ? 'No improvement plans assigned to you' : 'No PIPs created yet') : 'No PIPs match your filters'}
          </Typography>
          {canManage && pips.length === 0 && (
            <Button variant="contained" onClick={() => setCreateOpen(true)} sx={{ mt: 2, bgcolor: '#1e3a5f', fontWeight: 700 }}>
              Create First PIP
            </Button>
          )}
        </Box>
      ) : (
        <Grid container spacing={2.5}>
          {filtered.map(pip => (
            <Grid item xs={12} md={6} lg={4} key={pip.id}>
              <PipCard
                pip={pip}
                canManage={canManage}
                onClick={() => setDetailPipId(pip.id)}
              />
            </Grid>
          ))}
        </Grid>
      )}

      {/* Dialogs */}
      <CreatePipDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={newPip => { setPips(prev => [newPip, ...prev]); }}
        myEmployee={myEmployee}
        isAdmin={isAdmin}
        isHR={isHR}
      />

      <PipDetailDialog
        open={Boolean(detailPipId)}
        pipId={detailPipId}
        onClose={() => setDetailPipId(null)}
        onUpdated={load}
        myEmployee={myEmployee}
        canManage={canManage}
        isAdmin={isAdmin}
      />
    </Box>
  );
};

export default PerformancePipTab;
