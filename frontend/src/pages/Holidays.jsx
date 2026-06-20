import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, Button, Card, Chip, IconButton, Tooltip,
  Table, TableBody, TableCell, TableHead, TableRow, TablePagination,
  Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, FormControl, InputLabel, Select, MenuItem,
  Grid, CircularProgress, Alert, ToggleButton, ToggleButtonGroup,
  Divider, InputAdornment,
} from '@mui/material';
import AddIcon            from '@mui/icons-material/Add';
import EditIcon           from '@mui/icons-material/Edit';
import DeleteIcon         from '@mui/icons-material/Delete';
import CalendarMonthIcon  from '@mui/icons-material/CalendarMonth';
import TableRowsIcon      from '@mui/icons-material/TableRows';
import UploadFileIcon     from '@mui/icons-material/UploadFile';
import DownloadIcon       from '@mui/icons-material/Download';
import SearchIcon         from '@mui/icons-material/Search';
import TuneIcon           from '@mui/icons-material/Tune';
import CelebrationIcon    from '@mui/icons-material/Celebration';
import LocationOnIcon     from '@mui/icons-material/LocationOn';
import EventIcon          from '@mui/icons-material/Event';
import TodayIcon          from '@mui/icons-material/Today';
import { toast } from 'react-toastify';
import { holidayApi } from '../api/holidayApi';
import { employeeApi } from '../api/employeeApi';

// ── constants ──────────────────────────────────────────────────────────────────
const HOLIDAY_TYPES = [
  { value: 'PUBLIC_HOLIDAY',      label: 'Public Holiday',      color: '#ef4444', bg: '#fef2f2' },
  { value: 'RESTRICTED_HOLIDAY',  label: 'Restricted Holiday',  color: '#f59e0b', bg: '#fffbeb' },
  { value: 'OPTIONAL_HOLIDAY',    label: 'Optional Holiday',    color: '#3b82f6', bg: '#eff6ff' },
];
const TYPE_MAP = Object.fromEntries(HOLIDAY_TYPES.map(t => [t.value, t]));

const MONTHS = ['January','February','March','April','May','June',
                'July','August','September','October','November','December'];
const DAYS   = ['Su','Mo','Tu','We','Th','Fr','Sa'];

const BLANK = {
  name: '', date: '', holidayType: 'PUBLIC_HOLIDAY',
  location: 'ALL', department: '', description: '', active: true,
};

const fmtDate = (d) => {
  if (!d) return '—';
  const dt = new Date(d);
  return dt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
};
const dayName = (d) => d ? new Date(d).toLocaleDateString('en-IN', { weekday: 'long' }) : '—';

// ── TypeChip ──────────────────────────────────────────────────────────────────
const TypeChip = ({ type }) => {
  const t = TYPE_MAP[type] || { label: type, color: '#64748b', bg: '#f1f5f9' };
  return (
    <Chip label={t.label} size="small"
      sx={{ bgcolor: t.bg, color: t.color, fontWeight: 600, fontSize: 11,
            border: `1px solid ${t.color}33` }} />
  );
};

