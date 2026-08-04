import React, { useEffect, useState, useMemo, useRef } from 'react';
import {
  Box, Card, CardContent, Typography, Button, Chip, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, CircularProgress, IconButton,
  Tooltip, Table, TableHead, TableBody, TableRow, TableCell, TableContainer,
  TablePagination, InputAdornment, MenuItem, Grid, FormControl, InputLabel,
  Select, Stack, Alert, LinearProgress, Switch, FormControlLabel,
} from '@mui/material';
import AddIcon         from '@mui/icons-material/Add';
import EditIcon        from '@mui/icons-material/Edit';
import DeleteIcon      from '@mui/icons-material/Delete';
import SearchIcon      from '@mui/icons-material/Search';
import UploadIcon      from '@mui/icons-material/Upload';
import DownloadIcon    from '@mui/icons-material/Download';
import RefreshIcon     from '@mui/icons-material/Refresh';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon      from '@mui/icons-material/Cancel';
import QuizIcon        from '@mui/icons-material/Quiz';
import { questionBankApi } from '../api/questionBankApi';
import { toast } from 'react-toastify';

const DIFF_CFG = {
  EASY:   { label: 'Easy',   bg: '#dcfce7', color: '#16a34a' },
  MEDIUM: { label: 'Medium', bg: '#fef9c3', color: '#a16207' },
  HARD:   { label: 'Hard',   bg: '#fee2e2', color: '#dc2626' },
};

const TYPE_CFG = {
  TEXT: { label: 'Text',  bg: '#e0f2fe', color: '#0369a1' },
  MCQ:  { label: 'MCQ',   bg: '#f3e8ff', color: '#7c3aed' },
};

const EMPTY_FORM = {
  questionText: '', category: '', technology: '', difficulty: 'MEDIUM',
  questionType: 'TEXT', optionA: '', optionB: '', optionC: '', optionD: '',
  correctAnswer: '', expectedKeywords: '', marks: 5, status: 'ACTIVE',
};

const DiffChip  = ({ v }) => { const c = DIFF_CFG[v] || {}; return <Chip label={c.label || v} size="small" sx={{ bgcolor: c.bg, color: c.color, fontWeight: 700, fontSize: 11 }} />; };
const TypeChip  = ({ v }) => { const c = TYPE_CFG[v] || {}; return <Chip label={c.label || v} size="small" sx={{ bgcolor: c.bg, color: c.color, fontWeight: 700, fontSize: 11 }} />; };

