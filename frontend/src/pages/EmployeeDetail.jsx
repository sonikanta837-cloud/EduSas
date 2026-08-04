import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { setUser, logout } from '../store/authSlice';
import {
  Box, Typography, Avatar, Chip, Button, CircularProgress,
  Tab, Tabs, Grid, Divider, TextField, Card, IconButton,
  Table, TableBody, TableCell, TableHead, TableRow, Alert,
  FormControl, InputLabel, Select, MenuItem, List, ListItem, ListItemText, Tooltip,
  Dialog, DialogTitle, DialogContent, DialogActions, InputAdornment,
} from '@mui/material';
import ArrowBackIcon        from '@mui/icons-material/ArrowBack';
import BlockIcon            from '@mui/icons-material/Block';
import LockIcon             from '@mui/icons-material/Lock';
import EditIcon             from '@mui/icons-material/Edit';
import SaveIcon             from '@mui/icons-material/Save';
import CancelIcon           from '@mui/icons-material/Cancel';
import DeleteIcon           from '@mui/icons-material/Delete';
import TuneIcon             from '@mui/icons-material/Tune';
import CheckIcon            from '@mui/icons-material/Check';
import CloseIcon            from '@mui/icons-material/Close';
import EmailIcon            from '@mui/icons-material/Email';
import PhoneIcon            from '@mui/icons-material/Phone';
import BusinessIcon         from '@mui/icons-material/Business';
import WorkIcon             from '@mui/icons-material/Work';
import BadgeIcon            from '@mui/icons-material/Badge';
import PersonIcon           from '@mui/icons-material/Person';
import GroupIcon            from '@mui/icons-material/Group';
import EventNoteIcon        from '@mui/icons-material/EventNote';
import StarIcon             from '@mui/icons-material/Star';
import SchoolIcon           from '@mui/icons-material/School';
import CalendarTodayIcon    from '@mui/icons-material/CalendarToday';
import AccessTimeIcon       from '@mui/icons-material/AccessTime';
import VisibilityIcon      from '@mui/icons-material/Visibility';
import VisibilityOffIcon   from '@mui/icons-material/VisibilityOff';
import SupportAgentIcon    from '@mui/icons-material/SupportAgent';
import { employeeApi }      from '../api/employeeApi';
import { performanceApi }   from '../api/performanceApi';
import { leaveApi }         from '../api/leaveApi';
import { jobSummaryApi }     from '../api/jobSummaryApi';
import { timesheetEntryApi } from '../api/timesheetEntryApi';
import { courseApi }         from '../api/courseApi';
import { toast }            from 'react-toastify';

// ── Attendance formatting helpers ───────────────────────────────────────────
const fmtAttTime = (iso) => {
  if (!iso) return '—';
  const d = new Date(iso);
  return isNaN(d) ? '—' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};
const fmtAttMinutes = (mins) => {
  if (mins == null) return '—';
  const h = Math.floor(mins / 60);
  const m = Math.round(mins % 60);
  return `${h}h ${String(m).padStart(2, '0')}m`;
};
const ATT_STATUS_STYLES = {
  PRESENT:     { label: 'Present',     color: '#16a34a', bg: '#dcfce7' },
  UNDER_HOURS: { label: 'Under Hours', color: '#c2410c', bg: '#ffedd5' },
  OVERTIME:    { label: 'Overtime',    color: '#7c3aed', bg: '#ede9fe' },
  ABSENT:      { label: 'Absent',      color: '#dc2626', bg: '#fee2e2' },
  LEAVE:       { label: 'Leave',       color: '#2563eb', bg: '#dbeafe' },
  HOLIDAY:     { label: 'Holiday',     color: '#7e22ce', bg: '#f3e8ff' },
  WEEKEND:     { label: 'Weekly Off',  color: '#b45309', bg: '#fef3c7' },
};

// ── ManageListDialog ──────────────────────────────────────────────────────────
const ManageListDialog = ({ open, onClose, title, items, onAdd, onEdit, onDelete }) => {
  const [newVal,  setNewVal]  = React.useState('');
  const [editIdx, setEditIdx] = React.useState(null);
  const [editVal, setEditVal] = React.useState('');

  const commitAdd = () => {
    const v = newVal.trim();
    if (!v || items.includes(v)) return;
    onAdd(v); setNewVal('');
  };
  const startEdit  = (i)  => { setEditIdx(i); setEditVal(items[i]); };
  const commitEdit = ()   => { if (editVal.trim()) { onEdit(editIdx, editVal.trim()); } setEditIdx(null); };
  const cancelEdit = ()   => setEditIdx(null);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth
      PaperProps={{ sx: { maxHeight: '80vh' } }}>
      <Box sx={{ px: 3, pt: 2.5, pb: 1 }}>
        <Typography fontWeight={700} fontSize={16}>Manage {title}</Typography>
      </Box>
      <Divider />
      <Box sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
          <TextField size="small" fullWidth placeholder={`New ${title}…`}
            value={newVal} onChange={(e) => setNewVal(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && commitAdd()} />
          <Button variant="contained" size="small" onClick={commitAdd}>Add</Button>
        </Box>
        <List dense disablePadding sx={{ maxHeight: 320, overflowY: 'auto' }}>
          {items.map((item, i) => (
            <ListItem key={i} disablePadding divider
              sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc', px: 1, py: 0.5 }}>
              {editIdx === i ? (
                <Box sx={{ display: 'flex', gap: 0.5, flex: 1, alignItems: 'center', py: 0.5 }}>
                  <TextField size="small" fullWidth autoFocus value={editVal}
                    onChange={(e) => setEditVal(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter') commitEdit(); if (e.key === 'Escape') cancelEdit(); }} />
                  <Tooltip title="Save"><IconButton size="small" color="primary" onClick={commitEdit}><CheckIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Cancel"><IconButton size="small" onClick={cancelEdit}><CloseIcon fontSize="small" /></IconButton></Tooltip>
                </Box>
              ) : (
                <>
                  <ListItemText primary={item} primaryTypographyProps={{ fontSize: 13.5 }} sx={{ flex: 1 }} />
                  <Tooltip title="Edit"><IconButton size="small" onClick={() => startEdit(i)}><EditIcon sx={{ fontSize: 15 }} /></IconButton></Tooltip>
                  <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => onDelete(i)}><DeleteIcon sx={{ fontSize: 15 }} /></IconButton></Tooltip>
                </>
              )}
            </ListItem>
          ))}
        </List>
      </Box>
      <Divider />
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', px: 2, py: 1.5 }}>
        <Button onClick={onClose}>Close</Button>
      </Box>
    </Dialog>
  );
};

// ── Palette ───────────────────────────────────────────────────────────────────
const PALETTE = [
  '#4f7bea','#e89b3f','#e05c5c','#5cb85c','#9b59b6',
  '#1abc9c','#e74c3c','#3498db','#f39c12','#16a085',
];
const avatarBg = (name) => PALETTE[(name?.charCodeAt(0) || 0) % PALETTE.length];
const initials  = (name) =>
  (name || '?').split(' ').map((n) => n[0]).join('').substring(0, 2).toUpperCase();

// ── Small info card ───────────────────────────────────────────────────────────
const InfoCard = ({ icon, label, value }) => (
  <Box sx={{
    display: 'flex', alignItems: 'center', gap: 2,
    p: 2, border: '1px solid #e9ecef', borderRadius: 2,
    bgcolor: 'white',
  }}>
    <Box sx={{
      width: 38, height: 38, borderRadius: 2,
      bgcolor: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }}>
      {React.cloneElement(icon, { sx: { fontSize: 19, color: '#64748b' } })}
    </Box>
    <Box>
      <Typography sx={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.2 }}>{label}</Typography>
      <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: '#1e293b', lineHeight: 1.4 }}>
        {value || '—'}
      </Typography>
    </Box>
  </Box>
);

