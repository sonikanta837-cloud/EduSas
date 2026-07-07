import React, { useEffect, useState, useMemo } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Card, CardContent, Typography, Button, Chip, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, CircularProgress, IconButton,
  Tooltip, Table, TableHead, TableBody, TableRow, TableCell, TableContainer,
  TablePagination, InputAdornment, MenuItem, Grid, FormControl, InputLabel,
  Select, Stack, Alert, Divider, Avatar, LinearProgress, Rating,
} from '@mui/material';
import AddIcon          from '@mui/icons-material/Add';
import DeleteIcon       from '@mui/icons-material/Delete';
import SearchIcon       from '@mui/icons-material/Search';
import RefreshIcon      from '@mui/icons-material/Refresh';
import OpenInNewIcon    from '@mui/icons-material/OpenInNew';
import ContentCopyIcon  from '@mui/icons-material/ContentCopy';
import VideocamIcon     from '@mui/icons-material/Videocam';
import ArrowBackIcon    from '@mui/icons-material/ArrowBack';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleIcon  from '@mui/icons-material/CheckCircle';
import PendingIcon      from '@mui/icons-material/Pending';
import { videoInterviewApi } from '../api/videoInterviewApi';
import { questionBankApi }   from '../api/questionBankApi';
import { toast } from 'react-toastify';

const STATUS_CFG = {
  PENDING:        { label: 'Pending',         bg: '#f1f5f9', color: '#475569' },
  IN_PROGRESS:    { label: 'In Progress',     bg: '#dbeafe', color: '#1d4ed8' },
  COMPLETED:      { label: 'Completed',       bg: '#dcfce7', color: '#16a34a' },
  AUTO_SUBMITTED: { label: 'Auto Submitted',  bg: '#fef3c7', color: '#d97706' },
  EXPIRED:        { label: 'Expired',         bg: '#fee2e2', color: '#dc2626' },
};

const DIFF_OPTIONS = ['EASY','MEDIUM','HARD','MIXED'];

const StatusChip = ({ status }) => {
  const c = STATUS_CFG[status] || { label: status, bg: '#f1f5f9', color: '#475569' };
  return <Chip label={c.label} size="small" sx={{ bgcolor: c.bg, color: c.color, fontWeight: 700, fontSize: 11 }} />;
};

const fmtDt = (v) => v ? new Date(v).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
const fmtDate = (v) => v ? new Date(v).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

const EMPTY_FORM = {
  candidateName: '', email: '', mobile: '', position: '', technology: '',
  experience: '', durationMinutes: 45, numQuestions: 25, difficulty: 'MIXED',
};