// ── MiniCalendar ──────────────────────────────────────────────────────────────
const MiniCalendar = ({ year, month, holidays }) => {
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const today = new Date();
  const holidayMap = {};
  holidays.forEach(h => {
    const d = new Date(h.date);
    if (d.getFullYear() === year && d.getMonth() === month) {
      holidayMap[d.getDate()] = h;
    }
  });

  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  return (
    <Card sx={{ p: 2, height: '100%', border: '1px solid #e2e8f0', boxShadow: '0 1px 4px rgba(0,0,0,0.05)' }}>
      <Typography fontWeight={700} fontSize={13} color="#1e293b" mb={1.5}>
        {MONTHS[month]}
      </Typography>
      <Grid container columns={7} sx={{ mb: 0.5 }}>
        {DAYS.map(d => (
          <Grid item xs={1} key={d}>
            <Typography sx={{ fontSize: 10, fontWeight: 600, color: '#94a3b8', textAlign: 'center' }}>{d}</Typography>
          </Grid>
        ))}
      </Grid>
      <Grid container columns={7}>
        {cells.map((day, i) => {
          if (!day) return <Grid item xs={1} key={`e-${i}`} />;
          const h = holidayMap[day];
          const t = h ? TYPE_MAP[h.holidayType] : null;
          const isToday = today.getFullYear() === year && today.getMonth() === month && today.getDate() === day;
          const isSun = (firstDay + day - 1) % 7 === 0;
          const isSat = (firstDay + day - 1) % 7 === 6;
          return (
            <Grid item xs={1} key={day}>
              <Tooltip title={h ? `${h.name} (${TYPE_MAP[h.holidayType]?.label})` : ''} arrow placement="top">
                <Box sx={{
                  width: '100%', aspectRatio: '1',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  borderRadius: '50%', cursor: h ? 'pointer' : 'default',
                  bgcolor: h ? (t?.bg || '#f1f5f9') : isToday ? '#1e3a5f' : 'transparent',
                  border: isToday && !h ? '2px solid #1e3a5f' : 'none',
                }}>
                  <Typography sx={{
                    fontSize: 11, fontWeight: h ? 700 : isToday ? 700 : 400,
                    color: h ? (t?.color || '#374151') : isToday ? '#fff'
                         : (isSun || isSat) ? '#94a3b8' : '#374151',
                  }}>
                    {day}
                  </Typography>
                </Box>
              </Tooltip>
            </Grid>
          );
        })}
      </Grid>
      {Object.keys(holidayMap).length > 0 && (
        <Box sx={{ mt: 1.5, borderTop: '1px solid #f1f5f9', pt: 1 }}>
          {Object.entries(holidayMap).map(([, h]) => {
            const t = TYPE_MAP[h.holidayType];
            return (
              <Box key={h.id} sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5 }}>
                <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: t?.color || '#64748b', flexShrink: 0 }} />
                <Typography sx={{ fontSize: 10.5, color: '#374151', lineHeight: 1.3 }}>{h.name}</Typography>
              </Box>
            );
          })}
        </Box>
      )}
    </Card>
  );
};

