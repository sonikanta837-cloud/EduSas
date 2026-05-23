import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Box, Typography, Paper, Grid, Card, CardContent, Button, IconButton,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TablePagination, Chip, TextField, MenuItem, InputAdornment,
  Dialog, DialogTitle, DialogContent, DialogActions,
  Tooltip, CircularProgress, Alert, Divider, Avatar,
} from '@mui/material';
import SearchIcon         from '@mui/icons-material/Search';
import AddIcon            from '@mui/icons-material/Add';
import EditIcon           from '@mui/icons-material/Edit';
import DeleteIcon         from '@mui/icons-material/Delete';
import PersonAddIcon      from '@mui/icons-material/PersonAdd';
import AssignmentReturnIcon from '@mui/icons-material/AssignmentReturn';
import LaptopIcon         from '@mui/icons-material/Laptop';
import MonitorIcon        from '@mui/icons-material/Monitor';
import BadgeIcon          from '@mui/icons-material/Badge';
import KeyboardIcon       from '@mui/icons-material/Keyboard';
import MouseIcon          from '@mui/icons-material/Mouse';
import HeadsetIcon        from '@mui/icons-material/Headset';
import PhoneIphoneIcon    from '@mui/icons-material/PhoneIphone';
import TabletIcon         from '@mui/icons-material/Tablet';
import InventoryIcon      from '@mui/icons-material/Inventory';
import CodeIcon           from '@mui/icons-material/Code';
import CheckCircleIcon    from '@mui/icons-material/CheckCircle';
import BuildIcon          from '@mui/icons-material/Build';
import BlockIcon          from '@mui/icons-material/Block';
import FileDownloadIcon   from '@mui/icons-material/FileDownload';
import CloseIcon          from '@mui/icons-material/Close';
import { assetApi }       from '../api/assetApi';
import { employeeApi }    from '../api/employeeApi';
import * as XLSX          from 'xlsx';

// ─── constants ────────────────────────────────────────────────────────────────
const ASSET_TYPES = ['LAPTOP','MONITOR','ID_CARD','SOFTWARE_LICENSE','MOUSE','KEYBOARD','HEADSET','PHONE','TABLET','OTHER'];
const ASSET_STATUSES = ['AVAILABLE','ASSIGNED','UNDER_MAINTENANCE','RETIRED'];
const ASSET_CONDITIONS = ['NEW','GOOD','FAIR','POOR'];

const TYPE_ICONS = {
  LAPTOP:           <LaptopIcon sx={{ fontSize: 18 }} />,
  MONITOR:          <MonitorIcon sx={{ fontSize: 18 }} />,
  ID_CARD:          <BadgeIcon sx={{ fontSize: 18 }} />,
  SOFTWARE_LICENSE: <CodeIcon sx={{ fontSize: 18 }} />,
  MOUSE:            <MouseIcon sx={{ fontSize: 18 }} />,
  KEYBOARD:         <KeyboardIcon sx={{ fontSize: 18 }} />,
  HEADSET:          <HeadsetIcon sx={{ fontSize: 18 }} />,
  PHONE:            <PhoneIphoneIcon sx={{ fontSize: 18 }} />,
  TABLET:           <TabletIcon sx={{ fontSize: 18 }} />,
  OTHER:            <InventoryIcon sx={{ fontSize: 18 }} />,
};

const STATUS_CFG = {
  AVAILABLE:         { label: 'Available',          color: 'success', icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> },
  ASSIGNED:          { label: 'Assigned',           color: 'primary', icon: <PersonAddIcon sx={{ fontSize: 14 }} /> },
  UNDER_MAINTENANCE: { label: 'Maintenance',        color: 'warning', icon: <BuildIcon sx={{ fontSize: 14 }} /> },
  RETIRED:           { label: 'Retired',            color: 'error',   icon: <BlockIcon sx={{ fontSize: 14 }} /> },
};

const CONDITION_CFG = {
  NEW:  { label: 'New',  bg: '#dcfce7', color: '#166534' },
  GOOD: { label: 'Good', bg: '#dbeafe', color: '#1d4ed8' },
  FAIR: { label: 'Fair', bg: '#fef9c3', color: '#854d0e' },
  POOR: { label: 'Poor', bg: '#fee2e2', color: '#991b1b' },
};

