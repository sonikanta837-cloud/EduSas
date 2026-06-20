import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, Button, IconButton, Chip, TextField, Tooltip,
  Table, TableBody, TableCell, TableHead, TableRow, TableContainer,
  TablePagination, Dialog, DialogTitle, DialogContent, DialogActions,
  InputAdornment, CircularProgress, Divider, Paper, Tabs, Tab,
  Accordion, AccordionSummary, AccordionDetails, MenuItem, Alert,
  Grid,
} from '@mui/material';
import SearchIcon          from '@mui/icons-material/Search';
import AddIcon             from '@mui/icons-material/Add';
import EditIcon            from '@mui/icons-material/Edit';
import DeleteIcon          from '@mui/icons-material/Delete';
import DownloadIcon        from '@mui/icons-material/Download';
import DescriptionIcon     from '@mui/icons-material/Description';
import ArticleIcon         from '@mui/icons-material/Article';
import HelpOutlineIcon     from '@mui/icons-material/HelpOutline';
import ExpandMoreIcon      from '@mui/icons-material/ExpandMore';
import VisibilityOffIcon   from '@mui/icons-material/VisibilityOff';
import VisibilityIcon      from '@mui/icons-material/Visibility';
import UploadFileIcon      from '@mui/icons-material/UploadFile';
import CloseIcon           from '@mui/icons-material/Close';
import PrintIcon           from '@mui/icons-material/Print';
import PictureAsPdfIcon    from '@mui/icons-material/PictureAsPdf';
import AssignmentIcon      from '@mui/icons-material/Assignment';
import OpenInNewIcon       from '@mui/icons-material/OpenInNew';
import FilterListIcon      from '@mui/icons-material/FilterList';
import { resourceApi, faqApi } from '../api/resourceApi';
import { toast } from 'react-toastify';
import jsPDF from 'jspdf';

// ── constants ─────────────────────────────────────────────────────────────────
const CATEGORIES = {
  POLICY:        { label: 'Policy',         color: '#1d4ed8', bg: '#dbeafe' },
  FORM_TEMPLATE: { label: 'Form / Template', color: '#7c3aed', bg: '#ede9fe' },
  GENERAL:       { label: 'General',         color: '#0369a1', bg: '#e0f2fe' },
};

const POLICY_SUBCATEGORIES = ['Policy', 'Document'];

const FORM_SUBCATEGORIES = ['Forms', 'Templates'];

const FILE_TYPE_LABEL = (mime) => {
  if (!mime) return '—';
  if (mime.includes('pdf'))        return 'PDF';
  if (mime.includes('word') || mime.includes('docx')) return 'Word';
  if (mime.includes('excel') || mime.includes('xlsx') || mime.includes('spreadsheet')) return 'Excel';
  if (mime.includes('image'))      return 'Image';
  if (mime.includes('text'))       return 'Text';
  return mime.split('/')[1]?.toUpperCase() || '—';
};