// ── Role chip colours ─────────────────────────────────────────────────────────
const roleColor = { ADMIN: '#ef4444', DIRECTOR: '#4f46e5', MANAGER: '#f59e0b', ASSISTANT_MANAGER: '#f59e0b', HR: '#8b5cf6', EMPLOYEE: '#3b82f6' };

// ── Main component ────────────────────────────────────────────────────────────
const EmployeeDetailPage = () => {
  const { id }    = useParams();
  const navigate  = useNavigate();
  const dispatch  = useDispatch();
  const { user }  = useSelector((s) => s.auth);

  const [employee,     setEmployee]     = useState(null);
  const [myEmployee,   setMyEmployee]   = useState(null);
  const [loading,      setLoading]      = useState(true);
  const [accessDenied, setAccessDenied] = useState(false);
  const [editing,      setEditing]      = useState(false);
  const [form,         setForm]         = useState({});
  const [searchParams, setSearchParams] = useSearchParams();
  const tab    = searchParams.get('tab') || 'profile';
  const setTab = (v) => setSearchParams({ tab: v }, { replace: false });
  const [deptOptions,  setDeptOptions]  = useState(['Human Resource','Operation','Management','Marketing','IT']);
  const [posOptions,   setPosOptions]   = useState(['Accounts Trainee','Accounts Executive','Senior Accountant','Sr. Payroll Administrator','Assistant Manager','Manager','Business Development and Operation','HR','System Administrator']);
  const [locOptions,   setLocOptions]   = useState(['Mandsaur','Ahmedabad','Jamnagar']);
  const [manageOpen,   setManageOpen]   = useState(null);
  const [allEmployees, setAllEmployees] = useState([]);
  const [hrEmployees,  setHrEmployees]  = useState([]);

  const MANAGE_CFG = {
    dept: { label: 'Departments', items: deptOptions, setItems: setDeptOptions },
    pos:  { label: 'Positions',   items: posOptions,  setItems: setPosOptions  },
    loc:  { label: 'Locations',   items: locOptions,  setItems: setLocOptions  },
  };
  const mgCfg = manageOpen ? MANAGE_CFG[manageOpen] : null;

  // Data for each tab
  const [reportees,    setReportees]    = useState([]);
  const [leaves,       setLeaves]       = useState([]);
  const [reviews,      setReviews]      = useState([]);
  const [attendance,   setAttendance]   = useState([]);
  const [timesheets,   setTimesheets]   = useState([]);
  const [courses,      setCourses]      = useState([]);

  const isAdmin   = user?.role === 'ADMIN' || user?.role === 'DIRECTOR';
  const isHR      = user?.role === 'HR';
  const isManager = user?.role === 'MANAGER' || user?.role === 'ASSISTANT_MANAGER';

  const isOwnProfile    = myEmployee && parseInt(id) === myEmployee.id;
  const isTheirManager  = isManager && employee && myEmployee && employee.managerId === myEmployee.id;
  const canEdit         = isAdmin || isHR || isTheirManager || isOwnProfile;
  const canViewFull     = isAdmin || isHR || isOwnProfile || isTheirManager;
  const canViewCourses  = isAdmin || isHR || isOwnProfile || isTheirManager;

  // Load viewer's own employee record
  useEffect(() => {
    if (user?.userId) {
      employeeApi.getByUserId(user.userId).then(setMyEmployee).catch(() => {});
    }
  }, [user?.userId]);

  // Load all employees for manager dropdown (admin and HR)
  useEffect(() => {
    if (user?.role === 'ADMIN' || user?.role === 'DIRECTOR' || user?.role === 'HR') {
      employeeApi.getAll().then(setAllEmployees).catch(() => {});
      employeeApi.getHrUsers().then(setHrEmployees).catch(() => {});
    }
  }, [user?.role]);

  // Load employee
  useEffect(() => {
    setLoading(true);
    setAccessDenied(false);
    employeeApi.getById(id)
      .then((emp) => { setEmployee(emp); setForm({ ...emp, workEmail: emp.workEmail || emp.email }); })
      .catch((err) => {
        if (err.response?.status === 403) {
          setAccessDenied(true);
        } else {
          toast.error('Employee not found');
        }
      })
      .finally(() => setLoading(false));
  }, [id]);

  // Load tab data lazily
  useEffect(() => {
    if (!employee) return;
    if (tab === 'reportees') {
      if (employee.subordinateCount > 0) {
        employeeApi.getTeam(id).then(setReportees).catch(() => {});
      }
    }
    if (tab === 'courses') {
      if (canViewCourses) courseApi.getForEmployee(id).then(setCourses).catch(() => {});
      return;
    }
    if (!canViewFull) return;
    if (tab === 'leave')        leaveApi.getMyLeaves(id).then(setLeaves).catch(() => {});
    if (tab === 'performance')  performanceApi.getByEmployee(id).then(setReviews).catch(() => {});
    if (tab === 'attendance') {
      const end = new Date().toISOString().slice(0, 10);
      const start = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
      jobSummaryApi.getForEmployee(id, start, end).then((data) => {
        setAttendance(Array.isArray(data) ? [...data].reverse() : []);
      }).catch(() => {});
    }
    if (tab === 'timesheets') {
      const now = new Date();
      const yr  = now.getFullYear();
      const mo  = now.getMonth() + 1;          // 1-based current month
      const pmo = mo === 1 ? 12 : mo - 1;      // previous month
      const pyr = mo === 1 ? yr - 1 : yr;
      Promise.all([
        timesheetEntryApi.getMonthly(id, yr, mo),
        timesheetEntryApi.getMonthly(id, pyr, pmo),
      ]).then(([curr, prev]) => {
        const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
        const merged = [...curr, ...prev]
          .filter((e) => e.date >= cutoff)
          .sort((a, b) => b.date.localeCompare(a.date));
        setTimesheets(merged);
      }).catch(() => {});
      return;
    }
  }, [tab, employee, canViewFull, canViewCourses, id]); // eslint-disable-line


  const [confirmToggle, setConfirmToggle] = useState(false);
  const [showEditPassword, setShowEditPassword] = useState(false);

  const handleSave = async () => {
    if (!form.firstName?.trim()) { toast.error('First name is required'); return; }
    if (!form.lastName?.trim())  { toast.error('Last name is required'); return; }
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { toast.error('Invalid email format'); return; }
    if (form.phone && !/^[+]?[0-9]{7,15}$/.test(form.phone.trim())) { toast.error('Invalid phone number format'); return; }
    if (form.password && form.password.length < 8) { toast.error('New password must be at least 8 characters'); return; }
    try {
      const payload = {
        ...form,
        managerId: form.managerId ? Number(form.managerId) : null,
      };
      const updated = await employeeApi.update(id, payload);
      setEmployee(updated);
      setForm(updated);
      setEditing(false);
      toast.success('Profile updated successfully');

      // If the saved employee is the logged-in user, sync Redux + localStorage
      if (user && updated.userId === user.userId) {
        if (updated.email !== user.email) {
          // Login email changed — JWT is now invalid, must re-authenticate
          toast.info('Work email updated. Please log in again with your new email.', { autoClose: 5000 });
          dispatch(logout());
          navigate('/login');
          return;
        }
        const updatedUser = {
          ...user,
          fullName: updated.fullName,
          email:    updated.email,
          role:     updated.role,
        };
        dispatch(setUser(updatedUser));
        localStorage.setItem('user', JSON.stringify(updatedUser));
      }

      // Notify other pages (OrgChart) so they can patch stale state
      window.dispatchEvent(new CustomEvent('employee-updated', { detail: updated }));
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to update employee';
      toast.error(msg);
    }
  };

  const handleToggleStatus = async () => {
    try {
      await employeeApi.toggleStatus(id);
      const updated = await employeeApi.getById(id);
      setEmployee(updated);
      setConfirmToggle(false);
      toast.success(`Employee ${updated.active ? 'activated' : 'deactivated'} successfully`);
    } catch {
      toast.error('Failed to update status');
    }
  };

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
      <CircularProgress />
    </Box>
  );

  if (accessDenied) return (
    <Box sx={{
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', minHeight: 400, gap: 2, px: 3,
    }}>
      <Box sx={{
        width: 80, height: 80, borderRadius: '50%',
        bgcolor: '#fef2f2', display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <LockIcon sx={{ fontSize: 40, color: '#ef4444' }} />
      </Box>
      <Typography variant="h6" fontWeight={700} color="#0f172a">Access Restricted</Typography>
      <Typography color="text.secondary" textAlign="center" maxWidth={380}>
        You don't have permission to view this profile. Inactive employee profiles are accessible to admins only.
      </Typography>
      <Button
        variant="outlined" startIcon={<ArrowBackIcon />}
        onClick={() => navigate(-1)} sx={{ mt: 1 }}
      >
        Go Back
      </Button>
    </Box>
  );

  if (!employee) return <Typography>Employee not found</Typography>;

  const bg = avatarBg(employee.fullName);

  // Tabs definition
  const TABS = [
    { key: 'profile',     label: 'My Profile' },
    { key: 'reportees',   label: 'Reportees',   hidden: (employee.subordinateCount || 0) === 0 && !isAdmin },
    { key: 'leave',       label: 'Leave',        locked: !canViewFull },
    { key: 'attendance',  label: 'Attendance',   locked: !canViewFull },
    { key: 'timesheets',  label: 'Timesheets',   locked: !canViewFull },
    { key: 'performance', label: 'Performance',  locked: !canViewFull },
    { key: 'courses',     label: 'Courses',      locked: !canViewCourses },
  ].filter((t) => !t.hidden);

  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh', pb: 6 }}>

      {/* ── Back button ───────────────────────────────────────────────── */}
      <Box sx={{ px: { xs: 2, md: 4 }, pt: 2 }}>
        <IconButton onClick={() => navigate(-1)} sx={{ bgcolor: 'white', boxShadow: 1, '&:hover': { bgcolor: '#f1f5f9' } }}>
          <ArrowBackIcon fontSize="small" />
        </IconButton>
      </Box>

      {/* ── Inactive banner (admin viewing ex-employee) ──────────────── */}
      {!employee.active && isAdmin && (
        <Box sx={{
          mx: { xs: 2, md: 4 }, mt: 1.5,
          px: 2.5, py: 1.5,
          bgcolor: '#fef2f2', border: '1px solid #fecaca', borderRadius: 2,
          display: 'flex', alignItems: 'center', gap: 1.5,
        }}>
          <BlockIcon sx={{ color: '#dc2626', fontSize: 20 }} />
          <Typography sx={{ fontSize: 13.5, color: '#991b1b', fontWeight: 600 }}>
            Inactive Employee — Historical Record
          </Typography>
          <Typography sx={{ fontSize: 13, color: '#b91c1c', ml: 0.5 }}>
            This employee is no longer active. You are viewing their archived profile and historical data.
          </Typography>
        </Box>
      )}

      {/* ── Cover banner ──────────────────────────────────────────────── */}
      <Box sx={{
        height: 200, mx: { xs: 2, md: 4 }, mt: 1.5,
        borderRadius: 3,
        background: 'linear-gradient(135deg, #0f172a 0%, #1e3a5f 40%, #14532d 100%)',
        position: 'relative', overflow: 'hidden',
      }}>
        {/* decorative leaf pattern */}
        {[...Array(6)].map((_, i) => (
          <Box key={i} sx={{
            position: 'absolute',
            width: 80 + i * 20, height: 80 + i * 20,
            borderRadius: '50%',
            border: '1px solid rgba(255,255,255,0.06)',
            top: -20 + i * 15, right: -20 + i * 30,
          }} />
        ))}
      </Box>

      {/* ── Profile header ────────────────────────────────────────────── */}
      <Box sx={{ px: { xs: 2, md: 4 }, position: 'relative' }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 3, mt: -6 }}>
          {/* Avatar */}
          <Avatar
            sx={{
              width: 110, height: 110,
              bgcolor: bg,
              fontSize: '2.5rem', fontWeight: 700,
              border: '4px solid white',
              boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
              flexShrink: 0,
            }}
          >
            {initials(employee.fullName)}
          </Avatar>

          {/* Name + meta */}
          <Box sx={{ pb: 1, flex: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
              {employee.employeeCode && (
                <Typography sx={{ fontSize: 13, color: '#94a3b8', fontWeight: 500 }}>
                  {employee.employeeCode} ·
                </Typography>
              )}
              <Typography sx={{ fontSize: 20, fontWeight: 800, color: '#0f172a' }}>
                {employee.fullName}
              </Typography>
              <Chip
                label={employee.role}
                size="small"
                sx={{
                  bgcolor: roleColor[employee.role] + '18',
                  color: roleColor[employee.role],
                  fontWeight: 700, fontSize: 11,
                  border: `1px solid ${roleColor[employee.role]}33`,
                }}
              />
              {!employee.active && (
                <Chip label="Inactive" size="small" color="default" />
              )}
            </Box>
            <Typography sx={{ fontSize: 13.5, color: '#64748b', mt: 0.25 }}>
              {employee.position || 'No position set'}
              {employee.department ? ` · ${employee.department}` : ''}
              {employee.managerName ? ` · Reports to ${employee.managerName}` : ''}
            </Typography>
          </Box>

          {/* Edit / Save / Toggle Status buttons */}
          {canEdit && (
            <Box sx={{ pb: 1, display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {editing ? (
                <>
                  <Button size="small" variant="outlined" startIcon={<CancelIcon />}
                    onClick={() => { setEditing(false); setForm({ ...employee, workEmail: employee.workEmail || employee.email }); }}
                    sx={{ textTransform: 'none', bgcolor: '#ffffff', color: '#dc2626', borderColor: '#dc2626', '&:hover': { bgcolor: '#fff5f5', borderColor: '#b91c1c' } }}>
                    Cancel
                  </Button>
                  <Button size="small" variant="outlined" startIcon={<SaveIcon />}
                    onClick={handleSave}
                    disabled={!form.firstName?.trim()
                      || !form.lastName?.trim()
                      || (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
                      || (form.phone && !/^[+]?[0-9]{7,15}$/.test(form.phone.trim()))
                      || (form.password && form.password.length < 8)}
                    sx={{ textTransform: 'none', bgcolor: '#ffffff', color: '#1e3a5f', borderColor: '#1e3a5f', fontWeight: 600, borderRadius: '8px', '&:hover': { bgcolor: 'rgba(30,58,95,0.06)', borderColor: '#152d4a' } }}>
                    Save Changes
                  </Button>
                </>
              ) : (
                <>
                  <Button size="small" variant="outlined" startIcon={<EditIcon />}
                    onClick={() => setEditing(true)}
                    sx={{ textTransform: 'none' }}>
                    Edit Profile
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    color={employee.active ? 'error' : 'success'}
                    startIcon={<BlockIcon />}
                    onClick={() => setConfirmToggle(true)}
                    sx={{ textTransform: 'none' }}
                  >
                    {employee.active ? 'Deactivate' : 'Activate'}
                  </Button>
                </>
              )}
            </Box>
          )}
        </Box>

        {/* ── Tab bar ───────────────────────────────────────────────────── */}
        <Box sx={{
          mt: 2, bgcolor: 'white', borderRadius: 2,
          boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
          border: '1px solid #e2e8f0',
        }}>
          <Tabs
            value={tab}
            onChange={(_, v) => setTab(v)}
            variant="scrollable" scrollButtons="auto"
            sx={{
              px: 1,
              '& .MuiTab-root': { textTransform: 'none', fontWeight: 500, fontSize: 13.5, minWidth: 'auto', px: 2, py: 1.5 },
              '& .Mui-selected': { fontWeight: 700, color: '#0f172a' },
              '& .MuiTabs-indicator': { bgcolor: '#0f172a', height: 2 },
            }}
          >
            {TABS.map((t) => (
              <Tab key={t.key} value={t.key} label={t.label}
                disabled={t.locked}
                sx={t.locked ? { opacity: 0.4 } : {}}
              />
            ))}
          </Tabs>
        </Box>

        {/* ── Tab content ───────────────────────────────────────────────── */}
        <Box sx={{ mt: 2 }}>

          {/* ── MY PROFILE ─────────────────────────────────────────── */}
          {tab === 'profile' && (
            editing ? (
              <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                <Typography fontWeight={700} mb={2}>Edit Information</Typography>
                <Grid container spacing={2}>
                  {/* Basic */}
                  {[
                    ['employeeCode','Employee ID / Code', true], ['firstName','First Name'],
                    ['lastName','Last Name'], ['phone','Phone'],
                    ['workEmail','Work Email'], ['personalEmail','Personal Email'],
                  ].map(([f, l, disabled]) => (
                    <Grid item xs={12} sm={6} key={f}>
                      <TextField fullWidth size="small" label={l} value={form[f] || ''}
                        disabled={!!disabled}
                        onChange={(e) => setForm({ ...form, [f]: e.target.value })} />
                    </Grid>
                  ))}
                  <Grid item xs={12} sm={6}>
                    <TextField fullWidth size="small" label="Date of Birth" type="date"
                      InputLabelProps={{ shrink: true }}
                      inputProps={{ max: new Date().toISOString().split('T')[0] }}
                      value={form.dateOfBirth ? String(form.dateOfBirth).slice(0, 10) : ''}
                      onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value || null })} />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <FormControl size="small" fullWidth>
                      <InputLabel>Gender</InputLabel>
                      <Select value={form.gender || ''} label="Gender"
                        onChange={(e) => setForm({ ...form, gender: e.target.value })}>
                        <MenuItem value=""><em>None</em></MenuItem>
                        {['Male','Female','Other'].map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <FormControl size="small" fullWidth>
                      <InputLabel>Marital Status</InputLabel>
                      <Select value={form.maritalStatus || ''} label="Marital Status"
                        onChange={(e) => setForm({ ...form, maritalStatus: e.target.value })}>
                        <MenuItem value=""><em>None</em></MenuItem>
                        {['Single','Married','Divorced','Widowed'].map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
                      </Select>
                    </FormControl>
                  </Grid>
                  {/* Employment */}
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'flex-start' }}>
                      <FormControl size="small" fullWidth>
                        <InputLabel>Department</InputLabel>
                        <Select value={form.department || ''} label="Department"
                          onChange={(e) => setForm({ ...form, department: e.target.value })}>
                          <MenuItem value=""><em>None</em></MenuItem>
                          {deptOptions.map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
                        </Select>
                      </FormControl>
                      <Tooltip title="Manage Departments">
                        <IconButton size="small" onClick={() => setManageOpen('dept')}
                          sx={{ mt: 0.5, color: 'text.secondary' }}>
                          <TuneIcon sx={{ fontSize: 18 }} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'flex-start' }}>
                      <FormControl size="small" fullWidth>
                        <InputLabel>Position</InputLabel>
                        <Select value={form.position || ''} label="Position"
                          onChange={(e) => setForm({ ...form, position: e.target.value })}>
                          <MenuItem value=""><em>None</em></MenuItem>
                          {posOptions.map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
                        </Select>
                      </FormControl>
                      <Tooltip title="Manage Positions">
                        <IconButton size="small" onClick={() => setManageOpen('pos')}
                          sx={{ mt: 0.5, color: 'text.secondary' }}>
                          <TuneIcon sx={{ fontSize: 18 }} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <FormControl size="small" fullWidth>
                      <InputLabel>Employment Type</InputLabel>
                      <Select value={form.employmentType || ''} label="Employment Type"
                        onChange={(e) => setForm({ ...form, employmentType: e.target.value })}>
                        <MenuItem value=""><em>None</em></MenuItem>
                        {['Full-time','Part-time','Contract','Intern','Consultant'].map((o) => (
                          <MenuItem key={o} value={o}>{o}</MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <FormControl size="small" fullWidth>
                      <InputLabel>Source of Hire</InputLabel>
                      <Select value={form.sourceOfHire || ''} label="Source of Hire"
                        onChange={(e) => setForm({ ...form, sourceOfHire: e.target.value })}>
                        <MenuItem value=""><em>None</em></MenuItem>
                        {['LinkedIn','Referral','Job Portal','Walk-in','Campus','Other'].map((o) => (
                          <MenuItem key={o} value={o}>{o}</MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'flex-start' }}>
                      <FormControl size="small" fullWidth>
                        <InputLabel>Location</InputLabel>
                        <Select value={form.seatingLocation || ''} label="Location"
                          onChange={(e) => setForm({ ...form, seatingLocation: e.target.value })}>
                          <MenuItem value=""><em>None</em></MenuItem>
                          {locOptions.map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
                        </Select>
                      </FormControl>
                      <Tooltip title="Manage Locations">
                        <IconButton size="small" onClick={() => setManageOpen('loc')}
                          sx={{ mt: 0.5, color: 'text.secondary' }}>
                          <TuneIcon sx={{ fontSize: 18 }} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </Grid>
                  {/* Role — admin: all roles; HR: all except ADMIN */}
                  {(isAdmin || isHR) && (
                    <Grid item xs={12} sm={6}>
                      <FormControl size="small" fullWidth>
                        <InputLabel>Role</InputLabel>
                        <Select
                          value={form.role || 'EMPLOYEE'}
                          label="Role"
                          onChange={(e) => setForm({ ...form, role: e.target.value })}
                        >
                          <MenuItem value="EMPLOYEE">Employee</MenuItem>
                          <MenuItem value="MANAGER">Manager</MenuItem>
                          <MenuItem value="ASSISTANT_MANAGER">Assistant Manager</MenuItem>
                          <MenuItem value="HR">HR</MenuItem>
                          {isAdmin && <MenuItem value="ADMIN">Admin</MenuItem>}
                          {isAdmin && <MenuItem value="DIRECTOR">Director</MenuItem>}
                        </Select>
                      </FormControl>
                    </Grid>
                  )}
                  {/* Manager — admin and HR */}
                  {(isAdmin || isHR) && (
                    <Grid item xs={12} sm={6}>
                      <FormControl size="small" fullWidth>
                        <InputLabel>Manager</InputLabel>
                        <Select
                          value={form.managerId || ''}
                          label="Manager"
                          onChange={(e) => setForm({ ...form, managerId: e.target.value || null })}
                        >
                          <MenuItem value="">None</MenuItem>
                          {allEmployees
                            .filter((e) => e.id !== employee?.id && e.active !== false && (e.role === 'MANAGER' || e.role === 'ASSISTANT_MANAGER' || e.role === 'ADMIN' || e.role === 'DIRECTOR'))
                            .map((e) => (
                              <MenuItem key={e.id} value={e.id}>
                                {e.fullName} ({e.role})
                              </MenuItem>
                            ))}
                        </Select>
                      </FormControl>
                    </Grid>
                  )}
                  {/* Assigned HR — admin and HR */}
                  {(isAdmin || isHR) && (
                    <Grid item xs={12} sm={6}>
                      <FormControl size="small" fullWidth>
                        <InputLabel>Assigned HR</InputLabel>
                        <Select
                          value={form.assignedHrId || ''}
                          label="Assigned HR"
                          onChange={(e) => setForm({ ...form, assignedHrId: e.target.value || null })}
                        >
                          <MenuItem value="">None</MenuItem>
                          {hrEmployees.map((e) => (
                            <MenuItem key={e.id} value={e.id}>
                              {e.fullName}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </Grid>
                  )}
                  <Grid item xs={12} sm={6}>
                    <TextField fullWidth size="small" label="Date of Joining" type="date"
                      InputLabelProps={{ shrink: true }}
                      value={form.hireDate ? String(form.hireDate).slice(0, 10) : ''}
                      onChange={(e) => setForm({ ...form, hireDate: e.target.value || null })} />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField fullWidth size="small" label="Date of Exit" type="date"
                      InputLabelProps={{ shrink: true }}
                      value={form.dateOfExit ? String(form.dateOfExit).slice(0, 10) : ''}
                      onChange={(e) => setForm({ ...form, dateOfExit: e.target.value || null })} />
                  </Grid>
                  {/* Experience */}
                  {[
                    ['currentExperience','Current Experience'], ['totalExperience','Total Experience'],
                  ].map(([f, l]) => (
                    <Grid item xs={12} sm={6} key={f}>
                      <TextField fullWidth size="small" label={l} value={form[f] || ''}
                        autoComplete="off"
                        onChange={(e) => setForm({ ...form, [f]: e.target.value })} />
                    </Grid>
                  ))}
                  <Grid item xs={12}>
                    <TextField fullWidth multiline rows={2} size="small" label="Skills"
                      value={form.skills || ''}
                      onChange={(e) => setForm({ ...form, skills: e.target.value })} />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField fullWidth multiline rows={2} size="small" label="Experience Summary"
                      value={form.experience || ''}
                      onChange={(e) => setForm({ ...form, experience: e.target.value })} />
                  </Grid>
                  {/* Address */}
                  <Grid item xs={12}>
                    <TextField fullWidth multiline rows={2} size="small" label="Present Address"
                      value={form.presentAddress || ''}
                      onChange={(e) => setForm({ ...form, presentAddress: e.target.value })} />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField fullWidth multiline rows={2} size="small" label="Permanent Address"
                      value={form.permanentAddress || ''}
                      onChange={(e) => setForm({ ...form, permanentAddress: e.target.value })} />
                  </Grid>
                  {/* Identity & Access */}
                  <Grid item xs={12}>
                    <Divider sx={{ mt: 1 }}><Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600, px: 1 }}>Identity & Access</Typography></Divider>
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      fullWidth size="small" label="New Password"
                      placeholder="Leave blank to keep current"
                      type={showEditPassword ? 'text' : 'password'}
                      autoComplete="new-password"
                      value={form.password || ''}
                      onChange={(e) => setForm({ ...form, password: e.target.value })}
                      InputProps={{
                        endAdornment: (
                          <InputAdornment position="end">
                            <IconButton size="small" onClick={() => setShowEditPassword((p) => !p)} edge="end">
                              {showEditPassword ? <VisibilityOffIcon sx={{ fontSize: 18 }} /> : <VisibilityIcon sx={{ fontSize: 18 }} />}
                            </IconButton>
                          </InputAdornment>
                        ),
                      }}
                    />
                  </Grid>
                  {[['aadharNumber','Aadhar Number'],['panNumber','PAN Number'],['uanNumber','UAN Number']].map(([f, l]) => (
                    <Grid item xs={12} sm={6} key={f}>
                      <TextField fullWidth size="small" label={l} value={form[f] || ''}
                        onChange={(e) => setForm({ ...form, [f]: e.target.value })} />
                    </Grid>
                  ))}
                </Grid>
              </Card>
            ) : (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {/* ── Core info cards ── */}
                <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
                    <Typography fontWeight={700} fontSize={15}>Contact & Role</Typography>
                    {canEdit && (
                      <IconButton size="small" onClick={() => setEditing(true)}
                        sx={{ bgcolor: '#f1f5f9', '&:hover': { bgcolor: '#e2e8f0' } }}>
                        <EditIcon sx={{ fontSize: 14 }} />
                      </IconButton>
                    )}
                  </Box>
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6} md={4}>
                      <InfoCard icon={<EmailIcon />} label="Work Email" value={employee.workEmail || employee.email} />
                    </Grid>
                    {employee.personalEmail && (
                      <Grid item xs={12} sm={6} md={4}>
                        <InfoCard icon={<EmailIcon />} label="Personal Email" value={employee.personalEmail} />
                      </Grid>
                    )}
                    <Grid item xs={12} sm={6} md={4}>
                      <InfoCard icon={<PhoneIcon />}    label="Phone" value={employee.phone} />
                    </Grid>
                    <Grid item xs={12} sm={6} md={4}>
                      <InfoCard icon={<BusinessIcon />} label="Department" value={employee.department} />
                    </Grid>
                    <Grid item xs={12} sm={6} md={4}>
                      <InfoCard icon={<WorkIcon />}     label="Position" value={employee.position} />
                    </Grid>
                    <Grid item xs={12} sm={6} md={4}>
                      <InfoCard icon={<BadgeIcon />}    label="Role" value={employee.role} />
                    </Grid>
                    <Grid item xs={12} sm={6} md={4}>
                      <InfoCard icon={<PersonIcon />}   label="Manager" value={employee.managerName || 'No manager'} />
                    </Grid>
                    <Grid item xs={12} sm={6} md={4}>
                      <InfoCard icon={<SupportAgentIcon />} label="Assigned HR" value={employee.assignedHrName || 'Not assigned'} />
                    </Grid>
                    {employee.employmentType && (
                      <Grid item xs={12} sm={6} md={4}>
                        <InfoCard icon={<WorkIcon />}   label="Employment Type" value={employee.employmentType} />
                      </Grid>
                    )}
                    {employee.sourceOfHire && (
                      <Grid item xs={12} sm={6} md={4}>
                        <InfoCard icon={<PersonIcon />} label="Source of Hire" value={employee.sourceOfHire} />
                      </Grid>
                    )}
                    {employee.hireDate && (
                      <Grid item xs={12} sm={6} md={4}>
                        <InfoCard icon={<CalendarTodayIcon />} label="Date of Joining" value={String(employee.hireDate)} />
                      </Grid>
                    )}
                    {employee.dateOfExit && (
                      <Grid item xs={12} sm={6} md={4}>
                        <InfoCard icon={<CalendarTodayIcon />} label="Date of Exit" value={String(employee.dateOfExit)} />
                      </Grid>
                    )}
                    {employee.seatingLocation && (
                      <Grid item xs={12} sm={6} md={4}>
                        <InfoCard icon={<BusinessIcon />} label="Location" value={employee.seatingLocation} />
                      </Grid>
                    )}
                  </Grid>
                </Card>

                {/* ── Personal info ── */}
                {(employee.dateOfBirth || employee.gender || employee.maritalStatus || employee.age ||
                  (canViewFull && (employee.aadharNumber || employee.panNumber || employee.uanNumber))) && (
                  <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                    <Typography fontWeight={700} fontSize={15} mb={2}>Personal Details</Typography>
                    <Grid container spacing={2}>
                      {employee.dateOfBirth && (
                        <Grid item xs={12} sm={6} md={4}>
                          <InfoCard icon={<CalendarTodayIcon />} label="Date of Birth" value={String(employee.dateOfBirth)} />
                        </Grid>
                      )}
                      {employee.age && (
                        <Grid item xs={12} sm={6} md={4}>
                          <InfoCard icon={<PersonIcon />} label="Age" value={`${employee.age} years`} />
                        </Grid>
                      )}
                      {employee.gender && (
                        <Grid item xs={12} sm={6} md={4}>
                          <InfoCard icon={<PersonIcon />} label="Gender" value={employee.gender} />
                        </Grid>
                      )}
                      {employee.maritalStatus && (
                        <Grid item xs={12} sm={6} md={4}>
                          <InfoCard icon={<PersonIcon />} label="Marital Status" value={employee.maritalStatus} />
                        </Grid>
                      )}
                      {canViewFull && employee.aadharNumber && (
                        <Grid item xs={12} sm={6} md={4}>
                          <InfoCard icon={<BadgeIcon />} label="Aadhar Number" value={employee.aadharNumber} />
                        </Grid>
                      )}
                      {canViewFull && employee.panNumber && (
                        <Grid item xs={12} sm={6} md={4}>
                          <InfoCard icon={<BadgeIcon />} label="PAN Number" value={employee.panNumber} />
                        </Grid>
                      )}
                      {canViewFull && employee.uanNumber && (
                        <Grid item xs={12} sm={6} md={4}>
                          <InfoCard icon={<BadgeIcon />} label="UAN Number" value={employee.uanNumber} />
                        </Grid>
                      )}
                    </Grid>
                  </Card>
                )}

                {/* ── Experience ── */}
                {(employee.currentExperience || employee.totalExperience || employee.skills || employee.experience) && (
                  <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                    <Typography fontWeight={700} fontSize={15} mb={2}>Work Experience</Typography>
                    <Grid container spacing={2} mb={employee.skills || employee.experience ? 2 : 0}>
                      {employee.currentExperience && (
                        <Grid item xs={12} sm={6}>
                          <InfoCard icon={<WorkIcon />} label="Current Experience" value={employee.currentExperience} />
                        </Grid>
                      )}
                      {employee.totalExperience && (
                        <Grid item xs={12} sm={6}>
                          <InfoCard icon={<WorkIcon />} label="Total Experience" value={employee.totalExperience} />
                        </Grid>
                      )}
                    </Grid>
                    {employee.skills && (
                      <>
                        <Typography fontSize={13} fontWeight={600} mb={0.5}>Skills</Typography>
                        <Typography variant="body2" color="text.secondary" mb={2}>{employee.skills}</Typography>
                      </>
                    )}
                    {employee.experience && (
                      <>
                        <Typography fontSize={13} fontWeight={600} mb={0.5}>Experience Summary</Typography>
                        <Typography variant="body2" color="text.secondary">{employee.experience}</Typography>
                      </>
                    )}
                  </Card>
                )}

                {/* ── Address ── */}
                {(employee.presentAddress || employee.permanentAddress) && (
                  <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                    <Typography fontWeight={700} fontSize={15} mb={2}>Address</Typography>
                    <Grid container spacing={2}>
                      {employee.presentAddress && (
                        <Grid item xs={12} sm={6}>
                          <Typography fontSize={12} color="text.secondary" mb={0.5}>Present Address</Typography>
                          <Typography variant="body2">{employee.presentAddress}</Typography>
                        </Grid>
                      )}
                      {employee.permanentAddress && (
                        <Grid item xs={12} sm={6}>
                          <Typography fontSize={12} color="text.secondary" mb={0.5}>Permanent Address</Typography>
                          <Typography variant="body2">{employee.permanentAddress}</Typography>
                        </Grid>
                      )}
                    </Grid>
                  </Card>
                )}

                {/* ── Audit info ── */}
                {(employee.addedBy || employee.createdAt) && (
                  <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                    <Typography fontWeight={700} fontSize={15} mb={2}>Record Info</Typography>
                    <Grid container spacing={2}>
                      {employee.addedBy && (
                        <Grid item xs={12} sm={6} md={3}>
                          <InfoCard icon={<PersonIcon />} label="Added By" value={employee.addedBy} />
                        </Grid>
                      )}
                      {employee.createdAt && (
                        <Grid item xs={12} sm={6} md={3}>
                          <InfoCard icon={<CalendarTodayIcon />} label="Added Time"
                            value={new Date(employee.createdAt).toLocaleString('en-IN', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' })} />
                        </Grid>
                      )}
                      {employee.modifiedBy && (
                        <Grid item xs={12} sm={6} md={3}>
                          <InfoCard icon={<PersonIcon />} label="Modified By" value={employee.modifiedBy} />
                        </Grid>
                      )}
                      {employee.updatedAt && (
                        <Grid item xs={12} sm={6} md={3}>
                          <InfoCard icon={<CalendarTodayIcon />} label="Modified Time"
                            value={new Date(employee.updatedAt).toLocaleString('en-IN', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' })} />
                        </Grid>
                      )}
                    </Grid>
                  </Card>
                )}
              </Box>
            )
          )}

          {/* ── REPORTEES ──────────────────────────────────────────────── */}
          {tab === 'reportees' && (
            <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
              <Typography fontWeight={700} mb={2}>
                Direct Reports ({employee.subordinateCount || 0})
              </Typography>
              {reportees.length === 0 ? (
                <Box sx={{ textAlign: 'center', py: 4 }}>
                  <GroupIcon sx={{ fontSize: 40, color: '#cbd5e1', mb: 1 }} />
                  <Typography color="text.secondary" fontSize={13}>No direct reports</Typography>
                </Box>
              ) : (
                <Grid container spacing={2}>
                  {reportees.map((rep) => (
                    <Grid item xs={12} sm={6} md={4} key={rep.id}>
                      <Box
                        onClick={() => navigate(`/employees/${rep.id}`)}
                        sx={{
                          display: 'flex', alignItems: 'center', gap: 1.5,
                          p: 1.5, border: '1px solid #e2e8f0', borderRadius: 2,
                          cursor: 'pointer', bgcolor: 'white',
                          '&:hover': { borderColor: '#1976d2', boxShadow: '0 2px 8px rgba(25,118,210,0.12)' },
                        }}
                      >
                        <Avatar sx={{ width: 42, height: 42, bgcolor: avatarBg(rep.fullName), fontSize: '0.9375rem', fontWeight: 700 }}>
                          {initials(rep.fullName)}
                        </Avatar>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography fontSize={13} fontWeight={700} noWrap>{rep.fullName}</Typography>
                          <Typography fontSize={11} color="text.secondary" noWrap>{rep.position || rep.role}</Typography>
                        </Box>
                      </Box>
                    </Grid>
                  ))}
                </Grid>
              )}
            </Card>
          )}

          {/* ── LEAVE ─────────────────────────────────────────────────── */}
          {tab === 'leave' && (
            canViewFull ? (
              <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                <Typography fontWeight={700} mb={2}>Leave Records</Typography>
                {leaves.length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 4 }}>
                    <EventNoteIcon sx={{ fontSize: 40, color: '#cbd5e1', mb: 1 }} />
                    <Typography color="text.secondary" fontSize={13}>No leave records</Typography>
                  </Box>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: '#f8fafc' }}>
                        {['Type','Start','End','Days','Reason','Status'].map((h) => (
                          <TableCell key={h} sx={{ fontWeight: 600, fontSize: 12 }}>{h}</TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {leaves.map((l) => (
                        <TableRow key={l.id} hover>
                          <TableCell sx={{ fontSize: 13 }}>{l.leaveType}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{l.startDate}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{l.endDate}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{l.totalDays}</TableCell>
                          <TableCell sx={{ fontSize: 13, maxWidth: 180 }}>
                            <Typography fontSize={13} noWrap>{l.reason || '—'}</Typography>
                          </TableCell>
                          <TableCell>
                            <Chip label={l.status} size="small"
                              color={l.status === 'APPROVED' ? 'success' : l.status === 'REJECTED' ? 'error' : 'warning'} />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Card>
            ) : <Alert severity="warning">You don't have permission to view this.</Alert>
          )}

          {/* ── ATTENDANCE ────────────────────────────────────────────── */}
          {tab === 'attendance' && (
            canViewFull ? (
              <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                <Typography fontWeight={700} mb={2}>Attendance Records (Job Time Tracking, last 90 days)</Typography>
                {attendance.length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 4 }}>
                    <AccessTimeIcon sx={{ fontSize: 40, color: '#cbd5e1', mb: 1 }} />
                    <Typography color="text.secondary" fontSize={13}>No attendance records</Typography>
                  </Box>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: '#f8fafc' }}>
                        {['Date','First Login','Last Logout','Working','Break','Office','Overtime','Status'].map((h) => (
                          <TableCell key={h} sx={{ fontWeight: 600, fontSize: 12 }}>{h}</TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {attendance.slice(0, 30).map((a, i) => (
                        <TableRow key={i} hover>
                          <TableCell sx={{ fontSize: 13 }}>{a.workDate}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{fmtAttTime(a.firstLoginTime)}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{fmtAttTime(a.lastLogoutTime)}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{fmtAttMinutes(a.totalWorkingMinutes)}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{fmtAttMinutes(a.totalBreakMinutes)}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{fmtAttMinutes(a.totalOfficeMinutes)}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>{a.overtimeMinutes > 0 ? `+${fmtAttMinutes(a.overtimeMinutes)}` : '—'}</TableCell>
                          <TableCell>
                            <Chip
                              label={ATT_STATUS_STYLES[a.status]?.label || a.status}
                              size="small"
                              sx={{
                                bgcolor: ATT_STATUS_STYLES[a.status]?.bg || '#f1f5f9',
                                color: ATT_STATUS_STYLES[a.status]?.color || '#64748b',
                                fontWeight: 600,
                              }}
                            />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Card>
            ) : <Alert severity="warning">You don't have permission to view this.</Alert>
          )}

          {/* ── TIMESHEETS ────────────────────────────────────────────── */}
          {tab === 'timesheets' && (
            canViewFull ? (
              <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                <Typography fontWeight={700} mb={2}>Timesheets (Last 30 days)</Typography>
                {timesheets.length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 4 }}>
                    <AccessTimeIcon sx={{ fontSize: 40, color: '#cbd5e1', mb: 1 }} />
                    <Typography color="text.secondary" fontSize={13}>No timesheet entries logged</Typography>
                  </Box>
                ) : (
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: '#f8fafc' }}>
                        {['Date', 'Project', 'Task', 'Hours'].map((h) => (
                          <TableCell key={h} sx={{ fontWeight: 600, fontSize: 12 }}>{h}</TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {timesheets.map((t) => (
                        <TableRow key={t.id} hover>
                          <TableCell sx={{ fontSize: 13, whiteSpace: 'nowrap' }}>{t.date}</TableCell>
                          <TableCell sx={{ fontSize: 13 }}>
                            <Chip label={t.projectName || '—'} size="small"
                              sx={{ bgcolor: '#eff6ff', color: '#1d4ed8', fontWeight: 600, fontSize: 11 }} />
                          </TableCell>
                          <TableCell sx={{ fontSize: 13, color: '#475569' }}>{t.taskName || '—'}</TableCell>
                          <TableCell sx={{ fontSize: 13, fontWeight: 600 }}>
                            {t.hours != null ? `${t.hours}h` : '—'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Card>
            ) : <Alert severity="warning">You don't have permission to view this.</Alert>
          )}

          {/* ── PERFORMANCE ───────────────────────────────────────────── */}
          {tab === 'performance' && (
            canViewFull ? (
              <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
                <Typography fontWeight={700} mb={2}>Performance Reviews</Typography>
                {reviews.length === 0 ? (
                  <Box sx={{ textAlign: 'center', py: 4 }}>
                    <StarIcon sx={{ fontSize: 40, color: '#cbd5e1', mb: 1 }} />
                    <Typography color="text.secondary" fontSize={13}>No performance reviews yet</Typography>
                  </Box>
                ) : reviews.map((r) => (
                  <Box key={r.id} sx={{ p: 2, mb: 2, border: '1px solid #e2e8f0', borderRadius: 2 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                      <Typography fontWeight={700} fontSize={14}>{r.reviewPeriod}</Typography>
                      <Chip label={`${r.rating}/5`} size="small"
                        color={r.rating >= 4 ? 'success' : r.rating >= 3 ? 'warning' : 'error'} />
                    </Box>
                    <Typography fontSize={12} color="text.secondary" mb={0.5}>
                      Reviewed by: {r.reviewerName}
                    </Typography>
                    <Typography fontSize={13} color="#475569">{r.comments}</Typography>
                  </Box>
                ))}
              </Card>
            ) : <Alert severity="warning">You don't have permission to view this.</Alert>
          )}

          {/* ── COURSES ───────────────────────────────────────────────── */}
          {tab === 'courses' && (
            <Card sx={{ p: 3, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
              <Typography fontWeight={700} mb={2}>Courses & Certificates</Typography>
              {courses.length === 0 ? (
                <Box sx={{ textAlign: 'center', py: 4 }}>
                  <SchoolIcon sx={{ fontSize: 40, color: '#cbd5e1', mb: 1 }} />
                  <Typography color="text.secondary" fontSize={13}>Not enrolled in any courses</Typography>
                </Box>
              ) : (
                <Grid container spacing={2}>
                  {courses.map((c) => (
                    <Grid item xs={12} sm={6} md={4} key={c.id}>
                      <Box sx={{ p: 2, border: '1px solid #e2e8f0', borderRadius: 2, height: '100%' }}>
                        <Typography fontSize={13} fontWeight={700} mb={0.5} noWrap>{c.title}</Typography>
                        <Chip
                          label={c.enrollmentStatus?.replace('_', ' ') || 'Enrolled'}
                          size="small"
                          color={
                            c.enrollmentStatus === 'COMPLETED' ? 'success' :
                            c.enrollmentStatus === 'IN_PROGRESS' ? 'warning' :
                            c.enrollmentStatus === 'FAILED' ? 'error' : 'primary'
                          }
                          sx={{ mb: 1 }}
                        />
                        {c.certificateNumber && (
                          <Typography fontSize={11} color="success.dark" fontWeight={600}>
                            🏆 {c.certificateNumber}
                          </Typography>
                        )}
                        {c.examScore != null && (
                          <Typography fontSize={11} color="text.secondary">
                            Score: {c.examScore}%
                          </Typography>
                        )}
                      </Box>
                    </Grid>
                  ))}
                </Grid>
              )}
            </Card>
          )}

        </Box>
      </Box>

      {/* Manage list dialog */}
      {mgCfg && (
        <ManageListDialog
          open={Boolean(manageOpen)}
          onClose={() => setManageOpen(null)}
          title={mgCfg.label}
          items={mgCfg.items}
          onAdd={(v) => mgCfg.setItems((p) => [...p, v])}
          onEdit={(i, v) => mgCfg.setItems((p) => p.map((x, idx) => idx === i ? v : x))}
          onDelete={(i) => mgCfg.setItems((p) => p.filter((_, idx) => idx !== i))}
        />
      )}

      {/* ── Confirm Deactivate / Activate ── */}
      <Dialog open={confirmToggle} onClose={() => setConfirmToggle(false)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>
          {employee?.active ? 'Deactivate Employee' : 'Activate Employee'}
        </DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to {employee?.active ? 'deactivate' : 'activate'}{' '}
            <strong>{employee?.fullName}</strong>?
            {employee?.active && ' They will lose access to the system.'}
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setConfirmToggle(false)}>Cancel</Button>
          <Button
            variant="contained"
            color={employee?.active ? 'error' : 'success'}
            onClick={handleToggleStatus}
          >
            {employee?.active ? 'Deactivate' : 'Activate'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default EmployeeDetailPage;
