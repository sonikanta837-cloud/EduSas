import React, { useEffect, useState, useMemo } from 'react';
import { useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import {
  Box, Button, Card, CardContent, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography, TextField,
  InputAdornment, Chip, Avatar, IconButton, Dialog, DialogTitle,
  DialogContent, DialogActions, MenuItem, Select, FormControl,
  InputLabel, CircularProgress, Tooltip, Stack, Tabs, Tab,
  Divider, List, ListItem, ListItemText,
} from '@mui/material';
import SearchIcon       from '@mui/icons-material/Search';
import AddIcon          from '@mui/icons-material/Add';
import EditIcon         from '@mui/icons-material/Edit';
import VisibilityIcon   from '@mui/icons-material/Visibility';
import BlockIcon        from '@mui/icons-material/Block';
import UploadFileIcon   from '@mui/icons-material/UploadFile';
import DeleteIcon       from '@mui/icons-material/Delete';
import TuneIcon         from '@mui/icons-material/Tune';
import CheckIcon        from '@mui/icons-material/Check';
import CloseIcon        from '@mui/icons-material/Close';
import { employeeApi } from '../api/employeeApi';
import { resumeApi }   from '../api/resumeApi';
import { toast }       from 'react-toastify';

const roleColors = { ADMIN: 'error', MANAGER: 'warning', EMPLOYEE: 'primary' };

const INITIAL_DEPARTMENTS = [
  'Human Resource', 'Operation', 'Management', 'Marketing', 'IT',
];
const INITIAL_POSITIONS = [
  'Accounts Trainee', 'Accounts Executive', 'Senior Accountant',
  'Sr. Payroll Administrator', 'Manager', 'Business Development and Operation',
  'HR', 'System Administrator',
];
const INITIAL_LOCATIONS = ['Mandsaur', 'Ahmedabad', 'Jamnagar'];
const EMPLOYMENT_TYPES  = ['Full-time', 'Part-time', 'Contract', 'Intern', 'Consultant'];
const SOURCE_OF_HIRE    = ['LinkedIn', 'Referral', 'Job Portal', 'Walk-in', 'Campus', 'Other'];
const GENDERS           = ['Male', 'Female', 'Other', 'Prefer not to say'];
const MARITAL_STATUSES  = ['Single', 'Married', 'Divorced', 'Widowed'];

const EMPTY_FORM = {
  email: '', password: 'Temp@1234', firstName: '', lastName: '',
  employeeCode: '', phone: '', personalEmail: '',
  department: '', position: '', role: 'EMPLOYEE', managerId: '',
  employmentType: '', sourceOfHire: '', hireDate: '', dateOfExit: '',
  dateOfBirth: '', gender: '', maritalStatus: '',
  aadharNumber: '', panNumber: '', uanNumber: '',
  address: '', presentAddress: '', permanentAddress: '',
  seatingLocation: '', currentExperience: '', totalExperience: '',
  skills: '', experience: '', photoUrl: '',
};

const fmtDate = (d) => (d ? String(d).slice(0, 10) : '');
const fmtDateTime = (dt) => {
  if (!dt) return '—';
  return new Date(dt).toLocaleString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
};

// Table cell style helpers
const hdrCell     = { fontWeight: 700, fontSize: 12, whiteSpace: 'nowrap', py: 1.2, px: 1.5, color: '#374151' };
const cell        = { fontSize: 12.5, whiteSpace: 'nowrap', py: 1, px: 1.5, color: '#1e293b' };
const EMPID_W     = 90;
const EMPNAME_W   = 200;
const LOC_W       = 120;
const stickyId    = { position: 'sticky', left: 0,                        zIndex: 3, minWidth: EMPID_W,   maxWidth: EMPID_W   };
const stickyName  = { position: 'sticky', left: EMPID_W,                  zIndex: 3, minWidth: EMPNAME_W, maxWidth: EMPNAME_W };
const stickyLoc   = { position: 'sticky', left: EMPID_W + EMPNAME_W,      zIndex: 3, minWidth: LOC_W,     maxWidth: LOC_W     };
const stickyRight = { position: 'sticky', right: 0,                       zIndex: 3, minWidth: 110 };

const ROField = ({ label, value }) => (
  <Box>
    <Typography variant="caption" color="text.secondary">{label}</Typography>
    <Typography variant="body2" fontWeight={500}>{value || '—'}</Typography>
  </Box>
);

// ── ManageListDialog ──────────────────────────────────────────────────────────
const ManageListDialog = ({ open, onClose, title, items, onAdd, onEdit, onDelete }) => {
  const [newVal,  setNewVal]  = useState('');
  const [editIdx, setEditIdx] = useState(null);
  const [editVal, setEditVal] = useState('');

  const commitAdd = () => {
    const v = newVal.trim();
    if (!v) return;
    if (items.includes(v)) { toast.warning(`"${v}" already exists`); return; }
    onAdd(v);
    setNewVal('');
  };

  const startEdit = (i) => { setEditIdx(i); setEditVal(items[i]); };

  const commitEdit = () => {
    const v = editVal.trim();
    if (!v) return;
    if (items.includes(v) && items[editIdx] !== v) { toast.warning(`"${v}" already exists`); return; }
    onEdit(editIdx, v);
    setEditIdx(null);
  };

  const cancelEdit = () => setEditIdx(null);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth
      PaperProps={{ sx: { maxHeight: '80vh' } }}>
      <DialogTitle fontWeight={700} sx={{ pb: 1 }}>Manage {title}</DialogTitle>
      <Divider />
      <DialogContent sx={{ pt: 2 }}>
        {/* Add new row */}
        <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
          <TextField
            size="small" fullWidth
            placeholder={`New ${title}…`}
            value={newVal}
            onChange={(e) => setNewVal(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && commitAdd()}
          />
          <Button variant="contained" size="small" startIcon={<AddIcon />}
            onClick={commitAdd} sx={{ whiteSpace: 'nowrap' }}>
            Add
          </Button>
        </Box>

        {/* Existing items */}
        {items.length === 0 ? (
          <Typography variant="body2" color="text.secondary" textAlign="center" py={2}>
            No items yet — add one above
          </Typography>
        ) : (
          <List dense disablePadding sx={{ maxHeight: 340, overflowY: 'auto' }}>
            {items.map((item, i) => (
              <ListItem key={i} disablePadding divider
                sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc', px: 1, py: 0.5 }}>
                {editIdx === i ? (
                  <Box sx={{ display: 'flex', gap: 0.5, flex: 1, alignItems: 'center', py: 0.5 }}>
                    <TextField
                      size="small" fullWidth autoFocus
                      value={editVal}
                      onChange={(e) => setEditVal(e.target.value)}
                      onKeyDown={(e) => { if (e.key === 'Enter') commitEdit(); if (e.key === 'Escape') cancelEdit(); }}
                    />
                    <Tooltip title="Save">
                      <IconButton size="small" color="primary" onClick={commitEdit}>
                        <CheckIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Cancel">
                      <IconButton size="small" onClick={cancelEdit}>
                        <CloseIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Box>
                ) : (
                  <>
                    <ListItemText
                      primary={item}
                      primaryTypographyProps={{ fontSize: 13.5, fontWeight: 500 }}
                      sx={{ flex: 1 }}
                    />
                    <Tooltip title="Edit">
                      <IconButton size="small" onClick={() => startEdit(i)}>
                        <EditIcon sx={{ fontSize: 15 }} />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => onDelete(i)}>
                        <DeleteIcon sx={{ fontSize: 15 }} />
                      </IconButton>
                    </Tooltip>
                  </>
                )}
              </ListItem>
            ))}
          </List>
        )}
      </DialogContent>
      <Divider />
      <DialogActions sx={{ px: 2, py: 1.5 }}>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

// ── ManagedSelect — Select dropdown with a manage (⚙) button ─────────────────
const ManagedSelect = ({ label, fieldKey, value, options, setter, onManage }) => (
  <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'flex-start' }}>
    <FormControl size="small" fullWidth>
      <InputLabel>{label}</InputLabel>
      <Select
        value={value || ''}
        label={label}
        onChange={(e) => setter((f) => ({ ...f, [fieldKey]: e.target.value }))}
      >
        <MenuItem value=""><em>None</em></MenuItem>
        {options.map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
      </Select>
    </FormControl>
    <Tooltip title={`Manage ${label} list`}>
      <IconButton size="small" onClick={onManage}
        sx={{ mt: 0.5, color: 'text.secondary', '&:hover': { color: 'primary.main' } }}>
        <TuneIcon sx={{ fontSize: 18 }} />
      </IconButton>
    </Tooltip>
  </Box>
);

// ── EmployeeForm ──────────────────────────────────────────────────────────────
const EmployeeForm = ({ values, setter, isEdit, employees,
                        departments, positions, locations, onManage }) => {
  const [tab, setTab] = useState(0);

  const field = (key, label, opts = {}) => (
    <TextField
      label={label}
      value={values[key] ?? ''}
      size="small"
      onChange={(e) => setter((f) => ({ ...f, [key]: e.target.value }))}
      {...opts}
    />
  );

  const dateField = (key, label, opts = {}) => (
    <TextField
      label={label}
      type="date"
      value={fmtDate(values[key])}
      size="small"
      InputLabelProps={{ shrink: true }}
      onChange={(e) => setter((f) => ({ ...f, [key]: e.target.value || null }))}
      {...opts}
    />
  );

  const selectField = (key, label, options) => (
    <FormControl size="small" fullWidth>
      <InputLabel>{label}</InputLabel>
      <Select value={values[key] || ''} label={label}
        onChange={(e) => setter((f) => ({ ...f, [key]: e.target.value }))}>
        <MenuItem value=""><em>None</em></MenuItem>
        {options.map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
      </Select>
    </FormControl>
  );

  const grid2 = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mt: 1 };

  return (
    <Box>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto"
        sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
        <Tab label="Basic Info" />
        <Tab label="Employment" />
        <Tab label="Work Details" />
        <Tab label="Identity" />
        <Tab label="Address" />
        {isEdit && <Tab label="System" />}
      </Tabs>

      {/* ── Tab 0: Basic Info ── */}
      {tab === 0 && (
        <Box sx={grid2}>
          {field('employeeCode', 'Employee ID / Code', { sx: { gridColumn: 'span 2' } })}
          {field('firstName', 'First Name *')}
          {field('lastName', 'Last Name *')}
          {field('email', 'Work Email *', { sx: { gridColumn: 'span 2' } })}
          {!isEdit && field('password', 'Password *', { type: 'password' })}
          {field('phone', 'Phone')}
          {field('personalEmail', 'Personal Email')}
          {dateField('dateOfBirth', 'Date of Birth')}
          {isEdit && values.dateOfBirth && (
            <TextField label="Age" size="small" value={
              Math.floor((new Date() - new Date(values.dateOfBirth)) / (365.25 * 24 * 3600 * 1000))
            } InputProps={{ readOnly: true }} />
          )}
          {selectField('gender', 'Gender', GENDERS)}
          {selectField('maritalStatus', 'Marital Status', MARITAL_STATUSES)}
        </Box>
      )}

      {/* ── Tab 1: Employment ── */}
      {tab === 1 && (
        <Box sx={grid2}>
          {/* Department — Select + manage button */}
          <ManagedSelect
            label="Department" fieldKey="department"
            value={values.department} options={departments}
            setter={setter} onManage={() => onManage('dept')}
          />

          {/* Position — Select + manage button */}
          <ManagedSelect
            label="Position" fieldKey="position"
            value={values.position} options={positions}
            setter={setter} onManage={() => onManage('pos')}
          />

          {selectField('employmentType', 'Employment Type', EMPLOYMENT_TYPES)}
          {selectField('sourceOfHire',   'Source of Hire',  SOURCE_OF_HIRE)}
          {dateField('hireDate',  'Date of Joining')}
          {dateField('dateOfExit','Date of Exit')}

          <FormControl size="small" fullWidth>
            <InputLabel>Role</InputLabel>
            <Select value={values.role || 'EMPLOYEE'} label="Role"
              onChange={(e) => setter((f) => ({ ...f, role: e.target.value }))}>
              <MenuItem value="EMPLOYEE">Employee</MenuItem>
              <MenuItem value="MANAGER">Manager</MenuItem>
              <MenuItem value="ADMIN">Admin</MenuItem>
            </Select>
          </FormControl>

          <FormControl size="small" fullWidth>
            <InputLabel>Manager (optional)</InputLabel>
            <Select value={values.managerId || ''} label="Manager (optional)"
              onChange={(e) => setter((f) => ({ ...f, managerId: e.target.value }))}>
              <MenuItem value="">None</MenuItem>
              {employees
                .filter((e) => e.role === 'MANAGER' || e.role === 'ADMIN')
                .map((e) => (
                  <MenuItem key={e.id} value={e.id}>
                    {e.fullName} ({e.role})
                  </MenuItem>
                ))}
            </Select>
          </FormControl>
        </Box>
      )}

      {/* ── Tab 2: Work Details ── */}
      {tab === 2 && (
        <Box sx={grid2}>
          {field('currentExperience', 'Current Experience (e.g. 2 years)')}
          {field('totalExperience',   'Total Experience (e.g. 5 years)')}

          {/* Seating Location — Select + manage button */}
          <Box sx={{ gridColumn: 'span 2' }}>
            <ManagedSelect
              label="Seating Location" fieldKey="seatingLocation"
              value={values.seatingLocation} options={locations}
              setter={setter} onManage={() => onManage('loc')}
            />
          </Box>

          {field('skills',     'Skills',              { multiline: true, rows: 3, sx: { gridColumn: 'span 2' } })}
          {field('experience', 'Experience Summary',  { multiline: true, rows: 3, sx: { gridColumn: 'span 2' } })}
        </Box>
      )}

      {/* ── Tab 3: Identity ── */}
      {tab === 3 && (
        <Box sx={grid2}>
          {field('aadharNumber', 'Aadhar Number', { sx: { gridColumn: 'span 2' } })}
          {field('panNumber', 'PAN Number')}
          {field('uanNumber', 'UAN Number')}
        </Box>
      )}

      {/* ── Tab 4: Address ── */}
      {tab === 4 && (
        <Box sx={grid2}>
          {field('presentAddress',   'Present Address',   { multiline: true, rows: 3, sx: { gridColumn: 'span 2' } })}
          {field('permanentAddress', 'Permanent Address', { multiline: true, rows: 3, sx: { gridColumn: 'span 2' } })}
        </Box>
      )}

      {/* ── Tab 5: System (edit only, read-only) ── */}
      {isEdit && tab === 5 && (
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mt: 1 }}>
          <ROField label="Added By"      value={values.addedBy} />
          <ROField label="Modified By"   value={values.modifiedBy} />
          <ROField label="Added Time"    value={fmtDateTime(values.createdAt)} />
          <ROField label="Modified Time" value={fmtDateTime(values.updatedAt)} />
        </Box>
      )}
    </Box>
  );
};

