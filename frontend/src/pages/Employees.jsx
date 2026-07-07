import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { useSelector } from 'react-redux';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box, Button, Card, CardContent, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Typography, TextField,
  InputAdornment, Chip, Avatar, IconButton, Dialog, DialogTitle,
  DialogContent, DialogActions, MenuItem, Select, FormControl,
  InputLabel, CircularProgress, Tooltip, Stack, Tabs, Tab,
  Divider, List, ListItem, ListItemText, LinearProgress, TablePagination,
  Collapse,
} from '@mui/material';
import SearchIcon         from '@mui/icons-material/Search';
import AddIcon            from '@mui/icons-material/Add';
import EditIcon           from '@mui/icons-material/Edit';
import UploadFileIcon     from '@mui/icons-material/UploadFile';
import DeleteIcon         from '@mui/icons-material/Delete';
import TuneIcon           from '@mui/icons-material/Tune';
import CheckIcon          from '@mui/icons-material/Check';
import CloseIcon          from '@mui/icons-material/Close';
import PeopleIcon         from '@mui/icons-material/People';
import AccessTimeIcon     from '@mui/icons-material/AccessTime';
import TrendingUpIcon     from '@mui/icons-material/TrendingUp';
import SchoolIcon         from '@mui/icons-material/School';
import NavigateBeforeIcon from '@mui/icons-material/NavigateBefore';
import NavigateNextIcon   from '@mui/icons-material/NavigateNext';
import EmojiEventsIcon    from '@mui/icons-material/EmojiEvents';
import CheckCircleIcon    from '@mui/icons-material/CheckCircle';
import PersonRemoveIcon   from '@mui/icons-material/PersonRemove';
import VisibilityIcon        from '@mui/icons-material/Visibility';
import VisibilityOffIcon     from '@mui/icons-material/VisibilityOff';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon   from '@mui/icons-material/KeyboardArrowUp';
import DownloadIcon          from '@mui/icons-material/Download';
import { employeeApi }    from '../api/employeeApi';
import { resumeApi }      from '../api/resumeApi';
import { timesheetApi }      from '../api/timesheetApi';
import { timesheetEntryApi } from '../api/timesheetEntryApi';
import { performanceApi } from '../api/performanceApi';
import { courseApi }      from '../api/courseApi';
import { toast }          from 'react-toastify';

// ── constants ─────────────────────────────────────────────────────────────────
const roleColors = { ADMIN: 'error', MANAGER: 'warning', ASSISTANT_MANAGER: 'warning', HR: 'secondary', EMPLOYEE: 'primary' };
const roleCustomColors = { DIRECTOR: '#4f46e5' };

const INITIAL_DEPARTMENTS = [
  'Human Resource', 'Operation', 'Management', 'Marketing', 'IT',
];
const INITIAL_POSITIONS = [
  'Accounts Trainee', 'Accounts Executive', 'Senior Accountant',
  'Sr. Payroll Administrator', 'Assistant Manager', 'Manager',
  'Business Development and Operation', 'HR', 'System Administrator',
];
const INITIAL_LOCATIONS = ['Mandsaur', 'Ahmedabad', 'Jamnagar'];
const EMPLOYMENT_TYPES  = ['Full-time', 'Part-time', 'Contract', 'Intern', 'Consultant'];
const SOURCE_OF_HIRE    = ['LinkedIn', 'Referral', 'Job Portal', 'Walk-in', 'Campus', 'Other'];
const GENDERS           = ['Male', 'Female', 'Other', 'Prefer not to say'];
const MARITAL_STATUSES  = ['Single', 'Married', 'Divorced', 'Widowed'];