export default function QuestionBank() {
  const [questions,  setQuestions]  = useState([]);
  const [stats,      setStats]      = useState(null);
  const [techs,      setTechs]      = useState([]);
  const [cats,       setCats]       = useState([]);
  const [loading,    setLoading]    = useState(true);

  const [search,     setSearch]     = useState('');
  const [filterDiff, setFilterDiff] = useState('ALL');
  const [filterType, setFilterType] = useState('ALL');
  const [filterTech, setFilterTech] = useState('ALL');
  const [filterStat, setFilterStat] = useState('ALL');
  const [page, setPage] = useState(0);
  const RPP = 15;

  const [dialog, setDialog]   = useState(false);
  const [editing, setEditing] = useState(null);
  const [form,    setForm]    = useState(EMPTY_FORM);
  const [saving,  setSaving]  = useState(false);

  const [importDialog, setImportDialog] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [importing,    setImporting]    = useState(false);
  const fileRef = useRef(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const [qs, st, ts, cs] = await Promise.all([
        questionBankApi.getAll(),
        questionBankApi.getStats(),
        questionBankApi.getTechnologies(),
        questionBankApi.getCategories(),
      ]);
      setQuestions(Array.isArray(qs) ? qs : []);
      setStats(st);
      setTechs(Array.isArray(ts) ? ts : []);
      setCats(Array.isArray(cs) ? cs : []);
    } catch { toast.error('Failed to load questions'); }
    finally { setLoading(false); }
  };

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return questions.filter(x => {
      if (q && !x.questionText?.toLowerCase().includes(q) &&
               !x.technology?.toLowerCase().includes(q) &&
               !x.category?.toLowerCase().includes(q)) return false;
      if (filterDiff !== 'ALL' && x.difficulty !== filterDiff) return false;
      if (filterType !== 'ALL' && x.questionType !== filterType) return false;
      if (filterTech !== 'ALL' && x.technology !== filterTech) return false;
      if (filterStat !== 'ALL' && x.status !== filterStat) return false;
      return true;
    });
  }, [questions, search, filterDiff, filterType, filterTech, filterStat]);

  const paged = filtered.slice(page * RPP, (page + 1) * RPP);

  const openCreate = () => { setEditing(null); setForm(EMPTY_FORM); setDialog(true); };
  const openEdit   = (q) => {
    setEditing(q);
    setForm({
      questionText: q.questionText || '', category: q.category || '',
      technology: q.technology || '', difficulty: q.difficulty || 'MEDIUM',
      questionType: q.questionType || 'TEXT', optionA: q.optionA || '',
      optionB: q.optionB || '', optionC: q.optionC || '', optionD: q.optionD || '',
      correctAnswer: q.correctAnswer || '', expectedKeywords: q.expectedKeywords || '',
      marks: q.marks || 5, status: q.status || 'ACTIVE',
    });
    setDialog(true);
  };

  const handleSave = async () => {
    if (!form.questionText.trim()) { toast.error('Question text is required'); return; }
    setSaving(true);
    try {
      if (editing) {
        const updated = await questionBankApi.update(editing.id, form);
        setQuestions(prev => prev.map(q => q.id === editing.id ? updated : q));
        toast.success('Question updated');
      } else {
        const created = await questionBankApi.create(form);
        setQuestions(prev => [created, ...prev]);
        toast.success('Question created');
      }
      setDialog(false);
      load();
    } catch (err) { toast.error(err?.message || 'Save failed'); }
    finally { setSaving(false); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this question?')) return;
    try {
      await questionBankApi.delete(id);
      setQuestions(prev => prev.filter(q => q.id !== id));
      toast.success('Deleted');
    } catch { toast.error('Delete failed'); }
  };

  const handleToggle = async (id) => {
    try {
      const updated = await questionBankApi.toggleStatus(id);
      setQuestions(prev => prev.map(q => q.id === id ? updated : q));
    } catch { toast.error('Failed to toggle status'); }
  };

  const handleImport = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImporting(true);
    setImportResult(null);
    try {
      const result = await questionBankApi.importExcel(file);
      setImportResult(result);
      if (result.saved > 0) { load(); }
    } catch { toast.error('Import failed'); }
    finally { setImporting(false); if (fileRef.current) fileRef.current.value = ''; }
  };

  const handleExport = async () => {
    try {
      const res = await questionBankApi.exportExcel();
      if (!res.ok) { toast.error('Export failed'); return; }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'interview-questions.xlsx'; a.click();
      URL.revokeObjectURL(url);
    } catch { toast.error('Export failed'); }
  };

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}><CircularProgress /></Box>;

  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100%' }}>
      {/* ── Header ── */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Question Bank</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage interview questions for the AI Video Interview Portal
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Tooltip title="Refresh"><IconButton onClick={load} size="small" sx={{ border: '1px solid #e2e8f0', borderRadius: 2 }}><RefreshIcon /></IconButton></Tooltip>
          <Button variant="outlined" startIcon={<DownloadIcon />} onClick={handleExport} sx={{ textTransform: 'none', borderColor: '#e2e8f0' }}>Export</Button>
          <Button variant="outlined" startIcon={<UploadIcon />} onClick={() => { setImportResult(null); setImportDialog(true); }} sx={{ textTransform: 'none', borderColor: '#e2e8f0' }}>Import Excel</Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate} sx={{ textTransform: 'none', bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>Add Question</Button>
        </Stack>
      </Box>

      {/* ── Stats ── */}
      {stats && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          {[
            { label: 'Total', value: stats.total, color: '#1e3a5f', bg: '#e8edf4' },
            { label: 'Active', value: stats.active, color: '#16a34a', bg: '#dcfce7' },
            { label: 'Inactive', value: stats.inactive, color: '#dc2626', bg: '#fee2e2' },
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

      {/* ── Filters ── */}
      <Card sx={{ borderRadius: 3, mb: 2 }}>
        <CardContent sx={{ py: '14px !important' }}>
          <Grid container spacing={1.5} alignItems="center">
            <Grid item xs={12} sm={4}>
              <TextField fullWidth size="small" placeholder="Search questions, technology..." value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
                InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" sx={{ color: '#94a3b8' }} /></InputAdornment> }} />
            </Grid>
            {[
              { label: 'Difficulty', value: filterDiff, onChange: v => { setFilterDiff(v); setPage(0); }, options: ['ALL','EASY','MEDIUM','HARD'] },
              { label: 'Type',       value: filterType, onChange: v => { setFilterType(v); setPage(0); }, options: ['ALL','TEXT','MCQ'] },
              { label: 'Status',     value: filterStat, onChange: v => { setFilterStat(v); setPage(0); }, options: ['ALL','ACTIVE','INACTIVE'] },
            ].map(f => (
              <Grid item xs={6} sm={2} key={f.label}>
                <FormControl fullWidth size="small">
                  <InputLabel>{f.label}</InputLabel>
                  <Select value={f.value} label={f.label} onChange={e => f.onChange(e.target.value)}>
                    {f.options.map(o => <MenuItem key={o} value={o}>{o}</MenuItem>)}
                  </Select>
                </FormControl>
              </Grid>
            ))}
            <Grid item xs={6} sm={2}>
              <FormControl fullWidth size="small">
                <InputLabel>Technology</InputLabel>
                <Select value={filterTech} label="Technology" onChange={e => { setFilterTech(e.target.value); setPage(0); }}>
                  <MenuItem value="ALL">ALL</MenuItem>
                  {techs.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* ── Table ── */}
      <Card sx={{ borderRadius: 3 }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                {['#', 'Question', 'Technology', 'Category', 'Difficulty', 'Type', 'Marks', 'Status', 'Actions'].map(h => (
                  <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12, color: '#64748b', py: 1.5 }}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {paged.length === 0 ? (
                <TableRow><TableCell colSpan={9} sx={{ textAlign: 'center', py: 6, color: '#94a3b8' }}>
                  <QuizIcon sx={{ fontSize: 40, mb: 1, display: 'block', mx: 'auto' }} />
                  {questions.length === 0 ? 'No questions yet — add your first question or import from Excel' : 'No questions match your filters'}
                </TableCell></TableRow>
              ) : paged.map((q, idx) => (
                <TableRow key={q.id} hover sx={{ '&:hover': { bgcolor: '#f8fafc' } }}>
                  <TableCell sx={{ color: '#94a3b8', fontSize: 12 }}>{page * RPP + idx + 1}</TableCell>
                  <TableCell sx={{ maxWidth: 380 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500, lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                      {q.questionText}
                    </Typography>
                    {q.expectedKeywords && (
                      <Typography variant="caption" color="text.secondary" sx={{ fontSize: 11 }}>
                        Keywords: {q.expectedKeywords}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell><Typography variant="body2" sx={{ fontWeight: 600, fontSize: 12 }}>{q.technology || '—'}</Typography></TableCell>
                  <TableCell><Typography variant="body2" sx={{ fontSize: 12, color: '#64748b' }}>{q.category || '—'}</Typography></TableCell>
                  <TableCell><DiffChip v={q.difficulty} /></TableCell>
                  <TableCell><TypeChip v={q.questionType} /></TableCell>
                  <TableCell><Typography variant="body2" fontWeight={700} sx={{ color: '#1e3a5f' }}>{q.marks}</Typography></TableCell>
                  <TableCell>
                    <Switch checked={q.status === 'ACTIVE'} onChange={() => handleToggle(q.id)} size="small"
                      sx={{ '& .MuiSwitch-switchBase.Mui-checked': { color: '#16a34a' }, '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { bgcolor: '#16a34a' } }} />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5 }}>
                      <Tooltip title="Edit"><IconButton size="small" onClick={() => openEdit(q)} sx={{ color: '#1e3a5f' }}><EditIcon fontSize="small" /></IconButton></Tooltip>
                      <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => handleDelete(q.id)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div" count={filtered.length} page={page} rowsPerPage={RPP}
          onPageChange={(_, p) => setPage(p)} rowsPerPageOptions={[]} />
      </Card>

      {/* ── Add / Edit Dialog ── */}
      <Dialog open={dialog} onClose={() => setDialog(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ fontWeight: 700, pb: 1 }}>{editing ? 'Edit Question' : 'Add Question'}</DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={2} sx={{ mt: 0 }}>
            <Grid item xs={12}>
              <TextField fullWidth multiline rows={3} label="Question Text *" value={form.questionText} onChange={e => setForm(f => ({ ...f, questionText: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField fullWidth label="Technology" value={form.technology} onChange={e => setForm(f => ({ ...f, technology: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField fullWidth label="Category" value={form.category} onChange={e => setForm(f => ({ ...f, category: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={4}>
              <TextField fullWidth label="Marks" type="number" value={form.marks} onChange={e => setForm(f => ({ ...f, marks: Number(e.target.value) }))} inputProps={{ min: 1 }} />
            </Grid>
            <Grid item xs={6} sm={3}>
              <FormControl fullWidth>
                <InputLabel>Difficulty</InputLabel>
                <Select value={form.difficulty} label="Difficulty" onChange={e => setForm(f => ({ ...f, difficulty: e.target.value }))}>
                  {['EASY','MEDIUM','HARD'].map(d => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} sm={3}>
              <FormControl fullWidth>
                <InputLabel>Type</InputLabel>
                <Select value={form.questionType} label="Type" onChange={e => setForm(f => ({ ...f, questionType: e.target.value }))}>
                  {['TEXT','MCQ'].map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={6} sm={3}>
              <FormControl fullWidth>
                <InputLabel>Status</InputLabel>
                <Select value={form.status} label="Status" onChange={e => setForm(f => ({ ...f, status: e.target.value }))}>
                  {['ACTIVE','INACTIVE'].map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                </Select>
              </FormControl>
            </Grid>

            {form.questionType === 'MCQ' && (<>
              <Grid item xs={12}>
                <Typography variant="subtitle2" fontWeight={700} color="text.secondary" sx={{ textTransform: 'uppercase', fontSize: 11, letterSpacing: 0.6 }}>MCQ Options</Typography>
              </Grid>
              {['A','B','C','D'].map(l => (
                <Grid item xs={12} sm={6} key={l}>
                  <TextField fullWidth label={`Option ${l}`} value={form[`option${l}`]} onChange={e => setForm(f => ({ ...f, [`option${l}`]: e.target.value }))} />
                </Grid>
              ))}
              <Grid item xs={12} sm={4}>
                <FormControl fullWidth>
                  <InputLabel>Correct Answer</InputLabel>
                  <Select value={form.correctAnswer} label="Correct Answer" onChange={e => setForm(f => ({ ...f, correctAnswer: e.target.value }))}>
                    {['A','B','C','D'].map(l => <MenuItem key={l} value={l}>Option {l}</MenuItem>)}
                  </Select>
                </FormControl>
              </Grid>
            </>)}

            <Grid item xs={12}>
              <TextField fullWidth label="Expected Keywords (comma-separated)" value={form.expectedKeywords} onChange={e => setForm(f => ({ ...f, expectedKeywords: e.target.value }))} helperText="Keywords expected in text answers for evaluation hints" />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setDialog(false)} sx={{ textTransform: 'none', color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving || !form.questionText.trim()}
            sx={{ textTransform: 'none', bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            {saving ? <CircularProgress size={20} sx={{ color: '#fff' }} /> : (editing ? 'Update' : 'Create')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Import Dialog ── */}
      <Dialog open={importDialog} onClose={() => { setImportDialog(false); setImportResult(null); }} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>Import Questions from Excel</DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            Upload an .xlsx file with columns: Question, Category, Technology, Difficulty (EASY/MEDIUM/HARD),
            Type (TEXT/MCQ), Option A, Option B, Option C, Option D, Correct Answer, Expected Keywords, Marks.
            The first row is treated as a header and skipped.
          </Alert>
          <Button variant="outlined" component="label" startIcon={<UploadIcon />} sx={{ textTransform: 'none' }}>
            Choose Excel File
            <input type="file" accept=".xlsx" hidden ref={fileRef} onChange={handleImport} />
          </Button>
          {importing && <LinearProgress sx={{ mt: 2 }} />}
          {importResult && (
            <Box sx={{ mt: 2 }}>
              <Alert severity={importResult.failed > 0 ? 'warning' : 'success'} sx={{ mb: 1 }}>
                Imported {importResult.saved} question{importResult.saved !== 1 ? 's' : ''} successfully.
                {importResult.failed > 0 && ` ${importResult.failed} row(s) failed.`}
              </Alert>
              {importResult.errors?.length > 0 && (
                <Box sx={{ maxHeight: 200, overflow: 'auto', bgcolor: '#fef2f2', borderRadius: 1, p: 1.5 }}>
                  {importResult.errors.map((e, i) => (
                    <Typography key={i} variant="caption" sx={{ display: 'block', color: '#dc2626', mb: 0.5 }}>{e}</Typography>
                  ))}
                </Box>
              )}
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => { setImportDialog(false); setImportResult(null); }} sx={{ textTransform: 'none' }}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