const fmtSize = (bytes) => {
  if (!bytes) return '—';
  if (bytes < 1024)       return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const fmtDate = (dt) =>
  dt ? new Date(dt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

const hdr  = { fontWeight: 700, fontSize: 12, color: '#64748b', textTransform: 'uppercase',
               letterSpacing: '0.04em', py: 1.5, px: 2, whiteSpace: 'nowrap', borderBottom: '2px solid #f1f5f9' };
const cell = { py: 1.5, px: 2, fontSize: 13, color: '#334155', borderBottom: '1px solid #f8fafc' };

// ── Built-in template definitions ─────────────────────────────────────────────
const BUILT_IN_TEMPLATES = [
  {
    id:          'builtin-resignation',
    title:       'Resignation Letter',
    subCategory: 'Templates',
    description: 'Professional resignation letter — PDF & Print ready',
    source:      'Built-in',
    isBuiltin:   true,
    builtinKey:  'resignation',
    active:      true,
  },
];

// ── FormsTemplatesSection — unified table ──────────────────────────────────────
function FormsTemplatesSection({ resources, canManage, onRefresh }) {
  const [search,      setSearch]      = useState('');
  const [catFilter,   setCatFilter]   = useState('');
  const [page,        setPage]        = useState(0);
  const [rpp,         setRpp]         = useState(10);
  const [openBuiltin, setOpenBuiltin] = useState(null); // 'resignation' | null
  const [saving,      setSaving]      = useState(false);
  const [uploading,   setUploading]   = useState(false);

  // upload form
  const [uploadOpen, setUploadOpen] = useState(false);
  const [file,       setFile]       = useState(null);
  const [uTitle,     setUTitle]     = useState('');
  const [uSubCat,    setUSubCat]    = useState('');
  const [uDesc,      setUDesc]      = useState('');

  // edit form
  const [editTarget, setEditTarget] = useState(null);
  const [eTitle,     setETitle]     = useState('');
  const [eSubCat,    setESubCat]    = useState('');
  const [eDesc,      setEDesc]      = useState('');

  // delete
  const [delTarget, setDelTarget] = useState(null);

  // Merge built-ins + uploaded FORM_TEMPLATE resources
  const allRows = useMemo(() => {
    const uploaded = resources
      .filter(r => r.category === 'FORM_TEMPLATE')
      .filter(r => canManage || r.active)
      .map(r => ({ ...r, source: 'Uploaded', isBuiltin: false }));
    return [...BUILT_IN_TEMPLATES, ...uploaded];
  }, [resources, canManage]);

  const filteredRows = useMemo(() => {
    const q = search.toLowerCase();
    return allRows.filter(r =>
      (!catFilter || r.subCategory === catFilter) &&
      (!q || r.title?.toLowerCase().includes(q) ||
             r.subCategory?.toLowerCase().includes(q) ||
             r.description?.toLowerCase().includes(q))
    );
  }, [allRows, search, catFilter]);

  const paged = useMemo(
    () => filteredRows.slice(page * rpp, page * rpp + rpp),
    [filteredRows, page, rpp]
  );

  const openUpload = () => {
    setFile(null); setUTitle(''); setUSubCat(''); setUDesc('');
    setUploadOpen(true);
  };

  const handleUpload = async () => {
    if (!file)          { toast.error('Please select a file'); return; }
    if (!uTitle.trim()) { toast.error('Title is required');    return; }
    setUploading(true);
    try {
      await resourceApi.upload(file, uTitle, 'FORM_TEMPLATE', uSubCat, uDesc);
      toast.success('Template uploaded');
      setUploadOpen(false);
      onRefresh();
    } catch (e) { toast.error(e?.response?.data?.message || 'Upload failed'); }
    finally { setUploading(false); }
  };

  const handleEdit = async () => {
    if (!eTitle.trim()) { toast.error('Title is required'); return; }
    setSaving(true);
    try {
      await resourceApi.update(editTarget.id, { title: eTitle, subCategory: eSubCat, description: eDesc });
      toast.success('Template updated');
      setEditTarget(null);
      onRefresh();
    } catch { toast.error('Update failed'); }
    finally { setSaving(false); }
  };

  const handleToggle = async (r) => {
    try {
      await resourceApi.toggle(r.id);
      toast.success(r.active ? 'Template deactivated' : 'Template activated');
      onRefresh();
    } catch { toast.error('Failed to update status'); }
  };

  const handleDelete = async () => {
    setSaving(true);
    try {
      await resourceApi.delete(delTarget.id);
      toast.success('Template deleted');
      setDelTarget(null);
      onRefresh();
    } catch { toast.error('Delete failed'); }
    finally { setSaving(false); }
  };

  const handleDownload = async (r) => {
    try {
      const blob = await resourceApi.download(r.id);
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = r.originalFileName || r.title;
      a.click(); URL.revokeObjectURL(url);
    } catch { toast.error('Download failed'); }
  };


  return (
    <Box>
      {/* Toolbar */}
      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', mb: 2.5, flexWrap: 'wrap' }}>
        <TextField size="small" placeholder="Search forms and templates…"
          value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
          sx={{ width: 280 }}
          InputProps={{ startAdornment: (
            <InputAdornment position="start"><SearchIcon sx={{ fontSize: 18, color: '#94a3b8' }} /></InputAdornment>
          )}}
        />
        <TextField select size="small" label="Category" value={catFilter}
          onChange={e => { setCatFilter(e.target.value); setPage(0); }}
          sx={{ width: 190 }}
          InputProps={{ startAdornment: (
            <InputAdornment position="start"><FilterListIcon sx={{ fontSize: 16, color: '#94a3b8' }} /></InputAdornment>
          )}}>
          <MenuItem value="">All Categories</MenuItem>
          {FORM_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
        </TextField>
        <Box sx={{ flex: 1 }} />
        <Chip label={`${filteredRows.length} template${filteredRows.length !== 1 ? 's' : ''}`}
          sx={{ bgcolor: '#ede9fe', color: '#7c3aed', fontWeight: 600, fontSize: 12 }} />
        {canManage && (
          <Button size="small" variant="contained" startIcon={<UploadFileIcon />} onClick={openUpload}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Upload Template
          </Button>
        )}
      </Box>

      {/* Table */}
      <Paper sx={{ border: '1px solid #f1f5f9', borderRadius: 2, overflow: 'hidden',
                   boxShadow: '0 1px 4px rgba(0,0,0,0.05)' }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                {['Title', 'Category', 'Source', 'Actions'].map(h => (
                  <TableCell key={h} sx={hdr}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {paged.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} sx={{ textAlign: 'center', py: 6, color: '#94a3b8', fontSize: 13 }}>
                    {search || catFilter ? 'No templates match your filters' : 'No templates available'}
                  </TableCell>
                </TableRow>
              ) : paged.map(r => (
                <TableRow key={r.id} hover
                  sx={{ opacity: (!r.isBuiltin && !r.active) ? 0.5 : 1,
                        '&:hover': { bgcolor: '#fafbff' } }}>

                  {/* Title */}
                  <TableCell sx={{ ...cell, maxWidth: 340 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Box sx={{ width: 36, height: 36, borderRadius: 1.5,
                                 bgcolor: r.isBuiltin ? '#ede9fe' : '#ede9fe',
                                 flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        {r.isBuiltin
                          ? <AssignmentIcon sx={{ fontSize: 18, color: '#7c3aed' }} />
                          : <ArticleIcon    sx={{ fontSize: 18, color: '#7c3aed' }} />}
                      </Box>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography fontWeight={600} fontSize={13.5} noWrap>{r.title}</Typography>
                        {r.description && (
                          <Typography fontSize={11.5} color="text.secondary" noWrap>{r.description}</Typography>
                        )}
                        {canManage && !r.isBuiltin && !r.active && (
                          <Chip label="Inactive" size="small"
                            sx={{ height: 16, fontSize: 10, bgcolor: '#f1f5f9', color: '#94a3b8', mt: 0.25 }} />
                        )}
                      </Box>
                    </Box>
                  </TableCell>

                  {/* Category */}
                  <TableCell sx={cell}>
                    {r.subCategory
                      ? <Chip label={r.subCategory} size="small"
                          sx={{ bgcolor: '#ede9fe', color: '#7c3aed', fontWeight: 600, fontSize: 11 }} />
                      : <Typography fontSize={12} color="text.secondary">—</Typography>}
                  </TableCell>

                  {/* Source */}
                  <TableCell sx={cell}>
                    {r.isBuiltin
                      ? <Chip label="Built-in" size="small"
                          sx={{ bgcolor: '#d1fae5', color: '#065f46', fontWeight: 700, fontSize: 11 }} />
                      : <Chip label="Uploaded" size="small"
                          sx={{ bgcolor: '#dbeafe', color: '#1d4ed8', fontWeight: 600, fontSize: 11 }} />}
                  </TableCell>

                  {/* Actions */}
                  <TableCell sx={{ ...cell, whiteSpace: 'nowrap' }}>
                    {r.isBuiltin ? (
                      <Button size="small" variant="outlined"
                        onClick={() => setOpenBuiltin(r.builtinKey)}
                        sx={{ fontSize: 12, borderColor: '#7c3aed', color: '#7c3aed',
                              '&:hover': { bgcolor: '#ede9fe', borderColor: '#6d28d9' } }}>
                        Use Template
                      </Button>
                    ) : (
                      <>
                        <Button size="small" variant="outlined"
                          startIcon={<DownloadIcon sx={{ fontSize: 15 }} />}
                          onClick={() => handleDownload(r)}
                          sx={{ fontSize: 12, borderColor: '#0369a1', color: '#0369a1', mr: canManage ? 0.5 : 0,
                                '&:hover': { bgcolor: '#e0f2fe', borderColor: '#0284c7' } }}>
                          Download
                        </Button>
                        {canManage && (
                          <>
                            <Tooltip title="Edit">
                              <IconButton size="small"
                                onClick={() => { setEditTarget(r); setETitle(r.title); setESubCat(r.subCategory || ''); setEDesc(r.description || ''); }}
                                sx={{ color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                                <EditIcon sx={{ fontSize: 16 }} />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title={r.active ? 'Deactivate' : 'Activate'}>
                              <IconButton size="small" onClick={() => handleToggle(r)}
                                sx={{ color: r.active ? '#f59e0b' : '#16a34a',
                                      '&:hover': { bgcolor: r.active ? '#fef3c7' : '#dcfce7' } }}>
                                {r.active
                                  ? <VisibilityOffIcon sx={{ fontSize: 16 }} />
                                  : <VisibilityIcon   sx={{ fontSize: 16 }} />}
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Delete">
                              <IconButton size="small" onClick={() => setDelTarget(r)}
                                sx={{ color: '#ef4444', '&:hover': { bgcolor: '#fee2e2' } }}>
                                <DeleteIcon sx={{ fontSize: 16 }} />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={filteredRows.length} page={page}
          onPageChange={(_, p) => setPage(p)} rowsPerPage={rpp}
          onRowsPerPageChange={e => { setRpp(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
          sx={{ borderTop: '1px solid #f1f5f9', '& .MuiTablePagination-toolbar': { fontSize: 12 } }} />
      </Paper>

      {/* Built-in template dialogs */}
      <ResignationLetterTemplate
        open={openBuiltin === 'resignation'}
        onClose={() => setOpenBuiltin(null)}
      />

      {/* ── Upload Dialog ── */}
      <Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          Upload Form / Template
          <IconButton size="small" onClick={() => setUploadOpen(false)}><CloseIcon sx={{ fontSize: 18 }} /></IconButton>
        </DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}
            sx={{ justifyContent: 'flex-start', borderStyle: 'dashed', borderColor: '#cbd5e1',
                  color: '#475569', textTransform: 'none', '&:hover': { borderColor: '#94a3b8' } }}>
            {file ? file.name : 'Choose file (PDF, DOCX, XLSX…)'}
            <input type="file" hidden onChange={e => {
              const f = e.target.files?.[0];
              if (f) { setFile(f); if (!uTitle) setUTitle(f.name.replace(/\.[^/.]+$/, '')); }
            }} />
          </Button>
          <TextField size="small" fullWidth label="Title *"
            value={uTitle} onChange={e => setUTitle(e.target.value)} />
          <TextField select size="small" fullWidth label="Category"
            value={uSubCat} onChange={e => setUSubCat(e.target.value)}>
            <MenuItem value=""><em>None</em></MenuItem>
            {FORM_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
          </TextField>
          <TextField size="small" fullWidth multiline rows={2}
            label="Description (optional)"
            value={uDesc} onChange={e => setUDesc(e.target.value)} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setUploadOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleUpload} disabled={uploading}
            startIcon={uploading ? <CircularProgress size={14} color="inherit" /> : <UploadFileIcon />}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            {uploading ? 'Uploading…' : 'Upload'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Edit Dialog ── */}
      <Dialog open={Boolean(editTarget)} onClose={() => setEditTarget(null)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Edit Template</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField size="small" fullWidth label="Title *"
            value={eTitle} onChange={e => setETitle(e.target.value)} />
          <TextField select size="small" fullWidth label="Category"
            value={eSubCat} onChange={e => setESubCat(e.target.value)}>
            <MenuItem value=""><em>None</em></MenuItem>
            {FORM_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
          </TextField>
          <TextField size="small" fullWidth multiline rows={2}
            label="Description (optional)"
            value={eDesc} onChange={e => setEDesc(e.target.value)} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setEditTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={handleEdit} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <EditIcon />}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Delete Confirm ── */}
      <Dialog open={Boolean(delTarget)} onClose={() => setDelTarget(null)} maxWidth="xs" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, color: '#dc2626' }}>Delete Template</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Typography>Permanently delete <strong>{delTarget?.title}</strong>?</Typography>
          <Typography fontSize={12} color="error" mt={1}>This action cannot be undone.</Typography>
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setDelTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <DeleteIcon />}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

// ── FormsSection ─────────────────────────────────────────────────────────────
// eslint-disable-next-line no-unused-vars
function FormsSection({ resources, canManage, onRefresh }) {
  const [search,    setSearch]    = useState('');
  const [catFilter, setCatFilter] = useState('');
  const [page,      setPage]      = useState(0);
  const [rpp,       setRpp]       = useState(10);
  const [saving,    setSaving]    = useState(false);
  const [uploading, setUploading] = useState(false);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [file,       setFile]       = useState(null);
  const [uTitle,     setUTitle]     = useState('');
  const [uSubCat,    setUSubCat]    = useState('');
  const [uDesc,      setUDesc]      = useState('');

  const [editTarget, setEditTarget] = useState(null);
  const [eTitle,     setETitle]     = useState('');
  const [eSubCat,    setESubCat]    = useState('');
  const [eDesc,      setEDesc]      = useState('');

  const [delTarget, setDelTarget] = useState(null);

  const rows = useMemo(() => {
    const q = search.toLowerCase();
    return resources
      .filter(r => r.category === 'FORM_TEMPLATE')
      .filter(r => canManage || r.active)
      .filter(r => !catFilter || r.subCategory === catFilter)
      .filter(r => !q ||
        r.title?.toLowerCase().includes(q) ||
        r.subCategory?.toLowerCase().includes(q) ||
        r.description?.toLowerCase().includes(q));
  }, [resources, search, catFilter, canManage]);

  const paged = useMemo(() => rows.slice(page * rpp, page * rpp + rpp), [rows, page, rpp]);

  const openUpload = () => {
    setFile(null); setUTitle(''); setUSubCat(''); setUDesc('');
    setUploadOpen(true);
  };

  const handleUpload = async () => {
    if (!file)          { toast.error('Please select a file'); return; }
    if (!uTitle.trim()) { toast.error('Title is required');    return; }
    setUploading(true);
    try {
      await resourceApi.upload(file, uTitle, 'FORM_TEMPLATE', uSubCat, uDesc);
      toast.success('Template uploaded');
      setUploadOpen(false);
      onRefresh();
    } catch (e) { toast.error(e?.response?.data?.message || 'Upload failed'); }
    finally { setUploading(false); }
  };

  const handleEdit = async () => {
    if (!eTitle.trim()) { toast.error('Title is required'); return; }
    setSaving(true);
    try {
      await resourceApi.update(editTarget.id, { title: eTitle, subCategory: eSubCat, description: eDesc });
      toast.success('Template updated');
      setEditTarget(null);
      onRefresh();
    } catch { toast.error('Update failed'); }
    finally { setSaving(false); }
  };

  const handleToggle = async (r) => {
    try {
      await resourceApi.toggle(r.id);
      toast.success(r.active ? 'Template deactivated' : 'Template activated');
      onRefresh();
    } catch { toast.error('Failed to update status'); }
  };

  const handleDelete = async () => {
    setSaving(true);
    try {
      await resourceApi.delete(delTarget.id);
      toast.success('Template deleted');
      setDelTarget(null);
      onRefresh();
    } catch { toast.error('Delete failed'); }
    finally { setSaving(false); }
  };

  const handleDownload = async (r) => {
    try {
      const blob = await resourceApi.download(r.id);
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = r.originalFileName || r.title;
      a.click(); URL.revokeObjectURL(url);
    } catch { toast.error('Download failed'); }
  };

  return (
    <Box>
      {/* Section heading */}
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
                 flexWrap: 'wrap', gap: 1.5, mb: 2 }}>
        <Box>
          <Typography fontSize={12} fontWeight={700} color="#64748b"
            sx={{ textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            Uploaded Forms &amp; Templates
          </Typography>
          <Typography fontSize={12} color="text.secondary" mt={0.25}>
            Download and fill out the latest versions
          </Typography>
        </Box>
        {canManage && (
          <Button size="small" variant="contained" startIcon={<UploadFileIcon />} onClick={openUpload}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Upload
          </Button>
        )}
      </Box>

      {/* Filters */}
      <Box sx={{ display: 'flex', gap: 1.5, mb: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField size="small" placeholder="Search forms and templates…"
          value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
          sx={{ width: 280 }}
          InputProps={{ startAdornment: (
            <InputAdornment position="start"><SearchIcon sx={{ fontSize: 18, color: '#94a3b8' }} /></InputAdornment>
          )}}
        />
        <TextField select size="small" label="Category" value={catFilter}
          onChange={e => { setCatFilter(e.target.value); setPage(0); }}
          sx={{ width: 190 }}
          InputProps={{ startAdornment: (
            <InputAdornment position="start"><FilterListIcon sx={{ fontSize: 16, color: '#94a3b8' }} /></InputAdornment>
          )}}>
          <MenuItem value="">All Categories</MenuItem>
          {FORM_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
        </TextField>
        <Box sx={{ flex: 1 }} />
        <Chip label={`${rows.length} template${rows.length !== 1 ? 's' : ''}`}
          sx={{ bgcolor: '#ede9fe', color: '#7c3aed', fontWeight: 600, fontSize: 12 }} />
      </Box>

      {/* Table */}
      <Paper sx={{ border: '1px solid #f1f5f9', borderRadius: 2, overflow: 'hidden',
                   boxShadow: '0 1px 4px rgba(0,0,0,0.05)' }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                {['Title', 'Category', 'Actions'].map(h => (
                  <TableCell key={h} sx={hdr}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {paged.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={3}
                    sx={{ textAlign: 'center', py: 5, color: '#94a3b8', fontSize: 13 }}>
                    {canManage
                      ? 'No templates uploaded yet — click Upload to add one'
                      : 'No templates available'}
                  </TableCell>
                </TableRow>
              ) : paged.map(r => (
                <TableRow key={r.id} hover
                  sx={{ opacity: r.active ? 1 : 0.5, '&:hover': { bgcolor: '#f8fafc' } }}>

                  {/* Title */}
                  <TableCell sx={{ ...cell, maxWidth: 380 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Box sx={{ width: 36, height: 36, borderRadius: 1.5, bgcolor: '#ede9fe',
                                 flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <ArticleIcon sx={{ fontSize: 18, color: '#7c3aed' }} />
                      </Box>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography fontWeight={600} fontSize={13.5} noWrap>{r.title}</Typography>
                        {r.description && (
                          <Typography fontSize={11.5} color="text.secondary" noWrap>
                            {r.description}
                          </Typography>
                        )}
                        {canManage && !r.active && (
                          <Chip label="Inactive" size="small"
                            sx={{ height: 16, fontSize: 10, bgcolor: '#f1f5f9', color: '#94a3b8', mt: 0.3 }} />
                        )}
                      </Box>
                    </Box>
                  </TableCell>

                  {/* Category */}
                  <TableCell sx={cell}>
                    {r.subCategory ? (
                      <Chip label={r.subCategory} size="small"
                        sx={{ bgcolor: '#ede9fe', color: '#7c3aed', fontWeight: 600, fontSize: 11 }} />
                    ) : (
                      <Typography fontSize={12} color="text.secondary">—</Typography>
                    )}
                  </TableCell>

                  {/* Actions */}
                  <TableCell sx={{ ...cell, whiteSpace: 'nowrap' }}>
                    <Tooltip title="Download">
                      <IconButton size="small" onClick={() => handleDownload(r)}
                        sx={{ color: '#0369a1', '&:hover': { bgcolor: '#e0f2fe' } }}>
                        <DownloadIcon sx={{ fontSize: 17 }} />
                      </IconButton>
                    </Tooltip>
                    {canManage && (
                      <>
                        <Tooltip title="Edit">
                          <IconButton size="small"
                            onClick={() => { setEditTarget(r); setETitle(r.title); setESubCat(r.subCategory || ''); setEDesc(r.description || ''); }}
                            sx={{ color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                            <EditIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={r.active ? 'Deactivate' : 'Activate'}>
                          <IconButton size="small" onClick={() => handleToggle(r)}
                            sx={{ color: r.active ? '#f59e0b' : '#16a34a',
                                  '&:hover': { bgcolor: r.active ? '#fef3c7' : '#dcfce7' } }}>
                            {r.active
                              ? <VisibilityOffIcon sx={{ fontSize: 16 }} />
                              : <VisibilityIcon   sx={{ fontSize: 16 }} />}
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Delete">
                          <IconButton size="small" onClick={() => setDelTarget(r)}
                            sx={{ color: '#ef4444', '&:hover': { bgcolor: '#fee2e2' } }}>
                            <DeleteIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={rows.length} page={page}
          onPageChange={(_, p) => setPage(p)} rowsPerPage={rpp}
          onRowsPerPageChange={e => { setRpp(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
          sx={{ borderTop: '1px solid #f1f5f9', '& .MuiTablePagination-toolbar': { fontSize: 12 } }} />
      </Paper>

      {/* ── Upload Dialog ── */}
      <Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          Upload Form / Template
          <IconButton size="small" onClick={() => setUploadOpen(false)}><CloseIcon sx={{ fontSize: 18 }} /></IconButton>
        </DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}
            sx={{ justifyContent: 'flex-start', borderStyle: 'dashed', borderColor: '#cbd5e1',
                  color: '#475569', textTransform: 'none', '&:hover': { borderColor: '#94a3b8' } }}>
            {file ? file.name : 'Choose file (PDF, DOCX, XLSX…)'}
            <input type="file" hidden onChange={e => {
              const f = e.target.files?.[0];
              if (f) { setFile(f); if (!uTitle) setUTitle(f.name.replace(/\.[^/.]+$/, '')); }
            }} />
          </Button>
          <TextField size="small" fullWidth label="Title *"
            value={uTitle} onChange={e => setUTitle(e.target.value)} />
          <TextField select size="small" fullWidth label="Category"
            value={uSubCat} onChange={e => setUSubCat(e.target.value)}>
            <MenuItem value=""><em>None</em></MenuItem>
            {FORM_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
          </TextField>
          <TextField size="small" fullWidth multiline rows={2}
            label="Description (optional)"
            value={uDesc} onChange={e => setUDesc(e.target.value)} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setUploadOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleUpload} disabled={uploading}
            startIcon={uploading ? <CircularProgress size={14} color="inherit" /> : <UploadFileIcon />}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            {uploading ? 'Uploading…' : 'Upload'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Edit Dialog ── */}
      <Dialog open={Boolean(editTarget)} onClose={() => setEditTarget(null)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Edit Template</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField size="small" fullWidth label="Title *"
            value={eTitle} onChange={e => setETitle(e.target.value)} />
          <TextField select size="small" fullWidth label="Category"
            value={eSubCat} onChange={e => setESubCat(e.target.value)}>
            <MenuItem value=""><em>None</em></MenuItem>
            {FORM_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
          </TextField>
          <TextField size="small" fullWidth multiline rows={2}
            label="Description (optional)"
            value={eDesc} onChange={e => setEDesc(e.target.value)} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setEditTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={handleEdit} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <EditIcon />}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Delete Confirm ── */}
      <Dialog open={Boolean(delTarget)} onClose={() => setDelTarget(null)} maxWidth="xs" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, color: '#dc2626' }}>Delete Template</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Typography>Permanently delete <strong>{delTarget?.title}</strong>?</Typography>
          <Typography fontSize={12} color="error" mt={1}>This action cannot be undone.</Typography>
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setDelTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <DeleteIcon />}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

// ── PoliciesSection ───────────────────────────────────────────────────────────
function PoliciesSection({ resources, canManage, onRefresh }) {
  const [search,     setSearch]     = useState('');
  const [catFilter,  setCatFilter]  = useState('');
  const [page,       setPage]       = useState(0);
  const [rpp,        setRpp]        = useState(10);
  const [saving,     setSaving]     = useState(false);
  const [uploading,  setUploading]  = useState(false);

  // upload form
  const [uploadOpen, setUploadOpen] = useState(false);
  const [file,       setFile]       = useState(null);
  const [uTitle,     setUTitle]     = useState('');
  const [uSubCat,    setUSubCat]    = useState('');
  const [uDesc,      setUDesc]      = useState('');

  // edit form
  const [editTarget, setEditTarget] = useState(null);
  const [eTitle,     setETitle]     = useState('');
  const [eSubCat,    setESubCat]    = useState('');
  const [eDesc,      setEDesc]      = useState('');

  // delete confirm
  const [delTarget, setDelTarget] = useState(null);

  const rows = useMemo(() => {
    const q = search.toLowerCase();
    return resources
      .filter(r => r.category === 'POLICY')
      .filter(r => canManage || r.active)                           // hide inactive from employees
      .filter(r => !catFilter || r.subCategory === catFilter)
      .filter(r => !q ||
        r.title?.toLowerCase().includes(q) ||
        r.subCategory?.toLowerCase().includes(q) ||
        r.description?.toLowerCase().includes(q));
  }, [resources, search, catFilter, canManage]);

  const paged = useMemo(() => rows.slice(page * rpp, page * rpp + rpp), [rows, page, rpp]);

  const openUpload = () => {
    setFile(null); setUTitle(''); setUSubCat(''); setUDesc('');
    setUploadOpen(true);
  };
  const openEdit = (r) => {
    setEditTarget(r); setETitle(r.title); setESubCat(r.subCategory || ''); setEDesc(r.description || '');
  };

  const handleUpload = async () => {
    if (!file)          { toast.error('Please select a file'); return; }
    if (!uTitle.trim()) { toast.error('Title is required');    return; }
    setUploading(true);
    try {
      await resourceApi.upload(file, uTitle, 'POLICY', uSubCat, uDesc);
      toast.success('Document uploaded');
      setUploadOpen(false);
      onRefresh();
    } catch (e) { toast.error(e?.response?.data?.message || 'Upload failed'); }
    finally { setUploading(false); }
  };

  const handleEdit = async () => {
    if (!eTitle.trim()) { toast.error('Title is required'); return; }
    setSaving(true);
    try {
      await resourceApi.update(editTarget.id, { title: eTitle, subCategory: eSubCat, description: eDesc });
      toast.success('Document updated');
      setEditTarget(null);
      onRefresh();
    } catch { toast.error('Update failed'); }
    finally { setSaving(false); }
  };

  const handleToggle = async (r) => {
    try {
      await resourceApi.toggle(r.id);
      toast.success(r.active ? 'Document deactivated' : 'Document activated');
      onRefresh();
    } catch { toast.error('Failed to update status'); }
  };

  const handleDelete = async () => {
    setSaving(true);
    try {
      await resourceApi.delete(delTarget.id);
      toast.success('Document deleted');
      setDelTarget(null);
      onRefresh();
    } catch { toast.error('Delete failed'); }
    finally { setSaving(false); }
  };

  const handleView = async (r) => {
    try {
      const blob = await resourceApi.view(r.id);
      const url  = URL.createObjectURL(new Blob([blob], { type: r.fileType || 'application/octet-stream' }));
      window.open(url, '_blank');
    } catch { toast.error('Could not open document'); }
  };

  const handleDownload = async (r) => {
    try {
      const blob = await resourceApi.download(r.id);
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = r.originalFileName || r.title;
      a.click(); URL.revokeObjectURL(url);
    } catch { toast.error('Download failed'); }
  };

  const fmtUpdated = (dt) => {
    if (!dt) return '—';
    return new Date(dt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  };

  return (
    <Box>
      {/* Toolbar */}
      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', mb: 2.5, flexWrap: 'wrap' }}>
        <TextField size="small" placeholder="Search by title or category…"
          value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
          sx={{ width: 280 }}
          InputProps={{ startAdornment: (
            <InputAdornment position="start"><SearchIcon sx={{ fontSize: 18, color: '#94a3b8' }} /></InputAdornment>
          )}}
        />
        <TextField select size="small" label="Category" value={catFilter}
          onChange={e => { setCatFilter(e.target.value); setPage(0); }}
          sx={{ width: 170 }}
          InputProps={{ startAdornment: (
            <InputAdornment position="start"><FilterListIcon sx={{ fontSize: 16, color: '#94a3b8' }} /></InputAdornment>
          )}}>
          <MenuItem value="">All Categories</MenuItem>
          {POLICY_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
        </TextField>
        <Box sx={{ flex: 1 }} />
        <Chip label={`${rows.length} document${rows.length !== 1 ? 's' : ''}`}
          sx={{ bgcolor: '#dbeafe', color: '#1d4ed8', fontWeight: 600, fontSize: 12 }} />
        {canManage && (
          <Button size="small" variant="contained" startIcon={<UploadFileIcon />} onClick={openUpload}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Upload Document
          </Button>
        )}
      </Box>

      {/* Table */}
      <Paper sx={{ border: '1px solid #f1f5f9', borderRadius: 2, overflow: 'hidden',
                   boxShadow: '0 1px 4px rgba(0,0,0,0.05)' }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                {['Title', 'Category', 'Last Updated', 'Actions'].map(h => (
                  <TableCell key={h} sx={hdr}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {paged.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} sx={{ textAlign: 'center', py: 6, color: '#94a3b8', fontSize: 13 }}>
                    {canManage ? 'No documents yet — click Upload Document to add one' : 'No documents available'}
                  </TableCell>
                </TableRow>
              ) : paged.map(r => (
                <TableRow key={r.id} hover
                  sx={{ opacity: r.active ? 1 : 0.5, '&:hover': { bgcolor: '#f8fafc' } }}>

                  {/* Title */}
                  <TableCell sx={{ ...cell, maxWidth: 320 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Box sx={{ width: 34, height: 34, borderRadius: 1.5, bgcolor: '#dbeafe', flexShrink: 0,
                                 display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <DescriptionIcon sx={{ fontSize: 17, color: '#1d4ed8' }} />
                      </Box>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography fontWeight={600} fontSize={13} noWrap>{r.title}</Typography>
                        {r.description && (
                          <Typography fontSize={11} color="text.secondary" noWrap>{r.description}</Typography>
                        )}
                        {canManage && !r.active && (
                          <Chip label="Inactive" size="small"
                            sx={{ height: 16, fontSize: 10, bgcolor: '#f1f5f9', color: '#94a3b8', mt: 0.25 }} />
                        )}
                      </Box>
                    </Box>
                  </TableCell>

                  {/* Category */}
                  <TableCell sx={cell}>
                    {r.subCategory ? (
                      <Chip label={r.subCategory} size="small"
                        sx={{ bgcolor: '#dbeafe', color: '#1d4ed8', fontWeight: 600, fontSize: 11 }} />
                    ) : (
                      <Typography fontSize={12} color="text.secondary">—</Typography>
                    )}
                  </TableCell>

                  {/* Last Updated */}
                  <TableCell sx={{ ...cell, whiteSpace: 'nowrap', color: '#64748b', fontSize: 12 }}>
                    {fmtUpdated(r.updatedAt || r.uploadedAt)}
                  </TableCell>

                  {/* Actions */}
                  <TableCell sx={{ ...cell, whiteSpace: 'nowrap' }}>
                    <Tooltip title="View">
                      <IconButton size="small" onClick={() => handleView(r)}
                        sx={{ color: '#7c3aed', '&:hover': { bgcolor: '#ede9fe' } }}>
                        <OpenInNewIcon sx={{ fontSize: 16 }} />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Download">
                      <IconButton size="small" onClick={() => handleDownload(r)}
                        sx={{ color: '#0369a1', '&:hover': { bgcolor: '#e0f2fe' } }}>
                        <DownloadIcon sx={{ fontSize: 16 }} />
                      </IconButton>
                    </Tooltip>
                    {canManage && (
                      <>
                        <Tooltip title="Edit">
                          <IconButton size="small" onClick={() => openEdit(r)}
                            sx={{ color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                            <EditIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={r.active ? 'Deactivate' : 'Activate'}>
                          <IconButton size="small" onClick={() => handleToggle(r)}
                            sx={{ color: r.active ? '#f59e0b' : '#16a34a',
                                  '&:hover': { bgcolor: r.active ? '#fef3c7' : '#dcfce7' } }}>
                            {r.active
                              ? <VisibilityOffIcon sx={{ fontSize: 16 }} />
                              : <VisibilityIcon   sx={{ fontSize: 16 }} />}
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Delete">
                          <IconButton size="small" onClick={() => setDelTarget(r)}
                            sx={{ color: '#ef4444', '&:hover': { bgcolor: '#fee2e2' } }}>
                            <DeleteIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={rows.length} page={page}
          onPageChange={(_, p) => setPage(p)} rowsPerPage={rpp}
          onRowsPerPageChange={e => { setRpp(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
          sx={{ borderTop: '1px solid #f1f5f9', '& .MuiTablePagination-toolbar': { fontSize: 12 } }} />
      </Paper>

      {/* ── Upload Dialog ── */}
      <Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          Upload Document
          <IconButton size="small" onClick={() => setUploadOpen(false)}><CloseIcon sx={{ fontSize: 18 }} /></IconButton>
        </DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}
            sx={{ justifyContent: 'flex-start', borderStyle: 'dashed', borderColor: '#cbd5e1',
                  color: '#475569', textTransform: 'none', '&:hover': { borderColor: '#94a3b8' } }}>
            {file ? file.name : 'Choose file (PDF, DOCX, XLSX…)'}
            <input type="file" hidden onChange={e => {
              const f = e.target.files?.[0];
              if (f) { setFile(f); if (!uTitle) setUTitle(f.name.replace(/\.[^/.]+$/, '')); }
            }} />
          </Button>
          <TextField size="small" fullWidth label="Document Title *"
            value={uTitle} onChange={e => setUTitle(e.target.value)} />
          <TextField select size="small" fullWidth label="Category"
            value={uSubCat} onChange={e => setUSubCat(e.target.value)}>
            <MenuItem value=""><em>None</em></MenuItem>
            {POLICY_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
          </TextField>
          <TextField size="small" fullWidth multiline rows={2}
            label="Description (optional)"
            value={uDesc} onChange={e => setUDesc(e.target.value)} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setUploadOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleUpload} disabled={uploading}
            startIcon={uploading ? <CircularProgress size={14} color="inherit" /> : <UploadFileIcon />}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            {uploading ? 'Uploading…' : 'Upload'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Edit Dialog ── */}
      <Dialog open={Boolean(editTarget)} onClose={() => setEditTarget(null)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Edit Document</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField size="small" fullWidth label="Document Title *"
            value={eTitle} onChange={e => setETitle(e.target.value)} />
          <TextField select size="small" fullWidth label="Category"
            value={eSubCat} onChange={e => setESubCat(e.target.value)}>
            <MenuItem value=""><em>None</em></MenuItem>
            {POLICY_SUBCATEGORIES.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
          </TextField>
          <TextField size="small" fullWidth multiline rows={2}
            label="Description (optional)"
            value={eDesc} onChange={e => setEDesc(e.target.value)} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setEditTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={handleEdit} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <EditIcon />}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Delete Confirm ── */}
      <Dialog open={Boolean(delTarget)} onClose={() => setDelTarget(null)} maxWidth="xs" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, color: '#dc2626' }}>Delete Document</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Typography>Permanently delete <strong>{delTarget?.title}</strong>?</Typography>
          <Typography fontSize={12} color="error" mt={1}>This action cannot be undone.</Typography>
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setDelTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <DeleteIcon />}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

// ── Resignation Letter helpers ────────────────────────────────────────────────
const EMPTY_LETTER = {
  employeeName: '', employeeId: '', department: '', designation: '',
  managerName: '', resignationDate: '', lastWorkingDay: '',
  reason: '', signature: '', signDate: '',
};

const fmtLongDate = (iso) => {
  if (!iso) return '[Date]';
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'long', year: 'numeric' });
};

function buildLetterHTML(f) {
  const today = fmtLongDate(f.resignationDate || new Date().toISOString().slice(0, 10));
  const lastDay = fmtLongDate(f.lastWorkingDay);
  const signDay = fmtLongDate(f.signDate || new Date().toISOString().slice(0, 10));

  return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<title>Resignation Letter</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Inter', Arial, sans-serif; background: #fff; color: #1e293b; }
  .page { width: 210mm; min-height: 297mm; margin: 0 auto; padding: 20mm 22mm; }
  .header { display: flex; justify-content: space-between; align-items: flex-start; padding-bottom: 10px; border-bottom: 2px solid #1e3a5f; margin-bottom: 18px; }
  .brand-name { font-size: 22px; font-weight: 700; color: #1e3a5f; }
  .brand-sub  { font-size: 10px; color: #64748b; margin-top: 2px; }
  .date-right { font-size: 11px; color: #475569; text-align: right; padding-top: 4px; }
  .to-block   { margin-bottom: 18px; }
  .to-block p { font-size: 11px; color: #475569; line-height: 1.6; }
  .to-block .to-name { font-size: 12px; font-weight: 600; color: #1e293b; }
  .subject    { font-size: 12px; font-weight: 700; color: #1e293b; padding: 10px 0 10px; border-top: 1px solid #e2e8f0; border-bottom: 1px solid #e2e8f0; margin-bottom: 18px; }
  .body-text  { font-size: 11.5px; color: #334155; line-height: 1.85; margin-bottom: 14px; text-align: justify; }
  .salutation { font-size: 12px; color: #1e293b; margin-bottom: 14px; }
  .closing    { font-size: 11.5px; color: #334155; margin-bottom: 24px; }
  .sig-section { margin-top: 10px; }
  .sig-name   { font-size: 14px; font-weight: 700; color: #1e3a5f; margin-bottom: 4px; }
  .sig-line   { width: 160px; border-bottom: 1px solid #94a3b8; margin-bottom: 6px; height: 20px; }
  .sig-detail { font-size: 11px; color: #475569; line-height: 1.7; }
  @media print {
    body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .page { margin: 0; }
  }
</style>
</head>
<body>
<div class="page">
  <div class="header">
    <div>
      <div class="brand-name">EduSAS</div>
      <div class="brand-sub">By SAS KPO Services</div>
    </div>
    <div class="date-right">${today}</div>
  </div>

  <div class="to-block">
    <p>To,</p>
    <p class="to-name">${f.managerName || 'Reporting Manager'}</p>
    <p>Human Resources / Management</p>
    <p>EduSAS — SAS KPO Services</p>
  </div>

  <div class="subject">
    Subject: Resignation from the Position of ${f.designation || '[Designation]'}
  </div>

  <p class="salutation">Dear ${f.managerName || 'Sir/Madam'},</p>

  <p class="body-text">
    I, <strong>${f.employeeName || '[Employee Name]'}</strong> (Employee ID: <strong>${f.employeeId || '[Employee ID]'}</strong>),
    currently serving as <strong>${f.designation || '[Designation]'}</strong> in the
    <strong>${f.department || '[Department]'}</strong> department, hereby tender my resignation
    from my position, effective <strong>${lastDay}</strong>.
  </p>

  <p class="body-text">
    I am providing formal notice as required, and my last working day will be
    <strong>${lastDay}</strong>. I am committed to ensuring a smooth and complete
    handover of all responsibilities during this notice period.
  </p>

  ${f.reason ? `<p class="body-text"><strong>Reason for Resignation:</strong> ${f.reason}</p>` : ''}

  <p class="body-text">
    I would like to express my sincere gratitude for the opportunities, support, and
    valuable experience I have gained during my tenure with the organization. I have
    greatly appreciated being part of this team and the work culture here.
  </p>

  <p class="body-text">
    I would be happy to assist in training a replacement or completing any outstanding
    responsibilities to ensure a seamless transition. Please feel free to reach out for
    any clarification or assistance during this period.
  </p>

  <p class="body-text">Thank you for your understanding and support.</p>

  <p class="closing">Yours sincerely,</p>

  <div class="sig-section">
    ${f.signature
      ? `<div class="sig-name">${f.signature}</div>`
      : `<div class="sig-line"></div>`}
    <div class="sig-detail">
      <strong>${f.employeeName || '[Employee Name]'}</strong><br/>
      ${f.employeeId  ? `Employee ID: ${f.employeeId}<br/>` : ''}
      ${f.designation ? `Designation: ${f.designation}<br/>` : ''}
      ${f.department  ? `Department: ${f.department}<br/>` : ''}
      Date: ${signDay}
    </div>
  </div>
</div>
</body>
</html>`;
}

function generatePDF(f) {
  const doc  = new jsPDF({ unit: 'mm', format: 'a4' });
  const M    = 22;    // margin
  const PW   = 210;   // page width
  const CW   = PW - M * 2; // content width
  let y      = M;

  // helper: write wrapped text, returns new y
  const txt = (text, size, style = 'normal', rgb = [40, 40, 40]) => {
    doc.setFontSize(size);
    doc.setFont('helvetica', style);
    doc.setTextColor(...rgb);
    const lines = doc.splitTextToSize(String(text || ''), CW);
    if (lines.length) doc.text(lines, M, y);
    y += lines.length * (size * 0.42) + 1.5;
  };
  const sp   = (mm = 5) => { y += mm; };
  const hlin = (c = [30, 58, 95], w = 0.5) => {
    doc.setDrawColor(...c); doc.setLineWidth(w);
    doc.line(M, y, PW - M, y); y += 1;
  };

  // ── Header ─────────────────────────────────────────
  doc.setFontSize(20); doc.setFont('helvetica', 'bold');
  doc.setTextColor(30, 58, 95);
  doc.text('EduSAS', M, y);
  doc.setFontSize(9); doc.setFont('helvetica', 'normal');
  doc.setTextColor(100, 100, 100);
  doc.text('By SAS KPO Services', M, y + 5.5);

  // Date right-aligned
  const dateStr = fmtLongDate(f.resignationDate || new Date().toISOString().slice(0, 10));
  doc.setFontSize(10); doc.setFont('helvetica', 'normal');
  doc.setTextColor(80, 80, 80);
  const dw = doc.getTextWidth(dateStr);
  doc.text(dateStr, PW - M - dw, y);

  y += 12; hlin(); sp(7);

  // ── To ────────────────────────────────────────────
  txt('To,', 10, 'normal', [80, 80, 80]); sp(1);
  txt(f.managerName || 'Reporting Manager', 11, 'bold', [20, 20, 20]); sp(0.5);
  txt('Human Resources / Management', 10, 'normal', [80, 80, 80]);
  txt('EduSAS — SAS KPO Services', 10, 'normal', [80, 80, 80]);
  sp(7);

  // ── Subject ───────────────────────────────────────
  hlin([200, 200, 200], 0.3); sp(3);
  txt(`Subject: Resignation from the Position of ${f.designation || '[Designation]'}`, 11, 'bold', [20, 20, 20]);
  sp(2); hlin([200, 200, 200], 0.3); sp(6);

  // ── Salutation ────────────────────────────────────
  txt(`Dear ${f.managerName || 'Sir/Madam'},`, 11, 'normal', [20, 20, 20]); sp(5);

  // ── Body ─────────────────────────────────────────
  const lastDay = fmtLongDate(f.lastWorkingDay);
  txt(
    `I, ${f.employeeName || '[Employee Name]'} (Employee ID: ${f.employeeId || '[Employee ID]'}), ` +
    `currently serving as ${f.designation || '[Designation]'} in the ${f.department || '[Department]'} department, ` +
    `hereby tender my resignation from my position, effective ${lastDay}.`,
    11, 'normal', [40, 40, 40]
  ); sp(4);

  txt(
    `I am providing formal notice as required, and my last working day will be ${lastDay}. ` +
    `I am committed to ensuring a smooth and complete handover of all responsibilities during this notice period.`,
    11, 'normal', [40, 40, 40]
  ); sp(4);

  if (f.reason?.trim()) {
    txt(`Reason for Resignation: ${f.reason}`, 11, 'normal', [40, 40, 40]); sp(4);
  }

  txt(
    `I would like to express my sincere gratitude for the opportunities, support, and valuable experience ` +
    `I have gained during my tenure with the organization. I have greatly appreciated being part of this team.`,
    11, 'normal', [40, 40, 40]
  ); sp(4);

  txt(
    `I would be happy to assist in training a replacement or completing outstanding responsibilities ` +
    `to ensure a seamless transition. Please feel free to reach out for any clarification or assistance.`,
    11, 'normal', [40, 40, 40]
  ); sp(4);

  txt('Thank you for your understanding and support.', 11, 'normal', [40, 40, 40]); sp(7);
  txt('Yours sincerely,', 11, 'normal', [40, 40, 40]); sp(14);

  // ── Signature ─────────────────────────────────────
  if (f.signature?.trim()) {
    doc.setFontSize(13); doc.setFont('helvetica', 'bold');
    doc.setTextColor(30, 58, 95);
    doc.text(f.signature, M, y); y += 7;
  } else {
    doc.setDrawColor(160, 160, 160); doc.setLineWidth(0.3);
    doc.line(M, y, M + 60, y); y += 6;
  }

  doc.setFontSize(11); doc.setFont('helvetica', 'bold'); doc.setTextColor(20, 20, 20);
  doc.text(f.employeeName || '', M, y); y += 5.5;
  doc.setFontSize(10); doc.setFont('helvetica', 'normal'); doc.setTextColor(80, 80, 80);
  if (f.employeeId)   { doc.text(`Employee ID: ${f.employeeId}`, M, y);   y += 5; }
  if (f.designation)  { doc.text(`Designation: ${f.designation}`, M, y);  y += 5; }
  if (f.department)   { doc.text(`Department: ${f.department}`, M, y);    y += 5; }
  doc.text(`Date: ${fmtLongDate(f.signDate || new Date().toISOString().slice(0, 10))}`, M, y);

  doc.save(`Resignation_Letter_${f.employeeName.replace(/\s+/g, '_') || 'Draft'}.pdf`);
}

// ── ResignationLetterTemplate — controlled dialog ─────────────────────────────
function ResignationLetterTemplate({ open, onClose }) {
  const [preview, setPreview] = useState(false);
  const [form,    setForm]    = useState(EMPTY_LETTER);

  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.value }));

  const handleClose = () => {
    setPreview(false);
    setForm(EMPTY_LETTER);
    onClose();
  };

  const handlePrint = () => {
    const win = window.open('', '_blank', 'width=900,height=1100');
    win.document.write(buildLetterHTML(form));
    win.document.close();
    win.focus();
    setTimeout(() => { win.print(); }, 600);
  };

  const previewHTML = buildLetterHTML(form);

  return (
      <Dialog open={open} onClose={handleClose} maxWidth="lg" fullWidth
        PaperProps={{ sx: { borderRadius: 3, maxHeight: '95vh' } }}>

        <DialogTitle sx={{ fontWeight: 700, fontSize: 17, pb: 1,
          display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <AssignmentIcon sx={{ color: '#7c3aed', fontSize: 22 }} />
            Resignation Letter
          </Box>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            <Button size="small" variant={preview ? 'outlined' : 'contained'}
              onClick={() => setPreview(false)}
              sx={!preview ? { bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } } : {}}>
              Fill Details
            </Button>
            <Button size="small" variant={preview ? 'contained' : 'outlined'}
              onClick={() => setPreview(true)}
              sx={preview ? { bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } } : {}}>
              Preview
            </Button>
            <IconButton size="small" onClick={handleClose}>
              <CloseIcon sx={{ fontSize: 18 }} />
            </IconButton>
          </Box>
        </DialogTitle>
        <Divider />

        <DialogContent sx={{ p: 0 }}>

          {/* ── FORM VIEW ─────────────────────────────────────────── */}
          {!preview && (
            <Box sx={{ p: 3 }}>
              <Grid container spacing={2}>
                {/* Section: Employee Information */}
                <Grid item xs={12}>
                  <Typography fontWeight={700} fontSize={13} color="#64748b"
                    sx={{ textTransform: 'uppercase', letterSpacing: '0.05em', mb: 0.5 }}>
                    Employee Information
                  </Typography>
                  <Divider />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField size="small" fullWidth label="Employee Name *"
                    value={form.employeeName} onChange={set('employeeName')} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField size="small" fullWidth label="Employee ID"
                    value={form.employeeId} onChange={set('employeeId')} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField size="small" fullWidth label="Department"
                    value={form.department} onChange={set('department')} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField size="small" fullWidth label="Designation / Position"
                    value={form.designation} onChange={set('designation')} />
                </Grid>

                {/* Section: Resignation Details */}
                <Grid item xs={12} sx={{ mt: 1 }}>
                  <Typography fontWeight={700} fontSize={13} color="#64748b"
                    sx={{ textTransform: 'uppercase', letterSpacing: '0.05em', mb: 0.5 }}>
                    Resignation Details
                  </Typography>
                  <Divider />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField size="small" fullWidth label="Reporting Manager Name"
                    value={form.managerName} onChange={set('managerName')} />
                </Grid>
                <Grid item xs={12} sm={3}>
                  <TextField size="small" fullWidth label="Resignation Date" type="date"
                    InputLabelProps={{ shrink: true }}
                    value={form.resignationDate} onChange={set('resignationDate')} />
                </Grid>
                <Grid item xs={12} sm={3}>
                  <TextField size="small" fullWidth label="Last Working Day" type="date"
                    InputLabelProps={{ shrink: true }}
                    value={form.lastWorkingDay} onChange={set('lastWorkingDay')} />
                </Grid>
                <Grid item xs={12}>
                  <TextField size="small" fullWidth multiline rows={3}
                    label="Reason for Resignation (optional)"
                    placeholder="e.g. Pursuing higher education, personal reasons, better career opportunity…"
                    value={form.reason} onChange={set('reason')} />
                </Grid>

                {/* Section: Signature */}
                <Grid item xs={12} sx={{ mt: 1 }}>
                  <Typography fontWeight={700} fontSize={13} color="#64748b"
                    sx={{ textTransform: 'uppercase', letterSpacing: '0.05em', mb: 0.5 }}>
                    Signature
                  </Typography>
                  <Divider />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField size="small" fullWidth label="Signature (type full name)"
                    placeholder="Type your name as signature"
                    value={form.signature} onChange={set('signature')} />
                </Grid>
                <Grid item xs={12} sm={3}>
                  <TextField size="small" fullWidth label="Date" type="date"
                    InputLabelProps={{ shrink: true }}
                    value={form.signDate} onChange={set('signDate')} />
                </Grid>
              </Grid>

              <Alert severity="info" sx={{ mt: 3, borderRadius: 2, fontSize: 12 }}>
                Fields marked with * are required for the letter. Click <strong>Preview</strong> to see the formatted letter before downloading.
              </Alert>
            </Box>
          )}

          {/* ── PREVIEW VIEW ──────────────────────────────────────── */}
          {preview && (
            <Box sx={{ bgcolor: '#e2e8f0', p: 3, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <iframe
                title="letter-preview"
                srcDoc={previewHTML}
                style={{
                  width: '210mm',
                  minHeight: '297mm',
                  border: 'none',
                  borderRadius: 4,
                  boxShadow: '0 4px 24px rgba(0,0,0,0.18)',
                  background: '#fff',
                }}
              />
            </Box>
          )}
        </DialogContent>

        <Divider />
        <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
          <Button onClick={handleClose} sx={{ color: '#64748b' }}>Close</Button>
          <Box sx={{ flex: 1 }} />
          {preview && (
            <>
              <Button variant="outlined" startIcon={<PrintIcon />} onClick={handlePrint}
                sx={{ borderColor: '#0369a1', color: '#0369a1',
                      '&:hover': { bgcolor: '#e0f2fe', borderColor: '#0284c7' } }}>
                Print
              </Button>
              <Button variant="contained" startIcon={<PictureAsPdfIcon />}
                onClick={() => generatePDF(form)}
                sx={{ bgcolor: '#dc2626', '&:hover': { bgcolor: '#b91c1c' } }}>
                Download PDF
              </Button>
            </>
          )}
          {!preview && (
            <Button variant="contained" onClick={() => setPreview(true)}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
              Preview Letter →
            </Button>
          )}
        </DialogActions>
      </Dialog>
  );
}

// ── DocumentSection ───────────────────────────────────────────────────────────
// eslint-disable-next-line no-unused-vars
function DocumentSection({ category, resources, canManage, onRefresh, userId }) {
  const [search, setSearch] = useState('');
  const [page,   setPage]   = useState(0);
  const [rpp,    setRpp]    = useState(10);

  // dialogs
  const [uploadOpen, setUploadOpen] = useState(false);
  const [editTarget, setEditTarget] = useState(null);
  const [delTarget,  setDelTarget]  = useState(null);
  const [saving,     setSaving]     = useState(false);
  const [uploading,  setUploading]  = useState(false);

  // upload form
  const [file,        setFile]        = useState(null);
  const [titleInput,  setTitleInput]  = useState('');
  const [descInput,   setDescInput]   = useState('');

  // edit form
  const [editTitle, setEditTitle] = useState('');
  const [editDesc,  setEditDesc]  = useState('');

  const rows = useMemo(() => {
    const q = search.toLowerCase();
    return resources.filter(r =>
      r.category === category &&
      (!q || r.title?.toLowerCase().includes(q) ||
             r.originalFileName?.toLowerCase().includes(q))
    );
  }, [resources, category, search]);

  const paged = useMemo(() => rows.slice(page * rpp, page * rpp + rpp), [rows, page, rpp]);

  const handleUpload = async () => {
    if (!file) { toast.error('Please select a file'); return; }
    setUploading(true);
    try {
      await resourceApi.upload(file, titleInput || file.name, category, descInput);
      toast.success('File uploaded successfully');
      setUploadOpen(false);
      setFile(null); setTitleInput(''); setDescInput('');
      onRefresh();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Upload failed');
    } finally { setUploading(false); }
  };

  const handleEdit = async () => {
    if (!editTitle.trim()) { toast.error('Title is required'); return; }
    setSaving(true);
    try {
      await resourceApi.update(editTarget.id, { title: editTitle, description: editDesc });
      toast.success('Updated');
      setEditTarget(null);
      onRefresh();
    } catch { toast.error('Update failed'); }
    finally { setSaving(false); }
  };

  const handleToggle = async (r) => {
    try {
      await resourceApi.toggle(r.id);
      toast.success(r.active ? 'Deactivated' : 'Activated');
      onRefresh();
    } catch { toast.error('Failed to update status'); }
  };

  const handleDelete = async () => {
    setSaving(true);
    try {
      await resourceApi.delete(delTarget.id);
      toast.success('Deleted');
      setDelTarget(null);
      onRefresh();
    } catch { toast.error('Delete failed'); }
    finally { setSaving(false); }
  };

  const handleDownload = async (r) => {
    try {
      const blob = await resourceApi.download(r.id);
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = r.originalFileName || r.title;
      a.click(); URL.revokeObjectURL(url);
    } catch { toast.error('Download failed'); }
  };

  const catCfg = CATEGORIES[category] || CATEGORIES.GENERAL;

  return (
    <Box>
      {/* Built-in templates (Forms & Templates tab only) */}
      {category === 'FORM_TEMPLATE' && (
        <Box>
          <Typography fontSize={12} fontWeight={700} color="#64748b"
            sx={{ textTransform: 'uppercase', letterSpacing: '0.06em', mb: 1.5 }}>
            Built-in Templates
          </Typography>
          <ResignationLetterTemplate />
        </Box>
      )}

      {category !== 'FORM_TEMPLATE' && <Box>
      {/* Toolbar */}
      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', mb: 2, mt: 0, flexWrap: 'wrap' }}>
        <TextField
          size="small" placeholder="Search documents…"
          value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
          sx={{ width: 280 }}
          InputProps={{ startAdornment: (
            <InputAdornment position="start"><SearchIcon sx={{ fontSize: 18, color: '#94a3b8' }} /></InputAdornment>
          )}}
        />
        <Box sx={{ flex: 1 }} />
        <Chip label={`${rows.length} document${rows.length !== 1 ? 's' : ''}`}
          sx={{ bgcolor: catCfg.bg, color: catCfg.color, fontWeight: 600, fontSize: 12 }} />
        {canManage && (
          <Button size="small" variant="contained" startIcon={<UploadFileIcon />}
            onClick={() => { setFile(null); setTitleInput(''); setDescInput(''); setUploadOpen(true); }}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Upload
          </Button>
        )}
      </Box>

      {/* Table */}
      <Paper sx={{ border: '1px solid #f1f5f9', borderRadius: 2, overflow: 'hidden',
                   boxShadow: '0 1px 4px rgba(0,0,0,0.05)' }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                {['Title', 'File Type', 'Size', 'Uploaded By', 'Date', 'Status', 'Actions'].map(h => (
                  <TableCell key={h} sx={hdr}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {paged.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} sx={{ textAlign: 'center', py: 5, color: '#94a3b8', fontSize: 13 }}>
                    No documents found
                  </TableCell>
                </TableRow>
              ) : paged.map(r => (
                <TableRow key={r.id} hover sx={{ opacity: r.active ? 1 : 0.55,
                  '&:hover': { bgcolor: '#f8fafc' } }}>
                  <TableCell sx={cell}>
                    <Typography fontWeight={600} fontSize={13}>{r.title}</Typography>
                    {r.description && (
                      <Typography fontSize={11} color="text.secondary" noWrap sx={{ maxWidth: 240 }}>
                        {r.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell sx={cell}>
                    <Chip label={FILE_TYPE_LABEL(r.fileType)} size="small"
                      sx={{ bgcolor: '#f1f5f9', color: '#475569', fontSize: 11, fontWeight: 600 }} />
                  </TableCell>
                  <TableCell sx={{ ...cell, color: '#64748b' }}>{fmtSize(r.fileSize)}</TableCell>
                  <TableCell sx={{ ...cell, color: '#64748b', fontSize: 12 }}>{r.uploadedByName}</TableCell>
                  <TableCell sx={{ ...cell, color: '#64748b', fontSize: 12, whiteSpace: 'nowrap' }}>
                    {fmtDate(r.uploadedAt)}
                  </TableCell>
                  <TableCell sx={cell}>
                    <Chip label={r.active ? 'Active' : 'Inactive'} size="small"
                      color={r.active ? 'success' : 'default'}
                      sx={{ fontWeight: 600, fontSize: 11 }} />
                  </TableCell>
                  <TableCell sx={{ ...cell, whiteSpace: 'nowrap' }}>
                    <Tooltip title="Download">
                      <IconButton size="small" onClick={() => handleDownload(r)}
                        sx={{ color: '#0369a1', '&:hover': { bgcolor: '#e0f2fe' } }}>
                        <DownloadIcon sx={{ fontSize: 16 }} />
                      </IconButton>
                    </Tooltip>
                    {canManage && (
                      <>
                        <Tooltip title="Edit">
                          <IconButton size="small" onClick={() => { setEditTarget(r); setEditTitle(r.title); setEditDesc(r.description || ''); }}
                            sx={{ color: '#7c3aed', '&:hover': { bgcolor: '#ede9fe' } }}>
                            <EditIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={r.active ? 'Deactivate' : 'Activate'}>
                          <IconButton size="small" onClick={() => handleToggle(r)}
                            sx={{ color: r.active ? '#f59e0b' : '#16a34a',
                                  '&:hover': { bgcolor: r.active ? '#fef3c7' : '#dcfce7' } }}>
                            {r.active ? <VisibilityOffIcon sx={{ fontSize: 16 }} />
                                      : <VisibilityIcon   sx={{ fontSize: 16 }} />}
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Delete">
                          <IconButton size="small" onClick={() => setDelTarget(r)}
                            sx={{ color: '#ef4444', '&:hover': { bgcolor: '#fee2e2' } }}>
                            <DeleteIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={rows.length} page={page}
          onPageChange={(_, p) => setPage(p)} rowsPerPage={rpp}
          onRowsPerPageChange={e => { setRpp(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
          sx={{ borderTop: '1px solid #f1f5f9', '& .MuiTablePagination-toolbar': { fontSize: 12 } }} />
      </Paper>
      </Box>}

      {/* Upload Dialog */}
      <Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          Upload Document
          <IconButton size="small" onClick={() => setUploadOpen(false)}><CloseIcon sx={{ fontSize: 18 }} /></IconButton>
        </DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}
            sx={{ justifyContent: 'flex-start', borderStyle: 'dashed', borderColor: '#cbd5e1',
                  color: '#475569', '&:hover': { borderColor: '#94a3b8' } }}>
            {file ? file.name : 'Choose file (PDF, DOCX, XLSX…)'}
            <input type="file" hidden onChange={e => { const f = e.target.files?.[0]; if (f) { setFile(f); if (!titleInput) setTitleInput(f.name.replace(/\.[^/.]+$/, '')); } }} />
          </Button>
          <TextField size="small" label="Title *" value={titleInput}
            onChange={e => setTitleInput(e.target.value)} fullWidth />
          <TextField size="small" label="Description (optional)" value={descInput}
            onChange={e => setDescInput(e.target.value)} fullWidth multiline rows={2} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setUploadOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleUpload} disabled={uploading}
            startIcon={uploading ? <CircularProgress size={14} color="inherit" /> : <UploadFileIcon />}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            {uploading ? 'Uploading…' : 'Upload'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Dialog */}
      <Dialog open={Boolean(editTarget)} onClose={() => setEditTarget(null)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Edit Document</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField size="small" label="Title *" value={editTitle}
            onChange={e => setEditTitle(e.target.value)} fullWidth />
          <TextField size="small" label="Description" value={editDesc}
            onChange={e => setEditDesc(e.target.value)} fullWidth multiline rows={2} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setEditTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={handleEdit} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <EditIcon />}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirm */}
      <Dialog open={Boolean(delTarget)} onClose={() => setDelTarget(null)} maxWidth="xs" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, color: '#dc2626' }}>Delete Document</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Typography>Permanently delete <strong>{delTarget?.title}</strong>?</Typography>
          <Typography fontSize={12} color="error" mt={1}>This action cannot be undone.</Typography>
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setDelTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <DeleteIcon />}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

// ── FAQSection ────────────────────────────────────────────────────────────────
function FAQSection({ canManage }) {
  const [faqs,       setFaqs]       = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [search,     setSearch]     = useState('');
  const [formOpen,   setFormOpen]   = useState(false);
  const [editTarget, setEditTarget] = useState(null);
  const [delTarget,  setDelTarget]  = useState(null);
  const [saving,     setSaving]     = useState(false);
  const [question,   setQuestion]   = useState('');
  const [answer,     setAnswer]     = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await faqApi.getAll(canManage);
      setFaqs(Array.isArray(data) ? data : []);
    } catch { toast.error('Failed to load FAQs'); }
    finally { setLoading(false); }
  }, [canManage]);

  useEffect(() => { load(); }, [load]);

  const openCreate = () => { setEditTarget(null); setQuestion(''); setAnswer(''); setFormOpen(true); };
  const openEdit   = (f)  => { setEditTarget(f); setQuestion(f.question); setAnswer(f.answer); setFormOpen(true); };

  const handleSave = async () => {
    if (!question.trim()) { toast.error('Question is required'); return; }
    if (!answer.trim())   { toast.error('Answer is required');   return; }
    setSaving(true);
    try {
      if (editTarget) {
        await faqApi.update(editTarget.id, { question, answer });
        toast.success('FAQ updated');
      } else {
        await faqApi.create({ question, answer });
        toast.success('FAQ added');
      }
      setFormOpen(false);
      load();
    } catch { toast.error('Failed to save FAQ'); }
    finally { setSaving(false); }
  };

  const handleToggle = async (f) => {
    try { await faqApi.toggle(f.id); load(); }
    catch { toast.error('Failed to update'); }
  };

  const handleDelete = async () => {
    setSaving(true);
    try { await faqApi.delete(delTarget.id); toast.success('FAQ deleted'); setDelTarget(null); load(); }
    catch { toast.error('Delete failed'); }
    finally { setSaving(false); }
  };

  const visible = useMemo(() => {
    const q = search.toLowerCase();
    return faqs.filter(f => !q || f.question.toLowerCase().includes(q) || f.answer.toLowerCase().includes(q));
  }, [faqs, search]);

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}><CircularProgress size={28} /></Box>;

  return (
    <Box>
      {/* Toolbar */}
      <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
        <TextField size="small" placeholder="Search FAQs…" value={search}
          onChange={e => setSearch(e.target.value)} sx={{ width: 280 }}
          InputProps={{ startAdornment: (
            <InputAdornment position="start"><SearchIcon sx={{ fontSize: 18, color: '#94a3b8' }} /></InputAdornment>
          )}}
        />
        <Box sx={{ flex: 1 }} />
        <Chip label={`${visible.length} FAQ${visible.length !== 1 ? 's' : ''}`}
          sx={{ bgcolor: '#fef9c3', color: '#854d0e', fontWeight: 600, fontSize: 12 }} />
        {canManage && (
          <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={openCreate}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            Add FAQ
          </Button>
        )}
      </Box>

      {visible.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 8, color: '#94a3b8' }}>
          <HelpOutlineIcon sx={{ fontSize: 48, mb: 1, opacity: 0.4 }} />
          <Typography>No FAQs yet</Typography>
        </Box>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {visible.map((f, i) => (
            <Accordion key={f.id} disableGutters elevation={0}
              sx={{ border: '1px solid #e2e8f0', borderRadius: '10px !important',
                    opacity: f.active ? 1 : 0.55,
                    '&:before': { display: 'none' },
                    '&.Mui-expanded': { boxShadow: '0 2px 8px rgba(0,0,0,0.08)' } }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon sx={{ fontSize: 20 }} />}
                sx={{ px: 2.5, py: 0.5, minHeight: 52,
                      '& .MuiAccordionSummary-content': { my: 1 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flex: 1, mr: 1 }}>
                  <Box sx={{ width: 26, height: 26, borderRadius: '50%', bgcolor: '#dbeafe',
                             display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <Typography sx={{ fontSize: 11, fontWeight: 700, color: '#1d4ed8' }}>{i + 1}</Typography>
                  </Box>
                  <Typography fontWeight={600} fontSize={14} color={f.active ? '#1e293b' : '#94a3b8'}>
                    {f.question}
                  </Typography>
                  {canManage && !f.active && (
                    <Chip label="Hidden" size="small"
                      sx={{ bgcolor: '#f1f5f9', color: '#94a3b8', fontSize: 10, height: 18 }} />
                  )}
                </Box>
              </AccordionSummary>
              <AccordionDetails sx={{ px: 2.5, pt: 0, pb: 2 }}>
                <Box sx={{ pl: 4.5 }}>
                  <Typography fontSize={13.5} color="#475569" sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>
                    {f.answer}
                  </Typography>
                  {canManage && (
                    <Box sx={{ display: 'flex', gap: 1, mt: 1.5 }}>
                      <Button size="small" startIcon={<EditIcon sx={{ fontSize: 14 }} />}
                        onClick={() => openEdit(f)}
                        sx={{ fontSize: 12, color: '#7c3aed', '&:hover': { bgcolor: '#ede9fe' } }}>
                        Edit
                      </Button>
                      <Button size="small"
                        startIcon={f.active ? <VisibilityOffIcon sx={{ fontSize: 14 }} /> : <VisibilityIcon sx={{ fontSize: 14 }} />}
                        onClick={() => handleToggle(f)}
                        sx={{ fontSize: 12, color: '#f59e0b', '&:hover': { bgcolor: '#fef3c7' } }}>
                        {f.active ? 'Hide' : 'Show'}
                      </Button>
                      <Button size="small" startIcon={<DeleteIcon sx={{ fontSize: 14 }} />}
                        onClick={() => setDelTarget(f)}
                        sx={{ fontSize: 12, color: '#ef4444', '&:hover': { bgcolor: '#fee2e2' } }}>
                        Delete
                      </Button>
                    </Box>
                  )}
                </Box>
              </AccordionDetails>
            </Accordion>
          ))}
        </Box>
      )}

      {/* Add / Edit FAQ Dialog */}
      <Dialog open={formOpen} onClose={() => setFormOpen(false)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          {editTarget ? 'Edit FAQ' : 'New FAQ'}
          <IconButton size="small" onClick={() => setFormOpen(false)}><CloseIcon sx={{ fontSize: 18 }} /></IconButton>
        </DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField size="small" label="Question *" value={question}
            onChange={e => setQuestion(e.target.value)} fullWidth multiline rows={2} />
          <TextField size="small" label="Answer *" value={answer}
            onChange={e => setAnswer(e.target.value)} fullWidth multiline rows={4} />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setFormOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : null}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' } }}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirm */}
      <Dialog open={Boolean(delTarget)} onClose={() => setDelTarget(null)} maxWidth="xs" fullWidth
        PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, color: '#dc2626' }}>Delete FAQ</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Typography fontSize={14}>Delete this FAQ?</Typography>
          <Typography fontSize={13} color="text.secondary" mt={0.5}>"{delTarget?.question}"</Typography>
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setDelTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete} disabled={saving}>Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export default function ResourcesPage() {
  const { user }  = useSelector(s => s.auth);
  const canManage = user?.role === 'ADMIN' || user?.role === 'HR';

  const [tab,       setTab]       = useState(0);
  const [resources, setResources] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const data = await resourceApi.getAll();
      setResources(Array.isArray(data) ? data : []);
    } catch { setError('Failed to load resources'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const TABS = [
    { label: 'Policies & Documents', icon: <DescriptionIcon sx={{ fontSize: 18 }} />, category: 'POLICY' },
    { label: 'Forms & Templates',    icon: <ArticleIcon     sx={{ fontSize: 18 }} />, category: 'FORM_TEMPLATE' },
    { label: 'FAQs',                 icon: <HelpOutlineIcon sx={{ fontSize: 18 }} />, category: null },
  ];

  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh', p: { xs: 2, md: 3 } }}>

      {/* Header */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" fontWeight={800} color="#1e293b">Resources</Typography>
        <Typography fontSize={14} color="text.secondary" mt={0.25}>
          Company policies, forms, and frequently asked questions
        </Typography>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}>{error}</Alert>}

      {/* Tab navigation */}
      <Paper sx={{ borderRadius: 2, overflow: 'hidden', mb: 3,
                   boxShadow: '0 1px 4px rgba(0,0,0,0.06)', border: '1px solid #f1f5f9' }}>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="fullWidth"
          sx={{
            '& .MuiTab-root': { textTransform: 'none', fontWeight: 500, fontSize: 13.5,
                                 minHeight: 52, gap: 0.75 },
            '& .Mui-selected': { fontWeight: 700, color: '#1e3a5f' },
            '& .MuiTabs-indicator': { bgcolor: '#1e3a5f', height: 3 },
          }}>
          {TABS.map((t, i) => (
            <Tab key={i} icon={t.icon} iconPosition="start" label={t.label} />
          ))}
        </Tabs>
      </Paper>

      {/* Tab content */}
      <Paper sx={{ p: 3, borderRadius: 2, border: '1px solid #f1f5f9',
                   boxShadow: '0 1px 4px rgba(0,0,0,0.05)' }}>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress size={28} />
          </Box>
        ) : (
          <>
            {tab === 0 && (
              <PoliciesSection resources={resources}
                canManage={canManage} onRefresh={load} />
            )}
            {tab === 1 && (
              <FormsTemplatesSection resources={resources}
                canManage={canManage} onRefresh={load} />
            )}
            {tab === 2 && (
              <FAQSection canManage={canManage} />
            )}
          </>
        )}
      </Paper>
    </Box>
  );
}
