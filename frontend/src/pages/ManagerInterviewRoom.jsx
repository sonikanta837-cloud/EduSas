import React, { useEffect, useRef, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box, Typography, Card, CardContent, Button, Chip, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, Rating, Grid, LinearProgress,
  Alert, CircularProgress, Divider, Tooltip, IconButton, Badge
} from '@mui/material';
import ArrowBackIcon      from '@mui/icons-material/ArrowBack';
import RefreshIcon        from '@mui/icons-material/Refresh';
import CheckCircleIcon    from '@mui/icons-material/CheckCircle';
import CancelIcon         from '@mui/icons-material/Cancel';
import WarningAmberIcon   from '@mui/icons-material/WarningAmber';
import VideocamIcon       from '@mui/icons-material/Videocam';
import VideocamOffIcon    from '@mui/icons-material/VideocamOff';
import { interviewApi }  from '../api/interviewApi';
import { toast }         from 'react-toastify';

/* ─────────────────── Constants ─────────────────────────────────────────── */
const STUN_CONFIG = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' }] };
const STATUS_CFG = {
  PENDING_LINK:        { label: 'Link Not Generated', bg: '#f1f5f9', color: '#475569' },
  LINK_GENERATED:      { label: 'Waiting for Candidate', bg: '#dbeafe', color: '#1d4ed8' },
  IN_PROGRESS:         { label: 'In Progress', bg: '#fef3c7', color: '#d97706' },
  CANDIDATE_SUBMITTED: { label: 'Candidate Submitted', bg: '#dcfce7', color: '#16a34a' },
  EVALUATED:           { label: 'Evaluated', bg: '#f3e8ff', color: '#7c3aed' },
};
const EMPTY_EVAL = {
  technicalSkillsRating: 3, communicationRating: 3,
  problemSolvingRating: 3, codingAbilityRating: 3,
  architectureKnowledgeRating: 3, comments: '', decision: '',
};

