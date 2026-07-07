import React, { useEffect, useRef, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box, Typography, Card, CardContent, Button, Chip, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, Rating, Grid, Alert,
  CircularProgress, Divider, Tooltip, IconButton, Stack
} from '@mui/material';
import ArrowBackIcon    from '@mui/icons-material/ArrowBack';
import RefreshIcon      from '@mui/icons-material/Refresh';
import CheckCircleIcon  from '@mui/icons-material/CheckCircle';
import CancelIcon       from '@mui/icons-material/Cancel';
import PauseCircleIcon  from '@mui/icons-material/PauseCircle';
import VideocamIcon     from '@mui/icons-material/Videocam';
import VideocamOffIcon  from '@mui/icons-material/VideocamOff';
import { interviewApi } from '../api/interviewApi';
import { toast }        from 'react-toastify';

const STUN_CONFIG = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' }] };

const STATUS_CFG = {
  PENDING_LINK:        { label: 'Link Not Generated',   bg: '#f1f5f9', color: '#475569' },
  LINK_GENERATED:      { label: 'Waiting for Candidate',bg: '#dbeafe', color: '#1d4ed8' },
  IN_PROGRESS:         { label: 'In Progress',          bg: '#fef3c7', color: '#d97706' },
  CANDIDATE_SUBMITTED: { label: 'Candidate Submitted',  bg: '#dcfce7', color: '#16a34a' },
  EVALUATED:           { label: 'Evaluated',            bg: '#f3e8ff', color: '#7c3aed' },
};

const EMPTY_EVAL = {
  overallRating: 3, communicationRating: 3, cultureFitRating: 3,
  salaryDiscussion: '', expectedCtc: '', offeredCtc: '',
  noticePeriod: '', joiningDate: '', directorRemarks: '', finalDecision: '',
};

