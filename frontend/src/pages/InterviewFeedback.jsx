import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import {
  Box, Typography, Button, Grid, Card, CardContent, Avatar, Chip,
  Divider, Slider, TextField, CircularProgress, Rating,
} from '@mui/material';
import ArrowBackIcon      from '@mui/icons-material/ArrowBack';
import CheckCircleIcon    from '@mui/icons-material/CheckCircle';
import PauseCircleIcon    from '@mui/icons-material/PauseCircle';
import CancelIcon         from '@mui/icons-material/Cancel';
import DoneAllIcon        from '@mui/icons-material/DoneAll';
import BlockIcon          from '@mui/icons-material/Block';
import StarIcon           from '@mui/icons-material/Star';
import AttachFileIcon     from '@mui/icons-material/AttachFile';
import CalendarMonthIcon  from '@mui/icons-material/CalendarMonth';
import { interviewApi }  from '../api/interviewApi';
import { toast }         from 'react-toastify';

const fmtDt = (v) => v ? new Date(v).toLocaleString('en-US', {
  day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
}) : '—';

const ROUND_TYPE_COLOR = {
  TECHNICAL:    '#3b82f6',
  HR:           '#10b981',
  MANAGERIAL:   '#8b5cf6',
  CULTURAL_FIT: '#f59e0b',
  FINAL:        '#ef4444',
};

const RECOMMENDATION_OPTIONS = [
  { value: 'HIRE',          label: 'Move to Next Round', desc: 'Proceed to the next interview round', icon: <DoneAllIcon />,     color: '#16a34a', bg: '#f0fdf4', border: '#86efac' },
  { value: 'STRONG_HIRE',   label: 'Hire',               desc: 'Strong fit for the role',             icon: <CheckCircleIcon />, color: '#1d4ed8', bg: '#eff6ff', border: '#93c5fd' },
  { value: 'MAYBE',         label: 'Hold',               desc: 'Hold for further evaluation',         icon: <PauseCircleIcon />, color: '#d97706', bg: '#fffbeb', border: '#fcd34d' },
  { value: 'NO_HIRE',       label: 'Reject',             desc: 'Not a fit for the role',              icon: <CancelIcon />,      color: '#dc2626', bg: '#fef2f2', border: '#fca5a5' },
  { value: 'STRONG_NO_HIRE',label: 'Strong No Hire',     desc: 'Definite rejection for this role',    icon: <BlockIcon />,       color: '#7f1d1d', bg: '#fff1f2', border: '#fecdd3' },
];

const SKILLS = [
  { key: 'technicalRating',      label: 'Technical Knowledge' },
  { key: 'problemSolvingRating', label: 'Problem Solving' },
  { key: 'communicationRating',  label: 'Communication' },
  { key: 'cultureFitRating',     label: 'Culture Fit' },
];

/* ── Toggle button group (No / Maybe / Yes style) ── */
const ToggleGroup = ({ value, onChange, options }) => (
  <Box sx={{ display: 'flex' }}>
    {options.map((opt, i) => {
      const selected = value === opt.value;
      return (
        <Button
          key={opt.value}
          onClick={() => onChange(opt.value === value ? '' : opt.value)}
          size="small"
          sx={{
            minWidth: 74,
            fontWeight: 600,
            fontSize: 13,
            borderRadius: i === 0 ? '6px 0 0 6px' : i === options.length - 1 ? '0 6px 6px 0' : 0,
            border: '1px solid',
            borderRight: i < options.length - 1 ? 'none' : '1px solid',
            borderColor: selected ? opt.activeColor : '#e2e8f0',
            bgcolor: selected ? opt.activeColor : 'white',
            color: selected ? '#fff' : '#64748b',
            textTransform: 'none',
            '&:hover': {
              bgcolor: selected ? opt.activeColor : '#f8fafc',
              borderColor: opt.activeColor,
              borderRight: i < options.length - 1 ? 'none' : `1px solid ${opt.activeColor}`,
            },
          }}
        >
          {opt.label}
        </Button>
      );
    })}
  </Box>
);