/* ═══════════════════════════════════════════════════════════════════════ */
export default function ManagerInterviewRoom() {
  const { id }   = useParams();
  const navigate = useNavigate();

  const [room,       setRoom]      = useState(null);
  const [loading,    setLoading]   = useState(true);
  const [refreshing, setRefreshing]= useState(false);
  const [evalDialog, setEvalDialog]= useState(false);
  const [evalForm,   setEvalForm]  = useState(EMPTY_EVAL);
  const [saving,     setSaving]    = useState(false);
  const [liveConn,   setLiveConn]  = useState(false);

  const remoteVideoRef = useRef(null);
  const pcRef          = useRef(null);
  const pollIntervalRef= useRef(null);
  const iceSeenRef     = useRef(0);

  /* ── Load room data ── */
  const loadRoom = useCallback(async () => {
    try {
      const data = await interviewApi.getManagerRoom(id);
      setRoom(data);
    } catch (e) {
      toast.error('Failed to load interview room');
    } finally {
      setLoading(false);
    }
  }, [id]);

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      const data = await interviewApi.getManagerRoom(id);
      setRoom(data);
    } catch (e) {
      toast.error('Failed to refresh');
    } finally {
      setRefreshing(false);
    }
  }, [id]);

  const handleBack = () => {
    if (window.history.length <= 1) {
      window.close();
      // Fallback if close is blocked by the browser
      navigate('/interviews');
    } else {
      navigate(-1);
    }
  };

  useEffect(() => {
    loadRoom();
    // Poll every 5 seconds for live updates when in progress
    pollIntervalRef.current = setInterval(() => loadRoom(), 5000);
    return () => {
      clearInterval(pollIntervalRef.current);
      if (pcRef.current) { pcRef.current.close(); pcRef.current = null; }
    };
  }, [loadRoom]);

  /* ── WebRTC: join candidate's stream ── */
  const joinVideoCall = async () => {
    try {
      // Poll for candidate's offer
      let polls = 0;
      const offerPoller = setInterval(async () => {
        if (++polls > 30) { clearInterval(offerPoller); return; }
        const res = await interviewApi.getOffer(id);
        if (res?.sdp) {
          clearInterval(offerPoller);
          await setupWebRTCAnswer(JSON.parse(res.sdp));
        }
      }, 2000);
    } catch (e) {
      toast.warning('Could not connect to candidate video');
    }
  };

  const setupWebRTCAnswer = async (offer) => {
    const pc = new RTCPeerConnection(STUN_CONFIG);
    pcRef.current = pc;

    pc.ontrack = e => {
      if (remoteVideoRef.current && e.streams[0]) {
        remoteVideoRef.current.srcObject = e.streams[0];
        setLiveConn(true);
      }
    };

    pc.onicecandidate = async e => {
      if (e.candidate) {
        await interviewApi.addManagerIce(id, JSON.stringify(e.candidate)).catch(() => {});
      }
    };

    await pc.setRemoteDescription(offer);
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    await interviewApi.saveAnswerSdp(id, JSON.stringify(answer));

    // Poll for candidate's ICE candidates
    const icePoller = setInterval(async () => {
      const list = await interviewApi.getCandidateIce(id).catch(() => []);
      if (list.length > iceSeenRef.current) {
        for (let i = iceSeenRef.current; i < list.length; i++) {
          await pc.addIceCandidate(JSON.parse(list[i])).catch(() => {});
        }
        iceSeenRef.current = list.length;
      }
    }, 2000);
    setTimeout(() => clearInterval(icePoller), 120000);
  };

  /* ── Evaluation submit ── */
  const handleEvaluate = async () => {
    if (!evalForm.decision) { toast.error('Please select Approve or Reject'); return; }
    setSaving(true);
    try {
      await interviewApi.submitManagerEvaluation(id, evalForm);
      toast.success(evalForm.decision === 'APPROVE'
        ? 'Approved — candidate moves to Final Round'
        : 'Rejected — candidate notified');
      setEvalDialog(false);
      loadRoom();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to submit evaluation');
    } finally {
      setSaving(false);
    }
  };

  /* ── Helpers ── */
  const fmtDt = v => v ? new Date(v).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
      <CircularProgress />
    </Box>
  );
  if (!room) return <Alert severity="error" sx={{ m: 3 }}>Interview room not found.</Alert>;

  const status    = room.interviewStatus;
  const statusCfg = STATUS_CFG[status] || { label: status, bg: '#f1f5f9', color: '#475569' };
  const scorePercent = room.totalMarks > 0 ? Math.round((room.score / room.totalMarks) * 100) : 0;
  const canEval   = status === 'CANDIDATE_SUBMITTED' || status === 'IN_PROGRESS';
  const evaluated = status === 'EVALUATED';

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1400, mx: 'auto' }}>

      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <IconButton onClick={handleBack} sx={{ bgcolor: '#f1f5f9' }}>
          <ArrowBackIcon />
        </IconButton>
        <Box sx={{ flex: 1 }}>
          <Typography variant="h5" fontWeight={700}>
            Interview Room — {room.candidateName}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {room.appliedProfile} · {room.candidateEmail}
          </Typography>
        </Box>
        <Chip label={statusCfg.label}
          sx={{ bgcolor: statusCfg.bg, color: statusCfg.color, fontWeight: 700, px: 1 }} />
        <Tooltip title="Refresh">
          <IconButton onClick={handleRefresh} disabled={refreshing}>
            <RefreshIcon sx={{
              animation: refreshing ? 'spin 0.8s linear infinite' : 'none',
              '@keyframes spin': { '0%': { transform: 'rotate(0deg)' }, '100%': { transform: 'rotate(360deg)' } },
            }} />
          </IconButton>
        </Tooltip>
        {canEval && (
          <Button variant="contained" onClick={() => { setEvalForm(EMPTY_EVAL); setEvalDialog(true); }}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', fontWeight: 700 }}>
            Submit Evaluation
          </Button>
        )}
      </Box>

      <Grid container spacing={3}>

        {/* ── Left: Video + Stats ── */}
        <Grid item xs={12} md={4}>

          {/* Live video */}
          <Card sx={{ borderRadius: 3, mb: 2 }}>
            <CardContent sx={{ p: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                <Typography variant="subtitle2" fontWeight={700}>Live Camera</Typography>
                <Badge color={liveConn ? 'success' : 'default'} variant="dot">
                  <Chip icon={liveConn ? <VideocamIcon /> : <VideocamOffIcon />}
                    label={liveConn ? 'Connected' : 'Not Connected'}
                    size="small"
                    sx={{ bgcolor: liveConn ? '#dcfce7' : '#f1f5f9', color: liveConn ? '#16a34a' : '#64748b' }} />
                </Badge>
              </Box>
              <video ref={remoteVideoRef} autoPlay playsInline
                style={{ width: '100%', borderRadius: 8, background: '#1e293b', aspectRatio: '16/9', objectFit: 'cover', display: 'block' }} />
              {!liveConn && status === 'IN_PROGRESS' && (
                <Button fullWidth variant="outlined" size="small" onClick={joinVideoCall}
                  sx={{ mt: 1.5, textTransform: 'none', borderColor: '#6366f1', color: '#6366f1' }}>
                  Connect to Live Video
                </Button>
              )}
              {status === 'CANDIDATE_SUBMITTED' && room.videoUrl && (
                <Button fullWidth variant="outlined" size="small"
                  href={`/api${room.videoUrl}`} target="_blank"
                  sx={{ mt: 1.5, textTransform: 'none' }}>
                  Watch Recording
                </Button>
              )}
            </CardContent>
          </Card>

          {/* Interview stats */}
          <Card sx={{ borderRadius: 3, mb: 2 }}>
            <CardContent sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Session Info</Typography>
              {[
                ['Started',   fmtDt(room.startedAt)],
                ['Submitted', fmtDt(room.completedAt)],
                ['Duration',  `${room.durationMinutes} min`],
                ['Questions', `${room.questionsWithAnswers?.length || 0}`],
                ['Violations',room.violationCount || 0],
              ].map(([k, v]) => (
                <Box key={k} sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="caption" color="text.secondary">{k}</Typography>
                  <Typography variant="caption" fontWeight={600}>{v}</Typography>
                </Box>
              ))}
            </CardContent>
          </Card>

          {/* Score */}
          {room.score != null && (
            <Card sx={{ borderRadius: 3, mb: 2 }}>
              <CardContent sx={{ p: 2 }}>
                <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1 }}>MCQ Auto-Score</Typography>
                <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mb: 1.5 }}>
                  <Typography variant="h3" fontWeight={800} color="primary">{room.score}</Typography>
                  <Typography variant="body2" color="text.secondary">/ {room.totalMarks} marks</Typography>
                </Box>
                <LinearProgress variant="determinate" value={scorePercent}
                  sx={{ height: 8, borderRadius: 4, bgcolor: '#e2e8f0', '& .MuiLinearProgress-bar': { bgcolor: scorePercent >= 70 ? '#059669' : scorePercent >= 40 ? '#d97706' : '#dc2626' } }} />
                <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                  {scorePercent}% — Text answers require manual review
                </Typography>
              </CardContent>
            </Card>
          )}

          {/* Violations */}
          {(room.violations?.length > 0) && (
            <Card sx={{ borderRadius: 3, border: '1px solid #fecaca' }}>
              <CardContent sx={{ p: 2 }}>
                <Typography variant="subtitle2" fontWeight={700} color="error.main" sx={{ mb: 1.5 }}>
                  ⚠️ Violations ({room.violations.length})
                </Typography>
                {room.violations.map((v, i) => (
                  <Box key={i} sx={{ mb: 1, pb: 1, borderBottom: i < room.violations.length - 1 ? '1px solid #fee2e2' : 'none' }}>
                    <Chip label={`#${v.violationNumber} ${v.violationType}`} size="small"
                      sx={{ bgcolor: '#fee2e2', color: '#dc2626', fontWeight: 700, fontSize: 10, mb: 0.5 }} />
                    <Typography variant="caption" display="block" color="text.secondary">{v.description}</Typography>
                    <Typography variant="caption" display="block" color="text.disabled">
                      {v.occurredAt ? new Date(v.occurredAt).toLocaleTimeString() : ''}
                    </Typography>
                  </Box>
                ))}
              </CardContent>
            </Card>
          )}
        </Grid>

        {/* ── Right: Questions & Answers ── */}
        <Grid item xs={12} md={8}>
          <Card sx={{ borderRadius: 3 }}>
            <CardContent sx={{ p: 3 }}>
              <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 2 }}>
                Questions & Answers ({room.questionsWithAnswers?.length || 0})
              </Typography>

              {(room.questionsWithAnswers || []).map((qa, idx) => (
                <Box key={qa.questionId} sx={{ mb: 3, pb: 3, borderBottom: idx < room.questionsWithAnswers.length - 1 ? '1px solid #f1f5f9' : 'none' }}>
                  {/* Question header */}
                  <Box sx={{ display: 'flex', gap: 1, mb: 1.5, flexWrap: 'wrap' }}>
                    <Chip label={`Q${qa.questionNumber}`} size="small"
                      sx={{ bgcolor: '#f1f5f9', color: '#475569', fontWeight: 700 }} />
                    <Chip label={qa.questionType} size="small"
                      sx={{ bgcolor: qa.questionType === 'MCQ' ? '#ede9fe' : '#dcfce7',
                            color: qa.questionType === 'MCQ' ? '#7c3aed' : '#15803d', fontWeight: 700 }} />
                    <Chip label={`${qa.marks} marks`} size="small"
                      sx={{ bgcolor: '#fef3c7', color: '#92400e', fontWeight: 700 }} />
                    {qa.correct === true  && <Chip icon={<CheckCircleIcon />} label="Correct"  size="small" sx={{ bgcolor: '#dcfce7', color: '#16a34a', fontWeight: 700 }} />}
                    {qa.correct === false && <Chip icon={<CancelIcon />}      label="Wrong"    size="small" sx={{ bgcolor: '#fee2e2', color: '#dc2626', fontWeight: 700 }} />}
                  </Box>

                  {/* Question text */}
                  <Typography variant="body2" fontWeight={600} sx={{ mb: 1.5, color: '#1e293b', whiteSpace: 'pre-wrap' }}>
                    {qa.questionText}
                  </Typography>

                  {/* MCQ answer */}
                  {qa.questionType === 'MCQ' && qa.options && (
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, mb: 1 }}>
                      {qa.options.map((opt, oi) => {
                        const letter    = ['A','B','C','D'][oi];
                        const isCorrect = letter === qa.correctAnswer;
                        const isSelected= letter === qa.selectedOption;
                        return (
                          <Box key={letter} sx={{
                            display: 'flex', gap: 1.5, alignItems: 'center',
                            p: '8px 12px', borderRadius: 2,
                            bgcolor: isCorrect ? '#dcfce7' : isSelected ? '#fee2e2' : '#f8fafc',
                            border: `1px solid ${isCorrect ? '#86efac' : isSelected ? '#fca5a5' : '#e2e8f0'}`,
                          }}>
                            <Typography variant="caption" fontWeight={800}
                              sx={{ color: isCorrect ? '#16a34a' : isSelected ? '#dc2626' : '#94a3b8', minWidth: 18 }}>
                              {letter}
                            </Typography>
                            <Typography variant="caption" sx={{ flex: 1, color: '#374151' }}>{opt}</Typography>
                            {isCorrect  && <CheckCircleIcon sx={{ fontSize: 16, color: '#16a34a' }} />}
                            {isSelected && !isCorrect && <CancelIcon sx={{ fontSize: 16, color: '#dc2626' }} />}
                          </Box>
                        );
                      })}
                    </Box>
                  )}

                  {/* Text answer */}
                  {qa.questionType === 'TEXT' && (
                    <Box sx={{ p: 2, bgcolor: '#f8fafc', borderRadius: 2, border: '1px solid #e2e8f0' }}>
                      {qa.answerText ? (
                        <Typography variant="body2" sx={{ color: '#374151', whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>
                          {qa.answerText}
                        </Typography>
                      ) : (
                        <Typography variant="caption" color="text.disabled" fontStyle="italic">No answer provided</Typography>
                      )}
                    </Box>
                  )}
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Evaluation Dialog */}
      <Dialog open={evalDialog} onClose={() => !saving && setEvalDialog(false)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle fontWeight={700}>
          Submit Technical Evaluation
          <Typography variant="body2" color="text.secondary" fontWeight={400}>
            {room.candidateName} · {room.appliedProfile}
          </Typography>
        </DialogTitle>
        <DialogContent dividers>
          {[
            ['Technical Skills',      'technicalSkillsRating'],
            ['Communication',          'communicationRating'],
            ['Problem Solving',        'problemSolvingRating'],
            ['Coding Ability',         'codingAbilityRating'],
            ['Architecture Knowledge', 'architectureKnowledgeRating'],
          ].map(([label, field]) => (
            <Box key={field} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
              <Typography variant="body2" fontWeight={500}>{label}</Typography>
              <Rating value={evalForm[field]} onChange={(_, v) => setEvalForm(f => ({ ...f, [field]: v }))} />
            </Box>
          ))}
          <Divider sx={{ my: 2 }} />
          <TextField fullWidth multiline rows={3} label="Comments & Remarks"
            value={evalForm.comments}
            onChange={e => setEvalForm(f => ({ ...f, comments: e.target.value }))}
            sx={{ mb: 2 }} />
          <Box sx={{ display: 'flex', gap: 1.5 }}>
            <Button fullWidth variant={evalForm.decision === 'APPROVE' ? 'contained' : 'outlined'}
              onClick={() => setEvalForm(f => ({ ...f, decision: 'APPROVE' }))}
              startIcon={<CheckCircleIcon />}
              sx={{ textTransform: 'none', fontWeight: 700,
                ...(evalForm.decision === 'APPROVE' ? { bgcolor: '#059669', '&:hover': { bgcolor: '#047857' } } : { borderColor: '#059669', color: '#059669' }) }}>
              Approve
            </Button>
            <Button fullWidth variant={evalForm.decision === 'REJECT' ? 'contained' : 'outlined'}
              onClick={() => setEvalForm(f => ({ ...f, decision: 'REJECT' }))}
              startIcon={<CancelIcon />}
              sx={{ textTransform: 'none', fontWeight: 700,
                ...(evalForm.decision === 'REJECT' ? { bgcolor: '#dc2626', '&:hover': { bgcolor: '#b91c1c' } } : { borderColor: '#dc2626', color: '#dc2626' }) }}>
              Reject
            </Button>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setEvalDialog(false)} disabled={saving} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button variant="contained" onClick={handleEvaluate} disabled={saving || !evalForm.decision}
            sx={{ textTransform: 'none', fontWeight: 700, bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
            {saving ? 'Submitting…' : 'Submit Evaluation'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