// ── Main component ────────────────────────────────────────────────────────────
const HolidaysPage = () => {
  const { user } = useSelector(s => s.auth);
  const isAdminOrHR = user?.role === 'ADMIN' || user?.role === 'HR';

  const [holidays,   setHolidays]   = useState([]);
  const [years,      setYears]      = useState([]);
  const [locations,   setLocations]   = useState(['ALL']);
  const [departments, setDepartments] = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [view,       setView]       = useState('calendar');   // 'calendar' | 'table'
  const [year,       setYear]       = useState(new Date().getFullYear());
  const [filterLoc,  setFilterLoc]  = useState('ALL');
  const [filterType, setFilterType] = useState('');
  const [search,     setSearch]     = useState('');
  const [page,       setPage]       = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  // Dialog states
  const [formOpen,   setFormOpen]   = useState(false);
  const [editTarget, setEditTarget] = useState(null);
  const [form,       setForm]       = useState(BLANK);
  const [saving,     setSaving]     = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting,   setDeleting]   = useState(false);
  const [bulkOpen,   setBulkOpen]   = useState(false);
  const [deptMgrOpen, setDeptMgrOpen] = useState(false);
  const [newDeptName, setNewDeptName] = useState('');
  const [bulkText,   setBulkText]   = useState('');
  const [bulkParsed, setBulkParsed] = useState([]);
  const [bulkError,  setBulkError]  = useState('');
  const [bulkSaving, setBulkSaving] = useState(false);

  // ── load ──────────────────────────────────────────────────────────────────
  const load = useCallback(() => {
    setLoading(true);
    const req = isAdminOrHR ? holidayApi.getAll(year) : holidayApi.getMy(year);
    req.then(setHolidays).catch(() => toast.error('Failed to load holidays'))
       .finally(() => setLoading(false));
  }, [year, isAdminOrHR]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    holidayApi.getYears().then(setYears).catch(() => {});
    if (isAdminOrHR) {
      employeeApi.getLocations().then(locs => setLocations(['ALL', ...locs])).catch(() => {});
      setDepartments(['UK', 'US']);
    }
  }, [isAdminOrHR]);

  // ── derived ───────────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    let list = [...holidays];
    if (filterLoc && filterLoc !== 'ALL') list = list.filter(h => h.location === 'ALL' || h.location === filterLoc);
    if (filterType) list = list.filter(h => h.holidayType === filterType);
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(h => h.name.toLowerCase().includes(q) || (h.description || '').toLowerCase().includes(q));
    }
    return list;
  }, [holidays, filterLoc, filterType, search]);

  const stats = useMemo(() => {
    const now = new Date();
    const currentMonth = now.getMonth();
    const currentYear  = now.getFullYear();
    const active       = holidays.filter(h => h.active);
    const thisMonth    = active.filter(h => { const d = new Date(h.date); return d.getFullYear() === currentYear && d.getMonth() === currentMonth; });
    const upcoming     = active.filter(h => new Date(h.date) >= now).sort((a,b) => new Date(a.date)-new Date(b.date));
    const publicCount  = active.filter(h => h.holidayType === 'PUBLIC_HOLIDAY').length;
    return { total: active.length, thisMonth: thisMonth.length, publicCount, nextHoliday: upcoming[0] || null };
  }, [holidays]);

  // ── CRUD ──────────────────────────────────────────────────────────────────
  const openCreate = () => { setEditTarget(null); setForm({ ...BLANK }); setFormOpen(true); };
  const openEdit   = (h) => { setEditTarget(h); setForm({ name: h.name, date: String(h.date).slice(0,10), holidayType: h.holidayType, location: h.location, department: h.department || '', description: h.description || '', active: h.active }); setFormOpen(true); };

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Holiday name is required'); return; }
    if (!form.date)        { toast.error('Date is required'); return; }
    setSaving(true);
    try {
      if (editTarget) {
        const updated = await holidayApi.update(editTarget.id, form);
        setHolidays(prev => prev.map(h => h.id === editTarget.id ? updated : h));
        toast.success('Holiday updated');
      } else {
        const created = await holidayApi.create(form);
        setHolidays(prev => [...prev, created].sort((a,b) => new Date(a.date)-new Date(b.date)));
        toast.success('Holiday created');
      }
      setFormOpen(false);
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to save holiday');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await holidayApi.delete(deleteTarget.id);
      setHolidays(prev => prev.filter(h => h.id !== deleteTarget.id));
      toast.success('Holiday deleted');
      setDeleteTarget(null);
    } catch {
      toast.error('Failed to delete holiday');
    } finally {
      setDeleting(false);
    }
  };

  // ── Bulk upload ───────────────────────────────────────────────────────────
  const CSV_TEMPLATE = 'Name,Date (YYYY-MM-DD),Type (PUBLIC_HOLIDAY|RESTRICTED_HOLIDAY|OPTIONAL_HOLIDAY),Location (ALL or city),Description\n' +
    'New Year Day,2026-01-01,PUBLIC_HOLIDAY,ALL,New Year celebration\n' +
    'Republic Day,2026-01-26,PUBLIC_HOLIDAY,ALL,National Holiday\n';

  const downloadTemplate = () => {
    const blob = new Blob([CSV_TEMPLATE], { type: 'text/csv' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a'); a.href = url; a.download = 'holidays_template.csv'; a.click();
    URL.revokeObjectURL(url);
  };

  const parseBulkCSV = (text) => {
    setBulkError('');
    const lines = text.trim().split('\n').filter(Boolean);
    if (lines.length < 2) { setBulkError('No data rows found. First row must be the header.'); return; }
    const rows = lines.slice(1);
    const parsed = [];
    const errors = [];
    rows.forEach((line, i) => {
      const cols = line.split(',').map(c => c.trim().replace(/^"|"$/g, ''));
      const [name, date, holidayType, location, description] = cols;
      if (!name)   { errors.push(`Row ${i+2}: Name is required`); return; }
      if (!date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) { errors.push(`Row ${i+2}: Invalid date "${date}" — use YYYY-MM-DD`); return; }
      const validTypes = ['PUBLIC_HOLIDAY','RESTRICTED_HOLIDAY','OPTIONAL_HOLIDAY'];
      const type = (holidayType || 'PUBLIC_HOLIDAY').toUpperCase().replace(/ /g,'_');
      if (!validTypes.includes(type)) { errors.push(`Row ${i+2}: Invalid type "${holidayType}"`); return; }
      parsed.push({ name, date, holidayType: type, location: location || 'ALL', description: description || '', active: true });
    });
    if (errors.length) { setBulkError(errors.join('\n')); setBulkParsed([]); }
    else { setBulkParsed(parsed); }
  };

  const handleFileUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => { setBulkText(ev.target.result); parseBulkCSV(ev.target.result); };
    reader.readAsText(file);
  };

  const handleBulkSave = async () => {
    if (!bulkParsed.length) return;
    setBulkSaving(true);
    try {
      const created = await holidayApi.bulkCreate(bulkParsed);
      setHolidays(prev => [...prev, ...created].sort((a,b) => new Date(a.date)-new Date(b.date)));
      toast.success(`${created.length} holiday${created.length !== 1 ? 's' : ''} added`);
      setBulkOpen(false); setBulkText(''); setBulkParsed([]); setBulkError('');
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Bulk upload failed');
    } finally {
      setBulkSaving(false);
    }
  };

  // ── Stat card ─────────────────────────────────────────────────────────────
  const StatCard = ({ icon, label, value, sub, accent }) => (
    <Card sx={{ p: 2.5, display: 'flex', alignItems: 'center', gap: 2 }}>
      <Box sx={{ width: 44, height: 44, borderRadius: 2, bgcolor: accent + '18', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        {React.cloneElement(icon, { sx: { fontSize: 22, color: accent } })}
      </Box>
      <Box>
        <Typography sx={{ fontSize: 11, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 600 }}>{label}</Typography>
        <Typography sx={{ fontSize: 22, fontWeight: 800, color: '#0f172a', lineHeight: 1.2 }}>{value}</Typography>
        {sub && <Typography sx={{ fontSize: 11.5, color: '#64748b' }}>{sub}</Typography>}
      </Box>
    </Card>
  );

  return (
    <Box sx={{ p: { xs: 2, md: 3 }, bgcolor: '#f8fafc', minHeight: '100vh' }}>

      {/* ── Header ── */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h5" fontWeight={800} color="#0f172a">Holiday Calendar</Typography>
          <Typography sx={{ fontSize: 13.5, color: '#64748b', mt: 0.25 }}>
            {isAdminOrHR ? 'Manage yearly holidays across all locations' : 'View holidays applicable to your location'}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
          {/* Year selector */}
          <FormControl size="small" sx={{ minWidth: 110 }}>
            <InputLabel>Year</InputLabel>
            <Select value={year} label="Year" onChange={e => setYear(e.target.value)}>
              {years.map(y => <MenuItem key={y} value={y}>{y}</MenuItem>)}
            </Select>
          </FormControl>

          {/* View toggle */}
          <ToggleButtonGroup value={view} exclusive onChange={(_, v) => v && setView(v)} size="small"
            sx={{ bgcolor: 'white', border: '1px solid #e2e8f0', borderRadius: '8px' }}>
            <ToggleButton value="calendar" sx={{ px: 1.5, border: 'none' }}>
              <Tooltip title="Calendar View"><CalendarMonthIcon sx={{ fontSize: 18 }} /></Tooltip>
            </ToggleButton>
            <ToggleButton value="table" sx={{ px: 1.5, border: 'none' }}>
              <Tooltip title="Table View"><TableRowsIcon sx={{ fontSize: 18 }} /></Tooltip>
            </ToggleButton>
          </ToggleButtonGroup>

          {isAdminOrHR && (
            <>
              <Button variant="outlined" startIcon={<UploadFileIcon />}
                onClick={() => setBulkOpen(true)}
                sx={{ textTransform: 'none', borderRadius: '8px', fontSize: 13.5 }}>
                Bulk Upload
              </Button>
              <Button variant="contained" startIcon={<AddIcon />}
                onClick={openCreate}
                sx={{ textTransform: 'none', borderRadius: '8px', bgcolor: '#1e3a5f', fontSize: 13.5 }}>
                New Holiday
              </Button>
            </>
          )}
        </Box>
      </Box>

      {/* ── Stats ── */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard icon={<CelebrationIcon />} label="Total Holidays" value={stats.total} sub={`in ${year}`} accent="#6366f1" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard icon={<TodayIcon />} label="This Month" value={stats.thisMonth} sub={MONTHS[new Date().getMonth()]} accent="#14b8a6" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard icon={<EventIcon />} label="Public Holidays" value={stats.publicCount} sub="all locations" accent="#ef4444" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            icon={<LocationOnIcon />} label="Next Holiday" accent="#f59e0b"
            value={stats.nextHoliday ? fmtDate(stats.nextHoliday.date) : '—'}
            sub={stats.nextHoliday ? stats.nextHoliday.name : 'No upcoming holidays'}
          />
        </Grid>
      </Grid>

      {/* ── Legend + filters ── */}
      <Card sx={{ p: 2, mb: 2.5, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          {HOLIDAY_TYPES.map(t => (
            <Box key={t.value} sx={{ display: 'flex', alignItems: 'center', gap: 0.75, cursor: 'pointer' }}
              onClick={() => setFilterType(prev => prev === t.value ? '' : t.value)}>
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: t.color,
                         outline: filterType === t.value ? `2px solid ${t.color}` : 'none', outlineOffset: 2 }} />
              <Typography sx={{ fontSize: 12.5, color: filterType === t.value ? t.color : '#374151', fontWeight: filterType === t.value ? 700 : 400 }}>
                {t.label}
              </Typography>
            </Box>
          ))}
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
          {isAdminOrHR && (
            <FormControl size="small" sx={{ minWidth: 140 }}>
              <InputLabel>Location</InputLabel>
              <Select value={filterLoc} label="Location" onChange={e => setFilterLoc(e.target.value)}>
                {locations.map(l => <MenuItem key={l} value={l}>{l === 'ALL' ? 'All Locations' : l}</MenuItem>)}
              </Select>
            </FormControl>
          )}
          {view === 'table' && (
            <TextField size="small" placeholder="Search holidays…" value={search}
              onChange={e => setSearch(e.target.value)}
              InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon sx={{ fontSize: 17, color: '#94a3b8' }} /></InputAdornment> }}
              sx={{ minWidth: 200 }} />
          )}
        </Box>
      </Card>

      {/* ── Content ── */}
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}><CircularProgress /></Box>
      ) : view === 'calendar' ? (

        /* ── CALENDAR VIEW ── */
        <Grid container spacing={2}>
          {MONTHS.map((_, month) => (
            <Grid item xs={12} sm={6} md={4} lg={3} key={month}>
              <MiniCalendar year={year} month={month} holidays={filtered} />
            </Grid>
          ))}
        </Grid>

      ) : (

        /* ── TABLE VIEW ── */
        <Card>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                {['Holiday Name','Date','Day','Type','Location','Department','Description','Status', isAdminOrHR ? 'Actions' : null]
                  .filter(Boolean).map(h => (
                  <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12.5, color: '#374151', py: 1.5 }}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map(h => (
                <TableRow key={h.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                  <TableCell sx={{ fontWeight: 600, fontSize: 13, color: '#0f172a' }}>{h.name}</TableCell>
                  <TableCell sx={{ fontSize: 13, color: '#374151' }}>{fmtDate(h.date)}</TableCell>
                  <TableCell sx={{ fontSize: 13, color: '#64748b' }}>{dayName(h.date)}</TableCell>
                  <TableCell><TypeChip type={h.holidayType} /></TableCell>
                  <TableCell>
                    <Chip
                      label={!h.location || h.location === '' ? 'None' : h.location === 'ALL' ? 'All Locations' : h.location}
                      size="small"
                      sx={{ fontSize: 11, bgcolor: '#f1f5f9', color: '#475569' }} />
                  </TableCell>
                  <TableCell>
                    <Chip label={h.department || 'All Departments'} size="small"
                      sx={{ fontSize: 11, bgcolor: h.department ? '#eff6ff' : '#f1f5f9', color: h.department ? '#2563eb' : '#475569' }} />
                  </TableCell>
                  <TableCell sx={{ fontSize: 12.5, color: '#64748b', maxWidth: 200 }}>
                    <Typography noWrap sx={{ fontSize: 12.5, color: '#64748b', maxWidth: 180 }}>
                      {h.description || '—'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip label={h.active ? 'Active' : 'Inactive'} size="small"
                      sx={{ bgcolor: h.active ? '#dcfce7' : '#f1f5f9', color: h.active ? '#16a34a' : '#64748b', fontWeight: 600, fontSize: 11 }} />
                  </TableCell>
                  {isAdminOrHR && (
                    <TableCell>
                      <Box sx={{ display: 'flex', gap: 0.5 }}>
                        <Tooltip title="Edit"><IconButton size="small" onClick={() => openEdit(h)} sx={{ color: '#3b82f6' }}><EditIcon sx={{ fontSize: 16 }} /></IconButton></Tooltip>
                        <Tooltip title="Delete"><IconButton size="small" onClick={() => setDeleteTarget(h)} sx={{ color: '#ef4444' }}><DeleteIcon sx={{ fontSize: 16 }} /></IconButton></Tooltip>
                      </Box>
                    </TableCell>
                  )}
                </TableRow>
              ))}
              {filtered.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} sx={{ textAlign: 'center', py: 6, color: '#94a3b8' }}>
                    No holidays found for the selected filters
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
          <TablePagination
            component="div" count={filtered.length} page={page} rowsPerPage={rowsPerPage}
            onPageChange={(_, p) => setPage(p)}
            onRowsPerPageChange={e => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
            rowsPerPageOptions={[10, 25, 50]}
            sx={{ borderTop: '1px solid #f1f5f9' }}
          />
        </Card>
      )}

      {/* ── Create / Edit Dialog ── */}
      <Dialog open={formOpen} onClose={() => !saving && setFormOpen(false)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16, pb: 0 }}>
          {editTarget ? 'Edit Holiday' : 'New Holiday'}
        </DialogTitle>
        <Divider sx={{ mt: 1.5 }} />
        <DialogContent sx={{ pt: 2.5 }}>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Holiday Name *" value={form.name}
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField fullWidth size="small" label="Date *" type="date"
                InputLabelProps={{ shrink: true }} value={form.date}
                onChange={e => setForm(f => ({ ...f, date: e.target.value }))} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl size="small" fullWidth>
                <InputLabel>Holiday Type *</InputLabel>
                <Select value={form.holidayType} label="Holiday Type *"
                  onChange={e => setForm(f => ({ ...f, holidayType: e.target.value }))}>
                  {HOLIDAY_TYPES.map(t => (
                    <MenuItem key={t.value} value={t.value}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: t.color }} />
                        {t.label}
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6}>
              <FormControl size="small" fullWidth>
                <InputLabel>Location</InputLabel>
                <Select value={form.location} label="Location"
                  onChange={e => setForm(f => ({ ...f, location: e.target.value }))}>
                  <MenuItem value=""><em>None</em></MenuItem>
                  {locations.map(l => (
                    <MenuItem key={l} value={l}>{l === 'ALL' ? 'All Locations' : l}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6}>
              <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'flex-start' }}>
                <FormControl size="small" fullWidth>
                  <InputLabel>Department (optional)</InputLabel>
                  <Select value={form.department || ''} label="Department (optional)"
                    onChange={e => setForm(f => ({ ...f, department: e.target.value }))}>
                    <MenuItem value=""><em>All Departments</em></MenuItem>
                    {departments.map(d => <MenuItem key={d} value={d}>{d}</MenuItem>)}
                  </Select>
                </FormControl>
                <Tooltip title="Manage Departments">
                  <IconButton size="small" onClick={() => { setNewDeptName(''); setDeptMgrOpen(true); }}
                    sx={{ mt: 0.5, color: 'text.secondary' }}>
                    <TuneIcon sx={{ fontSize: 18 }} />
                  </IconButton>
                </Tooltip>
              </Box>
            </Grid>
            <Grid item xs={12}>
              <TextField fullWidth size="small" label="Description" multiline rows={2}
                value={form.description}
                onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={() => setFormOpen(false)} disabled={saving}
            sx={{ textTransform: 'none', borderRadius: '8px' }}>Cancel</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}
            sx={{ textTransform: 'none', borderRadius: '8px', bgcolor: '#1e3a5f', minWidth: 100 }}>
            {saving ? <CircularProgress size={18} color="inherit" /> : editTarget ? 'Save Changes' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Delete Confirm ── */}
      <Dialog open={!!deleteTarget} onClose={() => !deleting && setDeleteTarget(null)} maxWidth="xs" fullWidth
        PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16 }}>Delete Holiday</DialogTitle>
        <DialogContent>
          <Typography sx={{ fontSize: 14, color: '#374151' }}>
            Are you sure you want to delete <strong>{deleteTarget?.name}</strong>? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={() => setDeleteTarget(null)} disabled={deleting}
            sx={{ textTransform: 'none', borderRadius: '8px' }}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete} disabled={deleting}
            sx={{ textTransform: 'none', borderRadius: '8px', minWidth: 90 }}>
            {deleting ? <CircularProgress size={18} color="inherit" /> : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Manage Departments Dialog ── */}
      <Dialog open={deptMgrOpen} onClose={() => setDeptMgrOpen(false)} maxWidth="xs" fullWidth
        PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16, pb: 0 }}>Manage Departments</DialogTitle>
        <Divider sx={{ mt: 1.5 }} />
        <DialogContent sx={{ pt: 2 }}>
          <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
            <TextField size="small" fullWidth placeholder="New department name…"
              value={newDeptName} onChange={e => setNewDeptName(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter') {
                  const v = newDeptName.trim();
                  if (v && !departments.includes(v)) { setDepartments(prev => [...prev, v].sort()); setNewDeptName(''); }
                }
              }} />
            <Button variant="contained" size="small"
              sx={{ bgcolor: '#1e3a5f', textTransform: 'none', borderRadius: '8px', whiteSpace: 'nowrap' }}
              onClick={() => {
                const v = newDeptName.trim();
                if (v && !departments.includes(v)) { setDepartments(prev => [...prev, v].sort()); setNewDeptName(''); }
              }}>
              Add
            </Button>
          </Box>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            {departments.map(d => (
              <Chip key={d} label={d} size="small"
                onDelete={() => {
                  setDepartments(prev => prev.filter(x => x !== d));
                  if (form.department === d) setForm(f => ({ ...f, department: '' }));
                }}
                sx={{ bgcolor: '#eff6ff', color: '#2563eb', fontWeight: 600, fontSize: 12 }} />
            ))}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDeptMgrOpen(false)}
            sx={{ textTransform: 'none', borderRadius: '8px' }}>Done</Button>
        </DialogActions>
      </Dialog>

      {/* ── Bulk Upload Dialog ── */}
      <Dialog open={bulkOpen} onClose={() => !bulkSaving && setBulkOpen(false)} maxWidth="md" fullWidth
        PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16, pb: 0 }}>Bulk Upload Holidays</DialogTitle>
        <Divider sx={{ mt: 1.5 }} />
        <DialogContent sx={{ pt: 2.5 }}>
          <Alert severity="info" sx={{ mb: 2, borderRadius: '10px', fontSize: 13 }}>
            Upload a CSV file with columns: <strong>Name, Date (YYYY-MM-DD), Type, Location, Description</strong>.
            Type must be one of: PUBLIC_HOLIDAY, RESTRICTED_HOLIDAY, OPTIONAL_HOLIDAY.
            Use "ALL" for Location to apply to all locations.
          </Alert>
          <Box sx={{ display: 'flex', gap: 2, mb: 2.5, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button variant="outlined" startIcon={<DownloadIcon />} onClick={downloadTemplate}
              sx={{ textTransform: 'none', borderRadius: '8px', fontSize: 13 }}>
              Download Template
            </Button>
            <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}
              sx={{ textTransform: 'none', borderRadius: '8px', fontSize: 13 }}>
              Upload CSV File
              <input type="file" accept=".csv,.txt" hidden onChange={handleFileUpload} />
            </Button>
          </Box>

          <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#374151', mb: 1 }}>Or paste CSV content:</Typography>
          <TextField fullWidth multiline rows={6} size="small"
            placeholder={CSV_TEMPLATE}
            value={bulkText}
            onChange={e => { setBulkText(e.target.value); parseBulkCSV(e.target.value); }}
            sx={{ fontFamily: 'monospace', '& textarea': { fontSize: 12 } }} />

          {bulkError && (
            <Alert severity="error" sx={{ mt: 1.5, borderRadius: '10px', fontSize: 12, whiteSpace: 'pre-line' }}>
              {bulkError}
            </Alert>
          )}
          {bulkParsed.length > 0 && !bulkError && (
            <Alert severity="success" sx={{ mt: 1.5, borderRadius: '10px', fontSize: 13 }}>
              <strong>{bulkParsed.length} holiday{bulkParsed.length !== 1 ? 's' : ''}</strong> ready to import:
              <Box component="ul" sx={{ mt: 0.5, mb: 0, pl: 2 }}>
                {bulkParsed.slice(0, 5).map((h, i) => (
                  <li key={i}><Typography sx={{ fontSize: 12 }}>{h.name} — {h.date} ({h.holidayType.replace(/_/g,' ')})</Typography></li>
                ))}
                {bulkParsed.length > 5 && <li><Typography sx={{ fontSize: 12 }}>…and {bulkParsed.length - 5} more</Typography></li>}
              </Box>
            </Alert>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={() => { setBulkOpen(false); setBulkText(''); setBulkParsed([]); setBulkError(''); }}
            disabled={bulkSaving} sx={{ textTransform: 'none', borderRadius: '8px' }}>Cancel</Button>
          <Button variant="contained" onClick={handleBulkSave}
            disabled={bulkSaving || !bulkParsed.length || !!bulkError}
            sx={{ textTransform: 'none', borderRadius: '8px', bgcolor: '#1e3a5f', minWidth: 120 }}>
            {bulkSaving ? <CircularProgress size={18} color="inherit" /> : `Import ${bulkParsed.length || ''} Holidays`}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default HolidaysPage;