// ── Page ──────────────────────────────────────────────────────────────────────
const EmployeesPage = () => {
  const { user } = useSelector((s) => s.auth);
  const navigate = useNavigate();

  const [employees,   setEmployees]   = useState([]);
  const [loading,     setLoading]     = useState(true);
  const [search,      setSearch]      = useState('');
  const [roleFilter,  setRoleFilter]  = useState('ALL');
  const [addOpen,     setAddOpen]     = useState(false);
  const [form,        setForm]        = useState(EMPTY_FORM);
  const [editTarget,  setEditTarget]  = useState(null);
  const [editForm,    setEditForm]    = useState(EMPTY_FORM);
  const [toggleTarget,  setToggleTarget]  = useState(null);
  const [resumeParsing, setResumeParsing] = useState(false);
  const [saving,        setSaving]        = useState(false);

  const [departments, setDepartments] = useState(INITIAL_DEPARTMENTS);
  const [positions,   setPositions]   = useState(INITIAL_POSITIONS);
  const [locations,   setLocations]   = useState(INITIAL_LOCATIONS);

  // Which list is currently open in ManageListDialog: null | 'dept' | 'pos' | 'loc'
  const [manageOpen, setManageOpen] = useState(null);

  const MANAGE_CONFIG = {
    dept: { label: 'Departments', items: departments, setItems: setDepartments },
    pos:  { label: 'Positions',   items: positions,   setItems: setPositions   },
    loc:  { label: 'Locations',   items: locations,   setItems: setLocations   },
  };

  const fetchEmployees = () => {
    setLoading(true);
    employeeApi.getAll()
      .then(setEmployees)
      .catch(() => toast.error('Failed to load employees'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchEmployees(); }, []);

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return employees.filter((emp) => {
      const matchSearch = !q || [
        emp.firstName, emp.lastName, emp.fullName,
        emp.email, emp.department, emp.position, emp.employeeCode,
        emp.employmentType,
      ].some((v) => v?.toLowerCase().includes(q));
      const matchRole = roleFilter === 'ALL' || emp.role === roleFilter;
      return matchSearch && matchRole;
    });
  }, [employees, search, roleFilter]);

  // ─── Manage list handlers ─────────────────────────────────────────────────

  const handleManage = (key) => setManageOpen(key);

  const getManageConfig = () => manageOpen ? MANAGE_CONFIG[manageOpen] : null;

  const handleManageAdd = (val) => {
    const cfg = getManageConfig();
    if (!cfg) return;
    cfg.setItems((prev) => [...prev, val]);
  };

  const handleManageEdit = (idx, val) => {
    const cfg = getManageConfig();
    if (!cfg) return;
    cfg.setItems((prev) => prev.map((item, i) => (i === idx ? val : item)));
  };

  const handleManageDelete = (idx) => {
    const cfg = getManageConfig();
    if (!cfg) return;
    cfg.setItems((prev) => prev.filter((_, i) => i !== idx));
  };

  // ─── Add ─────────────────────────────────────────────────────────────────

  const handleAdd = async () => {
    setSaving(true);
    try {
      await employeeApi.create({ ...form, managerId: form.managerId || null });
      toast.success('Employee added successfully');
      setAddOpen(false);
      setForm(EMPTY_FORM);
      fetchEmployees();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to add employee');
    } finally {
      setSaving(false);
    }
  };

  // ─── Edit ─────────────────────────────────────────────────────────────────

  const openEdit = (emp) => {
    setEditTarget(emp);
    setEditForm({
      email:             emp.email             || '',
      password:          '',
      firstName:         emp.firstName         || '',
      lastName:          emp.lastName          || '',
      employeeCode:      emp.employeeCode      || '',
      phone:             emp.phone             || '',
      personalEmail:     emp.personalEmail     || '',
      department:        emp.department        || '',
      position:          emp.position          || '',
      role:              emp.role              || 'EMPLOYEE',
      managerId:         emp.managerId         || '',
      employmentType:    emp.employmentType    || '',
      sourceOfHire:      emp.sourceOfHire      || '',
      hireDate:          fmtDate(emp.hireDate),
      dateOfExit:        fmtDate(emp.dateOfExit),
      dateOfBirth:       fmtDate(emp.dateOfBirth),
      gender:            emp.gender            || '',
      maritalStatus:     emp.maritalStatus     || '',
      aadharNumber:      emp.aadharNumber      || '',
      panNumber:         emp.panNumber         || '',
      uanNumber:         emp.uanNumber         || '',
      address:           emp.address           || '',
      presentAddress:    emp.presentAddress    || '',
      permanentAddress:  emp.permanentAddress  || '',
      seatingLocation:   emp.seatingLocation   || '',
      currentExperience: emp.currentExperience || '',
      totalExperience:   emp.totalExperience   || '',
      skills:            emp.skills            || '',
      experience:        emp.experience        || '',
      photoUrl:          emp.photoUrl          || '',
      addedBy:           emp.addedBy,
      modifiedBy:        emp.modifiedBy,
      createdAt:         emp.createdAt,
      updatedAt:         emp.updatedAt,
    });
  };

  const handleEdit = async () => {
    setSaving(true);
    try {
      const payload = { ...editForm, managerId: editForm.managerId || null };
      if (!payload.password) delete payload.password;
      await employeeApi.update(editTarget.id, payload);
      toast.success('Employee updated successfully');
      setEditTarget(null);
      fetchEmployees();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update employee');
    } finally {
      setSaving(false);
    }
  };

  // ─── Toggle status ────────────────────────────────────────────────────────

  const handleConfirmToggle = async () => {
    try {
      await employeeApi.toggleStatus(toggleTarget.id);
      toast.success(`Employee ${toggleTarget.active ? 'deactivated' : 'activated'}`);
      setToggleTarget(null);
      fetchEmployees();
    } catch {
      toast.error('Failed to update status');
    }
  };

  // ─── Resume upload ────────────────────────────────────────────────────────

  const handleResumeUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';
    setResumeParsing(true);
    try {
      const parsed = await resumeApi.parse(file);
      setForm({
        ...EMPTY_FORM,
        firstName:  parsed.firstName  || '',
        lastName:   parsed.lastName   || '',
        email:      parsed.email      || '',
        phone:      parsed.phone      || '',
        skills:     parsed.skills     || '',
        experience: parsed.experience || '',
      });
      setAddOpen(true);
      toast.success('Resume parsed — please review and complete the form');
    } catch {
      toast.error('Failed to parse resume');
    } finally {
      setResumeParsing(false);
    }
  };

  // ─── Render ───────────────────────────────────────────────────────────────

  const manageConfig = getManageConfig();

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" fontWeight={700}>Employees</Typography>
        {user?.role === 'ADMIN' && (
          <Stack direction="row" spacing={1.5}>
            <input
              id="resume-upload-input"
              type="file" accept=".pdf,.docx"
              style={{ display: 'none' }}
              onChange={handleResumeUpload}
            />
            <Button
              variant="outlined"
              startIcon={resumeParsing ? <CircularProgress size={16} /> : <UploadFileIcon />}
              disabled={resumeParsing}
              onClick={() => document.getElementById('resume-upload-input').click()}
            >
              {resumeParsing ? 'Reading Resume…' : 'Upload Resume'}
            </Button>
            <Button variant="contained" startIcon={<AddIcon />}
              onClick={() => { setForm(EMPTY_FORM); setAddOpen(true); }}>
              Add Employee
            </Button>
          </Stack>
        )}
      </Box>

      {/* Search + filter bar */}
      <Card sx={{ mb: 2 }}>
        <CardContent sx={{ py: 2 }}>
          <Stack direction="row" spacing={2} alignItems="center">
            <TextField
              placeholder="Search by name, code, department..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              size="small" sx={{ width: 340 }}
              InputProps={{ startAdornment: (
                <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment>
              )}}
            />
            <FormControl size="small" sx={{ minWidth: 150 }}>
              <InputLabel>Role</InputLabel>
              <Select value={roleFilter} label="Role"
                onChange={(e) => setRoleFilter(e.target.value)}>
                <MenuItem value="ALL">All Roles</MenuItem>
                <MenuItem value="ADMIN">Admin</MenuItem>
                <MenuItem value="MANAGER">Manager</MenuItem>
                <MenuItem value="EMPLOYEE">Employee</MenuItem>
              </Select>
            </FormControl>
            {(search || roleFilter !== 'ALL') && (
              <Button size="small" onClick={() => { setSearch(''); setRoleFilter('ALL'); }}>Clear</Button>
            )}
            <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto !important' }}>
              {filtered.length} of {employees.length} employees
            </Typography>
          </Stack>
        </CardContent>
      </Card>

      {/* Table */}
      <Card>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table size="small" sx={{ minWidth: 2300 }}>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                {/* ── two sticky columns ── */}
                <TableCell sx={{ ...stickyId,   ...hdrCell, bgcolor: '#e2e8f0', borderRight: '1px solid #cbd5e1' }}>Emp ID</TableCell>
                <TableCell sx={{ ...stickyName, ...hdrCell, bgcolor: '#e2e8f0', borderRight: '1px solid #cbd5e1' }}>Employee Name</TableCell>
                <TableCell sx={hdrCell}>Phone</TableCell>
                <TableCell sx={hdrCell}>Personal Email</TableCell>
                <TableCell sx={hdrCell}>Department</TableCell>
                <TableCell sx={hdrCell}>Position</TableCell>
                <TableCell sx={hdrCell}>Employment Type</TableCell>
                <TableCell sx={hdrCell}>Source of Hire</TableCell>
                <TableCell sx={hdrCell}>Date of Joining</TableCell>
                <TableCell sx={hdrCell}>Date of Exit</TableCell>
                <TableCell sx={hdrCell}>Gender</TableCell>
                <TableCell sx={hdrCell}>Date of Birth</TableCell>
                <TableCell sx={hdrCell}>Age</TableCell>
                <TableCell sx={hdrCell}>Marital Status</TableCell>
                <TableCell sx={hdrCell}>Seating Location</TableCell>
                <TableCell sx={hdrCell}>Current Exp</TableCell>
                <TableCell sx={hdrCell}>Total Exp</TableCell>
                <TableCell sx={hdrCell}>Role</TableCell>
                <TableCell sx={hdrCell}>Manager</TableCell>
                <TableCell sx={hdrCell}>Added By</TableCell>
                <TableCell sx={hdrCell}>Modified By</TableCell>
                <TableCell sx={hdrCell}>Added Time</TableCell>
                <TableCell sx={hdrCell}>Modified Time</TableCell>
                <TableCell sx={hdrCell}>Status</TableCell>
                {/* sticky last column */}
                <TableCell sx={{ ...stickyRight, ...hdrCell }} align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={26} align="center" sx={{ py: 4 }}>
                    <CircularProgress />
                  </TableCell>
                </TableRow>
              ) : filtered.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={26} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                    No employees found
                  </TableCell>
                </TableRow>
              ) : filtered.map((emp) => {
                const isAdminRow    = emp.role === 'ADMIN';
                const viewerIsAdmin = user?.role === 'ADMIN';
                const canClick      = viewerIsAdmin || !isAdminRow;

                return (
                  <TableRow key={emp.id} hover>
                    {/* ── Emp ID — sticky col 1 ── */}
                    <TableCell sx={{ ...stickyId, ...cell, bgcolor: 'white', borderRight: '1px solid #e2e8f0' }}>
                      <Typography sx={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 13, color: '#1d4ed8' }}>
                        {emp.employeeCode || '—'}
                      </Typography>
                    </TableCell>

                    {/* ── Employee Name — sticky col 2 ── */}
                    <TableCell sx={{ ...stickyName, ...cell, bgcolor: 'white', borderRight: '1px solid #e2e8f0' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Avatar sx={{ width: 30, height: 30, bgcolor: '#1976d2', fontSize: '0.78rem', flexShrink: 0 }}>
                          {emp.firstName?.charAt(0)}
                        </Avatar>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography
                            variant="body2" fontWeight={600} noWrap
                            onClick={canClick ? () => navigate(`/employees/${emp.id}`) : undefined}
                            sx={canClick ? { cursor: 'pointer', '&:hover': { color: 'primary.main', textDecoration: 'underline' } } : {}}
                          >
                            {emp.fullName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" noWrap>{emp.email}</Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell sx={cell}>{emp.phone || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.personalEmail || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.department || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.position || '—'}</TableCell>
                    <TableCell sx={cell}>
                      {emp.employmentType
                        ? <Chip label={emp.employmentType} size="small" variant="outlined" sx={{ fontSize: 11 }} />
                        : '—'}
                    </TableCell>
                    <TableCell sx={cell}>{emp.sourceOfHire || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.hireDate ? String(emp.hireDate).slice(0,10) : '—'}</TableCell>
                    <TableCell sx={cell}>{emp.dateOfExit ? String(emp.dateOfExit).slice(0,10) : '—'}</TableCell>
                    <TableCell sx={cell}>{emp.gender || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.dateOfBirth ? String(emp.dateOfBirth).slice(0,10) : '—'}</TableCell>
                    <TableCell sx={cell}>{emp.age != null ? emp.age : '—'}</TableCell>
                    <TableCell sx={cell}>{emp.maritalStatus || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.seatingLocation || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.currentExperience || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.totalExperience || '—'}</TableCell>
                    <TableCell sx={cell}>
                      <Chip label={emp.role} size="small" color={roleColors[emp.role] || 'default'} />
                    </TableCell>
                    <TableCell sx={cell}>{emp.managerName || '—'}</TableCell>
                    <TableCell sx={cell}>
                      <Typography variant="caption" noWrap sx={{ display: 'block', maxWidth: 130 }}>
                        {emp.addedBy || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell sx={cell}>
                      <Typography variant="caption" noWrap sx={{ display: 'block', maxWidth: 130 }}>
                        {emp.modifiedBy || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell sx={cell}>
                      <Typography variant="caption" noWrap>{emp.createdAt ? fmtDateTime(emp.createdAt) : '—'}</Typography>
                    </TableCell>
                    <TableCell sx={cell}>
                      <Typography variant="caption" noWrap>{emp.updatedAt ? fmtDateTime(emp.updatedAt) : '—'}</Typography>
                    </TableCell>
                    <TableCell sx={cell}>
                      <Chip label={emp.active ? 'Active' : 'Inactive'} size="small"
                        color={emp.active ? 'success' : 'default'} />
                    </TableCell>

                    {/* Actions — sticky */}
                    <TableCell align="center"
                      sx={{ ...stickyRight, bgcolor: 'white', borderLeft: '1px solid #e2e8f0' }}>
                      {canClick && (
                        <Tooltip title="View">
                          <IconButton size="small" onClick={() => navigate(`/employees/${emp.id}`)}>
                            <VisibilityIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      )}
                      {user?.role === 'ADMIN' && (
                        <>
                          <Tooltip title="Edit">
                            <IconButton size="small" color="primary" onClick={() => openEdit(emp)}>
                              <EditIcon sx={{ fontSize: 16 }} />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title={emp.active ? 'Deactivate' : 'Activate'}>
                            <IconButton size="small" onClick={() => setToggleTarget(emp)}
                              color={emp.active ? 'error' : 'success'}>
                              <BlockIcon sx={{ fontSize: 16 }} />
                            </IconButton>
                          </Tooltip>
                        </>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* ── Add Employee dialog ── */}
      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="md" fullWidth
        PaperProps={{ sx: { maxHeight: '90vh' } }}>
        <DialogTitle fontWeight={700}>Add New Employee</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <EmployeeForm
            values={form} setter={setForm} isEdit={false}
            employees={employees}
            departments={departments} positions={positions} locations={locations}
            onManage={handleManage}
          />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleAdd} disabled={saving}
            startIcon={saving ? <CircularProgress size={16} /> : null}>
            {saving ? 'Adding…' : 'Add Employee'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Edit Employee dialog ── */}
      <Dialog open={Boolean(editTarget)} onClose={() => setEditTarget(null)} maxWidth="md" fullWidth
        PaperProps={{ sx: { maxHeight: '90vh' } }}>
        <DialogTitle fontWeight={700}>Edit Employee — {editTarget?.fullName}</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          {editTarget && (
            <EmployeeForm
              values={editForm} setter={setEditForm} isEdit={true}
              employees={employees}
              departments={departments} positions={positions} locations={locations}
              onManage={handleManage}
            />
          )}
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setEditTarget(null)}>Cancel</Button>
          <Button variant="contained" onClick={handleEdit} disabled={saving}
            startIcon={saving ? <CircularProgress size={16} /> : null}>
            {saving ? 'Saving…' : 'Save Changes'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Deactivate / Activate confirmation ── */}
      <Dialog open={Boolean(toggleTarget)} onClose={() => setToggleTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>
          {toggleTarget?.active ? 'Deactivate Employee' : 'Activate Employee'}
        </DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to {toggleTarget?.active ? 'deactivate' : 'activate'}{' '}
            <strong>{toggleTarget?.fullName}</strong>?
            {toggleTarget?.active && ' They will lose access to the system.'}
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setToggleTarget(null)}>Cancel</Button>
          <Button variant="contained" color={toggleTarget?.active ? 'error' : 'success'}
            onClick={handleConfirmToggle}>
            {toggleTarget?.active ? 'Deactivate' : 'Activate'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Manage List dialog (Departments / Positions / Locations) ── */}
      {manageConfig && (
        <ManageListDialog
          open={Boolean(manageOpen)}
          onClose={() => setManageOpen(null)}
          title={manageConfig.label}
          items={manageConfig.items}
          onAdd={handleManageAdd}
          onEdit={handleManageEdit}
          onDelete={handleManageDelete}
        />
      )}
    </Box>
  );
};

export default EmployeesPage;