export default function VideoInterviews() {
  const { user } = useSelector(s => s.auth);
  const canManage = ['ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER'].includes(user?.role);

  const [interviews,  setInterviews]  = useState([]);
  const [stats,       setStats]       = useState(null);
  const [techs,       setTechs]       = useState([]);
  const [loading,     setLoading]     = useState(true);

  const [selected,    setSelected]    = useState(null);
  const [detailLoad,  setDetailLoad]  = useState(false);

  const [search,      setSearch]      = useState('');
  const [filterStatus,setFilterStatus]= useState('ALL');
  const [page,        setPage]        = useState(0);
  const RPP = 10;

  const [createDialog, setCreateDialog] = useState(false);
  const [form,         setForm]         = useState(EMPTY_FORM);
  const [creating,     setCreating]     = useState(false);

  useEffect(() => { load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const [ivs, st, ts] = await Promise.all([
        videoInterviewApi.getAll(),
        videoInterviewApi.getStats(),
        questionBankApi.getTechnologies(),
      ]);
      setInterviews(Array.isArray(ivs) ? ivs : []);
      setStats(st);
      setTechs(Array.isArray(ts) ? ts : []);
    } catch { toast.error('Failed to load interviews'); }
    finally { setLoading(false); }
  };

  const openDetail = async (iv) => {
    setDetailLoad(true);
    try {
      const full = await videoInterviewApi.getById(iv.id);
      setSelected(full);
    } catch { toast.error('Failed to load interview details'); }
    finally { setDetailLoad(false); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Permanently delete this interview record?')) return;
    try {
      await videoInterviewApi.delete(id);
      toast.success('Deleted');
      if (selected?.id === id) setSelected(null);
      await load();
    } catch { toast.error('Delete failed'); }
  };

  const handleCreate = async () => {
    if (!form.candidateName.trim()) { toast.error('Candidate name is required'); return; }
    if (!form.email.trim())         { toast.error('Email is required'); return; }
    if (!form.technology.trim())    { toast.error('Technology is required'); return; }
    setCreating(true);
    try {
      const created = await videoInterviewApi.create(form);
      toast.success(`Interview created and invitation sent to ${form.email}`);
      setCreateDialog(false);
      setForm(EMPTY_FORM);
      setInterviews(prev => [created, ...prev]);
      await load();
    } catch (err) { toast.error(err?.response?.data?.message || err?.message || 'Create failed'); }
    finally { setCreating(false); }
  };

  const copyLink = (link) => {
    navigator.clipboard.writeText(link).then(() => toast.success('Link copied!'));
  };

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return interviews.filter(iv => {
      if (q && !iv.candidateName?.toLowerCase().includes(q) && !iv.email?.toLowerCase().includes(q) && !iv.technology?.toLowerCase().includes(q)) return false;
      if (filterStatus !== 'ALL' && iv.status !== filterStatus) return false;
      return true;
    });
  }, [interviews, search, filterStatus]);

  const paged = filtered.slice(page * RPP, (page + 1) * RPP);

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}><CircularProgress /></Box>;

  /* ── Detail view ── */
  if (selected) {
    const iv = selected;
    const answeredCount = (iv.answers || []).filter(a => a.answerText || a.selectedOption).length;
    const scorePercent  = iv.totalMarks ? Math.round((iv.score / iv.totalMarks) * 100) : null;

    return (
      <Box sx={{ bgcolor: '#f8fafc', minHeight: '100%' }}>
        {detailLoad && <LinearProgress sx={{ mb: 1 }} />}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
          <Box>
            <Button startIcon={<ArrowBackIcon />} size="small" onClick={() => setSelected(null)} sx={{ color: '#64748b', textTransform: 'none', mb: 1 }}>Back to List</Button>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar sx={{ bgcolor: '#1e3a5f', width: 48, height: 48, fontSize: 20 }}>{iv.candidateName?.charAt(0)?.toUpperCase()}</Avatar>
              <Box>
                <Typography variant="h5" fontWeight={700}>{iv.candidateName}</Typography>
                <Typography variant="body2" color="text.secondary">{iv.position} · {iv.technology}</Typography>
              </Box>
              <StatusChip status={iv.status} />
            </Box>
          </Box>
          <Stack direction="row" spacing={1}>
            {['ADMIN','DIRECTOR','HR'].includes(user?.role) && (
              <Tooltip title="Delete Interview">
                <IconButton size="small" color="error" onClick={() => handleDelete(iv.id)} sx={{ border: '1px solid #fecaca', borderRadius: 2 }}><DeleteIcon /></IconButton>
              </Tooltip>
            )}
          </Stack>
        </Box>

        <Grid container spacing={3}>
          {/* Left panel */}
          <Grid item xs={12} md={4}>
            <Card sx={{ borderRadius: 3, mb: 2 }}>
              <CardContent>
                <Typography variant="subtitle2" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', fontSize: 11, letterSpacing: 0.6, mb: 2 }}>Interview Info</Typography>
                {[
                  ['Candidate',   iv.candidateName],
                  ['Email',       iv.email],
                  ['Mobile',      iv.mobile],
                  ['Position',    iv.position],
                  ['Technology',  iv.technology],
                  ['Experience',  iv.experience],
                  ['Duration',    iv.durationMinutes + ' min'],
                  ['Questions',   iv.numQuestions],
                  ['Difficulty',  iv.difficulty],
                  ['Created By',  iv.createdBy],
                  ['Created On',  fmtDate(iv.createdAt)],
                  ['Expires',     fmtDate(iv.expiresAt)],
                  ['Started At',  fmtDt(iv.startedAt)],
                  ['Submitted',   fmtDt(iv.submittedAt)],
                ].filter(([,v]) => v).map(([l,v]) => (
                  <Box key={l} sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.25, pb: 1.25, borderBottom: '1px solid #f1f5f9' }}>
                    <Typography variant="caption" color="text.secondary" fontWeight={600}>{l}</Typography>
                    <Typography variant="caption" fontWeight={500} sx={{ maxWidth: 160, textAlign: 'right' }}>{v}</Typography>
                  </Box>
                ))}
              </CardContent>
            </Card>

            {/* Score card */}
            {(iv.status === 'COMPLETED' || iv.status === 'AUTO_SUBMITTED') && iv.totalMarks > 0 && (
              <Card sx={{ borderRadius: 3, mb: 2 }}>
                <CardContent>
                  <Typography variant="subtitle2" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', fontSize: 11, letterSpacing: 0.6, mb: 1.5 }}>MCQ Score</Typography>
                  <Box sx={{ textAlign: 'center', py: 1 }}>
                    <Typography variant="h3" fontWeight={800} sx={{ color: scorePercent >= 70 ? '#16a34a' : scorePercent >= 40 ? '#d97706' : '#dc2626' }}>
                      {iv.score}/{iv.totalMarks}
                    </Typography>
                    <Typography variant="h6" color="text.secondary">{scorePercent}%</Typography>
                    <LinearProgress variant="determinate" value={scorePercent} sx={{ mt: 1, height: 8, borderRadius: 4, bgcolor: '#f1f5f9', '& .MuiLinearProgress-bar': { bgcolor: scorePercent >= 70 ? '#16a34a' : scorePercent >= 40 ? '#f59e0b' : '#dc2626' } }} />
                  </Box>
                </CardContent>
              </Card>
            )}

            {/* Violations */}
            {iv.violationCount > 0 && (
              <Card sx={{ borderRadius: 3, border: '1px solid #fecaca', mb: 2 }}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                    <WarningAmberIcon sx={{ color: '#dc2626' }} />
                    <Typography variant="subtitle2" fontWeight={700} sx={{ color: '#dc2626' }}>
                      {iv.violationCount} Violation{iv.violationCount > 1 ? 's' : ''} Detected
                    </Typography>
                  </Box>
                  {(iv.violations || []).map((v, i) => (
                    <Box key={v.id} sx={{ mb: 1, p: 1, bgcolor: '#fef2f2', borderRadius: 1 }}>
                      <Typography variant="caption" fontWeight={700} sx={{ color: '#dc2626', display: 'block' }}>
                        #{v.violationNumber} · {v.violationType?.replace(/_/g,' ')}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">{v.description}</Typography>
                      <Typography variant="caption" sx={{ display: 'block', color: '#94a3b8', fontSize: 10, mt: 0.25 }}>{fmtDt(v.occurredAt)}</Typography>
                    </Box>
                  ))}
                </CardContent>
              </Card>
            )}

            {/* Interview link */}
            {iv.status === 'PENDING' && (
              <Card sx={{ borderRadius: 3 }}>
                <CardContent>
                  <Typography variant="subtitle2" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', fontSize: 11, letterSpacing: 0.6, mb: 1 }}>Interview Link</Typography>
                  <Typography variant="caption" sx={{ wordBreak: 'break-all', color: '#1e3a5f', fontWeight: 500 }}>{iv.interviewLink}</Typography>
                  <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
                    <Button size="small" startIcon={<ContentCopyIcon />} onClick={() => copyLink(iv.interviewLink)} sx={{ textTransform: 'none', fontSize: 12 }}>Copy Link</Button>
                    <Button size="small" startIcon={<OpenInNewIcon />} href={iv.interviewLink} target="_blank" rel="noopener noreferrer" sx={{ textTransform: 'none', fontSize: 12 }}>Open</Button>
                  </Stack>
                </CardContent>
              </Card>
            )}
          </Grid>

          {/* Right panel — Answers */}
          <Grid item xs={12} md={8}>
            {iv.videoUrl && (
              <Card sx={{ borderRadius: 3, mb: 2 }}>
                <CardContent>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <VideocamIcon sx={{ color: '#1e3a5f' }} />
                    <Typography variant="subtitle2" fontWeight={700}>Video Recording</Typography>
                  </Box>
                  <video controls width="100%" src={iv.videoUrl} style={{ borderRadius: 8, maxHeight: 300 }} />
                </CardContent>
              </Card>
            )}

            <Card sx={{ borderRadius: 3 }}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                  <Typography variant="subtitle2" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', fontSize: 11, letterSpacing: 0.6 }}>
                    Answers ({answeredCount}/{(iv.answers || []).length})
                  </Typography>
                </Box>
                {(iv.answers || []).length === 0 ? (
                  <Alert severity="info">No answers recorded — candidate has not started the interview yet.</Alert>
                ) : (iv.answers || []).map((a, idx) => (
                  <Box key={a.questionId} sx={{ mb: 2, pb: 2, borderBottom: '1px solid #f1f5f9' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.75 }}>
                      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, flex: 1 }}>
                        <Box sx={{ minWidth: 24, height: 24, borderRadius: '50%', bgcolor: '#1e3a5f', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, flexShrink: 0, mt: 0.25 }}>
                          {idx + 1}
                        </Box>
                        <Box sx={{ flex: 1 }}>
                          <Typography variant="body2" fontWeight={600} sx={{ lineHeight: 1.4, mb: 0.5 }}>{a.questionText}</Typography>
                          <Box sx={{ display: 'flex', gap: 1 }}>
                            <Chip label={a.questionType} size="small" sx={{ fontSize: 10, height: 20 }} />
                            <Chip label={`${a.marks} pts`} size="small" sx={{ fontSize: 10, height: 20 }} />
                          </Box>
                        </Box>
                      </Box>
                      {a.isMarked && <Chip label="Marked" size="small" sx={{ bgcolor: '#fef9c3', color: '#a16207', fontSize: 10 }} />}
                    </Box>
                    {a.questionType === 'MCQ' ? (
                      <Box sx={{ ml: 4, mt: 0.5 }}>
                        <Typography variant="caption" color="text.secondary">Selected: </Typography>
                        {a.selectedOption ? (
                          <Chip label={`Option ${a.selectedOption}`} size="small"
                            sx={{ bgcolor: a.correctAnswer && a.selectedOption === a.correctAnswer ? '#dcfce7' : '#fee2e2',
                                  color:   a.correctAnswer && a.selectedOption === a.correctAnswer ? '#16a34a' : '#dc2626',
                                  fontWeight: 700, fontSize: 11 }} />
                        ) : <Typography variant="caption" sx={{ color: '#94a3b8', fontStyle: 'italic' }}>Not answered</Typography>}
                        {a.correctAnswer && (
                          <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                            · Correct: Option {a.correctAnswer}
                          </Typography>
                        )}
                      </Box>
                    ) : (
                      <Box sx={{ ml: 4, mt: 0.75, p: 1.5, bgcolor: '#f8fafc', borderRadius: 1, border: '1px solid #e2e8f0' }}>
                        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', color: a.answerText ? '#374151' : '#94a3b8', fontStyle: a.answerText ? 'normal' : 'italic', fontSize: 13 }}>
                          {a.answerText || 'No answer provided'}
                        </Typography>
                      </Box>
                    )}
                  </Box>
                ))}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Box>
    );
  }

  /* ── List view ── */
  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100%' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Video Interviews</Typography>
          <Typography variant="body2" color="text.secondary">AI-powered video interview portal — create, manage and review candidate interviews</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Tooltip title="Refresh"><IconButton onClick={load} size="small" sx={{ border: '1px solid #e2e8f0', borderRadius: 2 }}><RefreshIcon /></IconButton></Tooltip>
          {canManage && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setForm(EMPTY_FORM); setCreateDialog(true); }}
              sx={{ textTransform: 'none', bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
              Create Interview
            </Button>
          )}
        </Stack>
      </Box>

      {/* Stats */}
      {stats && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          {[
            { label: 'Total',         value: stats.total,         color: '#1e3a5f', bg: '#e8edf4' },
            { label: 'Pending',       value: stats.pending,       color: '#475569', bg: '#f1f5f9' },
            { label: 'In Progress',   value: stats.inProgress,    color: '#1d4ed8', bg: '#dbeafe' },
            { label: 'Completed',     value: stats.completed,     color: '#16a34a', bg: '#dcfce7' },
            { label: 'Auto Submitted',value: stats.autoSubmitted, color: '#d97706', bg: '#fef3c7' },
          ].map(s => (
            <Grid item xs={6} sm={4} md={2} key={s.label}>
              <Card sx={{ borderRadius: 2, border: 'none', boxShadow: 'none', bgcolor: s.bg }}>
                <CardContent sx={{ py: '12px !important', px: 2 }}>
                  <Typography variant="h5" fontWeight={800} sx={{ color: s.color }}>{s.value}</Typography>
                  <Typography variant="caption" fontWeight={600} sx={{ color: s.color }}>{s.label}</Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {/* Filters */}
      <Card sx={{ borderRadius: 3, mb: 2 }}>
        <CardContent sx={{ py: '14px !important' }}>
          <Grid container spacing={1.5} alignItems="center">
            <Grid item xs={12} sm={5}>
              <TextField fullWidth size="small" placeholder="Search by name, email, technology..." value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
                InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" sx={{ color: '#94a3b8' }} /></InputAdornment> }} />
            </Grid>
            <Grid item xs={6} sm={3}>
              <FormControl fullWidth size="small">
                <InputLabel>Status</InputLabel>
                <Select value={filterStatus} label="Status" onChange={e => { setFilterStatus(e.target.value); setPage(0); }}>
                  <MenuItem value="ALL">ALL</MenuItem>
                  {Object.keys(STATUS_CFG).map(s => <MenuItem key={s} value={s}>{STATUS_CFG[s].label}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Table */}
      <Card sx={{ borderRadius: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                {['Candidate', 'Email', 'Technology', 'Duration', 'Status', 'Created', 'Score', 'Actions'].map(h => (
                  <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12, color: '#64748b', py: 1.5 }}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {paged.length === 0 ? (
                <TableRow><TableCell colSpan={8} sx={{ textAlign: 'center', py: 6, color: '#94a3b8' }}>
                  <VideocamIcon sx={{ fontSize: 40, mb: 1, display: 'block', mx: 'auto' }} />
                  {interviews.length === 0 ? 'No interviews yet — create the first one' : 'No interviews match your filters'}
                </TableCell></TableRow>
              ) : paged.map(iv => (
                <TableRow key={iv.id} hover sx={{ cursor: 'pointer', '&:hover': { bgcolor: '#f8fafc' } }} onClick={() => openDetail(iv)}>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Avatar sx={{ bgcolor: '#1e3a5f', width: 32, height: 32, fontSize: 13 }}>{iv.candidateName?.charAt(0)?.toUpperCase()}</Avatar>
                      <Box>
                        <Typography variant="body2" fontWeight={600} sx={{ fontSize: 13 }}>{iv.candidateName}</Typography>
                        <Typography variant="caption" color="text.secondary">{iv.position || '—'}</Typography>
                      </Box>
                    </Box>
                  </TableCell>
                  <TableCell><Typography variant="body2" sx={{ fontSize: 13 }}>{iv.email}</Typography></TableCell>
                  <TableCell><Chip label={iv.technology} size="small" sx={{ bgcolor: '#e0f2fe', color: '#0369a1', fontWeight: 600 }} /></TableCell>
                  <TableCell><Typography variant="body2" sx={{ fontSize: 13 }}>{iv.durationMinutes} min · {iv.numQuestions}Q</Typography></TableCell>
                  <TableCell onClick={e => e.stopPropagation()}><StatusChip status={iv.status} /></TableCell>
                  <TableCell><Typography variant="body2" sx={{ fontSize: 12, color: '#64748b' }}>{fmtDate(iv.createdAt)}</Typography></TableCell>
                  <TableCell>
                    {iv.score != null ? (
                      <Chip label={`${iv.score}/${iv.totalMarks}`} size="small"
                        sx={{ bgcolor: iv.totalMarks && (iv.score/iv.totalMarks) >= 0.7 ? '#dcfce7' : '#fee2e2',
                              color:   iv.totalMarks && (iv.score/iv.totalMarks) >= 0.7 ? '#16a34a' : '#dc2626',
                              fontWeight: 700 }} />
                    ) : <Typography variant="caption" color="text.secondary">—</Typography>}
                  </TableCell>
                  <TableCell onClick={e => e.stopPropagation()}>
                    <Box sx={{ display: 'flex', gap: 0.5 }}>
                      {iv.status === 'PENDING' && (
                        <Tooltip title="Copy Link">
                          <IconButton size="small" onClick={() => copyLink(iv.interviewLink)}><ContentCopyIcon fontSize="small" /></IconButton>
                        </Tooltip>
                      )}
                      {['ADMIN','DIRECTOR','HR'].includes(user?.role) && (
                        <Tooltip title="Delete">
                          <IconButton size="small" color="error" onClick={() => handleDelete(iv.id)}><DeleteIcon fontSize="small" /></IconButton>
                        </Tooltip>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={filtered.length} page={page} rowsPerPage={RPP}
          onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[]} />
      </Card>

      {/* Create Interview Dialog */}
      <Dialog open={createDialog} onClose={() => setCreateDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700} sx={{ pb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <VideocamIcon sx={{ color: '#1e3a5f' }} />
            Create Video Interview
          </Box>
        </DialogTitle>
        <DialogContent dividers>
          <Alert severity="info" sx={{ mb: 2, fontSize: 13 }}>
            A unique interview link will be generated and emailed to the candidate.
          </Alert>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Candidate Name *" value={form.candidateName} onChange={e => setForm(f => ({ ...f, candidateName: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Email *" type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Mobile" value={form.mobile} onChange={e => setForm(f => ({ ...f, mobile: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Position" value={form.position} onChange={e => setForm(f => ({ ...f, position: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl fullWidth size="small">
                <InputLabel>Technology *</InputLabel>
                <Select value={form.technology} label="Technology *" onChange={e => setForm(f => ({ ...f, technology: e.target.value }))}>
                  {techs.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                  <MenuItem value={form.technology && !techs.includes(form.technology) ? form.technology : '__custom__'} sx={{ display: 'none' }}>
                    {form.technology}
                  </MenuItem>
                </Select>
              </FormControl>
              <TextField fullWidth size="small" label="Or type technology" value={form.technology} onChange={e => setForm(f => ({ ...f, technology: e.target.value }))} sx={{ mt: 1 }} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Experience" placeholder="e.g. 3-5 years" value={form.experience} onChange={e => setForm(f => ({ ...f, experience: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Duration (minutes)" type="number" value={form.durationMinutes} onChange={e => setForm(f => ({ ...f, durationMinutes: Number(e.target.value) }))} inputProps={{ min: 10, max: 180 }} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Number of Questions" type="number" value={form.numQuestions} onChange={e => setForm(f => ({ ...f, numQuestions: Number(e.target.value) }))} inputProps={{ min: 5, max: 100 }} />
            </Grid>
            <Grid item xs={12}>
              <FormControl fullWidth size="small">
                <InputLabel>Question Difficulty</InputLabel>
                <Select value={form.difficulty} label="Question Difficulty" onChange={e => setForm(f => ({ ...f, difficulty: e.target.value }))}>
                  {DIFF_OPTIONS.map(d => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setCreateDialog(false)} sx={{ textTransform: 'none', color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={handleCreate}
            disabled={creating || !form.candidateName.trim() || !form.email.trim() || !form.technology.trim()}
            sx={{ textTransform: 'none', bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            {creating ? <CircularProgress size={20} sx={{ color: '#fff' }} /> : 'Generate & Send Invitation'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