const EMPTY_FORM = {
  projectFit: '',
  suspectedCheating: '',
  overallRating: 4,
  technicalRating: 3,
  problemSolvingRating: 3,
  communicationRating: 3,
  cultureFitRating: 3,
  strengths: '',
  weaknesses: '',
  comments: '',
  recommendation: '',
};

/* ═══════════════════════════════════════════════════════════════════════════ */
const InterviewFeedbackPage = () => {
  const { roundId }   = useParams();
  const navigate      = useNavigate();
  const location      = useLocation();

  const [round, setRound]           = useState(location.state?.round || null);
  const [loadingRound, setLoadingRound] = useState(!location.state?.round);
  const existingFeedback = location.state?.existingFeedback;
  const isEdit           = !!existingFeedback;

  useEffect(() => {
    if (!location.state?.round && roundId) {
      interviewApi.getRound(Number(roundId))
        .then(data => setRound(data))
        .catch(() => {})
        .finally(() => setLoadingRound(false));
    }
  }, [roundId]); // eslint-disable-line react-hooks/exhaustive-deps

  const [form, setForm] = useState(() => {
    if (existingFeedback) {
      return {
        projectFit:           existingFeedback.projectFit        || '',
        suspectedCheating:    existingFeedback.suspectedCheating || '',
        overallRating:        existingFeedback.overallRating        ?? 4,
        technicalRating:      existingFeedback.technicalRating      ?? 3,
        problemSolvingRating: existingFeedback.problemSolvingRating ?? 3,
        communicationRating:  existingFeedback.communicationRating  ?? 3,
        cultureFitRating:     existingFeedback.cultureFitRating     ?? 3,
        strengths:            existingFeedback.strengths   || '',
        weaknesses:           existingFeedback.weaknesses  || '',
        comments:             existingFeedback.comments    || '',
        recommendation:       existingFeedback.recommendation || '',
      };
    }
    return EMPTY_FORM;
  });

  const [saving, setSaving] = useState(false);

  const setField = (key, val) => setForm(f => ({ ...f, [key]: val }));

  if (loadingRound) {
    return (
      <Box sx={{ p: 4, textAlign: 'center' }}>
        <CircularProgress sx={{ color: '#1e3a5f' }} />
      </Box>
    );
  }

  if (!round) {
    return (
      <Box sx={{ p: 4, textAlign: 'center' }}>
        <CalendarMonthIcon sx={{ fontSize: 56, color: '#cbd5e1', mb: 2 }} />
        <Typography color="text.secondary" mb={2}>Round data not found.</Typography>
        <Button variant="contained" onClick={() => navigate('/interviews')}
          sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none' }}>
          Back to Interviews
        </Button>
      </Box>
    );
  }

  const typeColor  = ROUND_TYPE_COLOR[round.roundType] || '#64748b';
  const roundLabel = (round.roundType?.replace('_', ' ') || '') + ' Round';
  const statusLabel = round.status === 'COMPLETED' ? 'Completed' : round.status === 'IN_PROGRESS' ? 'In Progress' : (round.status || '');

  const handleSubmit = async () => {
    if (!form.recommendation) { toast.error('Please select a recommendation'); return; }
    setSaving(true);
    try {
      if (isEdit) {
        await interviewApi.updateFeedback(existingFeedback.id, form);
        toast.success('Feedback updated');
      } else {
        await interviewApi.submitFeedback(Number(roundId), form);
        toast.success('Feedback submitted — manager notified');
      }
      navigate('/interviews');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit feedback');
    } finally {
      setSaving(false);
    }
  };

  /* ── render ── */
  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh' }}>

      {/* ── Sticky header ── */}
      <Box sx={{
        bgcolor: '#fff', borderBottom: '1px solid #e2e8f0', px: 3, py: 2,
        display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', position: 'sticky', top: 0, zIndex: 10,
      }}>
        {/* Status badge */}
        <Box sx={{
          width: 40, height: 40, borderRadius: '50%', flexShrink: 0,
          bgcolor: round.feedbackSubmitted ? '#dcfce7' : '#fef3c7',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {round.feedbackSubmitted
            ? <CheckCircleIcon sx={{ color: '#16a34a', fontSize: 22 }} />
            : <StarIcon sx={{ color: '#d97706', fontSize: 22 }} />
          }
        </Box>

        {/* Title + meta */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
            <Typography variant="h6" fontWeight={700}>
              {roundLabel} ({statusLabel})
            </Typography>
            <Typography variant="body1" sx={{ color: '#6366f1', fontWeight: 600 }}>
              {round.candidateName}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 0.25, flexWrap: 'wrap' }}>
            {round.scheduledAt && (
              <Typography variant="caption" color="text.secondary">
                {fmtDt(round.scheduledAt)} · {round.durationMinutes} min
              </Typography>
            )}
            {round.location && (
              <Typography variant="caption" color="text.secondary">· {round.location}</Typography>
            )}
          </Box>
        </Box>

        {/* Actions */}
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" size="small" startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/interviews')}
            sx={{ textTransform: 'none', borderColor: '#e2e8f0', color: '#64748b' }}>
            Back
          </Button>
          <Button variant="contained" size="small" onClick={handleSubmit} disabled={saving}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', px: 2.5 }}>
            {saving ? <CircularProgress size={14} color="inherit" sx={{ mr: 0.75 }} /> : null}
            {isEdit ? 'Update' : 'Submit Feedback'}
          </Button>
        </Box>
      </Box>

      {/* ── Body ── */}
      <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1200, mx: 'auto' }}>
        <Grid container spacing={3}>

          {/* ══════════════════════════════════════════ LEFT COLUMN ══ */}
          <Grid item xs={12} md={8}>

            {/* 1. Interview Outcome */}
            <Card sx={{ mb: 2.5, borderRadius: 3 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="subtitle1" fontWeight={700} mb={2.5}>1. Interview Outcome</Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
                    <Typography variant="body2" fontWeight={500}>Project fit</Typography>
                    <ToggleGroup
                      value={form.projectFit}
                      onChange={(v) => setField('projectFit', v)}
                      options={[
                        { value: 'NO',    label: 'No',    activeColor: '#dc2626' },
                        { value: 'MAYBE', label: 'Maybe', activeColor: '#d97706' },
                        { value: 'YES',   label: 'Yes',   activeColor: '#16a34a' },
                      ]}
                    />
                  </Box>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
                    <Typography variant="body2" fontWeight={500}>Suspected in cheating</Typography>
                    <ToggleGroup
                      value={form.suspectedCheating}
                      onChange={(v) => setField('suspectedCheating', v)}
                      options={[
                        { value: 'YES',   label: 'Yes',   activeColor: '#dc2626' },
                        { value: 'MAYBE', label: 'Maybe', activeColor: '#d97706' },
                        { value: 'NO',    label: 'No',    activeColor: '#16a34a' },
                      ]}
                    />
                  </Box>
                </Box>
              </CardContent>
            </Card>

            {/* 2. Overall Rating */}
            <Card sx={{ mb: 2.5, borderRadius: 3 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="subtitle1" fontWeight={700} mb={2}>2. Overall Rating</Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                  <Rating
                    value={form.overallRating || 0}
                    onChange={(_, v) => setField('overallRating', v)}
                    size="large"
                    precision={1}
                    sx={{ fontSize: 40 }}
                  />
                  <Box sx={{
                    px: 2, py: 0.75, bgcolor: '#fef3c7', borderRadius: 2, border: '1px solid #fcd34d',
                  }}>
                    <Typography variant="body1" fontWeight={800} sx={{ color: '#92400e' }}>
                      {form.overallRating || 0}.0 / 5
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>

            {/* 3. Key Skills Evaluation */}
            <Card sx={{ mb: 2.5, borderRadius: 3 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="subtitle1" fontWeight={700} mb={3}>3. Key Skills Evaluation</Typography>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                  {SKILLS.map(({ key, label }) => (
                    <Box key={key}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.75 }}>
                        <Typography variant="body2" fontWeight={500} color="text.primary">{label}</Typography>
                        <Typography variant="body2" fontWeight={700} sx={{ color: '#1e3a5f', minWidth: 30, textAlign: 'right' }}>
                          {form[key]} / 5
                        </Typography>
                      </Box>
                      <Slider
                        value={form[key] || 1}
                        onChange={(_, v) => setField(key, v)}
                        min={1} max={5} step={1}
                        marks
                        sx={{
                          color: '#1e3a5f',
                          height: 6,
                          '& .MuiSlider-thumb': { width: 18, height: 18, '&:hover': { boxShadow: '0 0 0 8px rgba(30,58,95,0.16)' } },
                          '& .MuiSlider-rail': { bgcolor: '#e2e8f0', opacity: 1 },
                          '& .MuiSlider-mark': { bgcolor: '#cbd5e1', width: 4, height: 4, borderRadius: '50%' },
                          '& .MuiSlider-markActive': { bgcolor: '#fff', opacity: 1 },
                        }}
                      />
                    </Box>
                  ))}
                </Box>
              </CardContent>
            </Card>

            {/* 4. Detailed Feedback */}
            <Card sx={{ mb: 2.5, borderRadius: 3 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="subtitle1" fontWeight={700} mb={2}>4. Detailed Feedback</Typography>
                <Grid container spacing={2}>
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ p: 2.5, bgcolor: '#f0fdf4', borderRadius: 2, border: '1px solid #bbf7d0' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                        <Box sx={{
                          width: 22, height: 22, borderRadius: '50%', bgcolor: '#16a34a',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                          <Typography sx={{ color: '#fff', fontSize: 11, fontWeight: 800, lineHeight: 1 }}>S</Typography>
                        </Box>
                        <Typography variant="body2" fontWeight={700} sx={{ color: '#15803d' }}>Strengths</Typography>
                      </Box>
                      <TextField
                        multiline minRows={3}
                        placeholder="• Strong in core concepts&#10;• Clear communication&#10;• Good problem-solving approach"
                        value={form.strengths}
                        onChange={e => setField('strengths', e.target.value)}
                        fullWidth
                        size="small"
                        sx={{ '& .MuiOutlinedInput-root': { bgcolor: '#fff', borderRadius: 1.5 } }}
                      />
                    </Box>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ p: 2.5, bgcolor: '#fff7ed', borderRadius: 2, border: '1px solid #fed7aa' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                        <Box sx={{
                          width: 22, height: 22, borderRadius: '50%', bgcolor: '#d97706',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                          <Typography sx={{ color: '#fff', fontSize: 11, fontWeight: 800, lineHeight: 1 }}>D</Typography>
                        </Box>
                        <Typography variant="body2" fontWeight={700} sx={{ color: '#c2410c' }}>Areas of Improvement</Typography>
                      </Box>
                      <TextField
                        multiline minRows={3}
                        placeholder="• Can improve written communication&#10;• Needs more cloud services exposure"
                        value={form.weaknesses}
                        onChange={e => setField('weaknesses', e.target.value)}
                        fullWidth
                        size="small"
                        sx={{ '& .MuiOutlinedInput-root': { bgcolor: '#fff', borderRadius: 1.5 } }}
                      />
                    </Box>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>

            {/* 5. Your Comments */}
            <Card sx={{ mb: 2.5, borderRadius: 3 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="subtitle1" fontWeight={700} mb={2}>5. Your Comments</Typography>
                <TextField
                  multiline minRows={4}
                  placeholder="Candidate has solid skills and good potential. With better communication, can be a great asset to the team."
                  value={form.comments}
                  onChange={e => setField('comments', e.target.value.slice(0, 2000))}
                  fullWidth
                  inputProps={{ maxLength: 2000 }}
                  helperText={`${form.comments.length} / 2000`}
                  FormHelperTextProps={{ sx: { textAlign: 'right', mr: 0 } }}
                />
              </CardContent>
            </Card>

            {/* 6. Recommendation */}
            <Card sx={{ mb: 2.5, borderRadius: 3 }}>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="subtitle1" fontWeight={700} mb={2}>6. Recommendation</Typography>
                <Grid container spacing={1.5}>
                  {RECOMMENDATION_OPTIONS.map((opt) => {
                    const isSelected = form.recommendation === opt.value;
                    return (
                      <Grid item xs={12} sm={6} md={3} key={opt.value}>
                        <Box
                          onClick={() => setField('recommendation', isSelected ? '' : opt.value)}
                          sx={{
                            p: 2.5, borderRadius: 2.5, cursor: 'pointer', textAlign: 'center',
                            border: '2px solid',
                            borderColor: isSelected ? opt.color : '#e2e8f0',
                            bgcolor: isSelected ? opt.bg : 'white',
                            transition: 'all 0.15s ease',
                            '&:hover': { borderColor: opt.color, bgcolor: opt.bg },
                          }}
                        >
                          <Box sx={{
                            width: 44, height: 44, borderRadius: '50%',
                            bgcolor: isSelected ? opt.color : '#f1f5f9',
                            display: 'flex', alignItems: 'center', justifyContent: 'center', mx: 'auto', mb: 1.25,
                            '& svg': { color: isSelected ? '#fff' : '#94a3b8', fontSize: 22 },
                          }}>
                            {opt.icon}
                          </Box>
                          <Typography variant="body2" fontWeight={700}
                            sx={{ color: isSelected ? opt.color : '#1e293b', mb: 0.5 }}>
                            {opt.label}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" display="block" sx={{ lineHeight: 1.4 }}>
                            {opt.desc}
                          </Typography>
                        </Box>
                      </Grid>
                    );
                  })}
                </Grid>
              </CardContent>
            </Card>

            {/* Add Attachments (visual) */}
            <Card sx={{ mb: 3, borderRadius: 3 }}>
              <CardContent sx={{ p: 2.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                    <Box sx={{ width: 38, height: 38, bgcolor: '#f1f5f9', borderRadius: 2,
                      display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <AttachFileIcon sx={{ fontSize: 18, color: '#64748b' }} />
                    </Box>
                    <Box>
                      <Typography variant="body2" fontWeight={600}>Add Attachments</Typography>
                      <Typography variant="caption" color="text.secondary">Upload files (max 10MB each)</Typography>
                    </Box>
                  </Box>
                  <Typography variant="caption" color="text.disabled">Supported: PDF, DOC, DOCX, JPG, PNG</Typography>
                </Box>
              </CardContent>
            </Card>

            {/* Footer buttons */}
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1.5 }}>
              <Button variant="outlined" onClick={() => navigate('/interviews')}
                sx={{ textTransform: 'none', borderColor: '#e2e8f0', color: '#64748b' }}>
                Cancel
              </Button>
              <Button variant="contained" onClick={handleSubmit} disabled={saving}
                sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', px: 3 }}>
                {saving && <CircularProgress size={14} color="inherit" sx={{ mr: 0.75 }} />}
                {isEdit ? 'Update Feedback' : 'Submit Feedback'}
              </Button>
            </Box>
          </Grid>

          {/* ══════════════════════════════════════════ RIGHT SIDEBAR ══ */}
          <Grid item xs={12} md={4}>

            {/* Candidate Details */}
            <Card sx={{ mb: 2.5, borderRadius: 3 }}>
              <CardContent sx={{ p: 2.5 }}>
                <Typography variant="caption" fontWeight={700} color="text.secondary"
                  sx={{ textTransform: 'uppercase', letterSpacing: 0.7, fontSize: 10.5, display: 'block', mb: 2 }}>
                  Candidate Details
                </Typography>

                <Box sx={{ display: 'flex', gap: 1.75, mb: 2 }}>
                  <Avatar sx={{ bgcolor: '#1e3a5f', width: 52, height: 52, fontSize: 20, flexShrink: 0 }}>
                    {round.candidateName?.charAt(0).toUpperCase()}
                  </Avatar>
                  <Box>
                    <Typography variant="body1" fontWeight={700}>{round.candidateName}</Typography>
                    <Typography variant="body2" color="text.secondary">{round.candidatePosition}</Typography>
                  </Box>
                </Box>

                <Divider sx={{ mb: 2 }} />

                {[
                  ['Interview Round',  round.roundType?.replace('_', ' ')],
                  ['Interviewer',      round.interviewerName],
                  ['Interview Date',   fmtDt(round.scheduledAt)],
                  ['Duration',         round.durationMinutes ? `${round.durationMinutes} min` : '—'],
                  ['Mode',             round.location || 'Not specified'],
                ].map(([label, val]) => (
                  <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1.25 }}>
                    <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ whiteSpace: 'nowrap', mr: 1 }}>
                      {label}
                    </Typography>
                    <Typography variant="caption" fontWeight={500} sx={{ textAlign: 'right', color: '#1e293b' }}>
                      {val || '—'}
                    </Typography>
                  </Box>
                ))}

                {/* Round type chip */}
                <Divider sx={{ mt: 1, mb: 1.5 }} />
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>Round Type</Typography>
                  <Chip
                    label={round.roundType?.replace('_', ' ')}
                    size="small"
                    sx={{
                      bgcolor: (ROUND_TYPE_COLOR[round.roundType] || '#64748b') + '22',
                      color: ROUND_TYPE_COLOR[round.roundType] || '#64748b',
                      fontWeight: 700, fontSize: 11,
                    }}
                  />
                </Box>
              </CardContent>
            </Card>

            {/* Manager Notes */}
            {round.managerNotes && (
              <Card sx={{ mb: 2.5, borderRadius: 3, border: '1px solid #fcd34d' }}>
                <CardContent sx={{ p: 2.5, bgcolor: '#fffbeb' }}>
                  <Typography variant="subtitle2" fontWeight={700} sx={{ color: '#92400e', mb: 1 }}>
                    Notes from Manager
                  </Typography>
                  <Typography variant="body2" sx={{ color: '#78350f', lineHeight: 1.6 }}>
                    {round.managerNotes}
                  </Typography>
                </CardContent>
              </Card>
            )}

            {/* Assigned by */}
            <Card sx={{ borderRadius: 3 }}>
              <CardContent sx={{ p: 2.5 }}>
                <Typography variant="caption" fontWeight={700} color="text.secondary"
                  sx={{ textTransform: 'uppercase', letterSpacing: 0.7, fontSize: 10.5, display: 'block', mb: 2 }}>
                  Round Info
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.25 }}>
                  <Box sx={{
                    width: 10, height: 10, borderRadius: '50%', flexShrink: 0,
                    bgcolor: round.status === 'IN_PROGRESS' ? '#f59e0b'
                           : round.status === 'COMPLETED'   ? '#16a34a' : '#3b82f6',
                  }} />
                  <Typography variant="body2" fontWeight={600}>
                    {round.status?.replace('_', ' ') || '—'}
                  </Typography>
                </Box>
                {round.assignedByName && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    Assigned by: <strong>{round.assignedByName}</strong>
                  </Typography>
                )}
                {round.roundNumber && (
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                    Round #{round.roundNumber}
                  </Typography>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Box>
    </Box>
  );
};

export default InterviewFeedbackPage;
