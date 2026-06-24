import React, { useEffect, useState, useMemo } from 'react';
import { useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import {
  Box, Card, CardContent, Typography, Button, Chip, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, CircularProgress, IconButton,
  Tooltip, Divider, Tabs, Tab, Table, TableHead, TableBody, TableRow,
  TableCell, TableContainer, TablePagination, Stack, InputAdornment,
  MenuItem, Grid, Rating, LinearProgress, Collapse, Avatar,
  Radio, RadioGroup, FormControlLabel, FormLabel, FormControl
} from '@mui/material';
import AddIcon              from '@mui/icons-material/Add';
import SearchIcon           from '@mui/icons-material/Search';
import ArrowBackIcon        from '@mui/icons-material/ArrowBack';
import EditIcon             from '@mui/icons-material/Edit';
import DeleteIcon           from '@mui/icons-material/Delete';
import PersonAddIcon        from '@mui/icons-material/PersonAdd';
import CheckCircleIcon      from '@mui/icons-material/CheckCircle';
import CancelIcon           from '@mui/icons-material/Cancel';
import PauseCircleIcon      from '@mui/icons-material/PauseCircle';
import ExpandMoreIcon       from '@mui/icons-material/ExpandMore';
import ExpandLessIcon       from '@mui/icons-material/ExpandLess';
import WorkIcon             from '@mui/icons-material/Work';
import CalendarMonthIcon    from '@mui/icons-material/CalendarMonth';
import LocationOnIcon       from '@mui/icons-material/LocationOn';
import AccessTimeIcon       from '@mui/icons-material/AccessTime';
import StarIcon             from '@mui/icons-material/Star';
import AssessmentIcon       from '@mui/icons-material/Assessment';
import HistoryIcon          from '@mui/icons-material/History';
import PlayArrowIcon        from '@mui/icons-material/PlayArrow';
import RefreshIcon          from '@mui/icons-material/Refresh';
import NotificationsIcon    from '@mui/icons-material/Notifications';
import DownloadIcon         from '@mui/icons-material/Download';
import TodayIcon            from '@mui/icons-material/Today';
import Badge                from '@mui/material/Badge';
import { interviewApi }  from '../api/interviewApi';
import { employeeApi }   from '../api/employeeApi';
import { toast }         from 'react-toastify';

/* ── colour maps ─────────────────────────────────────────────────────────── */
const STATUS_COLOR = {
  PENDING:     { bg: '#f1f5f9', color: '#475569', label: 'Pending' },
  IN_PROGRESS: { bg: '#dbeafe', color: '#1d4ed8', label: 'In Progress' },
  SELECTED:    { bg: '#dcfce7', color: '#16a34a', label: 'Selected' },
  REJECTED:    { bg: '#fee2e2', color: '#dc2626', label: 'Rejected' },
  ON_HOLD:     { bg: '#fff7ed', color: '#c2410c', label: 'On Hold' },
  WITHDRAWN:   { bg: '#f3f4f6', color: '#6b7280', label: 'Withdrawn' },
};
const ROUND_STATUS_COLOR = {
  SCHEDULED:   { bg: '#dbeafe', color: '#1d4ed8' },
  IN_PROGRESS: { bg: '#fef3c7', color: '#d97706' },
  COMPLETED:   { bg: '#dcfce7', color: '#16a34a' },
  CANCELLED:   { bg: '#fee2e2', color: '#dc2626' },
  RESCHEDULED: { bg: '#f3e8ff', color: '#7c3aed' },
};
const ROUND_TYPE_COLOR = {
  TECHNICAL:    '#3b82f6',
  HR:           '#10b981',
  MANAGERIAL:   '#8b5cf6',
  CULTURAL_FIT: '#f59e0b',
  FINAL:        '#ef4444',
};
const REC_COLOR = {
  STRONG_HIRE:    { bg: '#dcfce7', color: '#16a34a' },
  HIRE:           { bg: '#d1fae5', color: '#059669' },
  MAYBE:          { bg: '#fff7ed', color: '#d97706' },
  NO_HIRE:        { bg: '#fee2e2', color: '#dc2626' },
  STRONG_NO_HIRE: { bg: '#fecaca', color: '#991b1b' },
};

const ROUND_TYPES  = ['TECHNICAL', 'HR', 'MANAGERIAL', 'CULTURAL_FIT', 'FINAL'];
const SOURCES      = ['LinkedIn', 'Referral', 'Job Board', 'Direct Application', 'Recruitment Agency', 'Other'];

const PIPELINE_STATUS = {
  PENDING:          { label: 'Pending',         bg: '#f1f5f9', color: '#475569' },
  ASSIGNED:         { label: 'Assigned',         bg: '#dbeafe', color: '#1d4ed8' },
  INTERVIEWING:     { label: 'Interviewing',     bg: '#fef3c7', color: '#d97706' },
  FEEDBACK_PENDING: { label: 'Feedback Pending', bg: '#f3e8ff', color: '#7c3aed' },
  SELECTED:         { label: 'Selected',         bg: '#dcfce7', color: '#16a34a' },
  REJECTED:         { label: 'Rejected',         bg: '#fee2e2', color: '#dc2626' },
  ON_HOLD:          { label: 'On Hold',          bg: '#fff7ed', color: '#c2410c' },
};

const fmtDt = (v) => v ? new Date(v).toLocaleString('en-US', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
const fmtDate = (v) => v ? new Date(v).toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

const StatusChip = ({ status }) => {
  const s = STATUS_COLOR[status] || { bg: '#f1f5f9', color: '#475569', label: status };
  return <Chip label={s.label} size="small" sx={{ bgcolor: s.bg, color: s.color, fontWeight: 700, fontSize: 11.5 }} />;
};

const RoundStatusChip = ({ status }) => {
  const s = ROUND_STATUS_COLOR[status] || { bg: '#f1f5f9', color: '#475569' };
  return <Chip label={(status || '').replace('_', ' ')} size="small" sx={{ bgcolor: s.bg, color: s.color, fontWeight: 700, fontSize: 11 }} />;
};

const RatingStars = ({ value }) => (
  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
    <Rating value={value || 0} readOnly size="small" precision={0.5} />
    {value != null && <Typography variant="caption" fontWeight={700}>{value}</Typography>}
  </Box>
);

/* ── Empty form constants ────────────────────────────────────────────────── */
const EMPTY_CANDIDATE = { name: '', email: '', phone: '', position: '', department: '', source: '', notes: '' };
const EMPTY_ROUND     = { roundType: 'TECHNICAL', interviewerId: '', scheduledAt: '', durationMinutes: 60, location: '', managerNotes: '' };
const EMPTY_ASSIGN    = { candidateId: '', interviewerId: '', roundType: 'TECHNICAL', date: '', time: '', durationMinutes: '60', instructions: '' };
const EMPTY_FEEDBACK  = {
  overallRating: 3, technicalRating: 3, communicationRating: 3,
  problemSolvingRating: 3, cultureFitRating: 3,
  strengths: '', weaknesses: '', comments: '', recommendation: 'HIRE',
};

/* ═══════════════════════════════════════════════════════════════════════════ */
const InterviewsPage = () => {
  const { user } = useSelector((s) => s.auth);
  const navigate  = useNavigate();
  const isAdmin   = user?.role === 'ADMIN'    || user?.role === 'DIRECTOR';
  const isManager = user?.role === 'MANAGER'  || user?.role === 'ASSISTANT_MANAGER';
  const isHR      = user?.role === 'HR';
  const canManage   = isAdmin || isManager || isHR;
  const canSchedule = isAdmin || isHR;   // only ADMIN/DIRECTOR/HR may schedule/edit/cancel rounds

  /* ── top-level state ──────────────────────────────────────────────────── */
  // Managers get assigned as interviewers; default to 'my-rounds' so they see assigned rounds immediately.
  // Admin/HR primarily manage candidates so they default to 'candidates'.
  const [tab,        setTab]        = useState((isAdmin || isHR) ? 'candidates' : 'my-rounds');
  const [loading,    setLoading]    = useState(true);
  const [candidates, setCandidates] = useState([]);
  const [myRounds,   setMyRounds]   = useState([]);
  const [stats,      setStats]      = useState(null);
  const [auditLogs,  setAuditLogs]  = useState([]);
  const [allEmployees, setAllEmployees] = useState([]);

  /* ── candidates list state ────────────────────────────────────────────── */
  const [search,      setSearch]      = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [candPage,    setCandPage]    = useState(0);
  const [candRpp,     setCandRpp]     = useState(10);

  /* ── detail view ──────────────────────────────────────────────────────── */
  const [detailCandidate, setDetailCandidate] = useState(null);
  const [detailLoading,   setDetailLoading]   = useState(false);
  const [expandedRound,   setExpandedRound]   = useState(null);
  const [candidateAudit,  setCandidateAudit]  = useState([]);

  /* ── dialogs ──────────────────────────────────────────────────────────── */
  const [candDialog,     setCandDialog]     = useState(false);
  const [editCandidate,  setEditCandidate]  = useState(null);
  const [candForm,       setCandForm]       = useState(EMPTY_CANDIDATE);

  const [roundDialog,    setRoundDialog]    = useState(false);
  const [editRound,      setEditRound]      = useState(null);
  const [roundForm,      setRoundForm]      = useState(EMPTY_ROUND);

  const [feedbackDialog, setFeedbackDialog] = useState(false);
  const [feedbackRound,  setFeedbackRound]  = useState(null);
  const [feedbackForm,   setFeedbackForm]   = useState(EMPTY_FEEDBACK);
  const [editFeedbackId, setEditFeedbackId] = useState(null);

  const [statusDialog,   setStatusDialog]   = useState(false);
  const [newStatus,      setNewStatus]      = useState('');

  const [assignDialog,   setAssignDialog]   = useState(false);
  const [assignForm,     setAssignForm]     = useState(EMPTY_ASSIGN);

  const [saving, setSaving] = useState(false);

  /* ── my-rounds filter ─────────────────────────────────────────────────── */
  const [myRoundFilter,   setMyRoundFilter]   = useState('ALL');
  const [pipelineFilter,  setPipelineFilter]  = useState('ALL');

  /* ── notifications ────────────────────────────────────────────────────── */
  const [notifications,    setNotifications]    = useState([]);
  const [unreadCount,      setUnreadCount]      = useState(0);
  const [notifOpen,        setNotifOpen]        = useState(false);

  const downloadIcs = async (roundId) => {
    try {
      const token = localStorage.getItem('accessToken');
      const base = process.env.REACT_APP_API_URL || '/api';
      const res = await fetch(`${base}/interviews/rounds/${roundId}/ics`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Download failed');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `interview-${roundId}.ics`; a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('Could not download calendar invite');
    }
  };

  /* ── init ─────────────────────────────────────────────────────────────── */
  useEffect(() => {
    const init = async () => {
      // Fetch myRounds and notifications independently
      const myRoundsPromise = interviewApi.getMyRounds()
        .then(r => setMyRounds(Array.isArray(r) ? r : []))
        .catch(() => {});
      interviewApi.getMyNotifications()
        .then(n => { setNotifications(Array.isArray(n) ? n : []); setUnreadCount((Array.isArray(n) ? n : []).filter(x => !x.read).length); })
        .catch(() => {});

      if (canManage) {
        try {
          const [cands, st, emps] = await Promise.all([
            interviewApi.getAllCandidates(),
            interviewApi.getStats(),
            employeeApi.getAll(),
          ]);
          setCandidates(Array.isArray(cands) ? cands : []);
          setStats(st);
          setAllEmployees(Array.isArray(emps) ? emps : []);
        } catch (err) {
          toast.error(err?.response?.status === 404
            ? 'Interview module not available — please restart the backend server'
            : 'Failed to load interview data');
        }
      }

      await myRoundsPromise;
      setLoading(false);
    };
    init();
  }, [canManage]);

  /* ── refresh helpers ──────────────────────────────────────────────────── */
  const refreshCandidates = async () => {
    const [cands, st] = await Promise.all([interviewApi.getAllCandidates(), interviewApi.getStats()]);
    setCandidates(Array.isArray(cands) ? cands : []);
    setStats(st);
  };

  const refreshMyRounds = async () => {
    const rounds = await interviewApi.getMyRounds();
    setMyRounds(Array.isArray(rounds) ? rounds : []);
  };

  const refreshDetail = async (id) => {
    setDetailLoading(true);
    try {
      const [c, logs] = await Promise.all([
        interviewApi.getCandidate(id),
        interviewApi.getAuditLogsForCandidate(id),
      ]);
      setDetailCandidate(c);
      setCandidateAudit(Array.isArray(logs) ? logs : []);
    } catch {
      toast.error('Failed to refresh candidate');
    } finally {
      setDetailLoading(false);
    }
  };

  /* ── candidate CRUD ───────────────────────────────────────────────────── */
  const openAddCandidate = () => {
    setEditCandidate(null);
    setCandForm(EMPTY_CANDIDATE);
    setCandDialog(true);
  };

  const openEditCandidate = (c, e) => {
    e?.stopPropagation();
    setEditCandidate(c);
    setCandForm({ name: c.name, email: c.email || '', phone: c.phone || '', position: c.position,
                  department: c.department || '', source: c.source || '', notes: c.notes || '' });
    setCandDialog(true);
  };

  const handleSaveCandidate = async () => {
    if (!candForm.name.trim() || !candForm.position.trim()) {
      toast.error('Name and Position are required'); return;
    }
    setSaving(true);
    try {
      if (editCandidate) {
        await interviewApi.updateCandidate(editCandidate.id, candForm);
        toast.success('Candidate updated');
        if (detailCandidate?.id === editCandidate.id) await refreshDetail(editCandidate.id);
      } else {
        await interviewApi.createCandidate(candForm);
        toast.success('Candidate added');
      }
      setCandDialog(false);
      await refreshCandidates();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save candidate');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteCandidate = async (id, e) => {
    e?.stopPropagation();
    if (!window.confirm('Delete this candidate and all interview records?')) return;
    try {
      await interviewApi.deleteCandidate(id);
      toast.success('Candidate deleted');
      if (detailCandidate?.id === id) setDetailCandidate(null);
      await refreshCandidates();
    } catch {
      toast.error('Failed to delete candidate');
    }
  };

  const openStatusDialog = (c) => {
    setNewStatus(c.status);
    setStatusDialog(true);
  };

  const openAssignDialog = (preselectedCandidate = null) => {
    setAssignForm({
      ...EMPTY_ASSIGN,
      candidateId: preselectedCandidate ? String(preselectedCandidate.id) : '',
    });
    setAssignDialog(true);
  };

  const handleAssignSubmit = async () => {
    const { candidateId, interviewerId, roundType, date, time, durationMinutes, instructions } = assignForm;
    if (!candidateId)   { toast.error('Select a candidate');   return; }
    if (!interviewerId) { toast.error('Select an interviewer'); return; }
    if (!date || !time) { toast.error('Set interview date and time'); return; }
    setSaving(true);
    try {
      const scheduledAt = new Date(`${date}T${time}`).toISOString();
      await interviewApi.scheduleRound(Number(candidateId), {
        roundType,
        interviewerId: Number(interviewerId),
        scheduledAt,
        durationMinutes: Number(durationMinutes) || 60,
        managerNotes: instructions,
      });
      toast.success('Interview assigned — interviewer notified');
      setAssignDialog(false);
      await refreshCandidates();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to assign interview');
    } finally {
      setSaving(false);
    }
  };

  const handleStatusChange = async () => {
    if (!newStatus || !detailCandidate) return;
    setSaving(true);
    try {
      await interviewApi.updateCandidateStatus(detailCandidate.id, newStatus);
      toast.success('Status updated');
      setStatusDialog(false);
      await Promise.all([refreshDetail(detailCandidate.id), refreshCandidates()]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update status');
    } finally {
      setSaving(false);
    }
  };

  /* ── round scheduling ─────────────────────────────────────────────────── */
  const openScheduleRound = () => {
    setEditRound(null);
    setRoundForm(EMPTY_ROUND);
    setRoundDialog(true);
  };

  const openEditRound = (round) => {
    setEditRound(round);
    setRoundForm({
      roundType: round.roundType || 'TECHNICAL',
      interviewerId: round.interviewerId || '',
      scheduledAt: round.scheduledAt ? round.scheduledAt.slice(0, 16) : '',
      durationMinutes: round.durationMinutes || 60,
      location: round.location || '',
      managerNotes: round.managerNotes || '',
      status: round.status,
    });
    setRoundDialog(true);
  };

  const handleSaveRound = async () => {
    if (!roundForm.interviewerId) { toast.error('Select an interviewer'); return; }
    setSaving(true);
    try {
      const payload = {
        ...roundForm,
        interviewerId: Number(roundForm.interviewerId),
        scheduledAt: roundForm.scheduledAt ? new Date(roundForm.scheduledAt).toISOString() : null,
      };
      if (editRound) {
        await interviewApi.updateRound(editRound.id, payload);
        toast.success('Round updated');
      } else {
        await interviewApi.scheduleRound(detailCandidate.id, payload);
        toast.success('Round scheduled — interviewer notified');
      }
      setRoundDialog(false);
      await refreshDetail(detailCandidate.id);
      await refreshCandidates();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save round');
    } finally {
      setSaving(false);
    }
  };

  const handleCancelRound = async (roundId) => {
    if (!window.confirm('Cancel this interview round?')) return;
    try {
      await interviewApi.cancelRound(roundId);
      toast.success('Round cancelled');
      await refreshDetail(detailCandidate.id);
    } catch {
      toast.error('Failed to cancel round');
    }
  };

  const handleStartRound = async (roundId) => {
    try {
      await interviewApi.startRound(roundId);
      toast.success('Interview started');
      await refreshMyRounds();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to start round');
    }
  };

  /* ── feedback ─────────────────────────────────────────────────────────── */
  const openFeedback = (round, existingFeedback) => {
    setFeedbackRound(round);
    if (existingFeedback) {
      setEditFeedbackId(existingFeedback.id);
      setFeedbackForm({
        overallRating: existingFeedback.overallRating || 3,
        technicalRating: existingFeedback.technicalRating || 3,
        communicationRating: existingFeedback.communicationRating || 3,
        problemSolvingRating: existingFeedback.problemSolvingRating || 3,
        cultureFitRating: existingFeedback.cultureFitRating || 3,
        strengths: existingFeedback.strengths || '',
        weaknesses: existingFeedback.weaknesses || '',
        comments: existingFeedback.comments || '',
        recommendation: existingFeedback.recommendation || 'HIRE',
      });
    } else {
      setEditFeedbackId(null);
      setFeedbackForm(EMPTY_FEEDBACK);
    }
    setFeedbackDialog(true);
  };

  const handleSubmitFeedback = async () => {
    if (!feedbackForm.recommendation) { toast.error('Select a recommendation'); return; }
    setSaving(true);
    try {
      if (editFeedbackId) {
        await interviewApi.updateFeedback(editFeedbackId, feedbackForm);
        toast.success('Feedback updated');
      } else {
        await interviewApi.submitFeedback(feedbackRound.id, feedbackForm);
        toast.success('Feedback submitted — manager notified');
      }
      setFeedbackDialog(false);
      await refreshMyRounds();
      if (detailCandidate) await refreshDetail(detailCandidate.id);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit feedback');
    } finally {
      setSaving(false);
    }
  };

  /* ── candidate detail open ────────────────────────────────────────────── */
  const openDetail = async (c) => {
    setDetailCandidate(null);
    setExpandedRound(null);
    setDetailLoading(true);
    try {
      const [detail, logs] = await Promise.all([
        interviewApi.getCandidate(c.id),
        interviewApi.getAuditLogsForCandidate(c.id),
      ]);
      setDetailCandidate(detail);
      setCandidateAudit(Array.isArray(logs) ? logs : []);
      // Auto-expand the most recent round that has submitted feedback so it's immediately visible
      const roundWithFeedback = [...(detail.rounds || [])]
        .reverse()
        .find(r => r.feedbackSubmitted || (r.feedbacks || []).length > 0);
      if (roundWithFeedback) setExpandedRound(roundWithFeedback.id);
    } catch {
      toast.error('Failed to load candidate details');
    } finally {
      setDetailLoading(false);
    }
  };

  /* ── computed ─────────────────────────────────────────────────────────── */
  const filteredCandidates = useMemo(() => {
    const q = search.toLowerCase();
    return candidates.filter(c => {
      const matchSearch = !q || c.name?.toLowerCase().includes(q)
        || c.position?.toLowerCase().includes(q) || c.department?.toLowerCase().includes(q)
        || c.email?.toLowerCase().includes(q);
      const matchStatus = statusFilter === 'ALL' || c.status === statusFilter;
      return matchSearch && matchStatus;
    });
  }, [candidates, search, statusFilter]);

  const filteredMyRounds = useMemo(() => {
    if (myRoundFilter === 'ALL') return myRounds;
    return myRounds.filter(r => r.status === myRoundFilter);
  }, [myRounds, myRoundFilter]);

  const myEmployee = useMemo(() => {
    if (!user) return null;
    return allEmployees.find(e => e.userId === user.userId) || null;
  }, [allEmployees, user]);

  /* ── director pipeline helpers ────────────────────────────────────────── */
  const getPipelineStatus = (c) => {
    if (c.status === 'SELECTED') return 'SELECTED';
    if (c.status === 'REJECTED') return 'REJECTED';
    if (c.status === 'ON_HOLD')  return 'ON_HOLD';
    const rounds = (c.rounds || []).filter(r => r.status !== 'CANCELLED');
    if (!rounds.length) return 'PENDING';
    const latest = rounds[rounds.length - 1];
    if (latest.status === 'SCHEDULED')   return 'ASSIGNED';
    if (latest.status === 'IN_PROGRESS') return 'INTERVIEWING';
    if (latest.status === 'COMPLETED')   return 'FEEDBACK_PENDING';
    return 'PENDING';
  };

  const getCurrentRound = (c) => {
    const rounds = (c.rounds || []).filter(r => r.status !== 'CANCELLED');
    if (!rounds.length) return null;
    return rounds.find(r => ['SCHEDULED', 'IN_PROGRESS'].includes(r.status)) || rounds[rounds.length - 1];
  };

  const pipelineStats = useMemo(() => ({
    total:          candidates.length,
    assigned:       candidates.filter(c => (c.rounds || []).some(r => ['SCHEDULED', 'IN_PROGRESS'].includes(r.status))).length,
    pendingFeedback: candidates.filter(c => {
      const rounds = (c.rounds || []).filter(r => r.status !== 'CANCELLED');
      return rounds.length > 0 && rounds[rounds.length - 1].status === 'COMPLETED' && c.status === 'IN_PROGRESS';
    }).length,
    selected:       candidates.filter(c => c.status === 'SELECTED').length,
    rejected:       candidates.filter(c => c.status === 'REJECTED').length,
  }), [candidates]);

  const filteredPipeline = useMemo(() => {
    const q = search.toLowerCase();
    return candidates.filter(c => {
      const matchSearch = !q || c.name?.toLowerCase().includes(q)
        || c.position?.toLowerCase().includes(q) || c.department?.toLowerCase().includes(q);
      if (!matchSearch) return false;
      if (pipelineFilter === 'ALL') return true;
      return getPipelineStatus(c) === pipelineFilter;
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candidates, search, pipelineFilter]);

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
      <CircularProgress sx={{ color: '#1e3a5f' }} />
    </Box>
  );

  /* ═══════════════════════════════════════════════════════════════════════ */
  /* CANDIDATE DETAIL VIEW                                                  */
  /* ═══════════════════════════════════════════════════════════════════════ */
  if (detailCandidate && tab === 'candidates') {
    const c = detailCandidate;
    const rounds = c.rounds || [];
    const canDecide = canSchedule;

    return (
      <Box sx={{ bgcolor: '#f8fafc', minHeight: '100%' }}>
        {detailLoading && <LinearProgress sx={{ mb: 1 }} />}

        {/* ── header ── */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
          <Box>
            <Button startIcon={<ArrowBackIcon />} size="small" onClick={() => setDetailCandidate(null)}
              sx={{ color: '#64748b', textTransform: 'none', mb: 1 }}>
              Back to Candidates
            </Button>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar sx={{ bgcolor: '#1e3a5f', width: 48, height: 48, fontSize: 20 }}>
                {c.name?.charAt(0).toUpperCase()}
              </Avatar>
              <Box>
                <Typography variant="h5" fontWeight={700}>{c.name}</Typography>
                <Typography variant="body2" color="text.secondary">{c.position}
                  {c.department && ` · ${c.department}`}</Typography>
              </Box>
              <StatusChip status={c.status} />
            </Box>
          </Box>
          <Box sx={{ display: 'flex', gap: 1 }}>
            {canSchedule && (
              <Button variant="outlined" startIcon={<EditIcon />} size="small"
                onClick={() => openEditCandidate(c)}
                sx={{ textTransform: 'none', borderColor: '#e2e8f0' }}>
                Edit
              </Button>
            )}
            {canDecide && (
              <Button variant="outlined" size="small" onClick={() => openStatusDialog(c)}
                sx={{ textTransform: 'none', borderColor: '#e2e8f0' }}>
                Change Status
              </Button>
            )}
            {canSchedule && (
              <Button variant="contained" startIcon={<AddIcon />} size="small"
                onClick={openScheduleRound}
                sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none' }}>
                Schedule Round
              </Button>
            )}
          </Box>
        </Box>

        <Grid container spacing={3}>
          {/* ── left column: candidate info ── */}
          <Grid item xs={12} md={4}>
            <Card sx={{ borderRadius: 3, mb: 2 }}>
              <CardContent>
                <Typography variant="subtitle2" fontWeight={700} color="text.secondary"
                  sx={{ textTransform: 'uppercase', letterSpacing: 0.5, mb: 2 }}>
                  Candidate Info
                </Typography>
                {[
                  ['Email', c.email],
                  ['Phone', c.phone],
                  ['Source', c.source],
                  ['Department', c.department],
                  ['Added By', c.createdByName],
                  ['Added On', fmtDate(c.createdAt)],
                ].map(([label, val]) => val ? (
                  <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.5 }}>
                    <Typography variant="caption" color="text.secondary" fontWeight={600}>{label}</Typography>
                    <Typography variant="caption" fontWeight={500} sx={{ maxWidth: 160, textAlign: 'right' }}>{val}</Typography>
                  </Box>
                ) : null)}
                {c.notes && (
                  <>
                    <Divider sx={{ my: 1.5 }} />
                    <Typography variant="caption" color="text.secondary" fontWeight={600}>Notes</Typography>
                    <Typography variant="body2" sx={{ mt: 0.5, color: '#475569', fontSize: 13 }}>{c.notes}</Typography>
                  </>
                )}
              </CardContent>
            </Card>

            {/* ── stats card ── */}
            <Card sx={{ borderRadius: 3, mb: 2 }}>
              <CardContent>
                <Typography variant="subtitle2" fontWeight={700} color="text.secondary"
                  sx={{ textTransform: 'uppercase', letterSpacing: 0.5, mb: 2 }}>
                  Interview Summary
                </Typography>
                <Grid container spacing={1}>
                  {[
                    ['Total Rounds',   c.totalRounds    || 0, '#1e3a5f'],
                    ['Completed',      c.completedRounds || 0, '#16a34a'],
                    ['Avg Rating',     c.averageRating  ? `${c.averageRating}/5` : 'N/A', '#d97706'],
                  ].map(([label, val, color]) => (
                    <Grid item xs={4} key={label}>
                      <Box sx={{ textAlign: 'center', p: 1, bgcolor: '#f8fafc', borderRadius: 2 }}>
                        <Typography variant="h6" fontWeight={800} sx={{ color }}>{val}</Typography>
                        <Typography variant="caption" color="text.secondary">{label}</Typography>
                      </Box>
                    </Grid>
                  ))}
                </Grid>
              </CardContent>
            </Card>

            {/* ── quick decision buttons ── */}
            {canDecide && (
              <Card sx={{ borderRadius: 3 }}>
                <CardContent>
                  <Typography variant="subtitle2" fontWeight={700} color="text.secondary"
                    sx={{ textTransform: 'uppercase', letterSpacing: 0.5, mb: 2 }}>
                    Final Decision
                  </Typography>
                  <Stack spacing={1}>
                    <Button fullWidth variant="contained" startIcon={<CheckCircleIcon />}
                      onClick={() => { setNewStatus('SELECTED'); setStatusDialog(true); }}
                      sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' }, textTransform: 'none', fontWeight: 600 }}>
                      Select Candidate
                    </Button>
                    <Button fullWidth variant="outlined" startIcon={<PauseCircleIcon />}
                      onClick={() => { setNewStatus('ON_HOLD'); setStatusDialog(true); }}
                      sx={{ borderColor: '#f59e0b', color: '#d97706', '&:hover': { borderColor: '#d97706' }, textTransform: 'none', fontWeight: 600 }}>
                      Put On Hold
                    </Button>
                    <Button fullWidth variant="outlined" startIcon={<CancelIcon />}
                      onClick={() => { setNewStatus('REJECTED'); setStatusDialog(true); }}
                      sx={{ borderColor: '#ef4444', color: '#dc2626', '&:hover': { borderColor: '#dc2626' }, textTransform: 'none', fontWeight: 600 }}>
                      Reject
                    </Button>
                  </Stack>
                </CardContent>
              </Card>
            )}
          </Grid>

          {/* ── right column: rounds timeline ── */}
          <Grid item xs={12} md={8}>
            <Card sx={{ borderRadius: 3, mb: 2 }}>
              <CardContent>
                <Typography variant="subtitle2" fontWeight={700} color="text.secondary"
                  sx={{ textTransform: 'uppercase', letterSpacing: 0.5, mb: 2 }}>
                  Interview Rounds ({rounds.length})
                </Typography>
                {rounds.length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 4 }}>
                    <CalendarMonthIcon sx={{ fontSize: 48, color: '#cbd5e1', mb: 1 }} />
                    <Typography color="text.secondary">No rounds scheduled yet.</Typography>
                    {canSchedule && <Button variant="contained" startIcon={<AddIcon />} onClick={openScheduleRound}
                      sx={{ mt: 2, bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none' }}>
                      Schedule First Round
                    </Button>}
                  </Box>
                ) : (
                  <Box>
                    {rounds.map((round, idx) => {
                      const isExpanded = expandedRound === round.id;
                      const typeColor  = ROUND_TYPE_COLOR[round.roundType] || '#64748b';
                      const feedbacks  = round.feedbacks || [];
                      return (
                        <Box key={round.id} sx={{ display: 'flex', gap: 2, mb: 1 }}>
                          {/* timeline line */}
                          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', minWidth: 36 }}>
                            <Box sx={{ width: 36, height: 36, borderRadius: '50%', bgcolor: typeColor,
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              color: '#fff', fontWeight: 800, fontSize: 14, flexShrink: 0 }}>
                              {round.roundNumber}
                            </Box>
                            {idx < rounds.length - 1 && (
                              <Box sx={{ width: 2, flex: 1, bgcolor: '#e2e8f0', mt: 0.5 }} />
                            )}
                          </Box>

                          {/* round card */}
                          <Box sx={{ flex: 1, mb: 2 }}>
                            <Card variant="outlined" sx={{ borderRadius: 2, borderColor: '#e2e8f0' }}>
                              <Box sx={{ p: 2, cursor: 'pointer', '&:hover': { bgcolor: '#f8fafc' } }}
                                onClick={() => setExpandedRound(isExpanded ? null : round.id)}>
                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                  <Box>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                                      <Chip label={round.roundType?.replace('_', ' ')} size="small"
                                        sx={{ bgcolor: typeColor + '22', color: typeColor, fontWeight: 700, fontSize: 11 }} />
                                      <RoundStatusChip status={round.status} />
                                      {feedbacks.length > 0 && (
                                        <Chip label={`${feedbacks.length} feedback`} size="small"
                                          sx={{ bgcolor: '#f0fdf4', color: '#16a34a', fontWeight: 600, fontSize: 11 }} />
                                      )}
                                    </Box>
                                    <Typography variant="body2" fontWeight={600}>
                                      Interviewer: {round.interviewerName || '—'}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {round.scheduledAt ? fmtDt(round.scheduledAt) : 'Not scheduled'}
                                      {round.durationMinutes && ` · ${round.durationMinutes} min`}
                                    </Typography>
                                  </Box>
                                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                    {canSchedule && round.status !== 'CANCELLED' && round.status !== 'COMPLETED' && (
                                      <>
                                        <Tooltip title="Edit Round">
                                          <IconButton size="small" onClick={(e) => { e.stopPropagation(); openEditRound(round); }}>
                                            <EditIcon fontSize="small" />
                                          </IconButton>
                                        </Tooltip>
                                        <Tooltip title="Cancel Round">
                                          <IconButton size="small" color="error"
                                            onClick={(e) => { e.stopPropagation(); handleCancelRound(round.id); }}>
                                            <CancelIcon fontSize="small" />
                                          </IconButton>
                                        </Tooltip>
                                      </>
                                    )}
                                    {isExpanded ? <ExpandLessIcon sx={{ color: '#94a3b8' }} /> : <ExpandMoreIcon sx={{ color: '#94a3b8' }} />}
                                  </Box>
                                </Box>
                              </Box>

                              <Collapse in={isExpanded}>
                                <Divider />
                                <Box sx={{ p: 2 }}>
                                  <Grid container spacing={2} sx={{ mb: 2 }}>
                                    {[
                                      ['Assigned By', round.assignedByName],
                                      ['Location',    round.location],
                                    ].map(([label, val]) => (
                                      <Grid item xs={6} key={label}>
                                        <Typography variant="caption" color="text.secondary" fontWeight={600}>{label}</Typography>
                                        <Typography variant="body2">{val || '—'}</Typography>
                                      </Grid>
                                    ))}
                                    {round.managerNotes && (
                                      <Grid item xs={12}>
                                        <Typography variant="caption" color="text.secondary" fontWeight={600}>Manager Notes</Typography>
                                        <Typography variant="body2">{round.managerNotes}</Typography>
                                      </Grid>
                                    )}
                                  </Grid>

                                  {/* Feedbacks */}
                                  {feedbacks.length > 0 && (
                                    <Box>
                                      <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>
                                        Feedback ({feedbacks.length})
                                      </Typography>
                                      {feedbacks.map((fb) => {
                                        const rec = REC_COLOR[fb.recommendation] || { bg: '#f1f5f9', color: '#475569' };
                                        return (
                                          <Box key={fb.id} sx={{ p: 2, bgcolor: '#f8fafc', borderRadius: 2, mb: 1.5, border: '1px solid #e2e8f0' }}>
                                            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.5 }}>
                                              <Box>
                                                <Typography variant="body2" fontWeight={700}>{fb.submittedByName}</Typography>
                                                <Typography variant="caption" color="text.secondary">{fmtDt(fb.submittedAt)}</Typography>
                                              </Box>
                                              {fb.recommendation && (
                                                <Chip label={fb.recommendation.replace('_', ' ')} size="small"
                                                  sx={{ bgcolor: rec.bg, color: rec.color, fontWeight: 700, fontSize: 11 }} />
                                              )}
                                            </Box>
                                            <Grid container spacing={1} sx={{ mb: 1.5 }}>
                                              {[
                                                ['Overall',         fb.overallRating],
                                                ['Technical',       fb.technicalRating],
                                                ['Communication',   fb.communicationRating],
                                                ['Problem Solving', fb.problemSolvingRating],
                                                ['Culture Fit',     fb.cultureFitRating],
                                              ].filter(([, v]) => v != null).map(([label, val]) => (
                                                <Grid item xs={6} key={label}>
                                                  <Typography variant="caption" color="text.secondary">{label}</Typography>
                                                  <RatingStars value={val} />
                                                </Grid>
                                              ))}
                                            </Grid>
                                            {fb.strengths && <Typography variant="body2" sx={{ mb: 0.5 }}><strong>Strengths:</strong> {fb.strengths}</Typography>}
                                            {fb.weaknesses && <Typography variant="body2" sx={{ mb: 0.5 }}><strong>Weaknesses:</strong> {fb.weaknesses}</Typography>}
                                            {fb.comments && <Typography variant="body2" color="text.secondary">{fb.comments}</Typography>}
                                          </Box>
                                        );
                                      })}
                                    </Box>
                                  )}
                                </Box>
                              </Collapse>
                            </Card>
                          </Box>
                        </Box>
                      );
                    })}
                  </Box>
                )}
              </CardContent>
            </Card>

            {/* ── Audit Log ── */}
            {candidateAudit.length > 0 && (
              <Card sx={{ borderRadius: 3 }}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                    <HistoryIcon sx={{ color: '#64748b', fontSize: 18 }} />
                    <Typography variant="subtitle2" fontWeight={700} color="text.secondary"
                      sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
                      Activity Log
                    </Typography>
                  </Box>
                  <Box>
                    {candidateAudit.slice(0, 10).map((log) => (
                      <Box key={log.id} sx={{ display: 'flex', gap: 2, mb: 1.5, alignItems: 'flex-start' }}>
                        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: '#1e3a5f', mt: 0.75, flexShrink: 0 }} />
                        <Box>
                          <Typography variant="caption" fontWeight={600}>{log.action.replace(/_/g, ' ')}</Typography>
                          {log.performedByName && (
                            <Typography variant="caption" color="text.secondary"> by {log.performedByName}</Typography>
                          )}
                          <Typography variant="caption" color="text.secondary" display="block">{fmtDt(log.createdAt)}</Typography>
                          {log.details && <Typography variant="caption" sx={{ color: '#64748b' }}>{log.details}</Typography>}
                        </Box>
                      </Box>
                    ))}
                  </Box>
                </CardContent>
              </Card>
            )}
          </Grid>
        </Grid>

        {/* Dialogs render within detail view too */}
        {renderDialogs()}
      </Box>
    );
  }

  /* ═══════════════════════════════════════════════════════════════════════ */
  /* MAIN CATALOG VIEW                                                       */
  /* ═══════════════════════════════════════════════════════════════════════ */

  function renderDialogs() {
    return (
      <>
        {/* ── Add/Edit Candidate ── */}
        <Dialog open={candDialog} onClose={() => setCandDialog(false)} maxWidth="sm" fullWidth>
          <DialogTitle fontWeight={700}>{editCandidate ? 'Edit Candidate' : 'Add Candidate'}</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
              <TextField label="Full Name *" value={candForm.name}
                onChange={e => setCandForm(f => ({ ...f, name: e.target.value }))} fullWidth />
              <TextField label="Position Applied For *" value={candForm.position}
                onChange={e => setCandForm(f => ({ ...f, position: e.target.value }))} fullWidth />
              <TextField label="Department" value={candForm.department}
                onChange={e => setCandForm(f => ({ ...f, department: e.target.value }))} fullWidth />
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <TextField label="Email" type="email" value={candForm.email}
                    onChange={e => setCandForm(f => ({ ...f, email: e.target.value }))} fullWidth />
                </Grid>
                <Grid item xs={6}>
                  <TextField label="Phone" value={candForm.phone}
                    onChange={e => setCandForm(f => ({ ...f, phone: e.target.value }))} fullWidth />
                </Grid>
              </Grid>
              <TextField label="Source" select value={candForm.source}
                onChange={e => setCandForm(f => ({ ...f, source: e.target.value }))} fullWidth>
                <MenuItem value="">— Select Source —</MenuItem>
                {SOURCES.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
              </TextField>
              <TextField label="Notes" value={candForm.notes}
                onChange={e => setCandForm(f => ({ ...f, notes: e.target.value }))}
                fullWidth multiline rows={2} />
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setCandDialog(false)}>Cancel</Button>
            <Button variant="contained" onClick={handleSaveCandidate} disabled={saving}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
              {saving ? 'Saving…' : editCandidate ? 'Save Changes' : 'Add Candidate'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Status Change ── */}
        <Dialog open={statusDialog} onClose={() => setStatusDialog(false)} maxWidth="xs" fullWidth>
          <DialogTitle fontWeight={700}>Change Candidate Status</DialogTitle>
          <DialogContent>
            <TextField label="Status" select value={newStatus}
              onChange={e => setNewStatus(e.target.value)} fullWidth sx={{ mt: 1 }}>
              {Object.entries(STATUS_COLOR).map(([val, { label }]) => (
                <MenuItem key={val} value={val}>{label}</MenuItem>
              ))}
            </TextField>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setStatusDialog(false)}>Cancel</Button>
            <Button variant="contained" onClick={handleStatusChange} disabled={saving}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
              {saving ? 'Saving…' : 'Update Status'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Schedule / Edit Round ── */}
        <Dialog open={roundDialog} onClose={() => setRoundDialog(false)} maxWidth="sm" fullWidth>
          <DialogTitle fontWeight={700}>{editRound ? 'Edit Round' : 'Schedule Interview Round'}</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
              <TextField label="Round Type" select value={roundForm.roundType}
                onChange={e => setRoundForm(f => ({ ...f, roundType: e.target.value }))} fullWidth>
                {ROUND_TYPES.map(t => <MenuItem key={t} value={t}>{t.replace('_', ' ')}</MenuItem>)}
              </TextField>
              <TextField label="Interviewer *" select value={roundForm.interviewerId}
                onChange={e => setRoundForm(f => ({ ...f, interviewerId: e.target.value }))} fullWidth>
                <MenuItem value="">— Select Interviewer —</MenuItem>
                {allEmployees.filter(e => e.active !== false).map(e => (
                  <MenuItem key={e.id} value={e.id}>
                    {e.firstName} {e.lastName} ({e.role})
                  </MenuItem>
                ))}
              </TextField>
              <Grid container spacing={2}>
                <Grid item xs={7}>
                  <TextField label="Scheduled Date & Time" type="datetime-local"
                    value={roundForm.scheduledAt}
                    onChange={e => setRoundForm(f => ({ ...f, scheduledAt: e.target.value }))}
                    fullWidth InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid item xs={5}>
                  <TextField label="Duration (min)" type="number"
                    value={roundForm.durationMinutes}
                    onChange={e => setRoundForm(f => ({ ...f, durationMinutes: parseInt(e.target.value) || 60 }))}
                    fullWidth inputProps={{ min: 15, step: 15 }} />
                </Grid>
              </Grid>
              <TextField label="Location / Meeting Link" value={roundForm.location}
                onChange={e => setRoundForm(f => ({ ...f, location: e.target.value }))} fullWidth
                placeholder="e.g. Conference Room A or Zoom link" />
              <TextField label="Notes for Interviewer" value={roundForm.managerNotes}
                onChange={e => setRoundForm(f => ({ ...f, managerNotes: e.target.value }))}
                fullWidth multiline rows={2}
                placeholder="What to focus on, specific skills to test, etc." />
              {editRound && (
                <TextField label="Round Status" select value={roundForm.status || ''}
                  onChange={e => setRoundForm(f => ({ ...f, status: e.target.value }))} fullWidth>
                  {Object.keys(ROUND_STATUS_COLOR).map(s => (
                    <MenuItem key={s} value={s}>{s.replace('_', ' ')}</MenuItem>
                  ))}
                </TextField>
              )}
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setRoundDialog(false)}>Cancel</Button>
            <Button variant="contained" onClick={handleSaveRound} disabled={saving}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
              {saving ? 'Saving…' : editRound ? 'Save Changes' : 'Schedule Round'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Feedback Dialog ── */}
        <Dialog open={feedbackDialog} onClose={() => setFeedbackDialog(false)} maxWidth="md" fullWidth
          PaperProps={{ sx: { maxHeight: '90vh' } }}>
          <DialogTitle fontWeight={700}>
            {editFeedbackId ? 'Edit Feedback' : 'Submit Interview Feedback'}
            {feedbackRound && (
              <Typography variant="body2" color="text.secondary" mt={0.25}>
                {feedbackRound.candidateName} — Round {feedbackRound.roundNumber} ({feedbackRound.roundType?.replace('_', ' ')})
              </Typography>
            )}
          </DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 1 }}>
              {/* Ratings */}
              <Box>
                <Typography variant="subtitle2" fontWeight={700} mb={2}>Ratings (1-5)</Typography>
                <Grid container spacing={2}>
                  {[
                    ['overallRating',         'Overall Rating'],
                    ['technicalRating',        'Technical Skills'],
                    ['communicationRating',    'Communication'],
                    ['problemSolvingRating',   'Problem Solving'],
                    ['cultureFitRating',       'Culture Fit'],
                  ].map(([field, label]) => (
                    <Grid item xs={12} sm={6} key={field}>
                      <Typography variant="caption" color="text.secondary" fontWeight={600}>{label}</Typography>
                      <Rating
                        value={feedbackForm[field] || 0}
                        onChange={(_, v) => setFeedbackForm(f => ({ ...f, [field]: v }))}
                        size="large"
                        sx={{ display: 'flex', mt: 0.5 }}
                      />
                    </Grid>
                  ))}
                </Grid>
              </Box>
              <Divider />
              {/* Text fields */}
              <TextField label="Strengths" value={feedbackForm.strengths}
                onChange={e => setFeedbackForm(f => ({ ...f, strengths: e.target.value }))}
                fullWidth multiline rows={2} placeholder="What stood out positively?" />
              <TextField label="Areas for Improvement" value={feedbackForm.weaknesses}
                onChange={e => setFeedbackForm(f => ({ ...f, weaknesses: e.target.value }))}
                fullWidth multiline rows={2} placeholder="What concerns do you have?" />
              <TextField label="Additional Comments" value={feedbackForm.comments}
                onChange={e => setFeedbackForm(f => ({ ...f, comments: e.target.value }))}
                fullWidth multiline rows={2} />
              {/* Recommendation */}
              <TextField label="Recommendation *" select value={feedbackForm.recommendation}
                onChange={e => setFeedbackForm(f => ({ ...f, recommendation: e.target.value }))} fullWidth>
                {[
                  ['STRONG_HIRE',    '⭐⭐ Strong Hire'],
                  ['HIRE',          '✅ Hire'],
                  ['MAYBE',         '🤔 Maybe'],
                  ['NO_HIRE',       '❌ No Hire'],
                  ['STRONG_NO_HIRE','🚫 Strong No Hire'],
                ].map(([val, label]) => <MenuItem key={val} value={val}>{label}</MenuItem>)}
              </TextField>
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setFeedbackDialog(false)}>Cancel</Button>
            <Button variant="contained" onClick={handleSubmitFeedback} disabled={saving}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
              {saving ? 'Saving…' : editFeedbackId ? 'Update Feedback' : 'Submit Feedback'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Assign Interview ── */}
        <Dialog open={assignDialog} onClose={() => setAssignDialog(false)} maxWidth="sm" fullWidth
          PaperProps={{ sx: { borderRadius: 3 } }}>
          <DialogTitle sx={{ pb: 1, fontWeight: 700, fontSize: 18, borderBottom: '1px solid #f1f5f9' }}>
            Assign Interview
          </DialogTitle>
          <DialogContent sx={{ pt: 1 }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1.5 }}>

              {/* Candidate */}
              <TextField label="Candidate *" select size="small" fullWidth
                value={assignForm.candidateId}
                onChange={e => setAssignForm(f => ({ ...f, candidateId: e.target.value }))}>
                <MenuItem value=""><em>— Select candidate —</em></MenuItem>
                {candidates
                  .filter(c => ['PENDING', 'IN_PROGRESS'].includes(c.status))
                  .map(c => (
                    <MenuItem key={c.id} value={String(c.id)}>
                      {c.name}
                      {c.position ? ` · ${c.position}` : ''}
                    </MenuItem>
                  ))}
              </TextField>

              {/* Assign To */}
              <TextField label="Assign To *" select size="small" fullWidth
                value={assignForm.interviewerId}
                onChange={e => setAssignForm(f => ({ ...f, interviewerId: e.target.value }))}>
                <MenuItem value=""><em>— Select interviewer —</em></MenuItem>
                {allEmployees.filter(e => e.active !== false).map(e => (
                  <MenuItem key={e.id} value={String(e.id)}>
                    {e.firstName} {e.lastName}
                    {e.role ? ` (${e.role.replace('_', ' ')})` : ''}
                  </MenuItem>
                ))}
              </TextField>

              {/* Interview Round */}
              <FormControl fullWidth>
                <FormLabel sx={{ fontSize: 13, fontWeight: 600, color: '#1e293b', mb: 1 }}>
                  Interview Round *
                </FormLabel>
                <RadioGroup row value={assignForm.roundType}
                  onChange={e => setAssignForm(f => ({ ...f, roundType: e.target.value }))}>
                  {[
                    { val: 'HR',           label: 'HR' },
                    { val: 'TECHNICAL',    label: 'Technical' },
                    { val: 'MANAGERIAL',   label: 'Managerial' },
                    { val: 'CULTURAL_FIT', label: 'Cultural Fit' },
                    { val: 'FINAL',        label: 'Final' },
                  ].map(r => (
                    <FormControlLabel key={r.val} value={r.val} label={r.label}
                      control={<Radio size="small" sx={{ '&.Mui-checked': { color: '#1e3a5f' } }} />}
                      sx={{ '& .MuiFormControlLabel-label': { fontSize: 14 } }}
                    />
                  ))}
                </RadioGroup>
              </FormControl>

              {/* Date + Time */}
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <TextField label="Date *" type="date" size="small" fullWidth
                    value={assignForm.date}
                    onChange={e => setAssignForm(f => ({ ...f, date: e.target.value }))}
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ min: new Date().toISOString().slice(0, 10) }} />
                </Grid>
                <Grid item xs={6}>
                  <TextField label="Time *" type="time" size="small" fullWidth
                    value={assignForm.time}
                    onChange={e => setAssignForm(f => ({ ...f, time: e.target.value }))}
                    InputLabelProps={{ shrink: true }} />
                </Grid>
              </Grid>

              {/* Duration */}
              <TextField label="Duration (minutes)" select size="small" fullWidth
                value={assignForm.durationMinutes}
                onChange={e => setAssignForm(f => ({ ...f, durationMinutes: e.target.value }))}>
                {['30', '45', '60', '90', '120'].map(d => (
                  <MenuItem key={d} value={d}>{d} Minutes</MenuItem>
                ))}
              </TextField>

              {/* Instructions */}
              <TextField label="Instructions / Notes" multiline rows={3} size="small" fullWidth
                placeholder="e.g. Evaluate taxation knowledge, focus on problem-solving…"
                value={assignForm.instructions}
                onChange={e => setAssignForm(f => ({ ...f, instructions: e.target.value }))} />
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2, borderTop: '1px solid #f1f5f9', gap: 1 }}>
            <Button onClick={() => setAssignDialog(false)} sx={{ color: '#64748b', textTransform: 'none' }}>
              Cancel
            </Button>
            <Button variant="contained" onClick={handleAssignSubmit} disabled={saving}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', px: 3 }}>
              {saving ? 'Assigning…' : 'Assign'}
            </Button>
          </DialogActions>
        </Dialog>
      </>
    );
  }

  /* ═══════════════════════════════════════════════════════════════════════ */
  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100%' }}>

      {/* ── Page header ── */}
      {isAdmin ? (
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
          <Box>
            <Typography variant="h5" fontWeight={700}>Interview Management Dashboard</Typography>
            <Typography variant="body2" color="text.secondary" mt={0.5}>
              Candidate pipeline · round assignments · hiring decisions
            </Typography>
          </Box>
          {tab === 'candidates' && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button variant="outlined" startIcon={<AddIcon />} onClick={openAddCandidate}
                sx={{ textTransform: 'none', borderRadius: 2, borderColor: '#1e3a5f', color: '#1e3a5f' }}>
                Add Candidate
              </Button>
              <Button variant="outlined" startIcon={<PersonAddIcon />}
                onClick={() => openAssignDialog()}
                sx={{ textTransform: 'none', borderRadius: 2, borderColor: '#7c3aed', color: '#7c3aed' }}>
                Assign Interview
              </Button>
              {/* <Button variant="contained" startIcon={<AssessmentIcon />}
                onClick={() => setTab('reports')}
                sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', borderRadius: 2 }}>
                Reports
              </Button> */}
            </Box>
          )}
        </Box>
      ) : (
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
          <Box>
            <Typography variant="h5" fontWeight={700}>Interview Management</Typography>
            <Typography variant="body2" color="text.secondary" mt={0.5}>
              Manage candidates, schedule rounds, track feedback and decisions
            </Typography>
          </Box>
          {canSchedule && tab === 'candidates' && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={openAddCandidate}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', borderRadius: 2 }}>
              Add Candidate
            </Button>
          )}
        </Box>
      )}

      {/* ── Tabs ── */}
      <Tabs value={tab} onChange={(_, v) => setTab(v)}
        sx={{ mb: 3, borderBottom: '1px solid #e2e8f0',
          '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, minWidth: 'auto', px: 2 },
          '& .MuiTabs-indicator': { bgcolor: '#1e3a5f' } }}>
        {canManage && <Tab label={isAdmin ? `Pipeline (${candidates.length})` : `Candidates (${candidates.length})`} value="candidates" />}
        {!isAdmin && <Tab label={`My Interviews (${myRounds.length})`} value="my-rounds" />}
        {canManage && <Tab label="Reports" value="reports" />}
        {isAdmin && <Tab label="Audit Log" value="audit" />}
      </Tabs>

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* DIRECTOR PIPELINE DASHBOARD                                         */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {tab === 'candidates' && isAdmin && (
        <Box>
          {/* ── Stats cards ── */}
          {/* <Grid container spacing={2} sx={{ mb: 3 }}>
            {[
              { label: 'Total Candidates',  value: pipelineStats.total,          icon: <WorkIcon />,          bg: '#1e3a5f', light: '#e8edf5' },
              { label: 'Assigned',          value: pipelineStats.assigned,        icon: <PersonAddIcon />,     bg: '#1d4ed8', light: '#dbeafe' },
              { label: 'Pending Feedback',  value: pipelineStats.pendingFeedback, icon: <AssessmentIcon />,    bg: '#7c3aed', light: '#f3e8ff' },
              { label: 'Selected',          value: pipelineStats.selected,        icon: <CheckCircleIcon />,   bg: '#16a34a', light: '#dcfce7' },
              { label: 'Rejected',          value: pipelineStats.rejected,        icon: <CancelIcon />,        bg: '#dc2626', light: '#fee2e2' },
            ].map(s => (
              <Grid item xs={12} sm={6} md key={s.label}>
                <Card sx={{ borderRadius: 2.5, border: 'none', boxShadow: '0 2px 12px rgba(0,0,0,0.07)' }}>
                  <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={600}
                        sx={{ textTransform: 'uppercase', letterSpacing: 0.5, fontSize: 10.5 }}>
                        {s.label}
                      </Typography>
                      <Box sx={{ bgcolor: s.light, borderRadius: 1.5, p: 0.75, display: 'flex',
                        '& svg': { fontSize: 18, color: s.bg } }}>
                        {s.icon}
                      </Box>
                    </Box>
                    <Typography variant="h4" fontWeight={700} sx={{ color: s.bg }}>{s.value}</Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid> */}

          {/* ── Search + Pipeline filter ── */}
          <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
            <TextField placeholder="Search candidates…" value={search}
              onChange={e => { setSearch(e.target.value); setCandPage(0); }} size="small"
              InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon sx={{ color: '#94a3b8' }} /></InputAdornment> }}
              sx={{ width: 260, bgcolor: '#fff', '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {[
                ['ALL',             `All (${candidates.length})`],
                ['PENDING',         `Pending (${candidates.filter(c => getPipelineStatus(c) === 'PENDING').length})`],
                ['ASSIGNED',        `Assigned (${candidates.filter(c => getPipelineStatus(c) === 'ASSIGNED').length})`],
                ['INTERVIEWING',    `Interviewing (${candidates.filter(c => getPipelineStatus(c) === 'INTERVIEWING').length})`],
                ['FEEDBACK_PENDING',`Feedback Pending (${candidates.filter(c => getPipelineStatus(c) === 'FEEDBACK_PENDING').length})`],
                ['SELECTED',        `Selected (${pipelineStats.selected})`],
                ['REJECTED',        `Rejected (${pipelineStats.rejected})`],
              ].map(([val, label]) => (
                <Chip key={val} label={label} onClick={() => { setPipelineFilter(val); setCandPage(0); }} size="small"
                  sx={{ cursor: 'pointer', fontWeight: 600,
                    bgcolor: pipelineFilter === val ? '#1e3a5f' : '#fff',
                    color: pipelineFilter === val ? '#fff' : '#64748b',
                    border: '1px solid #e2e8f0' }} />
              ))}
            </Box>
            {/* Refresh button — pipeline data can go stale if interviewers submit feedback while this page is open */}
            <Tooltip title="Refresh pipeline">
              <IconButton size="small" onClick={refreshCandidates}
                sx={{ ml: 'auto', bgcolor: '#fff', border: '1px solid #e2e8f0', borderRadius: 2 }}>
                <RefreshIcon fontSize="small" sx={{ color: '#64748b' }} />
              </IconButton>
            </Tooltip>
          </Box>

          {/* ── Pipeline table ── */}
          <Card sx={{ borderRadius: 2, overflow: 'hidden' }}>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                    {['#', 'Candidate', 'Position', 'Assigned To', 'Round', 'Interview Date', 'Status', 'Actions']
                      .map(h => <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569', py: 1.5 }}>{h}</TableCell>)}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredPipeline.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                        <WorkIcon sx={{ fontSize: 48, color: '#cbd5e1', mb: 1, display: 'block', mx: 'auto' }} />
                        <Typography color="text.secondary">No candidates found</Typography>
                      </TableCell>
                    </TableRow>
                  ) : filteredPipeline.slice(candPage * candRpp, (candPage + 1) * candRpp).map((c, i) => {
                    const ps       = getPipelineStatus(c);
                    const psCfg    = PIPELINE_STATUS[ps] || PIPELINE_STATUS.PENDING;
                    const curRound = getCurrentRound(c);
                    return (
                      <TableRow key={c.id} hover sx={{ cursor: 'pointer', bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}
                        onClick={() => openDetail(c)}>
                        <TableCell sx={{ fontSize: 13, color: '#94a3b8', fontWeight: 600 }}>{candPage * candRpp + i + 1}</TableCell>

                        {/* Candidate */}
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                            <Avatar sx={{ bgcolor: '#1e3a5f', width: 32, height: 32, fontSize: 13 }}>
                              {c.name?.charAt(0).toUpperCase()}
                            </Avatar>
                            <Box>
                              <Typography variant="body2" fontWeight={600}>{c.name}</Typography>
                              {c.email && <Typography variant="caption" color="text.secondary">{c.email}</Typography>}
                            </Box>
                          </Box>
                        </TableCell>

                        {/* Position */}
                        <TableCell>
                          <Typography variant="body2" fontWeight={600}>{c.position}</Typography>
                          {c.department && <Typography variant="caption" color="text.secondary">{c.department}</Typography>}
                        </TableCell>

                        {/* Assigned To */}
                        <TableCell>
                          {(c.currentRoundInterviewerName || curRound?.interviewerName) ? (
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <Avatar sx={{ width: 24, height: 24, fontSize: 11, bgcolor: '#6366f1' }}>
                                {(c.currentRoundInterviewerName || curRound.interviewerName).charAt(0)}
                              </Avatar>
                              <Typography variant="body2">
                                {c.currentRoundInterviewerName || curRound.interviewerName}
                              </Typography>
                            </Box>
                          ) : (
                            <Typography variant="body2" color="text.disabled" fontStyle="italic">Not Assigned</Typography>
                          )}
                        </TableCell>

                        {/* Round type */}
                        <TableCell>
                          {(c.currentRoundType || curRound?.roundType) ? (() => {
                            const rt = c.currentRoundType || curRound.roundType;
                            return (
                              <Chip label={rt.replace('_', ' ')} size="small"
                                sx={{ bgcolor: (ROUND_TYPE_COLOR[rt] || '#64748b') + '22',
                                  color: ROUND_TYPE_COLOR[rt] || '#64748b', fontWeight: 700, fontSize: 11 }} />
                            );
                          })() : '—'}
                        </TableCell>

                        {/* Interview Date */}
                        <TableCell sx={{ fontSize: 13, color: '#475569', whiteSpace: 'nowrap' }}>
                          {(c.currentRoundScheduledAt || curRound?.scheduledAt)
                            ? fmtDt(c.currentRoundScheduledAt || curRound?.scheduledAt) : '—'}
                        </TableCell>

                        {/* Pipeline status + feedback rating if available */}
                        <TableCell>
                          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                            <Chip label={psCfg.label} size="small"
                              sx={{ bgcolor: psCfg.bg, color: psCfg.color, fontWeight: 700, fontSize: 11.5 }} />
                            {c.averageRating != null && (
                              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.4 }}>
                                <StarIcon sx={{ fontSize: 12, color: '#f59e0b' }} />
                                <Typography variant="caption" fontWeight={700} sx={{ color: '#92400e', fontSize: 11 }}>
                                  {c.averageRating} / 5
                                </Typography>
                              </Box>
                            )}
                          </Box>
                        </TableCell>

                        {/* Actions */}
                        <TableCell onClick={e => e.stopPropagation()}>
                          <Box sx={{ display: 'flex', gap: 0.5 }}>
                            {ps === 'PENDING' ? (
                              <Button size="small" variant="contained"
                                onClick={e => { e.stopPropagation(); openAssignDialog(c); }}
                                sx={{ bgcolor: '#7c3aed', '&:hover': { bgcolor: '#6d28d9' }, textTransform: 'none', fontSize: 12, px: 1.5, py: 0.4 }}>
                                Assign
                              </Button>
                            ) : ps === 'FEEDBACK_PENDING' ? (
                              <Button size="small" variant="outlined"
                                onClick={() => openDetail(c)}
                                sx={{ borderColor: '#7c3aed', color: '#7c3aed', textTransform: 'none', fontSize: 12, px: 1.5, py: 0.4 }}>
                                Review
                              </Button>
                            ) : (
                              <Button size="small" variant="outlined"
                                onClick={() => openDetail(c)}
                                sx={{ borderColor: '#1e3a5f', color: '#1e3a5f', textTransform: 'none', fontSize: 12, px: 1.5, py: 0.4 }}>
                                View
                              </Button>
                            )}
                            <Tooltip title="Edit">
                              <IconButton size="small" onClick={e => openEditCandidate(c, e)}>
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Delete">
                              <IconButton size="small" color="error" onClick={e => handleDeleteCandidate(c.id, e)}>
                                <DeleteIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </Box>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
            {filteredPipeline.length > candRpp && (
              <TablePagination component="div" count={filteredPipeline.length}
                page={candPage} onPageChange={(_, p) => setCandPage(p)}
                rowsPerPage={candRpp} rowsPerPageOptions={[10, 25, 50]}
                onRowsPerPageChange={e => { setCandRpp(parseInt(e.target.value, 10)); setCandPage(0); }} />
            )}
          </Card>
        </Box>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* HR CANDIDATES TAB (non-admin)                                       */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {tab === 'candidates' && !isAdmin && canManage && (
        <Box>
          {/* Search + filter */}
          <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
            <TextField placeholder="Search candidates…" value={search}
              onChange={e => { setSearch(e.target.value); setCandPage(0); }} size="small"
              InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon sx={{ color: '#94a3b8' }} /></InputAdornment> }}
              sx={{ width: 280, bgcolor: '#fff', '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {['ALL', ...Object.keys(STATUS_COLOR)].map(s => (
                <Chip key={s} label={s === 'ALL' ? `All (${candidates.length})` : STATUS_COLOR[s]?.label}
                  onClick={() => { setStatusFilter(s); setCandPage(0); }} size="small"
                  sx={{ cursor: 'pointer', fontWeight: 600,
                    bgcolor: statusFilter === s ? '#1e3a5f' : '#fff',
                    color: statusFilter === s ? '#fff' : '#64748b',
                    border: '1px solid #e2e8f0' }} />
              ))}
            </Box>
          </Box>

          {/* Candidates table */}
          <Card sx={{ borderRadius: 2, overflow: 'hidden' }}>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                    {['#', 'Candidate', 'Position', 'Status', 'Rounds', 'Avg Rating', 'Source', 'Added By', 'Actions']
                      .map(h => <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569', py: 1.5 }}>{h}</TableCell>)}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredCandidates.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={9} align="center" sx={{ py: 6 }}>
                        <WorkIcon sx={{ fontSize: 48, color: '#cbd5e1', mb: 1, display: 'block', mx: 'auto' }} />
                        <Typography color="text.secondary">No candidates found</Typography>
                      </TableCell>
                    </TableRow>
                  ) : filteredCandidates.slice(candPage * candRpp, (candPage + 1) * candRpp).map((c, i) => (
                    <TableRow key={c.id} hover sx={{ cursor: 'pointer', bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}
                      onClick={() => openDetail(c)}>
                      <TableCell sx={{ fontSize: 13, color: '#94a3b8', fontWeight: 600 }}>{candPage * candRpp + i + 1}</TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                          <Avatar sx={{ bgcolor: '#1e3a5f', width: 32, height: 32, fontSize: 13 }}>
                            {c.name?.charAt(0).toUpperCase()}
                          </Avatar>
                          <Box>
                            <Typography variant="body2" fontWeight={600}>{c.name}</Typography>
                            {c.email && <Typography variant="caption" color="text.secondary">{c.email}</Typography>}
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell sx={{ fontSize: 13 }}>
                        <Typography variant="body2" fontWeight={600}>{c.position}</Typography>
                        {c.department && <Typography variant="caption" color="text.secondary">{c.department}</Typography>}
                      </TableCell>
                      <TableCell><StatusChip status={c.status} /></TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                          <Chip label={`${c.completedRounds}/${c.totalRounds}`} size="small"
                            sx={{ bgcolor: '#f1f5f9', color: '#475569', fontWeight: 600, fontSize: 11 }} />
                          {c.currentRoundType && (
                            <Typography variant="caption" sx={{ color: ROUND_TYPE_COLOR[c.currentRoundType] || '#64748b', fontWeight: 600 }}>
                              {c.currentRoundType.replace('_', ' ')}
                            </Typography>
                          )}
                        </Box>
                      </TableCell>
                      <TableCell>
                        {c.averageRating != null ? (
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            <StarIcon sx={{ fontSize: 14, color: '#f59e0b' }} />
                            <Typography variant="body2" fontWeight={600}>{c.averageRating}</Typography>
                          </Box>
                        ) : '—'}
                      </TableCell>
                      <TableCell sx={{ fontSize: 13, color: '#64748b' }}>{c.source || '—'}</TableCell>
                      <TableCell sx={{ fontSize: 13 }}>{c.createdByName || '—'}</TableCell>
                      <TableCell onClick={e => e.stopPropagation()}>
                        <Box sx={{ display: 'flex', gap: 0.5 }}>
                          {canSchedule && (
                            <Tooltip title="Edit">
                              <IconButton size="small" onClick={e => openEditCandidate(c, e)}>
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            {filteredCandidates.length > candRpp && (
              <TablePagination component="div" count={filteredCandidates.length}
                page={candPage} onPageChange={(_, p) => setCandPage(p)}
                rowsPerPage={candRpp} rowsPerPageOptions={[10, 25, 50]}
                onRowsPerPageChange={e => { setCandRpp(parseInt(e.target.value, 10)); setCandPage(0); }} />
            )}
          </Card>
        </Box>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* MY INTERVIEWS TAB                                                   */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {tab === 'my-rounds' && (
        <Box>
          {/* Notification bell + panel */}
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
            <Tooltip title={unreadCount > 0 ? `${unreadCount} unread notification${unreadCount > 1 ? 's' : ''}` : 'Notifications'}>
              <IconButton onClick={async () => {
                setNotifOpen(o => !o);
                if (unreadCount > 0) {
                  await interviewApi.markAllRead().catch(() => {});
                  setUnreadCount(0);
                  setNotifications(prev => prev.map(n => ({ ...n, read: true })));
                }
              }}>
                <Badge badgeContent={unreadCount} color="error">
                  <NotificationsIcon sx={{ color: '#1e3a5f' }} />
                </Badge>
              </IconButton>
            </Tooltip>
          </Box>

          {/* Notification panel */}
          {notifOpen && (
            <Card sx={{ mb: 3, border: '1px solid #e2e8f0', borderRadius: 3 }}>
              <Box sx={{ px: 2, py: 1.5, borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Typography fontWeight={700} fontSize={14}>Interview Notifications</Typography>
                <IconButton size="small" onClick={() => setNotifOpen(false)}><CancelIcon fontSize="small" /></IconButton>
              </Box>
              {notifications.length === 0 ? (
                <Box sx={{ py: 3, textAlign: 'center' }}>
                  <Typography variant="body2" color="text.secondary">No notifications yet</Typography>
                </Box>
              ) : (
                <Box sx={{ maxHeight: 320, overflowY: 'auto' }}>
                  {notifications.map(n => (
                    <Box key={n.id} sx={{ px: 2, py: 1.5, borderBottom: '1px solid #f1f5f9',
                      bgcolor: n.read ? '#fff' : '#eff6ff' }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        <Typography variant="body2" fontWeight={700}>{n.title}</Typography>
                        {!n.read && <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: '#3b82f6', mt: 0.5 }} />}
                      </Box>
                      <Typography variant="caption" color="text.secondary">{n.message}</Typography>
                      {n.scheduledAt && (
                        <Typography variant="caption" display="block" color="primary" mt={0.5}>
                          {new Date(n.scheduledAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
                        </Typography>
                      )}
                    </Box>
                  ))}
                </Box>
              )}
            </Card>
          )}

          {/* Today's interviews banner */}
          {(() => {
            const today = new Date(); today.setHours(0,0,0,0);
            const tomorrow = new Date(today); tomorrow.setDate(tomorrow.getDate() + 1);
            const todayRounds = myRounds.filter(r => {
              if (!r.scheduledAt || r.status === 'CANCELLED') return false;
              const d = new Date(r.scheduledAt); return d >= today && d < tomorrow;
            });
            if (todayRounds.length === 0) return null;
            return (
              <Card sx={{ mb: 3, border: '2px solid #f59e0b', bgcolor: '#fffbeb', borderRadius: 3 }}>
                <Box sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
                  <TodayIcon sx={{ color: '#d97706' }} />
                  <Typography fontWeight={700} color="warning.dark">
                    Today's Interviews ({todayRounds.length})
                  </Typography>
                </Box>
                <Divider />
                {todayRounds.map(r => (
                  <Box key={r.id} sx={{ px: 2, py: 1.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    borderBottom: '1px solid #fde68a' }}>
                    <Box>
                      <Typography variant="body2" fontWeight={700}>{r.candidateName}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {r.roundType?.replace('_',' ')} · {new Date(r.scheduledAt).toLocaleTimeString('en-IN', { timeStyle: 'short' })}
                        {r.location ? ` · ${r.location}` : ''}
                      </Typography>
                    </Box>
                    <Tooltip title="Download calendar invite (.ics)">
                      <IconButton size="small" onClick={() => downloadIcs(r.id)} sx={{ color: '#d97706' }}>
                        <DownloadIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Box>
                ))}
              </Card>
            );
          })()}

          {/* Filter chips */}
          <Box sx={{ display: 'flex', gap: 1, mb: 3, flexWrap: 'wrap' }}>
            {[
              ['ALL', `All (${myRounds.length})`],
              ['SCHEDULED',   `Scheduled (${myRounds.filter(r => r.status === 'SCHEDULED').length})`],
              ['IN_PROGRESS', `In Progress (${myRounds.filter(r => r.status === 'IN_PROGRESS').length})`],
              ['COMPLETED',   `Completed (${myRounds.filter(r => r.status === 'COMPLETED').length})`],
            ].map(([val, label]) => (
              <Chip key={val} label={label} onClick={() => setMyRoundFilter(val)} size="small"
                sx={{ cursor: 'pointer', fontWeight: 600,
                  bgcolor: myRoundFilter === val ? '#1e3a5f' : '#fff',
                  color: myRoundFilter === val ? '#fff' : '#64748b',
                  border: '1px solid #e2e8f0' }} />
            ))}
          </Box>

          {filteredMyRounds.length === 0 ? (
            <Card sx={{ textAlign: 'center', py: 6 }}>
              <CalendarMonthIcon sx={{ fontSize: 56, color: '#cbd5e1', mb: 2 }} />
              <Typography color="text.secondary">No interviews assigned to you</Typography>
            </Card>
          ) : (
            <Grid container spacing={2}>
              {filteredMyRounds.map((round) => {
                const typeColor = ROUND_TYPE_COLOR[round.roundType] || '#64748b';
                const myFeedback = round.feedbacks?.find(f => f.submittedByName != null);
                const canSubmitFeedback = round.status === 'IN_PROGRESS' && !round.feedbackSubmitted;
                const canEditFeedback   = round.feedbackSubmitted && round.feedbacks?.length > 0;

                return (
                  <Grid item xs={12} sm={6} md={4} key={round.id}>
                    <Card sx={{ borderRadius: 3, height: '100%', display: 'flex', flexDirection: 'column',
                      border: round.status === 'IN_PROGRESS' ? '2px solid #f59e0b' : '1px solid #e2e8f0' }}>
                      {/* Card header */}
                      <Box sx={{ p: 2, background: `linear-gradient(135deg, ${typeColor}22 0%, #f8fafc 100%)`,
                        borderBottom: '1px solid #e2e8f0' }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Box sx={{ width: 32, height: 32, borderRadius: '50%', bgcolor: typeColor,
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              color: '#fff', fontWeight: 800, fontSize: 13 }}>
                              {round.roundNumber}
                            </Box>
                            <Box>
                              <Typography variant="body2" fontWeight={700}>{round.roundType?.replace('_', ' ')} Round</Typography>
                              <Typography variant="caption" color="text.secondary">Round {round.roundNumber}</Typography>
                            </Box>
                          </Box>
                          <RoundStatusChip status={round.status} />
                        </Box>
                      </Box>

                      <CardContent sx={{ flex: 1, pt: 1.5 }}>
                        {/* Candidate info */}
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
                          <Avatar sx={{ bgcolor: '#1e3a5f', width: 36, height: 36, fontSize: 15 }}>
                            {round.candidateName?.charAt(0).toUpperCase()}
                          </Avatar>
                          <Box>
                            <Typography variant="body2" fontWeight={700}>{round.candidateName}</Typography>
                            <Typography variant="caption" color="text.secondary">{round.candidatePosition}</Typography>
                          </Box>
                        </Box>

                        <Divider sx={{ mb: 1.5 }} />

                        {/* Details */}
                        <Stack spacing={0.75}>
                          {round.scheduledAt && (
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <CalendarMonthIcon sx={{ fontSize: 15, color: '#94a3b8' }} />
                              <Typography variant="caption">{fmtDt(round.scheduledAt)}</Typography>
                            </Box>
                          )}
                          {round.durationMinutes && (
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <AccessTimeIcon sx={{ fontSize: 15, color: '#94a3b8' }} />
                              <Typography variant="caption">{round.durationMinutes} minutes</Typography>
                            </Box>
                          )}
                          {round.location && (
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <LocationOnIcon sx={{ fontSize: 15, color: '#94a3b8' }} />
                              <Typography variant="caption" sx={{ wordBreak: 'break-all' }}>{round.location}</Typography>
                            </Box>
                          )}
                          {round.assignedByName && (
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              <PersonAddIcon sx={{ fontSize: 15, color: '#94a3b8' }} />
                              <Typography variant="caption">Assigned by {round.assignedByName}</Typography>
                            </Box>
                          )}
                        </Stack>

                        {round.managerNotes && (
                          <Box sx={{ mt: 1.5, p: 1.5, bgcolor: '#fff7ed', borderRadius: 1.5, border: '1px solid #fed7aa' }}>
                            <Typography variant="caption" fontWeight={700} color="warning.dark">Notes from Manager</Typography>
                            <Typography variant="caption" display="block" color="warning.dark">{round.managerNotes}</Typography>
                          </Box>
                        )}

                        {/* Completed feedback summary */}
                        {round.feedbackSubmitted && round.averageRating && (
                          <Box sx={{ mt: 1.5, p: 1.5, bgcolor: '#f0fdf4', borderRadius: 1.5, border: '1px solid #bbf7d0' }}>
                            <Typography variant="caption" fontWeight={700} color="success.dark">Feedback Submitted</Typography>
                            <RatingStars value={round.averageRating} />
                          </Box>
                        )}
                      </CardContent>

                      {/* Card actions */}
                      <Box sx={{ p: 2, pt: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
                        {round.status === 'SCHEDULED' && (
                          <Button fullWidth variant="contained" startIcon={<PlayArrowIcon />}
                            onClick={() => handleStartRound(round.id)}
                            sx={{ textTransform: 'none', bgcolor: '#1d4ed8', '&:hover': { bgcolor: '#1e40af' } }}>
                            Start Interview
                          </Button>
                        )}
                        {canSubmitFeedback && (
                          <Button fullWidth variant="contained" startIcon={<StarIcon />}
                            onClick={() => navigate(`/interviews/feedback/${round.id}`, { state: { round } })}
                            sx={{ textTransform: 'none', bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
                            Submit Feedback
                          </Button>
                        )}
                        {canEditFeedback && (
                          <Button fullWidth variant="outlined" startIcon={<EditIcon />}
                            onClick={() => navigate(`/interviews/feedback/${round.id}`, { state: { round, existingFeedback: round.feedbacks[0] } })}
                            sx={{ textTransform: 'none' }}>
                            Edit Feedback
                          </Button>
                        )}
                        {round.status !== 'CANCELLED' && round.scheduledAt && (
                          <Button fullWidth variant="outlined" size="small" startIcon={<DownloadIcon />}
                            onClick={() => downloadIcs(round.id)}
                            sx={{ textTransform: 'none', borderColor: '#94a3b8', color: '#475569',
                              '&:hover': { borderColor: '#1e3a5f', color: '#1e3a5f' } }}>
                            Add to Calendar
                          </Button>
                        )}
                        {round.status === 'CANCELLED' && (
                          <Typography variant="caption" color="text.disabled" sx={{ textAlign: 'center', display: 'block' }}>
                            This round was cancelled
                          </Typography>
                        )}
                      </Box>
                    </Card>
                  </Grid>
                );
              })}
            </Grid>
          )}
        </Box>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* REPORTS TAB                                                         */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {tab === 'reports' && canManage && stats && (
        <Box>
          {/* Stat cards */}
          {/* <Grid container spacing={2} sx={{ mb: 3 }}>
            {[
              { label: 'Total Candidates',  val: stats.total,       color: '#1e3a5f', bg: '#eff6ff' },
              { label: 'In Progress',       val: stats.inProgress,  color: '#1d4ed8', bg: '#dbeafe' },
              { label: 'Selected',          val: stats.selected,    color: '#16a34a', bg: '#dcfce7' },
              { label: 'Rejected',          val: stats.rejected,    color: '#dc2626', bg: '#fee2e2' },
              { label: 'On Hold',           val: stats.onHold,      color: '#d97706', bg: '#fff7ed' },
              { label: 'Pending',           val: stats.pending,     color: '#7c3aed', bg: '#f5f3ff' },
            ].map(({ label, val, color, bg }) => (
              <Grid item xs={6} sm={4} md={2} key={label}>
                <Card sx={{ borderRadius: 2, textAlign: 'center', p: 2, bgcolor: bg, border: `1px solid ${color}22` }}>
                  <Typography variant="h4" fontWeight={800} sx={{ color }}>{val}</Typography>
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>{label}</Typography>
                </Card>
              </Grid>
            ))}
          </Grid> */}

          {/* Round and rating stats */}
          <Grid container spacing={3} sx={{ mb: 3 }}>
            <Grid item xs={12} md={6}>
              <Card sx={{ borderRadius: 2, p: 3 }}>
                <Typography variant="subtitle1" fontWeight={700} mb={2}>Interview Funnel</Typography>
                {[
                  { label: 'Total Candidates',    val: stats.total,       max: stats.total,    color: '#1e3a5f' },
                  { label: 'In Progress',         val: stats.inProgress,  max: stats.total,    color: '#1d4ed8' },
                  { label: 'Selected',            val: stats.selected,    max: stats.total,    color: '#16a34a' },
                  { label: 'On Hold',             val: stats.onHold,      max: stats.total,    color: '#d97706' },
                  { label: 'Rejected',            val: stats.rejected,    max: stats.total,    color: '#dc2626' },
                ].map(({ label, val, max, color }) => (
                  <Box key={label} sx={{ mb: 1.5 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                      <Typography variant="body2" color="text.secondary">{label}</Typography>
                      <Typography variant="body2" fontWeight={700} sx={{ color }}>{val}</Typography>
                    </Box>
                    <LinearProgress variant="determinate"
                      value={max > 0 ? (val / max) * 100 : 0}
                      sx={{ height: 8, borderRadius: 4, bgcolor: '#f1f5f9',
                        '& .MuiLinearProgress-bar': { bgcolor: color, borderRadius: 4 } }} />
                  </Box>
                ))}
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card sx={{ borderRadius: 2, p: 3 }}>
                <Typography variant="subtitle1" fontWeight={700} mb={2}>Round Statistics</Typography>
                <Grid container spacing={2}>
                  {[
                    { label: 'Total Rounds',    val: stats.totalRounds,     color: '#1e3a5f', bg: '#eff6ff' },
                    { label: 'Completed',       val: stats.completedRounds, color: '#16a34a', bg: '#dcfce7' },
                    { label: 'Scheduled',       val: stats.scheduledRounds, color: '#1d4ed8', bg: '#dbeafe' },
                  ].map(({ label, val, color, bg }) => (
                    <Grid item xs={4} key={label}>
                      <Box sx={{ textAlign: 'center', p: 2, bgcolor: bg, borderRadius: 2 }}>
                        <Typography variant="h5" fontWeight={800} sx={{ color }}>{val}</Typography>
                        <Typography variant="caption" color="text.secondary">{label}</Typography>
                      </Box>
                    </Grid>
                  ))}
                </Grid>
                <Divider sx={{ my: 2 }} />
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box>
                    <Typography variant="body2" color="text.secondary">Selection Rate</Typography>
                    <Typography variant="h5" fontWeight={800} sx={{ color: '#16a34a' }}>{stats.selectionRate}%</Typography>
                  </Box>
                  <Box>
                    <Typography variant="body2" color="text.secondary">Avg. Feedback Rating</Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Typography variant="h5" fontWeight={800} sx={{ color: '#f59e0b' }}>{stats.averageRating || 'N/A'}</Typography>
                      {stats.averageRating > 0 && <Typography variant="body2" color="text.secondary">/ 5</Typography>}
                    </Box>
                  </Box>
                  <Box>
                    <Typography variant="body2" color="text.secondary">Rejection Rate</Typography>
                    <Typography variant="h5" fontWeight={800} sx={{ color: '#dc2626' }}>{stats.rejectionRate}%</Typography>
                  </Box>
                </Box>
              </Card>
            </Grid>
          </Grid>

          {/* Candidates summary table */}
          <Card sx={{ borderRadius: 2, overflow: 'hidden' }}>
            <Box sx={{ p: 2, borderBottom: '1px solid #e2e8f0' }}>
              <Typography variant="subtitle1" fontWeight={700}>All Candidates Overview</Typography>
            </Box>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                    {['Candidate', 'Position', 'Status', 'Rounds Done', 'Avg Rating', 'Recommendation', 'Added On']
                      .map(h => <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569', py: 1.5 }}>{h}</TableCell>)}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {candidates.map((c, i) => {
                    const allFeedbacks = (c.rounds || []).flatMap(r => r.feedbacks || []);
                    const recs = allFeedbacks.map(f => f.recommendation).filter(Boolean);
                    const lastRec = recs.length > 0 ? recs[recs.length - 1] : null;
                    const rec = lastRec ? REC_COLOR[lastRec] : null;
                    return (
                      <TableRow key={c.id} hover sx={{ cursor: 'pointer', bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}
                        onClick={() => { openDetail(c); setTab('candidates'); }}>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                            <Avatar sx={{ bgcolor: '#1e3a5f', width: 28, height: 28, fontSize: 12 }}>
                              {c.name?.charAt(0)}
                            </Avatar>
                            <Typography variant="body2" fontWeight={600}>{c.name}</Typography>
                          </Box>
                        </TableCell>
                        <TableCell sx={{ fontSize: 13 }}>{c.position}</TableCell>
                        <TableCell><StatusChip status={c.status} /></TableCell>
                        <TableCell sx={{ fontSize: 13 }}>{c.completedRounds} / {c.totalRounds}</TableCell>
                        <TableCell>
                          {c.averageRating != null ? (
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                              <StarIcon sx={{ fontSize: 14, color: '#f59e0b' }} />
                              <Typography variant="body2" fontWeight={600}>{c.averageRating}</Typography>
                            </Box>
                          ) : '—'}
                        </TableCell>
                        <TableCell>
                          {lastRec ? (
                            <Chip label={lastRec.replace('_', ' ')} size="small"
                              sx={{ bgcolor: rec?.bg, color: rec?.color, fontWeight: 700, fontSize: 11 }} />
                          ) : '—'}
                        </TableCell>
                        <TableCell sx={{ fontSize: 13, color: '#64748b' }}>{fmtDate(c.createdAt)}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>
        </Box>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* AUDIT LOG TAB                                                        */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {tab === 'audit' && isAdmin && (
        <Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="subtitle1" fontWeight={700}>System Audit Log</Typography>
            <Button size="small" variant="outlined" startIcon={<AssessmentIcon />}
              onClick={async () => { const logs = await interviewApi.getAuditLogs(); setAuditLogs(Array.isArray(logs) ? logs : []); }}
              sx={{ textTransform: 'none' }}>
              Refresh
            </Button>
          </Box>
          <AuditLogLoader setAuditLogs={setAuditLogs} auditLogs={auditLogs} fmtDt={fmtDt} />
        </Box>
      )}

      {renderDialogs()}
    </Box>
  );
};

/* ── lazy audit log loader ─────────────────────────────────────────────── */
const AuditLogLoader = ({ auditLogs, setAuditLogs, fmtDt }) => {
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const rpp = 20;

  useEffect(() => {
    if (loaded) return;
    setLoading(true);
    interviewApi.getAuditLogs()
      .then(logs => { setAuditLogs(Array.isArray(logs) ? logs : []); setLoaded(true); })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [loaded, setAuditLogs]);

  if (loading) return <Box sx={{ textAlign: 'center', py: 4 }}><CircularProgress sx={{ color: '#1e3a5f' }} /></Box>;

  return (
    <Card sx={{ borderRadius: 2, overflow: 'hidden' }}>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow sx={{ bgcolor: '#f1f5f9' }}>
              {['#', 'Action', 'Entity', 'Performed By', 'Details', 'Date'].map(h => (
                <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569', py: 1.5 }}>{h}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {auditLogs.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                  <HistoryIcon sx={{ fontSize: 48, color: '#cbd5e1', mb: 1, display: 'block', mx: 'auto' }} />
                  <Typography color="text.secondary">No audit logs yet</Typography>
                </TableCell>
              </TableRow>
            ) : auditLogs.slice(page * rpp, (page + 1) * rpp).map((log, i) => (
              <TableRow key={log.id} hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
                <TableCell sx={{ fontSize: 13, color: '#94a3b8', fontWeight: 600 }}>{page * rpp + i + 1}</TableCell>
                <TableCell>
                  <Chip label={log.action.replace(/_/g, ' ')} size="small"
                    sx={{ bgcolor: '#f1f5f9', color: '#1e3a5f', fontWeight: 700, fontSize: 11 }} />
                </TableCell>
                <TableCell sx={{ fontSize: 13 }}>
                  <Typography variant="caption" fontWeight={600}>{log.entityType}</Typography>
                  <Typography variant="caption" color="text.secondary" display="block">#{log.entityId}</Typography>
                </TableCell>
                <TableCell sx={{ fontSize: 13 }}>{log.performedByName || '—'}</TableCell>
                <TableCell sx={{ fontSize: 12.5, color: '#475569', maxWidth: 280 }}>
                  <Typography variant="caption" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                    {log.details}
                  </Typography>
                </TableCell>
                <TableCell sx={{ fontSize: 13, color: '#64748b', whiteSpace: 'nowrap' }}>{fmtDt(log.createdAt)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {auditLogs.length > rpp && (
        <TablePagination component="div" count={auditLogs.length}
          page={page} onPageChange={(_, p) => setPage(p)}
          rowsPerPage={rpp} rowsPerPageOptions={[20]} />
      )}
    </Card>
  );
};

export default InterviewsPage;