const EMPTY_FORM = {
  email: '', password: '', firstName: '', lastName: '',
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
const hdrCell    = { fontWeight: 700, fontSize: 12, whiteSpace: 'nowrap', py: 1.2, px: 1.5, color: '#374151' };
const cell       = { fontSize: 12.5, whiteSpace: 'nowrap', py: 1, px: 1.5, color: '#1e293b' };
const EMPID_W    = 90;
const EMPNAME_W  = 200;
const LOC_W      = 120;
const stickyId   = { position: 'sticky', left: 0,                   zIndex: 3, minWidth: EMPID_W,   maxWidth: EMPID_W   };
const stickyName = { position: 'sticky', left: EMPID_W,             zIndex: 3, minWidth: EMPNAME_W, maxWidth: EMPNAME_W };
const stickyLoc  = { position: 'sticky', left: EMPID_W + EMPNAME_W, zIndex: 3, minWidth: LOC_W,     maxWidth: LOC_W     };

const ROField = ({ label, value }) => (
  <Box>
    <Typography variant="caption" color="text.secondary">{label}</Typography>
    <Typography variant="body2" fontWeight={500}>{value || '—'}</Typography>
  </Box>
);

// ── StatCard ──────────────────────────────────────────────────────────────────
const StatCard = ({ icon, label, value, color, subtitle }) => (
  <Card sx={{ flex: 1, minWidth: 150 }}>
    <CardContent sx={{ py: 1.5, px: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Box sx={{
          p: 1, borderRadius: 1.5,
          bgcolor: `${color}22`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexShrink: 0,
        }}>
          {React.cloneElement(icon, { sx: { color, fontSize: 22 } })}
        </Box>
        <Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.3, fontSize: 11 }}>
            {label}
          </Typography>
          <Typography variant="h6" fontWeight={700} sx={{ lineHeight: 1.2 }}>
            {value ?? '—'}
          </Typography>
          {subtitle && (
            <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
    </CardContent>
  </Card>
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

  const startEdit  = (i) => { setEditIdx(i); setEditVal(items[i]); };
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

// ── ManagedSelect ─────────────────────────────────────────────────────────────
const ManagedSelect = ({ label, fieldKey, value, options, setter, onManage, highlightSx }) => (
  <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'flex-start' }}>
    <FormControl size="small" fullWidth sx={highlightSx}>
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

// ── Field label map for the confidence banner ─────────────────────────────────
const PARSE_FIELD_LABELS = {
  firstName: 'First Name', lastName: 'Last Name', email: 'Work Email',
  phone: 'Phone', personalEmail: 'Personal Email', position: 'Position',
  totalExperience: 'Total Exp.', currentExperience: 'Current Exp.',
  skills: 'Skills', experience: 'Work History', summary: 'Summary',
  seatingLocation: 'Location', presentAddress: 'Address',
  dateOfBirth: 'Date of Birth', gender: 'Gender', maritalStatus: 'Marital Status',
  password: 'Password',
};

// ── EmployeeForm ──────────────────────────────────────────────────────────────
const EmployeeForm = ({ values, setter, isEdit, employees,
                        departments, positions, locations, onManage, parseResult }) => {
  const [tab,          setTab]          = useState(0);
  const [showPassword, setShowPassword] = useState(false);

  // Sets derived from parseResult for O(1) lookup
  const parsedFieldsSet = useMemo(() => new Set(parseResult?.parsedFields || []), [parseResult]);
  const reviewFieldsSet = useMemo(() => new Set(parseResult?.reviewFields  || []), [parseResult]);

  // Returns sx to apply a coloured border to a field based on parse outcome
  const hSx = useCallback((key) => {
    if (parsedFieldsSet.has(key)) return {
      '& .MuiOutlinedInput-root .MuiOutlinedInput-notchedOutline': { borderColor: '#22c55e', borderWidth: 2 },
      '& .MuiOutlinedInput-root:hover .MuiOutlinedInput-notchedOutline': { borderColor: '#16a34a' },
    };
    if (reviewFieldsSet.has(key)) return {
      '& .MuiOutlinedInput-root .MuiOutlinedInput-notchedOutline': { borderColor: '#f59e0b', borderWidth: 2 },
      '& .MuiOutlinedInput-root:hover .MuiOutlinedInput-notchedOutline': { borderColor: '#d97706' },
    };
    return {};
  }, [parsedFieldsSet, reviewFieldsSet]);

  const field = (key, label, opts = {}) => {
    const { sx: optSx, ...rest } = opts;
    return (
      <TextField
        label={label}
        value={values[key] ?? ''}
        size="small"
        onChange={(e) => setter((f) => ({ ...f, [key]: e.target.value }))}
        sx={{ ...hSx(key), ...optSx }}
        {...rest}
      />
    );
  };

  const dateField = (key, label, opts = {}) => {
    const { sx: optSx, ...rest } = opts;
    return (
      <TextField
        label={label}
        type="date"
        value={fmtDate(values[key])}
        size="small"
        InputLabelProps={{ shrink: true }}
        onChange={(e) => setter((f) => ({ ...f, [key]: e.target.value || null }))}
        sx={{ ...hSx(key), ...optSx }}
        {...rest}
      />
    );
  };

  const selectField = (key, label, options) => (
    <FormControl size="small" fullWidth sx={hSx(key)}>
      <InputLabel>{label}</InputLabel>
      <Select value={values[key] || ''} label={label}
        onChange={(e) => setter((f) => ({ ...f, [key]: e.target.value }))}>
        <MenuItem value=""><em>None</em></MenuItem>
        {options.map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
      </Select>
    </FormControl>
  );

  const grid2 = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, mt: 1 };
  const score = parseResult?.confidenceScore ?? 0;
  const scoreColor  = score >= 70 ? '#166534' : score >= 40 ? '#92400e' : '#9f1239';
  const scoreBg     = score >= 70 ? '#f0fdf4' : score >= 40 ? '#fffbeb' : '#fff1f2';
  const scoreBorder = score >= 70 ? '#bbf7d0' : score >= 40 ? '#fde68a' : '#fecdd3';
  const barColor    = score >= 70 ? '#22c55e' : score >= 40 ? '#f59e0b' : '#ef4444';
  const scoreLabel  = score >= 70 ? 'High confidence' : score >= 40 ? 'Medium confidence' : 'Low confidence';

  return (
    <Box>
      {/* ── Confidence banner (shown only after resume parse) ── */}
      {parseResult && !isEdit && (
        <Box sx={{ mb: 2, p: 2, borderRadius: 2, bgcolor: scoreBg, border: `1px solid ${scoreBorder}` }}>
          {/* Header row */}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.75 }}>
            <Typography fontWeight={700} fontSize={13.5} color={scoreColor}>
              Resume parsed — {score}% confidence
            </Typography>
            <Chip label={scoreLabel} size="small"
              sx={{ bgcolor: scoreBorder, color: scoreColor, fontWeight: 700, fontSize: 11 }} />
          </Box>

          {/* Progress bar */}
          <LinearProgress variant="determinate" value={score}
            sx={{ height: 6, borderRadius: 3, bgcolor: scoreBorder,
              '& .MuiLinearProgress-bar': { bgcolor: barColor, borderRadius: 3 } }} />

          {/* Auto-filled chips */}
          {parseResult.parsedFields?.length > 0 && (
            <Box sx={{ mt: 1.25, display: 'flex', flexWrap: 'wrap', gap: 0.5, alignItems: 'center' }}>
              <Typography fontSize={11} color="text.secondary" fontWeight={600} sx={{ mr: 0.25 }}>
                Auto-filled:
              </Typography>
              {parseResult.parsedFields.slice(0, 10).map((f) => (
                <Chip key={f} label={PARSE_FIELD_LABELS[f] || f} size="small"
                  sx={{ bgcolor: '#dcfce7', color: '#166534', fontSize: 10, height: 20,
                    border: '1px solid #bbf7d0', fontWeight: 600 }} />
              ))}
              {parseResult.parsedFields.length > 10 && (
                <Typography fontSize={10} color="text.secondary">
                  +{parseResult.parsedFields.length - 10} more
                </Typography>
              )}
            </Box>
          )}

          {/* Review messages */}
          {parseResult.reviewMessages?.length > 0 && (
            <Box sx={{ mt: 1.25, p: 1.25, borderRadius: 1.5,
              bgcolor: '#fffbeb', border: '1px solid #fde68a' }}>
              <Typography fontSize={11.5} fontWeight={700} color="#92400e" mb={0.5}>
                Needs manual review:
              </Typography>
              {parseResult.reviewMessages.map((msg, i) => (
                <Typography key={i} fontSize={11} color="#b45309"
                  sx={{ display: 'flex', gap: 0.5, mb: 0.25 }}>
                  · {msg}
                </Typography>
              ))}
            </Box>
          )}

          {/* Legend */}
          <Box sx={{ mt: 1, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            {[
              { color: '#22c55e', label: 'Auto-filled from resume' },
              { color: '#f59e0b', label: 'Requires manual entry' },
            ].map(({ color, label }) => (
              <Box key={label} sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <Box sx={{ width: 10, height: 10, borderRadius: '3px',
                  border: `2px solid ${color}`, flexShrink: 0 }} />
                <Typography fontSize={10} color="text.secondary">{label}</Typography>
              </Box>
            ))}
          </Box>
        </Box>
      )}

      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto"
        sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
        <Tab label="Basic Info" />
        <Tab label="Employment" />
        <Tab label="Work Details" />
        <Tab label="Identity" />
        <Tab label="Address" />
        {isEdit && <Tab label="System" />}
      </Tabs>

      {tab === 0 && (
        <Box sx={grid2}>
          <input type="text"     style={{ display: 'none' }} autoComplete="username"     readOnly />
          <input type="password" style={{ display: 'none' }} autoComplete="new-password" readOnly />
          {isEdit && field('employeeCode', 'Employee ID / Code', { sx: { gridColumn: 'span 2' } })}
          {field('firstName', 'First Name *')}
          {field('lastName',  'Last Name *')}
          {field('email', 'Work Email *', { sx: { gridColumn: 'span 2' }, inputProps: { autoComplete: 'off' } })}
          {!isEdit && (
            <TextField
              label="Password *"
              value={values.password ?? ''}
              size="small"
              type={showPassword ? 'text' : 'password'}
              inputProps={{ autoComplete: 'new-password' }}
              sx={hSx('password')}
              onChange={(e) => setter((f) => ({ ...f, password: e.target.value }))}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => setShowPassword((p) => !p)} edge="end">
                      {showPassword ? <VisibilityOffIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />
          )}
          {field('phone', 'Phone')}
          {field('personalEmail', 'Personal Email')}
          {dateField('dateOfBirth', 'Date of Birth', { inputProps: { max: new Date().toISOString().split('T')[0] } })}
          {isEdit && values.dateOfBirth && (
            <TextField label="Age" size="small"
              value={Math.floor((new Date() - new Date(values.dateOfBirth)) / (365.25 * 24 * 3600 * 1000))}
              InputProps={{ readOnly: true }} />
          )}
          {selectField('gender', 'Gender', GENDERS)}
          {selectField('maritalStatus', 'Marital Status', MARITAL_STATUSES)}
        </Box>
      )}

      {tab === 1 && (
        <Box sx={grid2}>
          <ManagedSelect
            label="Department" fieldKey="department"
            value={values.department} options={departments}
            setter={setter} onManage={() => onManage('dept')}
            highlightSx={hSx('department')}
          />
          <ManagedSelect
            label="Position" fieldKey="position"
            value={values.position} options={positions}
            setter={setter} onManage={() => onManage('pos')}
            highlightSx={hSx('position')}
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
              <MenuItem value="ASSISTANT_MANAGER">Assistant Manager</MenuItem>
              <MenuItem value="HR">HR</MenuItem>
              <MenuItem value="ADMIN">Admin</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" fullWidth>
            <InputLabel>Manager (optional)</InputLabel>
            <Select value={values.managerId || ''} label="Manager (optional)"
              onChange={(e) => setter((f) => ({ ...f, managerId: e.target.value }))}>
              <MenuItem value="">None</MenuItem>
              {employees
                .filter((e) => e.active !== false &&
                  (e.role === 'MANAGER' || e.role === 'ASSISTANT_MANAGER' || e.role === 'ADMIN'))
                .map((e) => (
                  <MenuItem key={e.id} value={e.id}>{e.fullName} ({e.role})</MenuItem>
                ))}
            </Select>
          </FormControl>
        </Box>
      )}

      {tab === 2 && (
        <Box sx={grid2}>
          {field('currentExperience', 'Current Experience (e.g. 2 years)')}
          {field('totalExperience',   'Total Experience (e.g. 5 years)')}
          <Box sx={{ gridColumn: 'span 2' }}>
            <ManagedSelect
              label="Location" fieldKey="seatingLocation"
              value={values.seatingLocation} options={locations}
              setter={setter} onManage={() => onManage('loc')}
              highlightSx={hSx('seatingLocation')}
            />
          </Box>
          {field('skills',     'Skills',             { multiline: true, rows: 3, sx: { gridColumn: 'span 2' } })}
          {field('experience', 'Experience Summary', { multiline: true, rows: 4, sx: { gridColumn: 'span 2' } })}
        </Box>
      )}

      {tab === 3 && (
        <Box sx={grid2}>
          {field('aadharNumber', 'Aadhar Number', { sx: { gridColumn: 'span 2' } })}
          {field('panNumber', 'PAN Number')}
          {field('uanNumber', 'UAN Number')}
        </Box>
      )}

      {tab === 4 && (
        <Box sx={grid2}>
          {field('presentAddress',   'Present Address',   { multiline: true, rows: 3, sx: { gridColumn: 'span 2' } })}
          {field('permanentAddress', 'Permanent Address', { multiline: true, rows: 3, sx: { gridColumn: 'span 2' } })}
        </Box>
      )}

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

// ── TimesheetTab ──────────────────────────────────────────────────────────────
const TimesheetTab = ({ employees, user }) => {
  const today = new Date().toISOString().slice(0, 10);
  const [date,       setDate]       = useState(today);
  const [raw,        setRaw]        = useState([]);
  const [entriesMap, setEntriesMap] = useState({}); // empId -> [{projectName, taskName, hours}]
  const [loading,    setLoading]    = useState(false);
  const [tsPage,     setTsPage]     = useState(0);
  const [tsRpp,      setTsRpp]      = useState(10);
  // Calculate hours from checkIn / checkOut strings ("HH:MM:SS")
  const calcHours = (checkIn, checkOut) => {
    if (!checkIn || !checkOut) return null;
    const toSec = (t) => { const [h, m, s] = t.split(':').map(Number); return h * 3600 + m * 60 + (s || 0); };
    const diff = toSec(checkOut) - toSec(checkIn);
    return diff > 0 ? Math.round((diff / 3600) * 100) / 100 : null;
  };

  const load = useCallback(async (d) => {
    setLoading(true);
    try {
      const [year, month] = d.split('-').map(Number);
      const activeEmps = employees.filter((e) => e.active);

      // For each employee: load stored TimesheetDTO (has workingHours from DB) + project entries
      const results = await Promise.all(
        activeEmps.map((emp) =>
          Promise.all([
            timesheetApi.getAttendanceByRange(emp.id, d, d)
              .then((r) => (Array.isArray(r) ? r : [])[0] ?? null)
              .catch(() => null),
            timesheetEntryApi.getMonthly(emp.id, year, month)
              .then((r) => (Array.isArray(r) ? r : []).filter((e) => String(e.date ?? e.workDate ?? '').slice(0, 10) === d))
              .catch(() => []),
          ]).then(([ts, entries]) => ({ emp, ts, entries }))
        )
      );

      // Build attendance map — calculate hours from checkIn/checkOut, not stored workingHours
      const attendanceMap = {};
      const entryMap      = {};
      results.forEach(({ emp, ts, entries }) => {
        const hasOpen = ts != null && ts.loginTime != null && ts.logoutTime == null;
        const checkIn  = ts?.loginTime  ?? null;
        const checkOut = ts?.logoutTime ?? null;
        attendanceMap[emp.id] = {
          checkIn,
          checkOut,
          totalHours: calcHours(checkIn, checkOut),
          hasOpen:    !!hasOpen,
          present:    ts != null && (ts.loginTime != null || ts.workingHours != null),
        };
        entryMap[emp.id] = entries;
      });

      setRaw(attendanceMap);
      setEntriesMap(entryMap);
    } catch {
      setRaw({});
      setEntriesMap({});
    } finally {
      setLoading(false);
    }
  }, [employees]);

  useEffect(() => { load(date); setTsPage(0); }, [date, load]);

  const shiftDay = (delta) => {
    const [y, m, d] = date.split('-').map(Number);
    const dt = new Date(Date.UTC(y, m - 1, d + delta));
    setDate(dt.toISOString().slice(0, 10));
  };
  const prevDay = () => shiftDay(-1);
  const nextDay = () => shiftDay(+1);

  const allActiveEmps = useMemo(() => employees.filter((e) => e.active), [employees]);

  // Build flat rows: one row per project entry, or one row per employee if no entries
  const rows = useMemo(() => {
    const pagedEmps = allActiveEmps.slice(tsPage * tsRpp, (tsPage + 1) * tsRpp);
    const result = [];
    pagedEmps.forEach((emp) => {
      const att     = raw[emp.id] ?? { checkIn: null, checkOut: null, totalHours: null, hasOpen: false, present: false };
      const entries = entriesMap[emp.id] || [];

      if (entries.length === 0) {
        result.push({ _emp: emp, att, projectName: null, timeSpent: null, _rowSpan: 1, _first: true });
      } else {
        entries.forEach((entry, ei) => {
          result.push({
            _emp: emp, att,
            projectName: entry.projectName || entry.project || '—',
            taskName:    entry.taskName    || entry.task    || null,
            timeSpent:   entry.hours       ?? entry.hoursSpent ?? entry.duration ?? null,
            _rowSpan: ei === 0 ? entries.length : 0,
            _first:   ei === 0,
          });
        });
      }
    });
    return result;
  }, [raw, allActiveEmps, entriesMap, tsPage, tsRpp]);

  const uniqueEmps   = useMemo(() => allActiveEmps.length, [allActiveEmps]);
  const presentCount = useMemo(() => Object.values(raw).filter((a) => a.present).length, [raw]);
  const absentCount = uniqueEmps - presentCount;

  const [_y, _m, _d] = date.split('-').map(Number);
  const dateLabel = new Date(Date.UTC(_y, _m - 1, _d)).toLocaleDateString('en-IN', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
    timeZone: 'UTC',
  });

  return (
    <Box>
      {/* Date navigation bar */}
      <Card sx={{ mb: 2 }}>
        <CardContent sx={{ py: 1.5 }}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Tooltip title="Previous day">
              <IconButton size="small" onClick={prevDay} sx={{ bgcolor: '#f1f5f9' }}>
                <NavigateBeforeIcon />
              </IconButton>
            </Tooltip>
            <TextField
              type="date"
              size="small"
              value={date}
              onChange={(e) => e.target.value && setDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ width: 160 }}
            />
            <Tooltip title="Next day">
              <IconButton size="small" onClick={nextDay} disabled={date >= today}
                sx={{ bgcolor: '#f1f5f9' }}>
                <NavigateNextIcon />
              </IconButton>
            </Tooltip>
            <Typography variant="body2" color="text.secondary" sx={{ ml: 1 }}>
              {dateLabel}
            </Typography>
            {loading && <CircularProgress size={18} sx={{ ml: 1 }} />}
            <Box sx={{ ml: 'auto !important', display: 'flex', gap: 1 }}>
              <Chip
                icon={<CheckCircleIcon sx={{ fontSize: '14px !important' }} />}
                label={`${presentCount} Present`}
                color="success" size="small" variant="outlined"
              />
              <Chip label={`${absentCount} Absent`} size="small" variant="outlined" />
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {/* Timesheet table */}
      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                <TableCell sx={hdrCell}>Employee</TableCell>
                <TableCell sx={hdrCell}>Project Name</TableCell>
                <TableCell sx={hdrCell}>Time Spent</TableCell>
                <TableCell sx={hdrCell}>Check In</TableCell>
                <TableCell sx={hdrCell}>Check Out</TableCell>
                <TableCell sx={hdrCell}>Total Hours</TableCell>
                <TableCell sx={hdrCell}>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading && rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                    <CircularProgress size={32} />
                  </TableCell>
                </TableRow>
              ) : rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                    No active employees found
                  </TableCell>
                </TableRow>
              ) : (() => {
                let serial = 0;
                return rows.map((r, i) => {
                  if (r._first) serial += 1;
                  const { checkIn, checkOut, totalHours, hasOpen, present } = r.att;
                  const fmtHms = (t) => t ? t.substring(0, 5) : '—'; // HH:MM from HH:MM:SS
                  const hrs    = totalHours != null ? Number(totalHours).toFixed(1) : null;
                  const bgColor = (serial % 2 === 0) ? '#f8fafc' : 'white';
                  return (
                    <TableRow key={`${r._emp.id}-${i}`} hover sx={{ bgcolor: bgColor }}>
                      {r._first && (
                        <TableCell rowSpan={r._rowSpan || 1} sx={{ ...cell, verticalAlign: 'middle' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Avatar sx={{ width: 28, height: 28, bgcolor: '#14b8a6', fontSize: '0.72rem', flexShrink: 0 }}>
                              {r._emp.firstName?.charAt(0) || '?'}
                            </Avatar>
                            <Box>
                              <Typography variant="body2" fontWeight={600} sx={{ fontSize: 12.5 }}>
                                {r._emp.fullName}
                              </Typography>
                            </Box>
                          </Box>
                        </TableCell>
                      )}
                      {/* Project Name */}
                      <TableCell sx={cell}>
                        {r.projectName ? (
                          <Box>
                            <Typography variant="body2" fontWeight={600} sx={{ fontSize: 12.5 }}>
                              {r.projectName}
                            </Typography>
                            {r.taskName && (
                              <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>
                                {r.taskName}
                              </Typography>
                            )}
                          </Box>
                        ) : (
                          <Typography variant="caption" color="text.secondary">—</Typography>
                        )}
                      </TableCell>
                      {/* Time Spent */}
                      <TableCell sx={cell}>
                        {r.timeSpent != null ? (
                          <Chip label={`${Number(r.timeSpent).toFixed(1)} hrs`} size="small"
                            sx={{ fontSize: 11, bgcolor: '#eff6ff', color: '#1d4ed8', fontWeight: 700 }} />
                        ) : (
                          <Typography variant="caption" color="text.secondary">—</Typography>
                        )}
                      </TableCell>
                      {r._first && (
                        <TableCell rowSpan={r._rowSpan || 1} sx={{ ...cell, fontFamily: 'monospace', color: '#15803d', verticalAlign: 'middle' }}>
                          {fmtHms(checkIn)}
                        </TableCell>
                      )}
                      {r._first && (
                        <TableCell rowSpan={r._rowSpan || 1} sx={{ ...cell, fontFamily: 'monospace', color: '#dc2626', verticalAlign: 'middle' }}>
                          {fmtHms(checkOut)}
                        </TableCell>
                      )}
                      {r._first && (
                        <TableCell rowSpan={r._rowSpan || 1} sx={{ ...cell, verticalAlign: 'middle' }}>
                          {hrs != null ? (
                            <Chip label={`${hrs} hrs`} size="small" color="primary" variant="outlined" sx={{ fontSize: 11 }} />
                          ) : hasOpen ? (
                            <Chip label="In Progress" size="small" color="warning" variant="outlined" sx={{ fontSize: 11 }} />
                          ) : '—'}
                        </TableCell>
                      )}
                      {r._first && (
                        <TableCell rowSpan={r._rowSpan || 1} sx={{ ...cell, verticalAlign: 'middle' }}>
                          <Chip
                            label={hasOpen ? 'Checked In' : present ? 'Present' : 'Absent'}
                            size="small"
                            color={hasOpen ? 'warning' : present ? 'success' : 'default'}
                            sx={{ fontSize: 11 }}
                          />
                        </TableCell>
                      )}
                    </TableRow>
                  );
                });
              })()}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={allActiveEmps.length}
          page={tsPage}
          onPageChange={(_, p) => setTsPage(p)}
          rowsPerPage={tsRpp}
          onRowsPerPageChange={(e) => { setTsRpp(parseInt(e.target.value, 10)); setTsPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
        />
      </Card>
    </Box>
  );
};

// ── PerformanceTab ────────────────────────────────────────────────────────────
const scoreColor = (s) => (s >= 4 ? 'success' : s >= 3 ? 'warning' : 'error');
const avatarPalette = ['#6366f1','#0ea5e9','#10b981','#f59e0b','#ef4444','#8b5cf6','#ec4899','#14b8a6','#f97316','#3b82f6'];
const avatarColor = (idx) => avatarPalette[idx % avatarPalette.length];

// Build base quarter labels for the last 2 years up to current quarter
const buildBaseQuarters = () => {
  const now  = new Date();
  const yr   = now.getFullYear();
  const curQ = Math.ceil((now.getMonth() + 1) / 3);
  const list = [];
  for (let y = yr - 1; y <= yr; y++) {
    const maxQ = y === yr ? curQ : 4;
    for (let q = 1; q <= maxQ; q++) list.push(`Q${q} ${y}`);
  }
  return list; // oldest → newest
};

const sortQuarters = (arr) =>
  [...arr].sort((a, b) => {
    const [qa, ya] = a.split(' ');
    const [qb, yb] = b.split(' ');
    const yd = parseInt(ya) - parseInt(yb);
    return yd !== 0 ? yd : parseInt(qa.slice(1)) - parseInt(qb.slice(1));
  });

const PerformanceTab = ({ employees }) => {
  const baseQuarters = useMemo(() => buildBaseQuarters(), []);
  const currentQ     = baseQuarters[baseQuarters.length - 1];

  const [selectedQ, setSelectedQ] = useState(currentQ);
  const [data,      setData]      = useState([]);
  const [loading,   setLoading]   = useState(false);
  const [loaded,    setLoaded]    = useState(false);
  const [perfPage,  setPerfPage]  = useState(0);
  const [perfRpp,   setPerfRpp]   = useState(10);
  const [expandedComments, setExpandedComments] = useState(new Set());


  useEffect(() => {
    if (loaded) return;
    setLoading(true);
    setLoaded(true);
    performanceApi.getAll()
      .then((r) => setData(Array.isArray(r) ? r : []))
      .catch(() => setData([]))
      .finally(() => setLoading(false));
  }, [loaded]);

  useEffect(() => {
    const handler = () => setLoaded(false);
    window.addEventListener('employee-updated', handler);
    return () => window.removeEventListener('employee-updated', handler);
  }, []);

  // Merge static range with any quarters that actually have reviews
  const allQuarters = useMemo(() => {
    const fromData = data.map(d => d.reviewPeriod).filter(Boolean);
    return sortQuarters([...new Set([...baseQuarters, ...fromData])]);
  }, [baseQuarters, data]);

  // Keep selectedQ valid when allQuarters expands
  useEffect(() => {
    if (allQuarters.length > 0 && !allQuarters.includes(selectedQ)) {
      setSelectedQ(allQuarters[allQuarters.length - 1]);
    }
  }, [allQuarters]); // eslint-disable-line

  const qIdx       = allQuarters.indexOf(selectedQ);
  const activeEmps = useMemo(() => employees.filter((e) => e.active), [employees]);

  useEffect(() => { setPerfPage(0); }, [selectedQ]);

  // Count how many employees have a review for the selected quarter
  const reviewedCount = useMemo(
    () => activeEmps.filter((emp) => data.some((d) => d.employeeId === emp.id && d.reviewPeriod === selectedQ)).length,
    [data, activeEmps, selectedQ]
  );

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
      <CircularProgress />
    </Box>
  );

  return (
    <Box>
      {/* Quarter navigator */}
      <Card sx={{ mb: 2 }}>
        <CardContent sx={{ py: 1.5 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Tooltip title="Previous quarter">
              <span>
                <IconButton size="small" onClick={() => setSelectedQ(allQuarters[qIdx - 1])}
                  disabled={qIdx <= 0} sx={{ bgcolor: '#f1f5f9' }}>
                  <NavigateBeforeIcon />
                </IconButton>
              </span>
            </Tooltip>

            {/* Quarter pills */}
            <Stack direction="row" spacing={1} sx={{ flex: 1, justifyContent: 'center' }} flexWrap="wrap">
              {allQuarters.map((q) => (
                <Chip
                  key={q}
                  label={q}
                  size="small"
                  onClick={() => setSelectedQ(q)}
                  color={q === selectedQ ? 'primary' : 'default'}
                  variant={q === selectedQ ? 'filled' : 'outlined'}
                  sx={{ cursor: 'pointer', fontWeight: q === selectedQ ? 700 : 400, fontSize: 12 }}
                />
              ))}
            </Stack>

            <Tooltip title="Next quarter">
              <span>
                <IconButton size="small" onClick={() => setSelectedQ(allQuarters[qIdx + 1])}
                  disabled={qIdx >= allQuarters.length - 1} sx={{ bgcolor: '#f1f5f9' }}>
                  <NavigateNextIcon />
                </IconButton>
              </span>
            </Tooltip>

            <Chip
              label={`${reviewedCount} / ${activeEmps.length} reviewed`}
              size="small"
              color={reviewedCount > 0 ? 'success' : 'default'}
              variant="outlined"
              sx={{ ml: 1, fontSize: 11, flexShrink: 0 }}
            />
          </Stack>
        </CardContent>
      </Card>

      {/* Performance table */}
      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                <TableCell sx={{ ...hdrCell, width: 200 }}>Employee</TableCell>
                <TableCell sx={{ ...hdrCell, width: 90, textAlign: 'center' }}>Rating</TableCell>
                <TableCell sx={{ ...hdrCell, width: 220 }}>Comments</TableCell>
                <TableCell sx={{ ...hdrCell, width: 180 }}>Strengths</TableCell>
                <TableCell sx={{ ...hdrCell, width: 180 }}>Improvement Area</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {activeEmps.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                    No employees found
                  </TableCell>
                </TableRow>
              ) : activeEmps.slice(perfPage * perfRpp, (perfPage + 1) * perfRpp).map((emp, i) => {
                const review   = data.find((d) => d.employeeId === emp.id && d.reviewPeriod === selectedQ);
                const score    = review?.rating ?? null;
                return (
                  <TableRow key={emp.id} hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
                    {/* Employee */}
                    <TableCell sx={cell}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Avatar sx={{ width: 30, height: 30, bgcolor: avatarColor(i), fontSize: '0.72rem', flexShrink: 0, fontWeight: 700 }}>
                          {emp.firstName?.charAt(0)}
                        </Avatar>
                        <Typography variant="body2" fontWeight={600} sx={{ fontSize: 12.5 }}>
                          {emp.fullName}
                        </Typography>
                      </Box>
                    </TableCell>

                    {/* Rating */}
                    <TableCell sx={{ ...cell, textAlign: 'center' }}>
                      {score != null ? (
                        <Chip label={`${score} / 5`} size="small" color={scoreColor(score)} sx={{ fontSize: 11, fontWeight: 700 }} />
                      ) : (
                        <Chip label="Not Reviewed" size="small" sx={{ fontSize: 10, fontWeight: 600, bgcolor: '#f1f5f9', color: '#94a3b8', border: '1px solid #e2e8f0' }} />
                      )}
                    </TableCell>

                    {/* Comments */}
                    <TableCell sx={{ ...cell, whiteSpace: 'normal', lineHeight: 1.5, maxWidth: 220 }}>
                      {review?.comments ? (() => {
                        const isExpanded = expandedComments.has(emp.id);
                        const isLong = review.comments.length > 45;
                        return (
                          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5, flexWrap: 'wrap' }}>
                            <Typography variant="body2" sx={{ fontSize: 12 }}>
                              {isLong && !isExpanded ? `${review.comments.slice(0, 45)}...` : review.comments}
                            </Typography>
                            {isLong && (
                              <Typography component="span"
                                sx={{ fontSize: 11, color: '#6366f1', cursor: 'pointer', fontWeight: 600, whiteSpace: 'nowrap', '&:hover': { textDecoration: 'underline' } }}
                                onClick={() => setExpandedComments(prev => {
                                  const next = new Set(prev);
                                  isExpanded ? next.delete(emp.id) : next.add(emp.id);
                                  return next;
                                })}>
                                {isExpanded ? 'Hide' : 'View'}
                              </Typography>
                            )}
                          </Box>
                        );
                      })() : <Typography variant="caption" color="text.secondary">—</Typography>}
                    </TableCell>

                    {/* Strengths */}
                    <TableCell sx={{ ...cell, maxWidth: 180, whiteSpace: 'normal', lineHeight: 1.5 }}>
                      {review?.strengths
                        ? <Typography variant="body2" sx={{ fontSize: 12, color: '#15803d' }}>{review.strengths}</Typography>
                        : <Typography variant="caption" color="text.secondary">—</Typography>}
                    </TableCell>

                    {/* Improvement Area */}
                    <TableCell sx={{ ...cell, maxWidth: 180, whiteSpace: 'normal', lineHeight: 1.5 }}>
                      {review?.areasOfImprovement
                        ? <Typography variant="body2" sx={{ fontSize: 12, color: '#b45309' }}>{review.areasOfImprovement}</Typography>
                        : <Typography variant="caption" color="text.secondary">—</Typography>}
                    </TableCell>

                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={activeEmps.length}
          page={perfPage}
          onPageChange={(_, p) => setPerfPage(p)}
          rowsPerPage={perfRpp}
          onRowsPerPageChange={(e) => { setPerfRpp(parseInt(e.target.value, 10)); setPerfPage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
        />
      </Card>


    </Box>
  );
};

// ── CoursesTab ────────────────────────────────────────────────────────────────
const CoursesTab = ({ user }) => {
  const [courses,        setCourses]        = useState([]);
  const [loading,        setLoading]        = useState(false);
  const [loaded,         setLoaded]         = useState(false);
  const [coursePage,     setCoursePage]     = useState(0);
  const [courseRpp,      setCourseRpp]      = useState(10);
  const [expandedRows,   setExpandedRows]   = useState(new Set());
  const [learnersMap,    setLearnersMap]    = useState({});
  const [learnersLoading, setLearnersLoading] = useState({});

  const isAdminRole   = user?.role === 'ADMIN' || user?.role === 'DIRECTOR' || user?.role === 'HR';
  const isManagerRole = user?.role === 'MANAGER' || user?.role === 'ASSISTANT_MANAGER';

  useEffect(() => {
    if (loaded) return;
    setLoading(true);
    setLoaded(true);
    if (isAdminRole) {
      courseApi.getAll()
        .then((r) => setCourses(Array.isArray(r) ? r : []))
        .catch(() => setCourses([]))
        .finally(() => setLoading(false));
    } else if (isManagerRole && user?.userId) {
      employeeApi.getByUserId(user.userId)
        .then((emp) => courseApi.getForManager(emp.id))
        .then((r) => setCourses(Array.isArray(r) ? r : []))
        .catch(() => setCourses([]))
        .finally(() => setLoading(false));
    } else {
      setCourses([]);
      setLoading(false);
    }
  }, [loaded]); // eslint-disable-line

  useEffect(() => {
    const handler = () => setLoaded(false);
    window.addEventListener('employee-updated', handler);
    return () => window.removeEventListener('employee-updated', handler);
  }, []);

  const toggleExpand = async (courseId) => {
    const next = new Set(expandedRows);
    if (next.has(courseId)) {
      next.delete(courseId);
    } else {
      next.add(courseId);
      if (!learnersMap[courseId]) {
        setLearnersLoading(p => ({ ...p, [courseId]: true }));
        try {
          const data = await courseApi.getLearners(courseId);
          setLearnersMap(p => ({ ...p, [courseId]: Array.isArray(data) ? data : [] }));
        } catch {
          setLearnersMap(p => ({ ...p, [courseId]: [] }));
        } finally {
          setLearnersLoading(p => ({ ...p, [courseId]: false }));
        }
      }
    }
    setExpandedRows(next);
  };

  const downloadCert = async (certNo, empName) => {
    try {
      const blob = await courseApi.downloadCertificatePdf(certNo);
      const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `Certificate_${(empName || '').replace(/\s+/g, '_')}.pdf`;
      document.body.appendChild(a); a.click();
      document.body.removeChild(a); URL.revokeObjectURL(url);
    } catch { /* silent */ }
  };

  const stats = useMemo(() => {
    const enrolled   = courses.reduce((s, c) => s + (c.enrollmentCount ?? 0), 0);
    const completed  = courses.reduce((s, c) => s + (c.completedCount  ?? 0), 0);
    const inProgress = courses.reduce((s, c) => s + (c.inProgressCount ?? 0), 0);
    const certs      = courses.reduce((s, c) => s + (c.completedCount  ?? 0), 0);
    return { enrolled, completed, inProgress, certs };
  }, [courses]);

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
      <CircularProgress />
    </Box>
  );

  return (
    <Box>
      {/* Summary stat cards */}
      <Stack direction="row" spacing={2} sx={{ mb: 3 }} flexWrap="wrap" useFlexGap>
        <StatCard icon={<SchoolIcon />}         label="Total Courses"   value={courses.length}    color="#6366f1" />
        <StatCard icon={<PeopleIcon />}         label="Enrollments"     value={stats.enrolled}    color="#14b8a6" />
        <StatCard icon={<TrendingUpIcon />}     label="In Progress"     value={stats.inProgress}  color="#f59e0b" />
        <StatCard icon={<CheckCircleIcon />}    label="Completed"       value={stats.completed}   color="#10b981" />
        <StatCard icon={<EmojiEventsIcon />}    label="Certificates"    value={stats.certs}       color="#f97316" />
      </Stack>

      {/* Courses table */}
      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                <TableCell sx={hdrCell}>Course Title</TableCell>
                <TableCell sx={hdrCell}>Duration</TableCell>
                <TableCell sx={hdrCell} align="center">Assigned</TableCell>
                <TableCell sx={hdrCell} align="center">In Progress</TableCell>
                <TableCell sx={hdrCell} align="center">Completed</TableCell>
                <TableCell sx={hdrCell}>Completion %</TableCell>
                <TableCell sx={hdrCell} align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {courses.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                    No courses found
                  </TableCell>
                </TableRow>
              ) : courses.slice(coursePage * courseRpp, (coursePage + 1) * courseRpp).map((course, i) => {
                const enrolled  = course.enrollmentCount ?? 0;
                const completed = course.completedCount  ?? 0;
                const inProg    = course.inProgressCount ?? 0;
                const pct       = enrolled > 0 ? Math.round((completed / enrolled) * 100) : 0;
                const names     = course.enrolledEmployeeNames || [];
                const SHOW_MAX  = 3;
                const isExpanded = expandedRows.has(course.id);
                const learners   = learnersMap[course.id] || [];
                const lLoading   = learnersLoading[course.id];

                const statusMeta = (s) => {
                  if (s === 'COMPLETED')   return { label: 'Completed',   color: '#10b981', bg: '#dcfce7' };
                  if (s === 'IN_PROGRESS') return { label: 'In Progress', color: '#f59e0b', bg: '#fef3c7' };
                  if (s === 'FAILED')      return { label: 'Failed',      color: '#ef4444', bg: '#fee2e2' };
                  return                          { label: 'Not Started', color: '#64748b', bg: '#f1f5f9' };
                };

                return (
                  <React.Fragment key={course.id}>
                    {/* ── Main row ── */}
                    <TableRow hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc', cursor: 'pointer' }}>
                      <TableCell sx={cell}>
                        <Typography variant="body2" fontWeight={600} sx={{ fontSize: 12.5 }}>{course.title}</Typography>
                        {course.description && (
                          <Typography variant="caption" color="text.secondary" noWrap
                            sx={{ fontSize: 11, display: 'block', maxWidth: 280 }}>{course.description}</Typography>
                        )}
                      </TableCell>
                      <TableCell sx={cell}>
                        {course.durationHours
                          ? <Chip label={`${course.durationHours}h`} size="small" variant="outlined" sx={{ fontSize: 11 }} />
                          : '—'}
                      </TableCell>
                      <TableCell align="center" sx={cell}>
                        {enrolled > 0 ? <Chip label={enrolled} size="small" color="primary" sx={{ fontSize: 11 }} />
                          : <Typography variant="caption" color="text.secondary">0</Typography>}
                      </TableCell>
                      <TableCell align="center" sx={cell}>
                        {inProg > 0 ? <Chip label={inProg} size="small" color="warning" sx={{ fontSize: 11 }} />
                          : <Typography variant="caption" color="text.secondary">0</Typography>}
                      </TableCell>
                      <TableCell align="center" sx={cell}>
                        {completed > 0 ? <Chip label={completed} size="small" color="success" sx={{ fontSize: 11 }} />
                          : <Typography variant="caption" color="text.secondary">0</Typography>}
                      </TableCell>
                      <TableCell sx={{ ...cell, minWidth: 120 }}>
                        <Box>
                          <LinearProgress variant="determinate" value={pct}
                            sx={{ height: 6, borderRadius: 3, bgcolor: '#e2e8f0',
                              '& .MuiLinearProgress-bar': { bgcolor: pct >= 80 ? '#10b981' : pct >= 40 ? '#f59e0b' : '#6366f1' } }} />
                          <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>{pct}%</Typography>
                        </Box>
                      </TableCell>
                      <TableCell align="center" sx={cell}>
                        <Tooltip title="View Learners">
                          <IconButton size="small" onClick={() => toggleExpand(course.id)}
                            sx={{ color: '#6366f1', '&:hover': { bgcolor: '#eef2ff' } }}>
                            <VisibilityIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>

                    {/* ── Expanded learner detail row ── */}
                    <TableRow>
                      <TableCell colSpan={7} sx={{ p: 0, borderBottom: isExpanded ? '1px solid #e2e8f0' : 'none' }}>
                        <Collapse in={isExpanded} timeout="auto" unmountOnExit>
                          <Box sx={{ px: 3, py: 2, bgcolor: '#f8faff', borderLeft: '3px solid #6366f1' }}>
                            <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700, color: '#3730a3', fontSize: 12.5 }}>
                              Learner Progress — {course.title}
                            </Typography>
                            {lLoading ? (
                              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 1 }}>
                                <CircularProgress size={16} /> <Typography variant="caption">Loading...</Typography>
                              </Box>
                            ) : learners.length === 0 ? (
                              <Typography variant="caption" color="text.secondary">No learners enrolled.</Typography>
                            ) : (
                              <Table size="small" sx={{ bgcolor: 'white', borderRadius: 1, overflow: 'hidden',
                                '& .MuiTableCell-root': { fontSize: 12, py: '6px' } }}>
                                <TableHead>
                                  <TableRow sx={{ bgcolor: '#eef2ff' }}>
                                    <TableCell sx={{ fontWeight: 700, color: '#3730a3', fontSize: 11.5 }}>Employee</TableCell>
                                    <TableCell sx={{ fontWeight: 700, color: '#3730a3', fontSize: 11.5 }}>Status</TableCell>
                                    <TableCell sx={{ fontWeight: 700, color: '#3730a3', fontSize: 11.5, minWidth: 140 }}>Progress</TableCell>
                                    <TableCell sx={{ fontWeight: 700, color: '#3730a3', fontSize: 11.5 }}>Completion Date</TableCell>
                                    <TableCell sx={{ fontWeight: 700, color: '#3730a3', fontSize: 11.5 }} align="center">Certificate</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {learners.map((l, idx) => {
                                    const sm = statusMeta(l.status);
                                    return (
                                      <TableRow key={l.employeeId} hover>
                                        <TableCell>
                                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                            <Avatar sx={{ width: 24, height: 24, fontSize: 10,
                                              bgcolor: '#6366f1', color: 'white' }}>
                                              {l.employeeName?.charAt(0)}
                                            </Avatar>
                                            <Box>
                                              <Typography sx={{ fontSize: 12, fontWeight: 600, lineHeight: 1.2 }}>
                                                {l.employeeName}
                                              </Typography>
                                            </Box>
                                          </Box>
                                        </TableCell>
                                        <TableCell>
                                          <Chip label={sm.label} size="small"
                                            sx={{ fontSize: 10, height: 20, fontWeight: 600,
                                              bgcolor: sm.bg, color: sm.color, border: 'none' }} />
                                        </TableCell>
                                        <TableCell>
                                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                            <LinearProgress variant="determinate" value={l.progressPercent}
                                              sx={{ flex: 1, height: 5, borderRadius: 3, bgcolor: '#e2e8f0',
                                                '& .MuiLinearProgress-bar': {
                                                  bgcolor: l.status === 'COMPLETED' ? '#10b981'
                                                         : l.status === 'IN_PROGRESS' ? '#f59e0b' : '#cbd5e1'
                                                }
                                              }} />
                                            <Typography sx={{ fontSize: 10.5, color: '#64748b', minWidth: 28 }}>
                                              {l.progressPercent}%
                                            </Typography>
                                          </Box>
                                        </TableCell>
                                        <TableCell sx={{ color: '#64748b' }}>
                                          {l.completionDate || '—'}
                                        </TableCell>
                                        <TableCell align="center">
                                          {l.certificateNumber ? (
                                            <Tooltip title="Download Certificate">
                                              <IconButton size="small"
                                                onClick={() => downloadCert(l.certificateNumber, l.employeeName)}
                                                sx={{ color: '#10b981', '&:hover': { bgcolor: '#f0fdf4' } }}>
                                                <DownloadIcon sx={{ fontSize: 16 }} />
                                              </IconButton>
                                            </Tooltip>
                                          ) : (
                                            <Typography variant="caption" color="text.disabled">—</Typography>
                                          )}
                                        </TableCell>
                                      </TableRow>
                                    );
                                  })}
                                </TableBody>
                              </Table>
                            )}
                          </Box>
                        </Collapse>
                      </TableCell>
                    </TableRow>
                  </React.Fragment>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={courses.length}
          page={coursePage}
          onPageChange={(_, p) => setCoursePage(p)}
          rowsPerPage={courseRpp}
          onRowsPerPageChange={(e) => { setCourseRpp(parseInt(e.target.value, 10)); setCoursePage(0); }}
          rowsPerPageOptions={[10, 25, 50]}
        />
      </Card>
    </Box>
  );
};

// ── DepartmentTreeTab ─────────────────────────────────────────────────────────
const DEPT_COLORS = [
  { bg: '#ede9fe', text: '#7c3aed', border: '#c4b5fd' },
  { bg: '#dbeafe', text: '#1d4ed8', border: '#93c5fd' },
  { bg: '#dcfce7', text: '#15803d', border: '#86efac' },
  { bg: '#fef9c3', text: '#a16207', border: '#fde047' },
  { bg: '#ffe4e6', text: '#be123c', border: '#fda4af' },
  { bg: '#e0f2fe', text: '#0369a1', border: '#7dd3fc' },
];

const DT_ROW_H   = 80;
const DT_CARD_H  = 68;
const DT_CARD_W  = 290;
const DT_EMP_W   = 260;
const DT_CONN_W  = 40;
const DT_BRANCH  = 24;
const DT_BLUE    = '#14b8a6';

const DepartmentTreeTab = ({ employees, onNavigate }) => { // eslint-disable-line no-unused-vars
  const activeEmps = useMemo(() => employees.filter((e) => e.active), [employees]);

  const deptMap = useMemo(() => {
    const map = new Map();
    activeEmps.forEach((emp) => {
      const dept = emp.department || 'Unassigned';
      if (!map.has(dept)) map.set(dept, []);
      map.get(dept).push(emp);
    });
    return map;
  }, [activeEmps]);

  const departments = useMemo(() => [...deptMap.keys()].sort(), [deptMap]);
  const [selectedDept, setSelectedDept] = useState(null);

  useEffect(() => {
    if (departments.length > 0 && selectedDept === null) setSelectedDept(departments[0]);
  }, [departments, selectedDept]);

  const selIdx  = selectedDept ? departments.indexOf(selectedDept) : -1;
  const deptEmps = useMemo(
    () => (selectedDept ? (deptMap.get(selectedDept) || []) : []),
    [deptMap, selectedDept],
  );

  const abbr = (name) => name.split(' ').map((w) => w[0]).join('').substring(0, 2).toUpperCase();

  return (
    <Box sx={{ overflowX: 'auto', overflowY: 'auto', maxHeight: 580, pb: 1 }}>
      <Box sx={{ display: 'inline-flex', alignItems: 'flex-start', minWidth: 'max-content' }}>

        {/* ── Left: department list ── */}
        <Box sx={{ flexShrink: 0 }}>
          {departments.map((dept, idx) => {
            const count = deptMap.get(dept)?.length || 0;
            const isSel = selectedDept === dept;
            const color = DEPT_COLORS[idx % DEPT_COLORS.length];
            return (
              <Box key={dept} sx={{ height: DT_ROW_H, display: 'flex', alignItems: 'center', gap: 1 }}>
                <Box
                  onClick={() => setSelectedDept(dept)}
                  sx={{
                    width: DT_CARD_W, height: DT_CARD_H, flexShrink: 0,
                    display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5,
                    border: isSel ? `1.5px solid ${DT_BLUE}` : '1px solid #e2e8f0',
                    borderRadius: '10px',
                    bgcolor: isSel ? '#f0fdfb' : 'white',
                    cursor: 'pointer', transition: 'all 0.15s',
                    boxShadow: isSel ? '0 2px 8px rgba(20,184,166,0.12)' : '0 1px 3px rgba(0,0,0,0.05)',
                    '&:hover': { borderColor: DT_BLUE, bgcolor: '#f0fdfb' },
                  }}
                >
                  <Box sx={{
                    width: 34, height: 34, borderRadius: 1.5, flexShrink: 0,
                    bgcolor: isSel ? 'rgba(20,184,166,0.15)' : color.bg,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontWeight: 800, fontSize: 13, color: isSel ? DT_BLUE : color.text,
                  }}>
                    {abbr(dept)}
                  </Box>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography fontWeight={600} fontSize={13.5} noWrap sx={{ color: isSel ? DT_BLUE : '#1e293b' }}>
                      {dept}
                    </Typography>
                    <Typography fontSize={11} color="text.disabled">-</Typography>
                  </Box>
                </Box>
                {/* Count badge outside the card */}
                <Box sx={{
                  minWidth: 28, height: 22, px: '6px', flexShrink: 0,
                  bgcolor: isSel ? DT_BLUE : '#e2e8f0',
                  color: isSel ? 'white' : '#475569',
                  borderRadius: '5px', fontSize: 11, fontWeight: 700,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  {count}
                </Box>
              </Box>
            );
          })}
        </Box>

        {/* ── Horizontal connector → employee column ── */}
        {selectedDept && deptEmps.length > 0 && (
          <Box sx={{
            width: DT_CONN_W, height: 2, bgcolor: DT_BLUE, flexShrink: 0,
            alignSelf: 'flex-start',
            mt: `${selIdx * DT_ROW_H + DT_ROW_H / 2 - 1}px`,
          }} />
        )}

        {/* ── Employee column (offset to align with selected dept row) ── */}
        {selectedDept && deptEmps.length > 0 && (
          <Box sx={{ position: 'relative', flexShrink: 0, alignSelf: 'flex-start', mt: `${selIdx * DT_ROW_H}px` }}>
            {deptEmps.length > 1 && (
              <Box sx={{
                position: 'absolute', zIndex: 0,
                left: 0,
                top: `${DT_ROW_H / 2}px`,
                bottom: `${DT_ROW_H / 2}px`,
                width: 2, bgcolor: '#cbd5e1',
              }} />
            )}
            {deptEmps.map((emp) => (
              <Box key={emp.id} sx={{ height: DT_ROW_H, display: 'flex', alignItems: 'center', position: 'relative', zIndex: 1 }}>
                <Box sx={{ width: DT_BRANCH, height: 2, bgcolor: '#cbd5e1', flexShrink: 0 }} />
                <Box
                  onClick={onNavigate ? () => onNavigate(emp.id) : undefined}
                  sx={{
                    width: DT_EMP_W, height: DT_CARD_H, flexShrink: 0,
                    display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5,
                    border: '1px solid #e2e8f0', borderRadius: '10px', bgcolor: 'white',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                    cursor: onNavigate ? 'pointer' : 'default', transition: 'all 0.15s',
                    '&:hover': onNavigate ? { borderColor: DT_BLUE, bgcolor: '#f0fdfb', boxShadow: '0 2px 8px rgba(20,184,166,0.12)' } : {},
                  }}
                >
                  <Avatar src={emp.photoUrl || emp.profileImageUrl}
                    sx={{ width: 40, height: 40, bgcolor: '#94a3b8', fontSize: '0.9rem', flexShrink: 0 }}>
                    {emp.firstName?.charAt(0)}
                  </Avatar>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography fontWeight={700} fontSize={13} noWrap>{emp.fullName}</Typography>
                    <Typography fontSize={11} color="text.secondary" noWrap>{emp.position || emp.role || '—'}</Typography>
                  </Box>
                </Box>
              </Box>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
};

// ── Main EmployeesPage ────────────────────────────────────────────────────────
const EmployeesPage = () => {
  const { user } = useSelector((s) => s.auth);
  const navigate = useNavigate();

  // ── Employee list state ───────────────────────────────────────────────────
  const [employees,     setEmployees]     = useState([]);
  const [exEmployees,   setExEmployees]   = useState([]);
  const [loading,       setLoading]       = useState(true);
  const [search,        setSearch]        = useState('');
  const [roleFilter,     setRoleFilter]    = useState('ALL');
  const [locationFilter, setLocationFilter]= useState('ALL');
  const [addOpen,       setAddOpen]       = useState(false);
  const [form,          setForm]          = useState(EMPTY_FORM);
  const [toggleTarget,  setToggleTarget]  = useState(null);
  const [resumeParsing, setResumeParsing] = useState(false);
  const [parsedData,    setParsedData]    = useState(null);
  const [saving,        setSaving]        = useState(false);

  // ── Pagination ────────────────────────────────────────────────────────────
  const [empPage, setEmpPage] = useState(0);
  const [empRpp,  setEmpRpp]  = useState(10);
  const [exPage,  setExPage]  = useState(0);
  const [exRpp,   setExRpp]   = useState(10);

  // ── Active tab ────────────────────────────────────────────────────────────
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab    = parseInt(searchParams.get('tab') || '0', 10);
  const setActiveTab = (v) => setSearchParams({ tab: v }, { replace: false });

  // ── Managed lists ─────────────────────────────────────────────────────────
  const [departments, setDepartments] = useState(INITIAL_DEPARTMENTS);
  const [positions,   setPositions]   = useState(INITIAL_POSITIONS);
  const [locations,   setLocations]   = useState(INITIAL_LOCATIONS);
  const [manageOpen,  setManageOpen]  = useState(null);

  const MANAGE_CONFIG = {
    dept: { label: 'Departments', items: departments, setItems: setDepartments },
    pos:  { label: 'Positions',   items: positions,   setItems: setPositions   },
    loc:  { label: 'Locations',   items: locations,   setItems: setLocations   },
  };

  const isManagerRole = user?.role === 'MANAGER' || user?.role === 'ASSISTANT_MANAGER';

  const fetchEmployees = () => {
    setLoading(true);
    const activeRequest = isManagerRole && user?.employeeId
      ? employeeApi.getTeam(user.employeeId)
      : employeeApi.getAll();
    const exRequest = (isAdmin || (empPerms?.canExEmployees)) ? employeeApi.getExEmployees() : Promise.resolve([]);
    activeRequest
      .then(setEmployees)
      .catch(() => toast.error('Failed to load employees'))
      .finally(() => setLoading(false));
    exRequest
      .then(setExEmployees)
      .catch(() => setExEmployees([]));
  };

  useEffect(() => { fetchEmployees(); }, [user?.role, user?.employeeId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Patch the employee list in-place when a profile is saved from EmployeeDetail
  useEffect(() => {
    const handler = ({ detail: updated }) => {
      setEmployees(prev => prev.map(emp => emp.id === updated.id ? updated : emp));
      setExEmployees(prev => prev.map(emp => emp.id === updated.id ? updated : emp));
    };
    window.addEventListener('employee-updated', handler);
    return () => window.removeEventListener('employee-updated', handler);
  }, []);

  // ── Filter ────────────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return employees.filter((emp) => {
      const matchSearch = !q || [
        emp.firstName, emp.lastName, emp.fullName,
        emp.email, emp.department, emp.position, emp.employeeCode, emp.employmentType,
      ].some((v) => v?.toLowerCase().includes(q));
      const matchRole     = roleFilter     === 'ALL' || emp.role            === roleFilter;
      const matchLocation = locationFilter === 'ALL' || emp.seatingLocation === locationFilter;
      return matchSearch && matchRole && matchLocation;
    });
  }, [employees, search, roleFilter, locationFilter]);

  // exEmployees loaded from dedicated /api/employees/ex endpoint

  useEffect(() => { setEmpPage(0); }, [search, roleFilter, locationFilter]);

  // ── Manage list handlers ──────────────────────────────────────────────────
  const getManageConfig  = () => manageOpen ? MANAGE_CONFIG[manageOpen] : null;
  const handleManageAdd  = (val) => { const c = getManageConfig(); if (c) c.setItems((p) => [...p, val]); };
  const handleManageEdit = (idx, val) => { const c = getManageConfig(); if (c) c.setItems((p) => p.map((item, i) => (i === idx ? val : item))); };
  const handleManageDel  = (idx) => { const c = getManageConfig(); if (c) c.setItems((p) => p.filter((_, i) => i !== idx)); };

  // ── Add ───────────────────────────────────────────────────────────────────
  const handleAdd = async () => {
    if (!form.firstName?.trim()) { toast.error('First name is required'); return; }
    if (!form.lastName?.trim())  { toast.error('Last name is required');  return; }
    if (!form.email?.trim())     { toast.error('Work email is required'); return; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { toast.error('Invalid email format'); return; }
    if (!form.password?.trim())  { toast.error('Password is required — please set a login password'); return; }
    if (form.password.length < 8) { toast.error('Password must be at least 8 characters'); return; }
    if (form.phone && !/^[+]?[0-9]{7,15}$/.test(form.phone.trim())) { toast.error('Invalid phone number format'); return; }
    setSaving(true);
    try {
      await employeeApi.create({ ...form, managerId: form.managerId || null });
      toast.success('Employee added successfully');
      setAddOpen(false);
      setForm(EMPTY_FORM);
      setParsedData(null);
      fetchEmployees();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to add employee');
    } finally {
      setSaving(false);
    }
  };

  // ── Toggle status ─────────────────────────────────────────────────────────
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

  // ── Resume upload ─────────────────────────────────────────────────────────
  const handleResumeUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';
    setResumeParsing(true);
    try {
      const parsed = await resumeApi.parse(file);
      // Password is never in a resume — always flag it for manual entry
      setParsedData({
        ...parsed,
        reviewFields:   [...(parsed.reviewFields  || []), 'password'],
        reviewMessages: [...(parsed.reviewMessages || []),
          'Password is required — set a login password in Basic Info tab'],
      });
      setForm({
        ...EMPTY_FORM,
        firstName:         parsed.firstName         || '',
        lastName:          parsed.lastName          || '',
        email:             parsed.email             || '',
        phone:             parsed.phone             || '',
        personalEmail:     parsed.personalEmail     || '',
        position:          parsed.position          || '',
        totalExperience:   parsed.totalExperience   || '',
        currentExperience: parsed.currentExperience || '',
        skills:            parsed.skills            || '',
        experience:        parsed.experience        || '',
        seatingLocation:   parsed.seatingLocation   || '',
        presentAddress:    parsed.presentAddress    || '',
        dateOfBirth:       parsed.dateOfBirth       || '',
        gender:            parsed.gender            || '',
        maritalStatus:     parsed.maritalStatus     || '',
      });
      setAddOpen(true);
      const score = parsed.confidenceScore ?? 0;
      const level = score >= 70 ? 'High' : score >= 40 ? 'Medium' : 'Low';
      toast.success(`Resume parsed — ${score}% confidence (${level}). Review highlighted fields before saving.`);
    } catch {
      toast.error('Failed to parse resume. Check the file format and try again.');
    } finally {
      setResumeParsing(false);
    }
  };

  const manageConfig = getManageConfig();
  const isAdmin = user?.role === 'ADMIN' || user?.role === 'DIRECTOR';

  // ── Granular employee-action permissions ──────────────────────────────────
  // If allowedModules is stored, use the explicit emp:* keys.
  // Otherwise fall back to role-based defaults.
  const empPerms = (() => {
    if (!user?.allowedModules) return null;
    try {
      const list = JSON.parse(user.allowedModules);
      return {
        canAdd:         list.includes('emp:add'),
        canUpload:      list.includes('emp:upload_resume'),
        canViewDetail:  list.includes('emp:view_detail'),
        canEdit:        list.includes('emp:edit_profile'),
        canExEmployees: list.includes('emp:ex_employees'),
      };
    } catch { return null; }
  })();

  const canAddEmployee   = empPerms ? empPerms.canAdd         : isAdmin;
  const canUploadResume  = empPerms ? empPerms.canUpload       : isAdmin;
  const canExEmployees   = empPerms ? empPerms.canExEmployees  : isAdmin;


  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <Box>
      {/* ── Tab bar ── */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
          <Tab icon={<PeopleIcon sx={{ fontSize: 18 }} />} iconPosition="start" label="Employees"    sx={{ minHeight: 48, gap: 0.5, textTransform: 'none', fontWeight: 600 }} />
          <Tab icon={<AccessTimeIcon sx={{ fontSize: 18 }} />} iconPosition="start" label="Timesheet"    sx={{ minHeight: 48, gap: 0.5, textTransform: 'none', fontWeight: 600 }} />
          <Tab icon={<TrendingUpIcon sx={{ fontSize: 18 }} />} iconPosition="start" label="Performance"  sx={{ minHeight: 48, gap: 0.5, textTransform: 'none', fontWeight: 600 }} />
          <Tab icon={<SchoolIcon sx={{ fontSize: 18 }} />} iconPosition="start" label="Courses"          sx={{ minHeight: 48, gap: 0.5, textTransform: 'none', fontWeight: 600 }} />
          {canExEmployees && <Tab icon={<PersonRemoveIcon sx={{ fontSize: 18 }} />} iconPosition="start" label="Ex-Employees" sx={{ minHeight: 48, gap: 0.5, textTransform: 'none', fontWeight: 600 }} />}
        </Tabs>
        {activeTab === 0 && (canAddEmployee || canUploadResume) && (
          <Stack direction="row" spacing={1.5} sx={{ flexShrink: 0, pb: 0.5 }}>
            {canUploadResume && (
              <>
                <input
                  id="resume-upload-input"
                  type="file" accept=".pdf,.docx"
                  style={{ display: 'none' }}
                  onChange={handleResumeUpload}
                />
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={resumeParsing ? <CircularProgress size={14} /> : <UploadFileIcon />}
                  disabled={resumeParsing}
                  onClick={() => document.getElementById('resume-upload-input').click()}
                >
                  {resumeParsing ? 'Reading…' : 'Upload Resume'}
                </Button>
              </>
            )}
            {canAddEmployee && (
              <Button variant="contained" size="small" startIcon={<AddIcon />}
                onClick={() => { setForm(EMPTY_FORM); setAddOpen(true); }}>
                Add Employee
              </Button>
            )}
          </Stack>
        )}
      </Box>

      {/* ── Tab 0: Employees ── */}
      {activeTab === 0 && (
        <>
          {/* Search + filter bar */}
          <Card sx={{ mb: 2 }}>
            <CardContent sx={{ py: 2 }}>
              <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
                <TextField
                  placeholder="Search by name, code, department..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  size="small" sx={{ width: 300 }}
                  InputProps={{ startAdornment: (
                    <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment>
                  )}}
                />
                <FormControl size="small" sx={{ minWidth: 130 }}>
                  <InputLabel>Role</InputLabel>
                  <Select value={roleFilter} label="Role" onChange={(e) => setRoleFilter(e.target.value)}>
                    <MenuItem value="ALL">All Roles</MenuItem>
                    <MenuItem value="ADMIN">Admin</MenuItem>
                    <MenuItem value="MANAGER">Manager</MenuItem>
                    <MenuItem value="ASSISTANT_MANAGER">Assistant Manager</MenuItem>
                    <MenuItem value="HR">HR</MenuItem>
                    <MenuItem value="EMPLOYEE">Employee</MenuItem>
                  </Select>
                </FormControl>
                <FormControl size="small" sx={{ minWidth: 130 }}>
                  <InputLabel>Location</InputLabel>
                  <Select value={locationFilter} label="Location" onChange={(e) => setLocationFilter(e.target.value)}>
                    <MenuItem value="ALL">All Locations</MenuItem>
                    {locations.map((loc) => <MenuItem key={loc} value={loc}>{loc}</MenuItem>)}
                  </Select>
                </FormControl>
                {(search || roleFilter !== 'ALL' || locationFilter !== 'ALL') && (
                  <Button size="small" onClick={() => { setSearch(''); setRoleFilter('ALL'); setLocationFilter('ALL'); }}>
                    Clear
                  </Button>
                )}
                <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto !important' }}>
                  {filtered.length} of {employees.length} employees
                </Typography>
              </Stack>
            </CardContent>
          </Card>

          {/* Employee table */}
          <Card>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table size="small" sx={{ minWidth: 1300 }}>
                <TableHead>
                  <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                    <TableCell sx={{ ...stickyId,   ...hdrCell, bgcolor: '#e2e8f0', borderRight: '1px solid #cbd5e1' }}>Emp ID</TableCell>
                    <TableCell sx={{ ...stickyName, ...hdrCell, bgcolor: '#e2e8f0', borderRight: '1px solid #cbd5e1' }}>Employee Name</TableCell>
                    <TableCell sx={{ ...stickyLoc,  ...hdrCell, bgcolor: '#e2e8f0', borderRight: '1px solid #cbd5e1' }}>Location</TableCell>
                    <TableCell sx={hdrCell}>Department</TableCell>
                    <TableCell sx={hdrCell}>Position</TableCell>
                    <TableCell sx={hdrCell}>Role</TableCell>
                    <TableCell sx={hdrCell}>Manager</TableCell>
                    <TableCell sx={hdrCell}>Current Exp</TableCell>
                    <TableCell sx={hdrCell}>Total Exp</TableCell>
                    <TableCell sx={hdrCell}>Employment Type</TableCell>
                    <TableCell sx={hdrCell}>Source of Hire</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loading ? (
                    <TableRow>
                      <TableCell colSpan={11} align="center" sx={{ py: 4 }}>
                        <CircularProgress />
                      </TableCell>
                    </TableRow>
                  ) : filtered.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={11} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                        No employees found
                      </TableCell>
                    </TableRow>
                  ) : filtered.slice(empPage * empRpp, (empPage + 1) * empRpp).map((emp) => {
                    const viewerIsAdmin   = user?.role === 'ADMIN' || user?.role === 'DIRECTOR';
                    const viewerIsManager = user?.role === 'MANAGER' || user?.role === 'ASSISTANT_MANAGER';
                    const isDirectReport  = emp.managerId === user?.employeeId;
                    const canClick        = empPerms
                      ? (empPerms.canViewDetail || viewerIsAdmin || (viewerIsManager && isDirectReport))
                      : (viewerIsAdmin || (viewerIsManager && isDirectReport));

                    return (
                      <TableRow key={emp.id} hover
                        onClick={canClick ? () => navigate(`/employees/${emp.id}`) : undefined}
                        sx={canClick ? { cursor: 'pointer' } : {}}>
                        <TableCell sx={{ ...stickyId,   ...cell, bgcolor: 'white', borderRight: '1px solid #e2e8f0' }}>
                          <Typography sx={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 13, color: '#1d4ed8' }}>
                            {emp.employeeCode || '—'}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ ...stickyName, ...cell, bgcolor: 'white', borderRight: '1px solid #e2e8f0' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Avatar sx={{ width: 30, height: 30, bgcolor: '#1976d2', fontSize: '0.78rem', flexShrink: 0 }}>
                              {emp.firstName?.charAt(0)}
                            </Avatar>
                            <Typography variant="body2" fontWeight={600} noWrap
                              sx={canClick ? { color: 'primary.main' } : {}}>
                              {emp.fullName}
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell sx={{ ...stickyLoc, ...cell, bgcolor: 'white', borderRight: '1px solid #e2e8f0' }}>
                          {emp.seatingLocation || '—'}
                        </TableCell>
                        <TableCell sx={cell}>{emp.department || '—'}</TableCell>
                        <TableCell sx={cell}>{emp.position || '—'}</TableCell>
                        <TableCell sx={cell}>
                          <Chip
                            label={emp.role}
                            size="small"
                            color={roleCustomColors[emp.role] ? undefined : (roleColors[emp.role] || 'default')}
                            sx={roleCustomColors[emp.role] ? { bgcolor: roleCustomColors[emp.role], color: '#fff', fontWeight: 600 } : {}}
                          />
                        </TableCell>
                        <TableCell sx={cell}>{emp.managerName || '—'}</TableCell>
                        <TableCell sx={cell}>{emp.currentExperience || '—'}</TableCell>
                        <TableCell sx={cell}>{emp.totalExperience || '—'}</TableCell>
                        <TableCell sx={cell}>
                          {emp.employmentType
                            ? <Chip label={emp.employmentType} size="small" variant="outlined" sx={{ fontSize: 11 }} />
                            : '—'}
                        </TableCell>
                        <TableCell sx={cell}>{emp.sourceOfHire || '—'}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={filtered.length}
              page={empPage}
              onPageChange={(_, p) => setEmpPage(p)}
              rowsPerPage={empRpp}
              onRowsPerPageChange={(e) => { setEmpRpp(parseInt(e.target.value, 10)); setEmpPage(0); }}
              rowsPerPageOptions={[10, 25, 50]}
            />
          </Card>
        </>
      )}

      {/* ── Tab 1: Timesheet ── */}
      {activeTab === 1 && <TimesheetTab employees={employees} user={user} />}

      {/* ── Tab 2: Performance ── */}
      {activeTab === 2 && <PerformanceTab employees={employees} />}

      {/* ── Tab 3: Courses ── */}
      {activeTab === 3 && <CoursesTab user={user} />}

      {/* ── Tab 4: Ex-Employees ── */}
      {activeTab === 4 && (
        <Card>
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small" sx={{ minWidth: 900 }}>
              <TableHead>
                <TableRow sx={{ bgcolor: '#fef2f2' }}>
                  <TableCell sx={{ ...hdrCell, color: '#991b1b' }}>Emp ID</TableCell>
                  <TableCell sx={{ ...hdrCell, color: '#991b1b' }}>Employee Name</TableCell>
                  <TableCell sx={{ ...hdrCell, color: '#991b1b' }}>Department</TableCell>
                  <TableCell sx={{ ...hdrCell, color: '#991b1b' }}>Position</TableCell>
                  <TableCell sx={{ ...hdrCell, color: '#991b1b' }}>Employment Type</TableCell>
                  <TableCell sx={{ ...hdrCell, color: '#991b1b' }}>Date of Joining</TableCell>
                  <TableCell sx={{ ...hdrCell, color: '#991b1b' }}>Date of Exit</TableCell>
                  <TableCell sx={{ ...hdrCell, color: '#991b1b' }}>Manager</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                      <CircularProgress />
                    </TableCell>
                  </TableRow>
                ) : exEmployees.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                      No ex-employees found
                    </TableCell>
                  </TableRow>
                ) : exEmployees.slice(exPage * exRpp, (exPage + 1) * exRpp).map((emp, i) => (
                  <TableRow
                    key={emp.id} hover
                    onClick={() => navigate(`/employees/${emp.id}`)}
                    sx={{ bgcolor: i % 2 === 0 ? 'white' : '#fff5f5', cursor: 'pointer' }}
                  >
                    <TableCell sx={cell}>
                      <Typography sx={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 13, color: '#dc2626' }}>
                        {emp.employeeCode || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell sx={cell}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Avatar sx={{ width: 28, height: 28, bgcolor: '#ef4444', fontSize: '0.72rem', flexShrink: 0 }}>
                          {emp.firstName?.charAt(0)}
                        </Avatar>
                        <Typography variant="body2" fontWeight={600} sx={{ fontSize: 12.5 }}>
                          {emp.fullName}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell sx={cell}>{emp.department || '—'}</TableCell>
                    <TableCell sx={cell}>{emp.position || '—'}</TableCell>
                    <TableCell sx={cell}>
                      {emp.employmentType
                        ? <Chip label={emp.employmentType} size="small" variant="outlined" sx={{ fontSize: 11 }} />
                        : '—'}
                    </TableCell>
                    <TableCell sx={cell}>{emp.hireDate ? String(emp.hireDate).slice(0, 10) : '—'}</TableCell>
                    <TableCell sx={cell}>
                      {emp.dateOfExit
                        ? <Chip label={String(emp.dateOfExit).slice(0, 10)} size="small"
                            sx={{ bgcolor: '#fee2e2', color: '#991b1b', fontSize: 11, fontFamily: 'monospace' }} />
                        : '—'}
                    </TableCell>
                    <TableCell sx={cell}>{emp.managerName || '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={exEmployees.length}
            page={exPage}
            onPageChange={(_, p) => setExPage(p)}
            rowsPerPage={exRpp}
            onRowsPerPageChange={(e) => { setExRpp(parseInt(e.target.value, 10)); setExPage(0); }}
            rowsPerPageOptions={[10, 25, 50]}
          />
        </Card>
      )}

      {/* ── Add Employee dialog ── */}
      <Dialog open={addOpen} onClose={() => { setAddOpen(false); setParsedData(null); }} maxWidth="md" fullWidth
        PaperProps={{ sx: { maxHeight: '90vh' } }}>
        <DialogTitle fontWeight={700}>Add New Employee</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <EmployeeForm
            values={form} setter={setForm} isEdit={false}
            employees={employees}
            departments={departments} positions={positions} locations={locations}
            onManage={(k) => setManageOpen(k)}
            parseResult={parsedData}
          />
        </DialogContent>
        <Divider />
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleAdd}
            disabled={saving
              || !form.firstName?.trim()
              || !form.lastName?.trim()
              || !form.email?.trim()
              || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email || '')
              || !form.password?.trim()
              || (form.password || '').length < 8}
            startIcon={saving ? <CircularProgress size={16} /> : null}>
            {saving ? 'Adding…' : 'Add Employee'}
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

      {/* ── Manage List dialog ── */}
      {manageConfig && (
        <ManageListDialog
          open={Boolean(manageOpen)}
          onClose={() => setManageOpen(null)}
          title={manageConfig.label}
          items={manageConfig.items}
          onAdd={handleManageAdd}
          onEdit={handleManageEdit}
          onDelete={handleManageDel}
        />
      )}
    </Box>
  );
};

export default EmployeesPage;