const KPI_CARDS = [
  { key: 'total',            label: 'Total Assets',       accent: '#6366f1', bg: '#ede9fe' },
  { key: 'available',        label: 'Available',          accent: '#16a34a', bg: '#dcfce7' },
  { key: 'assigned',         label: 'Assigned',           accent: '#0369a1', bg: '#e0f2fe' },
  { key: 'underMaintenance', label: 'Under Maintenance',  accent: '#d97706', bg: '#fef3c7' },
  { key: 'retired',          label: 'Retired',            accent: '#dc2626', bg: '#fee2e2' },
];

const EMPTY_FORM = {
  assetName: '', assetType: 'LAPTOP', brand: '', model: '',
  serialNumber: '', purchaseDate: '', purchasePrice: '',
  status: 'AVAILABLE', condition: 'GOOD', notes: '',
};

const EMPTY_ASSIGN = { employeeId: '', assignedDate: '', expectedReturnDate: '' };

// ─── style helpers ─────────────────────────────────────────────────────────────
const hdr = {
  fontWeight: 700, fontSize: '0.75rem', color: '#64748b',
  textTransform: 'uppercase', letterSpacing: '0.05em',
  py: 1.5, px: 2, borderBottom: '2px solid #f1f5f9', whiteSpace: 'nowrap',
};
const cell = { py: 1.5, px: 2, fontSize: '0.85rem', color: '#334155', borderBottom: '1px solid #f8fafc' };

// ─── sub-components ────────────────────────────────────────────────────────────
function KpiCard({ label, value, accent, bg }) {
  return (
    <Card sx={{ borderRadius: 3, border: '1px solid #f1f5f9', boxShadow: '0 1px 6px rgba(0,0,0,0.06)', height: '100%' }}>
      <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Box sx={{ width: 48, height: 48, borderRadius: 2.5, bgcolor: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <InventoryIcon sx={{ fontSize: 24, color: accent }} />
          </Box>
          <Box>
            <Typography sx={{ fontSize: '1.75rem', fontWeight: 800, color: accent, lineHeight: 1 }}>{value ?? '–'}</Typography>
            <Typography sx={{ fontSize: '0.78rem', color: '#64748b', mt: 0.3, fontWeight: 500 }}>{label}</Typography>
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}

function TypeLabel({ type }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, color: '#475569' }}>
      {TYPE_ICONS[type] || <InventoryIcon sx={{ fontSize: 18 }} />}
      <Typography sx={{ fontSize: '0.82rem' }}>{(type || '').replace(/_/g, ' ')}</Typography>
    </Box>
  );
}

function StatusBadge({ status }) {
  const cfg = STATUS_CFG[status] || { label: status, color: 'default', icon: null };
  return <Chip label={cfg.label} color={cfg.color} size="small" sx={{ fontWeight: 600, fontSize: '0.72rem' }} />;
}

function ConditionBadge({ condition }) {
  const cfg = CONDITION_CFG[condition] || { label: condition, bg: '#f1f5f9', color: '#475569' };
  return (
    <Chip label={cfg.label} size="small"
      sx={{ fontWeight: 600, fontSize: '0.72rem', bgcolor: cfg.bg, color: cfg.color, border: 'none' }} />
  );
}