export default function DirectorInterviewRoom() {
  const { id }   = useParams();
  const navigate = useNavigate();

  const [room,       setRoom]       = useState(null);
  const [loading,    setLoading]    = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [evalDialog, setEvalDialog] = useState(false);
  const [evalForm,   setEvalForm]   = useState(EMPTY_EVAL);
  const [saving,     setSaving]     = useState(false);
  const [liveConn,   setLiveConn]   = useState(false);

  const remoteVideoRef = useRef(null);
  const pcRef          = useRef(null);
  const pollIntervalRef= useRef(null);
  const iceSeenRef     = useRef(0);

  /* ── Load room ──────────────────────────────────────────────────────────── */
  const loadRoom = useCallback(async () => {
    try {
      const data = await interviewApi.getDirectorRoom(id);
      setRoom(data);
    } catch {
      toast.error('Failed to load interview room');
    } finally {
      setLoading(false);
    }
  }, [id]);

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      const data = await interviewApi.getDirectorRoom(id);
      setRoom(data);
    } catch {
      toast.error('Failed to refresh');
    } finally {
      setRefreshing(false);
    }
  }, [id]);

  const handleBack = () => {
    if (window.history.length <= 1) { window.close(); navigate('/interviews'); }
    else navigate(-1);
  };

  useEffect(() => {
    loadRoom();
    pollIntervalRef.current = setInterval(() => loadRoom(), 5000);
    return () => {
      clearInterval(pollIntervalRef.current);
      if (pcRef.current) { pcRef.current.close(); pcRef.current = null; }
    };
  }, [loadRoom]);

  /* ── WebRTC answer ──────────────────────────────────────────────────────── */
  const joinVideoCall = async () => {
    try {
      let polls = 0;
      const offerPoller = setInterval(async () => {
        if (++polls > 30) { clearInterval(offerPoller); return; }
        const res = await interviewApi.getFinalOffer(id);
        if (res?.sdp) {
          clearInterval(offerPoller);
          await setupWebRTCAnswer(JSON.parse(res.sdp));
        }
      }, 2000);
    } catch {
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
        await interviewApi.addDirectorIce(id, JSON.stringify(e.candidate)).catch(() => {});
      }
    };

    await pc.setRemoteDescription(offer);
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    await interviewApi.saveFinalAnswerSdp(id, JSON.stringify(answer));

    const icePoller = setInterval(async () => {
      const list = await interviewApi.getCandidateFinalIce(id).catch(() => []);
      if (list.length > iceSeenRef.current) {
        for (let i = iceSeenRef.current; i < list.length; i++) {
          await pc.addIceCandidate(JSON.parse(list[i])).catch(() => {});
        }
        iceSeenRef.current = list.length;
      }
    }, 2000);
    setTimeout(() => clearInterval(icePoller), 120000);
  };

  /* ── Evaluation submit ──────────────────────────────────────────────────── */
  const handleEvaluate = async (decision) => {
    if (!evalForm.directorRemarks?.trim()) { toast.error('Director Remarks are required'); return; }
    if (decision === 'APPROVE' && (!evalForm.offeredCtc?.trim() || !evalForm.joiningDate)) {
      toast.error('Offered CTC and Joining Date are required to approve');
      return;
    }
    setSaving(true);
    try {
      await interviewApi.submitDirectorEvaluation(id, {
        ...evalForm,
        finalDecision: decision,
        joiningDate: evalForm.joiningDate || null,
      });
      const msg = decision === 'APPROVE'
        ? 'Approved — offer email sent, HR notified'
        : decision === 'HOLD'
        ? 'On Hold — HR notified'
        : 'Rejected — candidate notified';
      toast.success(msg);
      setEvalDialog(false);
      loadRoom();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to submit evaluation');
    } finally {
      setSaving(false);
    }
  };

  const fmtDt = v => v ? new Date(v).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';

  /* ── Loading / error states ─────────────────────────────────────────────── */
  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
      <CircularProgress />
    </Box>
  );
  if (!room) return <Alert severity="error" sx={{ m: 3 }}>Interview room not found.</Alert>;

  const status    = room.interviewStatus;
  const statusCfg = STATUS_CFG[status] || { label: status, bg: '#f1f5f9', color: '#475569' };
  const canEval   = status === 'CANDIDATE_SUBMITTED' || status === 'IN_PROGRESS'
    || (status === 'EVALUATED' && room.finalDecision === 'HOLD');
  const evaluated = status === 'EVALUATED';

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, maxWidth: 1100, mx: 'auto' }}>

      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <IconButton onClick={handleBack} sx={{ bgcolor: '#f1f5f9' }}>
          <ArrowBackIcon />
        </IconButton>
        <Box sx={{ flex: 1 }}>
          <Typography variant="h5" fontWeight={700}>
            Final Interview Room — {room.candidateName}
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
          <Button variant="contained"
            onClick={() => { setEvalForm({ ...EMPTY_EVAL, ...(room.overallRating ? { overallRating: room.overallRating, communicationRating: room.communicationRating, cultureFitRating: room.cultureFitRating, salaryDiscussion: room.salaryDiscussion || '', expectedCtc: room.expectedCtc || '', offeredCtc: room.offeredCtc || '', noticePeriod: room.noticePeriod || '', directorRemarks: room.directorRemarks || '', finalDecision: room.finalDecision || '' } : {}) }); setEvalDialog(true); }}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', fontWeight: 700 }}>
            {room.finalDecision === 'HOLD' ? 'Update Decision' : 'Submit Evaluation'}
          </Button>
        )}
      </Box>

      <Grid container spacing={3}>

        {/* ── Left: Video ── */}
        <Grid item xs={12} md={5}>
          <Card sx={{ borderRadius: 3 }}>
            <CardContent sx={{ p: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                <Typography variant="subtitle2" fontWeight={700}>Live Camera</Typography>
                <Chip
                  icon={liveConn ? <VideocamIcon sx={{ fontSize: 14 }} /> : <VideocamOffIcon sx={{ fontSize: 14 }} />}
                  label={liveConn ? 'Connected' : 'Not Connected'}
                  size="small"
                  sx={{ bgcolor: liveConn ? '#dcfce7' : '#f1f5f9', color: liveConn ? '#16a34a' : '#64748b', fontWeight: 600 }}
                />
              </Box>
              <Box sx={{ bgcolor: '#0f172a', borderRadius: 2, overflow: 'hidden', aspectRatio: '16/9', position: 'relative' }}>
                <video ref={remoteVideoRef} autoPlay playsInline
                  style={{ width: '100%', height: '100%', objectFit: 'cover', display: liveConn ? 'block' : 'none' }} />
                {!liveConn && (
                  <Box sx={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1 }}>
                    <VideocamOffIcon sx={{ color: '#475569', fontSize: 36 }} />
                    <Typography variant="caption" color="#475569">
                      {status === 'IN_PROGRESS' ? 'Connecting…' : 'Waiting for candidate to start'}
                    </Typography>
                  </Box>
                )}
              </Box>
              {status === 'IN_PROGRESS' && !liveConn && (
                <Button fullWidth variant="outlined" sx={{ mt: 1.5, textTransform: 'none' }} onClick={joinVideoCall}>
                  Connect Video
                </Button>
              )}
            </CardContent>
          </Card>

          {/* Timeline */}
          <Card sx={{ borderRadius: 3, mt: 2 }}>
            <CardContent sx={{ p: 2 }}>
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Timeline</Typography>
              {[
                ['Started',   room.startedAt],
                ['Submitted', room.completedAt],
                ['Evaluated', room.evaluatedAt],
              ].map(([label, val]) => (
                <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', py: 0.5 }}>
                  <Typography variant="caption" color="text.secondary">{label}</Typography>
                  <Typography variant="caption" fontWeight={600} color={val ? 'text.primary' : 'text.disabled'}>
                    {fmtDt(val)}
                  </Typography>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>

        {/* ── Right: Evaluation Summary ── */}
        <Grid item xs={12} md={7}>
          {evaluated && room.finalDecision && (
            <Card sx={{ borderRadius: 3, mb: 2,
              bgcolor: room.finalDecision === 'APPROVE' ? '#f0fdf4' : room.finalDecision === 'HOLD' ? '#fffbeb' : '#fef2f2',
              border: `1px solid ${room.finalDecision === 'APPROVE' ? '#bbf7d0' : room.finalDecision === 'HOLD' ? '#fde68a' : '#fecaca'}` }}>
              <CardContent sx={{ p: 2.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
                  {room.finalDecision === 'APPROVE' ? <CheckCircleIcon sx={{ color: '#16a34a', fontSize: 28 }} />
                   : room.finalDecision === 'HOLD'   ? <PauseCircleIcon sx={{ color: '#d97706', fontSize: 28 }} />
                   : <CancelIcon sx={{ color: '#dc2626', fontSize: 28 }} />}
                  <Typography variant="h6" fontWeight={700}
                    color={room.finalDecision === 'APPROVE' ? '#16a34a' : room.finalDecision === 'HOLD' ? '#d97706' : '#dc2626'}>
                    {room.finalDecision === 'APPROVE' ? 'Approved — Selected' : room.finalDecision === 'HOLD' ? 'On Hold' : 'Rejected'}
                  </Typography>
                </Box>

                <Grid container spacing={1.5}>
                  {[
                    ['Overall Rating',   room.overallRating,       'rating'],
                    ['Communication',    room.communicationRating,  'rating'],
                    ['Culture Fit',      room.cultureFitRating,     'rating'],
                    ['Expected CTC',     room.expectedCtc,          'text'],
                    ['Offered CTC',      room.offeredCtc,           'text'],
                    ['Notice Period',    room.noticePeriod,         'text'],
                  ].map(([label, val, type]) => (
                    <Grid item xs={12} sm={6} key={label}>
                      <Typography variant="caption" color="text.secondary">{label}</Typography>
                      {type === 'rating' ? (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                          <Rating value={val || 0} readOnly size="small" />
                          <Typography variant="caption" fontWeight={700}>{val || '—'}/5</Typography>
                        </Box>
                      ) : (
                        <Typography variant="body2" fontWeight={600}>{val || '—'}</Typography>
                      )}
                    </Grid>
                  ))}
                  {room.salaryDiscussion && (
                    <Grid item xs={12}>
                      <Typography variant="caption" color="text.secondary">Salary Discussion</Typography>
                      <Typography variant="body2">{room.salaryDiscussion}</Typography>
                    </Grid>
                  )}
                  {room.directorRemarks && (
                    <Grid item xs={12}>
                      <Typography variant="caption" color="text.secondary">Director Remarks</Typography>
                      <Typography variant="body2">{room.directorRemarks}</Typography>
                    </Grid>
                  )}
                </Grid>
              </CardContent>
            </Card>
          )}

          {!evaluated && (
            <Card sx={{ borderRadius: 3, border: '2px dashed #e2e8f0' }}>
              <CardContent sx={{ p: 3, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  {status === 'CANDIDATE_SUBMITTED'
                    ? 'Candidate has ended the interview. Click Submit Evaluation to proceed.'
                    : status === 'IN_PROGRESS'
                    ? 'Interview is in progress. You can submit evaluation during or after.'
                    : 'Waiting for the candidate to join the interview.'}
                </Typography>
              </CardContent>
            </Card>
          )}
        </Grid>
      </Grid>

      {/* ── Evaluation Dialog ── */}
      <Dialog open={evalDialog} onClose={() => !saving && setEvalDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Final Evaluation</DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={2} sx={{ pt: 1 }}>

            {/* Ratings */}
            {[
              ['Overall Rating',       'overallRating'],
              ['Communication',        'communicationRating'],
              ['Culture Fit',          'cultureFitRating'],
            ].map(([label, key]) => (
              <Grid item xs={12} sm={6} key={key}>
                <Typography variant="caption" color="text.secondary">{label}</Typography>
                <Rating value={evalForm[key]}
                  onChange={(_, v) => setEvalForm(f => ({ ...f, [key]: v }))} />
              </Grid>
            ))}

            <Grid item xs={12}><Divider /></Grid>

            {/* CTC & Notice */}
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Expected CTC"
                value={evalForm.expectedCtc}
                onChange={e => setEvalForm(f => ({ ...f, expectedCtc: e.target.value }))}
                placeholder="e.g. ₹8 LPA" />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Offered CTC"
                value={evalForm.offeredCtc}
                onChange={e => setEvalForm(f => ({ ...f, offeredCtc: e.target.value }))}
                placeholder="e.g. ₹10 LPA" helperText="Required to approve" />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Notice Period"
                value={evalForm.noticePeriod}
                onChange={e => setEvalForm(f => ({ ...f, noticePeriod: e.target.value }))}
                placeholder="e.g. 30 days" />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Joining Date" type="date"
                value={evalForm.joiningDate}
                onChange={e => setEvalForm(f => ({ ...f, joiningDate: e.target.value }))}
                InputLabelProps={{ shrink: true }} helperText="Required to approve" />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Salary Discussion" multiline rows={2}
                value={evalForm.salaryDiscussion}
                onChange={e => setEvalForm(f => ({ ...f, salaryDiscussion: e.target.value }))}
                placeholder="Notes on salary negotiation…" />
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Director Remarks *" multiline rows={3}
                value={evalForm.directorRemarks}
                onChange={e => setEvalForm(f => ({ ...f, directorRemarks: e.target.value }))}
                placeholder="Overall impression, strengths, concerns…" />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2, gap: 1, flexWrap: 'wrap' }}>
          <Button onClick={() => setEvalDialog(false)} disabled={saving} sx={{ textTransform: 'none' }}>
            Cancel
          </Button>
          <Box sx={{ flex: 1 }} />
          <Button variant="outlined" color="error"
            disabled={saving || !evalForm.directorRemarks?.trim()}
            startIcon={<CancelIcon />}
            onClick={() => handleEvaluate('REJECT')}
            sx={{ textTransform: 'none', fontWeight: 600 }}>
            Reject
          </Button>
          <Button variant="outlined"
            disabled={saving || !evalForm.directorRemarks?.trim()}
            startIcon={<PauseCircleIcon />}
            onClick={() => handleEvaluate('HOLD')}
            sx={{ textTransform: 'none', fontWeight: 600, borderColor: '#d97706', color: '#d97706' }}>
            Hold
          </Button>
          <Button variant="contained"
            disabled={saving || !evalForm.directorRemarks?.trim() || !evalForm.offeredCtc?.trim() || !evalForm.joiningDate}
            startIcon={<CheckCircleIcon />}
            onClick={() => handleEvaluate('APPROVE')}
            sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' }, textTransform: 'none', fontWeight: 700 }}>
            {saving ? 'Saving…' : 'Approve'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