// ─── main page ─────────────────────────────────────────────────────────────────
export default function AssetManagement() {
  const [assets,    setAssets]    = useState([]);
  const [stats,     setStats]     = useState({});
  const [employees, setEmployees] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState('');

  // filters
  const [search,       setSearch]       = useState('');
  const [filterType,   setFilterType]   = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterDept,   setFilterDept]   = useState('');

  // pagination
  const [page,    setPage]    = useState(0);
  const [rowsPP,  setRowsPP]  = useState(10);

  // dialogs
  const [addOpen,    setAddOpen]    = useState(false);
  const [editOpen,   setEditOpen]   = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);
  const [returnOpen, setReturnOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [selected,   setSelected]   = useState(null);

  // forms
  const [form,         setForm]         = useState(EMPTY_FORM);
  const [assignForm,   setAssignForm]   = useState(EMPTY_ASSIGN);
  const [saving,       setSaving]       = useState(false);
  const [formError,    setFormError]    = useState('');

  // ── load data ─────────────────────────────────────────────────────────────
  const loadAll = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [assetsRes, statsRes, empRes] = await Promise.all([
        assetApi.getAll(),
        assetApi.getStats(),
        employeeApi.getAll(),
      ]);
      setAssets(Array.isArray(assetsRes) ? assetsRes : (assetsRes?.data || []));
      setStats(statsRes || {});
      const empList = Array.isArray(empRes) ? empRes : (empRes?.data || []);
      setEmployees(empList.filter(e => e.status !== 'INACTIVE'));
    } catch (e) {
      setError('Failed to load asset data.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  // ── derived data ──────────────────────────────────────────────────────────
  const departments = useMemo(() => [...new Set(employees.map(e => e.department).filter(Boolean))].sort(), [employees]);

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return assets.filter(a => {
      if (filterType   && a.assetType !== filterType)                   return false;
      if (filterStatus && a.status    !== filterStatus)                 return false;
      if (filterDept   && a.assignedEmployeeDepartment !== filterDept)  return false;
      if (q && !(
        (a.assetCode  || '').toLowerCase().includes(q) ||
        (a.assetName  || '').toLowerCase().includes(q) ||
        (a.brand      || '').toLowerCase().includes(q) ||
        (a.model      || '').toLowerCase().includes(q) ||
        (a.serialNumber || '').toLowerCase().includes(q) ||
        (a.assignedEmployeeName || '').toLowerCase().includes(q)
      )) return false;
      return true;
    });
  }, [assets, search, filterType, filterStatus, filterDept]);

  const paginated = useMemo(() => filtered.slice(page * rowsPP, page * rowsPP + rowsPP), [filtered, page, rowsPP]);

  // ── handlers: Add ─────────────────────────────────────────────────────────
  const openAdd = () => { setForm(EMPTY_FORM); setFormError(''); setAddOpen(true); };

  const handleAdd = async () => {
    if (!form.assetName.trim() || !form.assetType) { setFormError('Asset name and type are required.'); return; }
    setSaving(true); setFormError('');
    try {
      await assetApi.create({
        ...form,
        purchasePrice: form.purchasePrice ? parseFloat(form.purchasePrice) : null,
        purchaseDate:  form.purchaseDate  || null,
      });
      setAddOpen(false);
      loadAll();
    } catch (e) {
      setFormError(e?.response?.data?.message || 'Failed to create asset.');
    } finally { setSaving(false); }
  };

  // ── handlers: Edit ────────────────────────────────────────────────────────
  const openEdit = (a) => {
    setSelected(a);
    setForm({
      assetName:    a.assetName    || '',
      assetType:    a.assetType    || 'LAPTOP',
      brand:        a.brand        || '',
      model:        a.model        || '',
      serialNumber: a.serialNumber || '',
      purchaseDate: a.purchaseDate || '',
      purchasePrice: a.purchasePrice != null ? String(a.purchasePrice) : '',
      status:       a.status       || 'AVAILABLE',
      condition:    a.condition    || 'GOOD',
      notes:        a.notes        || '',
    });
    setFormError('');
    setEditOpen(true);
  };

  const handleEdit = async () => {
    if (!form.assetName.trim()) { setFormError('Asset name is required.'); return; }
    setSaving(true); setFormError('');
    try {
      await assetApi.update(selected.id, {
        ...form,
        purchasePrice: form.purchasePrice ? parseFloat(form.purchasePrice) : null,
        purchaseDate:  form.purchaseDate  || null,
      });
      setEditOpen(false);
      loadAll();
    } catch (e) {
      setFormError(e?.response?.data?.message || 'Failed to update asset.');
    } finally { setSaving(false); }
  };

  // ── handlers: Assign ──────────────────────────────────────────────────────
  const openAssign = (a) => { setSelected(a); setAssignForm(EMPTY_ASSIGN); setFormError(''); setAssignOpen(true); };

  const handleAssign = async () => {
    if (!assignForm.employeeId) { setFormError('Please select an employee.'); return; }
    setSaving(true); setFormError('');
    try {
      await assetApi.assign(
        selected.id,
        assignForm.employeeId,
        assignForm.assignedDate       || undefined,
        assignForm.expectedReturnDate || undefined,
      );
      setAssignOpen(false);
      loadAll();
    } catch (e) {
      setFormError(e?.response?.data?.message || 'Failed to assign asset.');
    } finally { setSaving(false); }
  };

  // ── handlers: Return ──────────────────────────────────────────────────────
  const openReturn = (a) => { setSelected(a); setReturnOpen(true); };

  const handleReturn = async () => {
    setSaving(true);
    try {
      await assetApi.returnAsset(selected.id);
      setReturnOpen(false);
      loadAll();
    } catch (e) {
      setError(e?.response?.data?.message || 'Failed to return asset.');
    } finally { setSaving(false); }
  };

  // ── handlers: Delete ──────────────────────────────────────────────────────
  const openDelete = (a) => { setSelected(a); setDeleteOpen(true); };

  const handleDelete = async () => {
    setSaving(true);
    try {
      await assetApi.delete(selected.id);
      setDeleteOpen(false);
      loadAll();
    } catch (e) {
      setError(e?.response?.data?.message || 'Failed to delete asset.');
    } finally { setSaving(false); }
  };

  // ── export ────────────────────────────────────────────────────────────────
  const exportExcel = () => {
    const rows = filtered.map(a => ({
      'Asset Code':     a.assetCode,
      'Asset Name':     a.assetName,
      'Type':           (a.assetType || '').replace(/_/g, ' '),
      'Brand':          a.brand        || '',
      'Model':          a.model        || '',
      'Serial No.':     a.serialNumber || '',
      'Purchase Date':  a.purchaseDate || '',
      'Purchase Price': a.purchasePrice != null ? a.purchasePrice : '',
      'Status':         (a.status    || '').replace(/_/g, ' '),
      'Condition':      a.condition  || '',
      'Assigned To':    a.assignedEmployeeName || '',
      'Department':     a.assignedEmployeeDepartment || '',
      'Emp Code':       a.assignedEmployeeCode || '',
      'Assigned Date':  a.assignedDate       || '',
      'Expected Return':a.expectedReturnDate  || '',
      'Actual Return':  a.actualReturnDate    || '',
      'Notes':          a.notes              || '',
    }));
    const wb = XLSX.utils.book_new();
    const ws = XLSX.utils.json_to_sheet(rows);
    XLSX.utils.book_append_sheet(wb, ws, 'Assets');
    XLSX.writeFile(wb, `assets_${new Date().toISOString().slice(0,10)}.xlsx`);
  };

  // ── form change helpers ───────────────────────────────────────────────────
  const onForm = (field) => (e) => setForm(f => ({ ...f, [field]: e.target.value }));
  const onAssign = (field) => (e) => setAssignForm(f => ({ ...f, [field]: e.target.value }));

  // ─── render ───────────────────────────────────────────────────────────────
  if (loading) return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '60vh', gap: 2 }}>
      <CircularProgress size={32} sx={{ color: '#14b8a6' }} />
      <Typography color="text.secondary">Loading asset data…</Typography>
    </Box>
  );

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, bgcolor: '#f8fafc', minHeight: '100vh' }}>

      {/* ── Header ── */}
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800, color: '#1e293b', mb: 0.3 }}>Asset Management</Typography>
          <Typography sx={{ fontSize: '0.88rem', color: '#64748b' }}>Track and manage company assets assigned to employees</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
          <Button variant="outlined" startIcon={<FileDownloadIcon />} onClick={exportExcel}
            sx={{ borderColor: '#e2e8f0', color: '#475569', '&:hover': { borderColor: '#94a3b8', bgcolor: '#f8fafc' } }}>
            Export Excel
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={openAdd}
            sx={{ bgcolor: '#14b8a6', '&:hover': { bgcolor: '#0d9488' } }}>
            Add Asset
          </Button>
        </Box>
      </Box>

      {error && <Alert severity="error" onClose={() => setError('')} sx={{ mb: 2, borderRadius: 2 }}>{error}</Alert>}

      {/* ── KPI Cards ── */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {KPI_CARDS.map(k => (
          <Grid item xs={12} sm={6} md={2.4} key={k.key}>
            <KpiCard label={k.label} value={stats[k.key]} accent={k.accent} bg={k.bg} />
          </Grid>
        ))}
      </Grid>

      {/* ── Filters ── */}
      <Paper sx={{ p: 2, mb: 2.5, border: '1px solid #f1f5f9', boxShadow: '0 1px 4px rgba(0,0,0,0.04)', borderRadius: 2.5 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} sm={6} md={4}>
            <TextField size="small" fullWidth placeholder="Search by name, code, brand, serial…"
              value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
              InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon sx={{ fontSize: 18, color: '#94a3b8' }} /></InputAdornment> }}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2, bgcolor: '#f8fafc' } }} />
          </Grid>
          <Grid item xs={6} sm={3} md={2}>
            <TextField select size="small" fullWidth label="Asset Type" value={filterType}
              onChange={e => { setFilterType(e.target.value); setPage(0); }}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
              <MenuItem value="">All Types</MenuItem>
              {ASSET_TYPES.map(t => <MenuItem key={t} value={t}>{t.replace(/_/g, ' ')}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={6} sm={3} md={2}>
            <TextField select size="small" fullWidth label="Status" value={filterStatus}
              onChange={e => { setFilterStatus(e.target.value); setPage(0); }}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
              <MenuItem value="">All Statuses</MenuItem>
              {ASSET_STATUSES.map(s => <MenuItem key={s} value={s}>{s.replace(/_/g, ' ')}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={6} sm={3} md={2}>
            <TextField select size="small" fullWidth label="Department" value={filterDept}
              onChange={e => { setFilterDept(e.target.value); setPage(0); }}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
              <MenuItem value="">All Departments</MenuItem>
              {departments.map(d => <MenuItem key={d} value={d}>{d}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={6} sm={3} md={2}>
            <Button variant="text" onClick={() => { setSearch(''); setFilterType(''); setFilterStatus(''); setFilterDept(''); setPage(0); }}
              sx={{ color: '#64748b', '&:hover': { color: '#14b8a6' }, fontSize: '0.82rem' }}>
              Clear Filters
            </Button>
          </Grid>
        </Grid>
      </Paper>

      {/* ── Table ── */}
      <Paper sx={{ border: '1px solid #f1f5f9', boxShadow: '0 1px 6px rgba(0,0,0,0.05)', borderRadius: 2.5, overflow: 'hidden' }}>
        <Box sx={{ px: 2.5, py: 1.8, borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography sx={{ fontWeight: 700, color: '#1e293b', fontSize: '0.95rem' }}>
            Asset Inventory
          </Typography>
          <Chip label={`${filtered.length} assets`} size="small" sx={{ bgcolor: '#f1f5f9', color: '#64748b', fontWeight: 600, fontSize: '0.75rem' }} />
        </Box>

        <TableContainer>
          <Table stickyHeader size="small">
            <TableHead>
              <TableRow>
                {['Asset Code','Asset Name','Type','Brand / Model','Serial No.','Assigned To','Status','Condition','Actions'].map(h => (
                  <TableCell key={h} sx={hdr}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {paginated.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} sx={{ textAlign: 'center', py: 5, color: '#94a3b8', fontSize: '0.9rem' }}>
                    No assets found
                  </TableCell>
                </TableRow>
              ) : paginated.map(a => (
                <TableRow key={a.id} hover sx={{ '&:hover': { bgcolor: '#f8fafc' } }}>
                  <TableCell sx={cell}>
                    <Typography sx={{ fontWeight: 700, fontSize: '0.82rem', color: '#6366f1', fontFamily: 'monospace' }}>{a.assetCode}</Typography>
                  </TableCell>
                  <TableCell sx={cell}>
                    <Typography sx={{ fontWeight: 600, fontSize: '0.85rem', color: '#1e293b' }}>{a.assetName}</Typography>
                    {a.notes && <Typography sx={{ fontSize: '0.72rem', color: '#94a3b8', mt: 0.2 }} noWrap>{a.notes}</Typography>}
                  </TableCell>
                  <TableCell sx={cell}><TypeLabel type={a.assetType} /></TableCell>
                  <TableCell sx={cell}>
                    <Typography sx={{ fontSize: '0.83rem', color: '#334155' }}>{[a.brand, a.model].filter(Boolean).join(' · ') || '—'}</Typography>
                  </TableCell>
                  <TableCell sx={cell}>
                    <Typography sx={{ fontSize: '0.8rem', fontFamily: 'monospace', color: '#475569' }}>{a.serialNumber || '—'}</Typography>
                  </TableCell>
                  <TableCell sx={cell}>
                    {a.assignedEmployeeName ? (
                      <Box>
                        <Typography sx={{ fontSize: '0.83rem', fontWeight: 600, color: '#1e293b' }}>{a.assignedEmployeeName}</Typography>
                        <Typography sx={{ fontSize: '0.72rem', color: '#64748b' }}>{a.assignedEmployeeDepartment} · {a.assignedEmployeeCode}</Typography>
                        {a.assignedDate && <Typography sx={{ fontSize: '0.7rem', color: '#94a3b8' }}>Since {a.assignedDate}</Typography>}
                      </Box>
                    ) : <Typography sx={{ fontSize: '0.82rem', color: '#94a3b8' }}>—</Typography>}
                  </TableCell>
                  <TableCell sx={cell}><StatusBadge status={a.status} /></TableCell>
                  <TableCell sx={cell}><ConditionBadge condition={a.condition} /></TableCell>
                  <TableCell sx={{ ...cell, whiteSpace: 'nowrap' }}>
                    {a.status === 'AVAILABLE' && (
                      <Tooltip title="Assign to Employee">
                        <IconButton size="small" onClick={() => openAssign(a)} sx={{ color: '#0369a1', '&:hover': { bgcolor: '#e0f2fe' } }}>
                          <PersonAddIcon sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                    )}
                    {a.status === 'ASSIGNED' && (
                      <Tooltip title="Return Asset">
                        <IconButton size="small" onClick={() => openReturn(a)} sx={{ color: '#16a34a', '&:hover': { bgcolor: '#dcfce7' } }}>
                          <AssignmentReturnIcon sx={{ fontSize: 17 }} />
                        </IconButton>
                      </Tooltip>
                    )}
                    <Tooltip title="Edit Asset">
                      <IconButton size="small" onClick={() => openEdit(a)} sx={{ color: '#6366f1', '&:hover': { bgcolor: '#ede9fe' } }}>
                        <EditIcon sx={{ fontSize: 17 }} />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete Asset">
                      <IconButton size="small" onClick={() => openDelete(a)} sx={{ color: '#ef4444', '&:hover': { bgcolor: '#fee2e2' } }}>
                        <DeleteIcon sx={{ fontSize: 17 }} />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>

        <TablePagination
          component="div"
          count={filtered.length}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          rowsPerPage={rowsPP}
          onRowsPerPageChange={e => { setRowsPP(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
          sx={{ borderTop: '1px solid #f1f5f9', '& .MuiTablePagination-toolbar': { fontSize: '0.82rem' } }}
        />
      </Paper>

      {/* ════ Add Asset Dialog ════ */}
      <AssetFormDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="Add New Asset"
        form={form}
        onChange={onForm}
        onSubmit={handleAdd}
        saving={saving}
        error={formError}
      />

      {/* ════ Edit Asset Dialog ════ */}
      <AssetFormDialog
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title={`Edit — ${selected?.assetCode || ''}`}
        form={form}
        onChange={onForm}
        onSubmit={handleEdit}
        saving={saving}
        error={formError}
        isEdit
        assetStatus={selected?.status}
      />

      {/* ════ Assign Dialog ════ */}
      <Dialog open={assignOpen} onClose={() => setAssignOpen(false)} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, pb: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          Assign Asset — {selected?.assetCode}
          <IconButton size="small" onClick={() => setAssignOpen(false)}><CloseIcon sx={{ fontSize: 18 }} /></IconButton>
        </DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2.5 }}>
          {formError && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}>{formError}</Alert>}
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField select fullWidth label="Assign To Employee *" value={assignForm.employeeId}
                onChange={onAssign('employeeId')} size="small"
                sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
                {employees.map(e => (
                  <MenuItem key={e.id} value={e.id}>
                    {e.fullName} — {e.department} ({e.employeeCode})
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth type="date" label="Assigned Date" size="small"
                value={assignForm.assignedDate} onChange={onAssign('assignedDate')}
                InputLabelProps={{ shrink: true }}
                sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
            </Grid>
            <Grid item xs={6}>
              <TextField fullWidth type="date" label="Expected Return Date" size="small"
                value={assignForm.expectedReturnDate} onChange={onAssign('expectedReturnDate')}
                InputLabelProps={{ shrink: true }}
                sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
            </Grid>
          </Grid>
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
          <Button variant="outlined" onClick={() => setAssignOpen(false)}
            sx={{ borderColor: '#e2e8f0', color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={handleAssign} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <PersonAddIcon />}
            sx={{ bgcolor: '#0369a1', '&:hover': { bgcolor: '#0284c7' } }}>
            Assign Asset
          </Button>
        </DialogActions>
      </Dialog>

      {/* ════ Return Confirm Dialog ════ */}
      <Dialog open={returnOpen} onClose={() => setReturnOpen(false)} maxWidth="xs" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700 }}>Return Asset</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Typography sx={{ color: '#475569' }}>
            Mark <strong>{selected?.assetCode} — {selected?.assetName}</strong> as returned from{' '}
            <strong>{selected?.assignedEmployeeName}</strong>?
          </Typography>
          <Typography sx={{ fontSize: '0.82rem', color: '#94a3b8', mt: 1 }}>
            The asset status will be set back to <strong>Available</strong>.
          </Typography>
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
          <Button variant="outlined" onClick={() => setReturnOpen(false)}
            sx={{ borderColor: '#e2e8f0', color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={handleReturn} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <AssignmentReturnIcon />}
            sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' } }}>
            Confirm Return
          </Button>
        </DialogActions>
      </Dialog>

      {/* ════ Delete Confirm Dialog ════ */}
      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)} maxWidth="xs" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
        <DialogTitle sx={{ fontWeight: 700, color: '#dc2626' }}>Delete Asset</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Typography sx={{ color: '#475569' }}>
            Permanently delete <strong>{selected?.assetCode} — {selected?.assetName}</strong>?
          </Typography>
          <Typography sx={{ fontSize: '0.82rem', color: '#ef4444', mt: 1 }}>This action cannot be undone.</Typography>
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
          <Button variant="outlined" onClick={() => setDeleteOpen(false)}
            sx={{ borderColor: '#e2e8f0', color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete} disabled={saving}
            startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <DeleteIcon />}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>

    </Box>
  );
}

// ─── Asset Form Dialog (shared by Add + Edit) ──────────────────────────────────
function AssetFormDialog({ open, onClose, title, form, onChange, onSubmit, saving, error, isEdit, assetStatus }) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
      <DialogTitle sx={{ fontWeight: 700, pb: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {title}
        <IconButton size="small" onClick={onClose}><CloseIcon sx={{ fontSize: 18 }} /></IconButton>
      </DialogTitle>
      <Divider />
      <DialogContent sx={{ pt: 2.5 }}>
        {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }}>{error}</Alert>}
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth label="Asset Name *" value={form.assetName} onChange={onChange('assetName')} size="small"
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField select fullWidth label="Asset Type *" value={form.assetType} onChange={onChange('assetType')} size="small"
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
              {ASSET_TYPES.map(t => <MenuItem key={t} value={t}>{t.replace(/_/g, ' ')}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth label="Brand" value={form.brand} onChange={onChange('brand')} size="small"
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth label="Model" value={form.model} onChange={onChange('model')} size="small"
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth label="Serial Number" value={form.serialNumber} onChange={onChange('serialNumber')} size="small"
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth label="Condition" select value={form.condition} onChange={onChange('condition')} size="small"
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
              {ASSET_CONDITIONS.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth type="date" label="Purchase Date" value={form.purchaseDate} onChange={onChange('purchaseDate')} size="small"
              InputLabelProps={{ shrink: true }} sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField fullWidth type="number" label="Purchase Price" value={form.purchasePrice} onChange={onChange('purchasePrice')} size="small"
              InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
          </Grid>
          {isEdit && assetStatus !== 'ASSIGNED' && (
            <Grid item xs={12} sm={6}>
              <TextField select fullWidth label="Status" value={form.status} onChange={onChange('status')} size="small"
                sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
                {ASSET_STATUSES.map(s => <MenuItem key={s} value={s}>{s.replace(/_/g, ' ')}</MenuItem>)}
              </TextField>
            </Grid>
          )}
          <Grid item xs={12}>
            <TextField fullWidth multiline rows={2} label="Notes" value={form.notes} onChange={onChange('notes')} size="small"
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
          </Grid>
        </Grid>
      </DialogContent>
      <Divider />
      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button variant="outlined" onClick={onClose} sx={{ borderColor: '#e2e8f0', color: '#64748b' }}>Cancel</Button>
        <Button variant="contained" onClick={onSubmit} disabled={saving}
          startIcon={saving ? <CircularProgress size={14} color="inherit" /> : (isEdit ? <EditIcon /> : <AddIcon />)}
          sx={{ bgcolor: '#14b8a6', '&:hover': { bgcolor: '#0d9488' } }}>
          {isEdit ? 'Save Changes' : 'Add Asset'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
