import React, { useEffect, useState, useMemo } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Card, CardContent, Grid, Typography, TextField, Button,
  Avatar, Chip, CircularProgress, Divider, IconButton, Tooltip,
  Tab, Tabs, LinearProgress
} from '@mui/material';
import PushPinOutlinedIcon from '@mui/icons-material/PushPinOutlined';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EmailIcon from '@mui/icons-material/Email';
import PhoneIcon from '@mui/icons-material/Phone';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import WorkIcon from '@mui/icons-material/Work';
import SchoolIcon from '@mui/icons-material/School';
import CardMembershipIcon from '@mui/icons-material/CardMembership';
import BadgeIcon from '@mui/icons-material/Badge';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import BeachAccessIcon from '@mui/icons-material/BeachAccess';
import CakeIcon from '@mui/icons-material/Cake';
import BusinessIcon from '@mui/icons-material/Business';
import PersonIcon from '@mui/icons-material/Person';
import DownloadIcon from '@mui/icons-material/Download';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import GroupIcon from '@mui/icons-material/Group';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import { Bar, Doughnut } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale, LinearScale, BarElement,
  Title, Tooltip as ChartTooltip, Legend, ArcElement,
} from 'chart.js';
import { useNavigate } from 'react-router-dom';
import { employeeApi } from '../api/employeeApi';
import { courseApi } from '../api/courseApi';
import { performanceApi } from '../api/performanceApi';
import { timesheetApi } from '../api/timesheetApi';
import { leaveApi } from '../api/leaveApi';
import { toast } from 'react-toastify';

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, ChartTooltip, Legend, ArcElement);

const ROLE_COLOR = { ADMIN: '#ef4444', MANAGER: '#f59e0b', ASSISTANT_MANAGER: '#f97316', HR: '#8b5cf6', EMPLOYEE: '#3b82f6' };
const ROLE_BG   = { ADMIN: '#fef2f2', MANAGER: '#fffbeb', ASSISTANT_MANAGER: '#fff7ed', HR: '#f5f3ff', EMPLOYEE: '#eff6ff' };

/* ── reusable widget card ── */
const W = ({ title, seeAll, seeAllN, onSeeAll, children, minH, sx = {} }) => (
  <Card sx={{ borderRadius: '8px', boxShadow: '0 1px 6px rgba(0,0,0,0.08)', height: '100%', minHeight: minH, ...sx }}>
    <CardContent sx={{ p: '16px !important', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
        <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>{title}</Typography>
        <Box sx={{ display: 'flex', gap: 0.5, color: '#d1d5db' }}>
          <PushPinOutlinedIcon sx={{ fontSize: 15 }} />
          <MoreHorizIcon sx={{ fontSize: 15 }} />
        </Box>
      </Box>
      <Box sx={{ flex: 1 }}>{children}</Box>
      {seeAll && (
        <Box sx={{ mt: 1.5, pt: 1.5, borderTop: '1px solid #f3f4f6', textAlign: 'center' }}>
          <Typography onClick={onSeeAll} sx={{ fontSize: 12.5, fontWeight: 600, color: '#0891b2', cursor: onSeeAll ? 'pointer' : 'default', '&:hover': { textDecoration: onSeeAll ? 'underline' : 'none' } }}>
            {seeAll}{seeAllN !== undefined ? ` ${seeAllN}` : ''}
          </Typography>
        </Box>
      )}
    </CardContent>
  </Card>
);

/* ── section header ── */
const Sect = ({ title }) => (
  <Typography sx={{ fontSize: 14.5, fontWeight: 700, color: '#111827', mb: 1.5, mt: 3.5, pb: 0.5, borderBottom: '2px solid #f3f4f6' }}>
    {title}
  </Typography>
);

/* ── donut for reported time ── */
const TimeDonut = ({ pct, label, hours, target, breakdown }) => {
  const data = {
    datasets: [{
      data: [Math.min(pct, 100), Math.max(0, 100 - pct)],
      backgroundColor: [pct >= 95 ? '#16a34a' : pct >= 70 ? '#0891b2' : '#f59e0b', '#f3f4f6'],
      borderWidth: 0,
    }],
  };
  const opts = { responsive: true, maintainAspectRatio: false, cutout: '72%', plugins: { legend: { display: false }, tooltip: { enabled: false } } };
  const ringColor = pct >= 95 ? '#16a34a' : pct >= 70 ? '#0891b2' : '#f59e0b';
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
      <Box sx={{ position: 'relative', width: 62, height: 62, flexShrink: 0 }}>
        <Doughnut data={data} options={opts} />
        <Box sx={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', pointerEvents: 'none' }}>
          <Typography sx={{ fontSize: 11.5, fontWeight: 800, color: ringColor, lineHeight: 1 }}>{pct}%</Typography>
          <Typography sx={{ fontSize: 8.5, color: '#9ca3af', lineHeight: 1.2 }}>{label}</Typography>
        </Box>
      </Box>
      <Box>
        <Typography sx={{ fontSize: 11.5, fontWeight: 600, color: '#374151', mb: 0.4 }}>{hours} / {target}h</Typography>
        {breakdown.map((b, i) => (
          <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.2 }}>
            <Box sx={{ width: 7, height: 7, borderRadius: '2px', bgcolor: b.color, flexShrink: 0 }} />
            <Typography sx={{ fontSize: 10.5, color: '#6b7280' }}>{b.label} • {b.value}</Typography>
          </Box>
        ))}
      </Box>
    </Box>
  );
};

/* ── PDF download — delegates to server-side iText certificate generator ── */
const downloadCertificate = async (courseName, employeeName, certNo) => {
  try {
    const blob = await courseApi.downloadCertificatePdf(certNo);
    const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `Certificate_${(employeeName || '').replace(/\s+/g, '_')}_${courseName.replace(/[^a-zA-Z0-9]/g, '_').substring(0, 30)}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  } catch (err) {
    console.error('Certificate download failed', err);
  }
};

/* ═══════════════════════════════════════════════ */
const ProfilePage = () => {
  const { user } = useSelector((s) => s.auth);
  const navigate = useNavigate();
  const [employee, setEmployee] = useState(null);
  const [certificates, setCertificates] = useState([]);
  const [courses, setCourses] = useState([]);
  const [performance, setPerformance] = useState([]);
  const [leaves, setLeaves] = useState([]);
  const [attendance, setAttendance] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({});
  const [activeTab, setActiveTab] = useState(0);
  const [addingEdu, setAddingEdu] = useState(false);
  const [newEdu, setNewEdu] = useState({ institute: '', degree: '', year: '' });

  useEffect(() => {
    const load = async () => {
      try {
        const emp = await employeeApi.getByUserId(user.userId);
        setEmployee(emp);
        setForm(emp);
        const res = await Promise.allSettled([
          courseApi.getMyCertificates(),
          courseApi.getForEmployee(emp.id),
          performanceApi.getByEmployee(emp.id),
          leaveApi.getMyLeaves(emp.id),
          timesheetApi.getAttendance(emp.id),
        ]);
        if (res[0].status === 'fulfilled') setCertificates(Array.isArray(res[0].value) ? res[0].value : []);
        if (res[1].status === 'fulfilled') setCourses(Array.isArray(res[1].value) ? res[1].value : []);
        if (res[2].status === 'fulfilled') setPerformance(Array.isArray(res[2].value) ? res[2].value : []);
        if (res[3].status === 'fulfilled') setLeaves(Array.isArray(res[3].value) ? res[3].value : []);
        if (res[4].status === 'fulfilled') setAttendance(Array.isArray(res[4].value) ? res[4].value : []);
      } catch { toast.error('Failed to load profile'); }
      finally { setLoading(false); }
    };
    load();
  }, [user]);

  const handleSave = async () => {
    try {
      const updated = await employeeApi.update(employee.id, form);
      setEmployee(updated); setEditing(false);
      toast.success('Profile updated!');
      window.dispatchEvent(new CustomEvent('employee-updated', { detail: updated }));
    } catch { toast.error('Failed to update profile'); }
  };

  /* ── education helpers ── */
  const eduEntries = useMemo(() => {
    if (!employee?.experience) return [];
    return employee.experience.split('\n')
      .filter(l => l.startsWith('EDU|'))
      .map(l => { const [, institute, degree, year] = l.split('|'); return { institute, degree, year }; });
  }, [employee]);

  const handleAddEdu = async () => {
    if (!newEdu.institute.trim()) return;
    const line = `EDU|${newEdu.institute.trim()}|${newEdu.degree.trim()}|${newEdu.year.trim()}`;
    const base = employee?.experience || '';
    const updated = await employeeApi.update(employee.id, { ...employee, experience: base ? `${base}\n${line}` : line });
    setEmployee(updated); setForm(updated);
    setNewEdu({ institute: '', degree: '', year: '' }); setAddingEdu(false);
    toast.success('Education added!');
  };

  const handleRemoveEdu = async (idx) => {
    const lines = (employee?.experience || '').split('\n');
    const kept = lines.filter((l, i) => {
      const eduLines = lines.map((ll, ii) => ({ ll, ii })).filter(x => x.ll.startsWith('EDU|'));
      return !(l.startsWith('EDU|') && eduLines.findIndex(x => x.ii === i) === idx);
    });
    const updated = await employeeApi.update(employee.id, { ...employee, experience: kept.join('\n') });
    setEmployee(updated); setForm(updated);
    toast.success('Education removed');
  };

  /* ── computed values ── */
  const fullName = employee ? `${employee.firstName || ''} ${employee.lastName || ''}`.trim() : user?.fullName || '';
  const roleColor = ROLE_COLOR[user?.role] || '#3b82f6';
  const roleBg    = ROLE_BG[user?.role]    || '#eff6ff';

  const skillsList = useMemo(() =>
    employee?.skills ? employee.skills.split(',').map(s => s.trim()).filter(Boolean) : [], [employee]);

  const avgPerf = useMemo(() =>
    performance.length > 0 ? (performance.reduce((s, p) => s + (p.rating || 0), 0) / performance.length).toFixed(1) : null, [performance]);

  const completedCourses = useMemo(() => courses.filter(c => c.enrollmentStatus === 'COMPLETED').length, [courses]);
  const enrolledCourses  = useMemo(() => courses.filter(c => ['ENROLLED','IN_PROGRESS','COMPLETED'].includes(c.enrollmentStatus)).length, [courses]);

  const approvedLeaves = useMemo(() => leaves.filter(l => l.status === 'APPROVED').length, [leaves]);
  const LEAVE_TOTAL = 24;
  const leaveBalance = Math.max(0, LEAVE_TOTAL - approvedLeaves);

  /* years in company */
  const yearsInCo = useMemo(() => {
    if (!employee?.hireDate) return null;
    const hire = new Date(employee.hireDate);
    const now  = new Date();
    const totalMonths = (now.getFullYear() - hire.getFullYear()) * 12 + (now.getMonth() - hire.getMonth());
    return { years: Math.floor(totalMonths / 12), months: totalMonths % 12, totalMonths };
  }, [employee]);

  /* birthday */
  const bday = useMemo(() => {
    if (!employee?.dateOfBirth) return null;
    const d = new Date(employee.dateOfBirth);
    const now = new Date();
    const age = now.getFullYear() - d.getFullYear() - (now < new Date(now.getFullYear(), d.getMonth(), d.getDate()) ? 1 : 0);
    return { date: d, age, fmt: d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) };
  }, [employee]);

  /* attendance by month (last 2 months) */
  const monthlyAtt = useMemo(() => {
    const map = {};
    attendance.forEach(a => {
      const d = new Date(a.date || a.workDate || a.createdAt);
      const key = `${d.getFullYear()}-${String(d.getMonth()).padStart(2,'0')}`;
      if (!map[key]) map[key] = { label: d.toLocaleString('default', { month: 'short' }), hours: 0, leavH: 0 };
      map[key].hours += parseFloat(a.totalHours || a.hoursWorked || 0);
    });
    return Object.entries(map).sort(([a],[b]) => a.localeCompare(b)).slice(-2).map(([,v]) => v);
  }, [attendance]);

  /* courses by year (for trainings widget) */
  const coursesByYear = useMemo(() => {
    const map = {};
    courses.forEach(c => {
      const yr = c.enrolledDate ? new Date(c.enrolledDate).getFullYear() : new Date().getFullYear();
      if (!map[yr]) map[yr] = 0;
      map[yr]++;
    });
    return Object.entries(map).sort(([a],[b]) => b - a).slice(0, 3);
  }, [courses]);

  /* leave by type */
  const leaveByType = useMemo(() => {
    const map = {};
    leaves.filter(l => l.status === 'APPROVED').forEach(l => {
      const t = l.leaveType || l.type || 'Annual';
      if (!map[t]) map[t] = 0;
      map[t]++;
    });
    return Object.entries(map);
  }, [leaves]);

  /* performance bar chart */
  const perfChart = useMemo(() => ({
    labels: performance.slice(-6).map(p => new Date(p.reviewDate || p.createdAt).toLocaleDateString('en-US', { month: 'short', year: '2-digit' })),
    datasets: [{
      label: 'Score', data: performance.slice(-6).map(p => p.rating || 0),
      backgroundColor: performance.slice(-6).map((_, i) => i % 2 === 0 ? '#0891b2' : '#6366f1'),
      borderRadius: 5, borderSkipped: false,
    }],
  }), [performance]);

  const perfOpts = useMemo(() => ({
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => `Score: ${c.raw}/5` } } },
    scales: {
      y: { min: 0, max: 5, ticks: { stepSize: 1, color: '#9ca3af', font: { size: 10 } }, grid: { color: '#f3f4f6', drawBorder: false } },
      x: { ticks: { color: '#9ca3af', font: { size: 10 } }, grid: { display: false } },
    },
  }), []);

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 360 }}>
      <CircularProgress sx={{ color: '#0891b2' }} />
    </Box>
  );

  const TARGET_HRS = 168; // 21 working days × 8h

  /* ═══════════ RENDER ═══════════ */
  return (
    <Box sx={{ bgcolor: '#f5f6f8', minHeight: '100%', pb: 6 }}>

      {/* ═══ PROFILE HEADER CARD ═══ */}
      <Card sx={{ borderRadius: 0, boxShadow: '0 1px 4px rgba(0,0,0,0.08)', mb: 0 }}>
        <CardContent sx={{ p: '0 !important' }}>

          {/* manager breadcrumb */}
          {employee?.managerName && (
            <Box sx={{ px: 3, py: 1, bgcolor: '#fafafa', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap' }}>
              <BusinessIcon sx={{ fontSize: 13, color: '#9ca3af' }} />
              {employee.managerName.split(' → ').map((n, i, arr) => (
                <React.Fragment key={i}>
                  <Typography sx={{ fontSize: 12.5, color: '#6b7280', cursor: 'pointer', '&:hover': { color: '#0891b2' } }}>{n}</Typography>
                  {i < arr.length - 1 && <NavigateNextIcon sx={{ fontSize: 14, color: '#d1d5db' }} />}
                </React.Fragment>
              ))}
              <NavigateNextIcon sx={{ fontSize: 14, color: '#d1d5db' }} />
              <Typography sx={{ fontSize: 12.5, color: '#0891b2', fontWeight: 600 }}>{fullName}</Typography>
              {employee.subordinateCount > 0 && (
                <>
                  <NavigateNextIcon sx={{ fontSize: 14, color: '#d1d5db' }} />
                  <Typography sx={{ fontSize: 12.5, color: '#6b7280' }}>{employee.subordinateCount} reporting {employee.subordinateCount === 1 ? 'person' : 'people'}</Typography>
                </>
              )}
            </Box>
          )}

          {/* main header */}
          <Box sx={{ display: 'flex', gap: 3, px: 3, py: 2.5, alignItems: 'flex-start' }}>
            {/* avatar */}
            <Avatar sx={{
              width: 88, height: 88, flexShrink: 0, fontSize: '1.9rem', fontWeight: 800,
              background: 'linear-gradient(135deg, #0891b2, #0e7490)',
              border: '3px solid #e0f2fe', boxShadow: '0 4px 16px rgba(8,145,178,0.25)',
            }}>
              {(employee?.firstName?.charAt(0) || '')}{(employee?.lastName?.charAt(0) || '')}
            </Avatar>

            {/* name + details */}
            <Box sx={{ flex: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5, flexWrap: 'wrap' }}>
                <Typography sx={{ fontSize: 22, fontWeight: 800, color: '#111827' }}>{fullName}</Typography>
                <Chip label={user?.role?.replace(/_/g, ' ')} size="small"
                  sx={{ bgcolor: roleBg, color: roleColor, fontWeight: 700, fontSize: 11, height: 22, border: `1px solid ${roleColor}33` }} />
                {employee?.active === false && (
                  <Chip label="Inactive" size="small" sx={{ bgcolor: '#fef2f2', color: '#dc2626', fontWeight: 700, fontSize: 11, height: 22 }} />
                )}
              </Box>
              <Typography sx={{ fontSize: 14.5, color: '#4b5563', fontWeight: 600, mb: 0.75 }}>
                {employee?.position || '—'}{employee?.department ? ` • ${employee.department}` : ''}
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
                {[
                  { icon: <BadgeIcon sx={{ fontSize: 13 }} />, text: employee?.employeeCode ? `#${employee.employeeCode}` : null },
                  { icon: <EmailIcon sx={{ fontSize: 13 }} />, text: user?.email },
                  { icon: <PhoneIcon sx={{ fontSize: 13 }} />, text: employee?.phone },
                  { icon: <LocationOnIcon sx={{ fontSize: 13 }} />, text: employee?.location || employee?.address },
                  { icon: <CalendarTodayIcon sx={{ fontSize: 13 }} />, text: employee?.hireDate ? `Joined ${employee.hireDate}` : null },
                ].filter(x => x.text).map((x, i) => (
                  <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: '#6b7280' }}>
                    {x.icon}
                    <Typography sx={{ fontSize: 12.5, color: '#6b7280' }}>{x.text}</Typography>
                  </Box>
                ))}
              </Box>
              {/* skills row */}
              {skillsList.length > 0 && (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mt: 1.25 }}>
                  {skillsList.slice(0, 6).map((s, i) => {
                    const palette = [['#e0f2fe','#0369a1'],['#f0fdf4','#15803d'],['#fef3c7','#b45309'],['#f5f3ff','#7c3aed'],['#fff7ed','#c2410c'],['#fdf4ff','#9333ea']];
                    const [bg, col] = palette[i % palette.length];
                    return <Chip key={i} label={s} size="small" sx={{ height: 22, fontSize: 11.5, fontWeight: 600, bgcolor: bg, color: col }} />;
                  })}
                  {skillsList.length > 6 && <Chip label={`+${skillsList.length - 6} more`} size="small" sx={{ height: 22, fontSize: 11.5, bgcolor: '#f3f4f6', color: '#6b7280' }} />}
                </Box>
              )}
            </Box>

            {/* right info box */}
            <Box sx={{ flexShrink: 0, display: { xs: 'none', lg: 'block' }, minWidth: 200 }}>
              <Box sx={{ bgcolor: '#f9fafb', border: '1px solid #e5e7eb', borderRadius: 2, p: 1.5 }}>
                {[
                  ['Profile Type', user?.role?.replace(/_/g,' ')],
                  ['Department', employee?.department],
                  ['Employment', employee?.employmentType],
                  ['Key Skills', skillsList.slice(0,2).join(', ') || '—'],
                ].map(([l, v]) => (
                  <Box key={l} sx={{ mb: 1, '&:last-child': { mb: 0 } }}>
                    <Typography sx={{ fontSize: 10.5, color: '#9ca3af', fontWeight: 500 }}>{l}</Typography>
                    <Typography sx={{ fontSize: 12.5, fontWeight: 600, color: '#374151' }}>{v || '—'}</Typography>
                  </Box>
                ))}
              </Box>
            </Box>
          </Box>

          {/* tab nav */}
          <Box sx={{ borderTop: '1px solid #e5e7eb' }}>
            <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}
              sx={{
                px: 2, minHeight: 44,
                '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, fontSize: 13.5, minHeight: 44, py: 0 },
                '& .MuiTabs-indicator': { bgcolor: '#0891b2', height: 2.5 },
                '& .Mui-selected': { color: '#0891b2 !important' },
              }}>
              <Tab label="Overview" />
              <Tab label="Work Experience" />
              <Tab label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                  Skills
                  {skillsList.length > 0 && <Chip label={skillsList.length} size="small" sx={{ height: 18, fontSize: 10.5, bgcolor: '#fef9c3', color: '#854d0e', fontWeight: 700 }} />}
                </Box>
              } />
              <Tab label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                  Performance
                  {performance.length > 0 && <Chip label={performance.length} size="small" sx={{ height: 18, fontSize: 10.5, bgcolor: '#eef2ff', color: '#4338ca', fontWeight: 700 }} />}
                </Box>
              } />
            </Tabs>
          </Box>
        </CardContent>
      </Card>

      {/* ═══ EDIT CONTROLS (float if editing) ═══ */}
      {editing && (
        <Box sx={{ bgcolor: '#fff', borderBottom: '1px solid #e5e7eb', px: 3, py: 1, display: 'flex', justifyContent: 'flex-end', gap: 1, position: 'sticky', top: 0, zIndex: 100 }}>
          <Button startIcon={<CancelIcon sx={{ fontSize: 15 }} />} onClick={() => { setEditing(false); setForm(employee); }}
            sx={{ textTransform: 'none', fontWeight: 600, fontSize: 13, borderRadius: 2 }}>Cancel</Button>
          <Button variant="contained" startIcon={<SaveIcon sx={{ fontSize: 15 }} />} onClick={handleSave}
            sx={{ bgcolor: '#0891b2', '&:hover': { bgcolor: '#0e7490' }, textTransform: 'none', fontWeight: 600, fontSize: 13, borderRadius: 2 }}>Save Changes</Button>
        </Box>
      )}

      {/* ═══════════════ PAGE CONTENT ═══════════════ */}
      <Box sx={{ px: { xs: 1.5, md: 3 }, pt: 0 }}>

        {/* ─────────── TAB 0: OVERVIEW ─────────── */}
        {activeTab === 0 && (
          <Box>

            {/* ── STAFFING PROFILE ── */}
            <Sect title="Staffing Profile" />
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6} md={3}>
                <W title="Education" minH={150}>
                  <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
                    <Button size="small" startIcon={<AddIcon sx={{ fontSize: 13 }} />} onClick={() => setAddingEdu(true)}
                      sx={{ textTransform: 'none', fontSize: 12, color: '#0891b2', fontWeight: 600 }}>Add</Button>
                  </Box>

                  {addingEdu && (
                    <Box sx={{ mb: 1.5, p: 1.25, bgcolor: '#f0f9ff', borderRadius: 1.5, border: '1px solid #bae6fd' }}>
                      <Grid container spacing={1}>
                        <Grid item xs={12}>
                          <TextField fullWidth size="small" label="Institute Name" value={newEdu.institute}
                            onChange={e => setNewEdu(p => ({ ...p, institute: e.target.value }))}
                            sx={{ '& .MuiOutlinedInput-root': { borderRadius: 1.5, fontSize: 12.5 } }}
                            InputLabelProps={{ style: { fontSize: 12.5 } }} />
                        </Grid>
                        <Grid item xs={8}>
                          <TextField fullWidth size="small" label="Degree / Course" value={newEdu.degree}
                            onChange={e => setNewEdu(p => ({ ...p, degree: e.target.value }))}
                            sx={{ '& .MuiOutlinedInput-root': { borderRadius: 1.5, fontSize: 12.5 } }}
                            InputLabelProps={{ style: { fontSize: 12.5 } }} />
                        </Grid>
                        <Grid item xs={4}>
                          <TextField fullWidth size="small" label="Year" value={newEdu.year}
                            onChange={e => setNewEdu(p => ({ ...p, year: e.target.value }))}
                            sx={{ '& .MuiOutlinedInput-root': { borderRadius: 1.5, fontSize: 12.5 } }}
                            InputLabelProps={{ style: { fontSize: 12.5 } }} />
                        </Grid>
                        <Grid item xs={12}>
                          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
                            <Button size="small" onClick={() => { setAddingEdu(false); setNewEdu({ institute: '', degree: '', year: '' }); }}
                              sx={{ textTransform: 'none', fontSize: 12 }}>Cancel</Button>
                            <Button size="small" variant="contained" onClick={handleAddEdu}
                              sx={{ bgcolor: '#0891b2', '&:hover': { bgcolor: '#0e7490' }, textTransform: 'none', fontSize: 12, borderRadius: 1.5 }}>Save</Button>
                          </Box>
                        </Grid>
                      </Grid>
                    </Box>
                  )}

                  {eduEntries.length === 0 && !addingEdu ? (
                    <Box sx={{ textAlign: 'center', py: 2 }}>
                      <SchoolIcon sx={{ fontSize: 32, color: '#e5e7eb', mb: 0.5 }} />
                      <Typography sx={{ fontSize: 12, color: '#9ca3af' }}>No education added yet</Typography>
                    </Box>
                  ) : eduEntries.map((edu, i) => (
                    <Box key={i} sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.25, py: 0.75, borderBottom: i < eduEntries.length - 1 ? '1px solid #f3f4f6' : 'none' }}>
                      <Box sx={{ width: 30, height: 30, borderRadius: 1, bgcolor: '#f0fdf4', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, mt: 0.25 }}>
                        <SchoolIcon sx={{ fontSize: 15, color: '#16a34a' }} />
                      </Box>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#111827', lineHeight: 1.3 }}>{edu.institute}</Typography>
                        {edu.degree && <Typography sx={{ fontSize: 12, color: '#374151' }}>{edu.degree}</Typography>}
                        {edu.year && <Typography sx={{ fontSize: 11, color: '#9ca3af' }}>{edu.year}</Typography>}
                      </Box>
                      <IconButton size="small" onClick={() => handleRemoveEdu(i)}
                        sx={{ color: '#d1d5db', '&:hover': { color: '#ef4444' }, p: 0.25, flexShrink: 0 }}>
                        <DeleteOutlineIcon sx={{ fontSize: 15 }} />
                      </IconButton>
                    </Box>
                  ))}
                </W>
              </Grid>

              {/* Personal info summary */}
              <Grid item xs={12} md={9}>
                <W title="Personal Information" minH={120}>
                  <Grid container spacing={1}>
                    {[
                      ['Email', user?.email],
                      ['Phone', employee?.phone],
                      ['Date of Birth', employee?.dateOfBirth],
                      ['Location', employee?.location || employee?.address],
                      ['Department', employee?.department],
                      ['Position', employee?.position],
                      ['Employment Type', employee?.employmentType],
                      ['Employee Code', employee?.employeeCode ? `#${employee.employeeCode}` : null],
                      ['Manager', employee?.managerName],
                      ['Hire Date', employee?.hireDate],
                      ['Source of Hire', employee?.sourceOfHire],
                      ['Current Experience', employee?.currentExperience],
                      ['Total Experience', employee?.totalExperience],
                    ].map(([l, v]) => (
                      <Grid item xs={6} sm={4} key={l}>
                        <Box sx={{ p: 1, bgcolor: '#f9fafb', borderRadius: 1.5 }}>
                          <Typography sx={{ fontSize: 10, color: '#9ca3af', fontWeight: 500, mb: 0.25 }}>{l}</Typography>
                          <Typography sx={{ fontSize: 12, fontWeight: 600, color: '#374151', wordBreak: 'break-word' }}>{v || '—'}</Typography>
                        </Box>
                      </Grid>
                    ))}
                  </Grid>
                </W>
              </Grid>
            </Grid>

            {/* ── ORG PROFILE ── */}
            <Sect title="Org Profile" />
            <Grid container spacing={2}>
              {/* Years in company */}
              <Grid item xs={12} sm={6} md={3}>
                <W title="Years in SAS" minH={150}>
                  {yearsInCo ? (
                    <Box>
                      <Box sx={{ display: 'flex', gap: 0.5, mb: 1.5 }}>
                        {Array.from({ length: Math.min(yearsInCo.years + (yearsInCo.months > 0 ? 1 : 0), 8) }).map((_, i) => (
                          <Box key={i} sx={{ flex: 1, height: 14, borderRadius: 1, bgcolor: i < yearsInCo.years ? '#16a34a' : '#bbf7d0' }} />
                        ))}
                      </Box>
                      <Typography sx={{ fontSize: 22, fontWeight: 800, color: '#16a34a', lineHeight: 1.1 }}>
                        {yearsInCo.years > 0 ? `${yearsInCo.years} yr${yearsInCo.years !== 1 ? 's' : ''}` : ''}{yearsInCo.months > 0 ? ` ${yearsInCo.months} mo` : ''}
                      </Typography>
                      <Typography sx={{ fontSize: 12, color: '#6b7280', mt: 0.25 }}>Since {employee?.hireDate}</Typography>
                    </Box>
                  ) : (
                    <Typography sx={{ fontSize: 12.5, color: '#9ca3af', fontStyle: 'italic' }}>Hire date not set</Typography>
                  )}
                </W>
              </Grid>

              {/* Org Info */}
              <Grid item xs={12} sm={6} md={3}>
                <W title="Org Info" minH={150}>
                  {[
                    { dot: '#0891b2', label: employee?.department || '—', sub: 'Department' },
                    { dot: '#6366f1', label: employee?.position || '—', sub: 'Position' },
                    { dot: '#16a34a', label: employee?.employmentType || '—', sub: 'Employment' },
                  ].map(({ dot, label, sub }, i) => (
                    <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 1.25, py: 0.75, borderBottom: i < 2 ? '1px solid #f9fafb' : 'none' }}>
                      <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: dot, flexShrink: 0 }} />
                      <Box>
                        <Typography sx={{ fontSize: 12.5, fontWeight: 600, color: '#374151', lineHeight: 1.2 }}>{label}</Typography>
                        <Typography sx={{ fontSize: 10.5, color: '#9ca3af' }}>{sub}</Typography>
                      </Box>
                    </Box>
                  ))}
                </W>
              </Grid>

              {/* Birthday */}
              <Grid item xs={12} sm={6} md={3}>
                <W title="Birthday" minH={150}>
                  {bday ? (
                    <Box sx={{ textAlign: 'center', py: 1 }}>
                      <CakeIcon sx={{ fontSize: 40, color: '#ec4899', mb: 0.75 }} />
                      <Typography sx={{ fontSize: 17, fontWeight: 800, color: '#111827' }}>
                        {bday.date.toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' })}
                      </Typography>
                      <Typography sx={{ fontSize: 12.5, color: '#6b7280', mt: 0.25 }}>Birthday • Age {bday.age}</Typography>
                      <Chip label={`${bday.age} years old`} size="small" sx={{ mt: 0.75, bgcolor: '#fdf4ff', color: '#9333ea', fontWeight: 600, fontSize: 11 }} />
                    </Box>
                  ) : (
                    <Box sx={{ textAlign: 'center', py: 2 }}>
                      <CakeIcon sx={{ fontSize: 36, color: '#e5e7eb', mb: 0.5 }} />
                      <Typography sx={{ fontSize: 12.5, color: '#9ca3af' }}>Date of birth not set</Typography>
                    </Box>
                  )}
                </W>
              </Grid>
            </Grid>

            {/* ── TIME TRACKING + PROFESSIONAL PROFILE ── */}
            <Sect title="Time Tracking & Professional Profile" />
            <Grid container spacing={2}>
              {/* Reported Time */}
              <Grid item xs={12} sm={6} md={3}>
                <W title="Reported Time" minH={100}>
                  {attendance.length === 0 ? (
                    <Box sx={{ textAlign: 'center', py: 2 }}>
                      <AccessTimeIcon sx={{ fontSize: 30, color: '#e5e7eb', mb: 0.5 }} />
                      <Typography sx={{ fontSize: 12, color: '#9ca3af' }}>No attendance data available</Typography>
                    </Box>
                  ) : (
                    <Box>
                      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                        {monthlyAtt.length === 0 ? (
                          (() => {
                            const totalHrs = attendance.reduce((s, a) => s + parseFloat(a.totalHours || a.hoursWorked || 0), 0);
                            const pct = Math.min(100, Math.round((totalHrs / TARGET_HRS) * 100));
                            return (
                              <TimeDonut pct={pct} label="Total" hours={Math.round(totalHrs)} target={TARGET_HRS}
                                breakdown={[{ color: '#0891b2', label: 'Work', value: `${Math.round(totalHrs)}h` }]} />
                            );
                          })()
                        ) : monthlyAtt.map((m, i) => {
                          const pct = Math.min(100, Math.round((m.hours / TARGET_HRS) * 100));
                          return (
                            <TimeDonut key={i} pct={pct} label={m.label} hours={Math.round(m.hours)} target={TARGET_HRS}
                              breakdown={[
                                { color: '#0891b2', label: 'Work', value: `${Math.round(m.hours)}h` },
                                { color: '#f59e0b', label: 'Leave', value: `${Math.round(m.leavH || 0)}h` },
                              ]} />
                          );
                        })}
                      </Box>
                    </Box>
                  )}
                </W>
              </Grid>

              {/* Vacation / Leave */}
              <Grid item xs={12} sm={6} md={3}>
                <W title="Vacation" minH={100}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <BeachAccessIcon sx={{ fontSize: 26, color: '#16a34a' }} />
                    <Box>
                      <Typography sx={{ fontSize: 20, fontWeight: 800, color: '#16a34a', lineHeight: 1 }}>{leaveBalance}d</Typography>
                      <Typography sx={{ fontSize: 11, color: '#6b7280' }}>Available to go</Typography>
                    </Box>
                  </Box>
                  <Divider sx={{ mb: 0.75 }} />
                  {[
                    { icon: '☀', l: 'Annual Leave', taken: approvedLeaves, of: LEAVE_TOTAL, c: '#0891b2' },
                    { icon: '🏥', l: 'Sick Leave', taken: leaves.filter(l => l.status === 'APPROVED' && (l.leaveType || '').toLowerCase().includes('sick')).length, of: 6, c: '#ef4444' },
                  ].map(({ icon, l, taken, of, c }) => (
                    <Box key={l} sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5 }}>
                      <Typography sx={{ fontSize: 12 }}>{icon}</Typography>
                      <Box sx={{ flex: 1 }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                          <Typography sx={{ fontSize: 11, color: '#374151', fontWeight: 500 }}>{l}</Typography>
                          <Typography sx={{ fontSize: 10.5, color: '#6b7280' }}>{taken}d of {of}d</Typography>
                        </Box>
                        <LinearProgress variant="determinate" value={Math.min(100, (taken / of) * 100)}
                          sx={{ height: 3, borderRadius: 2, bgcolor: '#f3f4f6', mt: 0.2, '& .MuiLinearProgress-bar': { bgcolor: c } }} />
                      </Box>
                    </Box>
                  ))}
                </W>
              </Grid>
              {/* Key Strengths */}
              <Grid item xs={12} sm={6} md={3}>
                <W title="Key Strengths" seeAll="See All Skills" seeAllN={skillsList.length} onSeeAll={() => setActiveTab(2)} minH={160}>
                  {skillsList.length === 0 ? (
                    <Box sx={{ textAlign: 'center', py: 2 }}>
                      <Typography sx={{ fontSize: 12, color: '#9ca3af', fontStyle: 'italic' }}>No skills added yet</Typography>
                      <Button size="small" startIcon={<EditIcon sx={{ fontSize: 12 }} />} onClick={() => setEditing(true)}
                        sx={{ mt: 0.75, textTransform: 'none', fontSize: 11.5, color: '#0891b2' }}>Add Skills</Button>
                    </Box>
                  ) : (
                    <Box>
                      {skillsList.slice(0, 6).map((s, i) => {
                        const dots = ['#6366f1','#0891b2','#16a34a','#f59e0b','#ec4899','#8b5cf6'];
                        return (
                          <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 0.5, borderBottom: i < Math.min(skillsList.length, 6) - 1 ? '1px solid #f9fafb' : 'none' }}>
                            <Box sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: dots[i % dots.length], flexShrink: 0 }} />
                            <Typography sx={{ fontSize: 12, color: '#374151', fontWeight: 500 }}>{s}</Typography>
                          </Box>
                        );
                      })}
                    </Box>
                  )}
                </W>
              </Grid>

              {/* Courses / Training count */}
              <Grid item xs={12} sm={6} md={3}>
                <W title="Trainings" seeAll={enrolledCourses > 0 ? 'See All' : null} seeAllN={enrolledCourses} onSeeAll={() => navigate('/courses')} minH={130}>
                  {enrolledCourses === 0 ? (
                    <Box sx={{ textAlign: 'center', py: 2 }}>
                      <SchoolIcon sx={{ fontSize: 28, color: '#e5e7eb', mb: 0.5 }} />
                      <Typography sx={{ fontSize: 12, color: '#9ca3af' }}>No trainings enrolled</Typography>
                    </Box>
                  ) : (
                    <Box>
                      {coursesByYear.map(([yr, cnt], i) => {
                        const maxCnt = Math.max(...coursesByYear.map(([,n]) => n), 1);
                        const pct = (cnt / maxCnt) * 100;
                        return (
                          <Box key={yr} sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.75 }}>
                            <Typography sx={{ fontSize: 11, fontWeight: 700, color: '#374151', width: 32, flexShrink: 0 }}>{yr}</Typography>
                            <Box sx={{ flex: 1, height: 8, bgcolor: '#f3f4f6', borderRadius: 2, overflow: 'hidden' }}>
                              <Box sx={{ height: '100%', width: `${pct}%`, bgcolor: i === 0 ? '#f59e0b' : '#0891b2', borderRadius: 2, transition: 'width 0.5s' }} />
                            </Box>
                            <Typography sx={{ fontSize: 11, color: '#6b7280', width: 24, textAlign: 'right' }}>{cnt}</Typography>
                          </Box>
                        );
                      })}
                      <Divider sx={{ my: 1 }} />
                      <Typography sx={{ fontSize: 18, fontWeight: 800, color: '#0891b2' }}>{enrolledCourses}</Typography>
                      <Typography sx={{ fontSize: 11, color: '#6b7280' }}>Trainings Enrolled in Total</Typography>
                      <Typography sx={{ fontSize: 10.5, color: '#9ca3af' }}>{completedCourses} Completed</Typography>
                    </Box>
                  )}
                </W>
              </Grid>
            </Grid>
          </Box>
        )}

        {/* ─────────── TAB 1: WORK EXPERIENCE ─────────── */}
        {activeTab === 1 && (
          <Box sx={{ mt: 2 }}>
            <W title="Work Experience & Education">
              <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
                {!editing ? (
                  <Button startIcon={<EditIcon sx={{ fontSize: 14 }} />} size="small" onClick={() => setEditing(true)}
                    sx={{ textTransform: 'none', fontWeight: 600, color: '#0891b2', borderColor: '#0891b2', borderRadius: 2 }} variant="outlined">Edit</Button>
                ) : (
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button size="small" onClick={() => { setEditing(false); setForm(employee); }}
                      sx={{ textTransform: 'none', fontWeight: 600, borderRadius: 2 }}>Cancel</Button>
                    <Button variant="contained" size="small" onClick={handleSave}
                      sx={{ bgcolor: '#0891b2', '&:hover': { bgcolor: '#0e7490' }, textTransform: 'none', fontWeight: 600, borderRadius: 2 }}>Save</Button>
                  </Box>
                )}
              </Box>
              {editing ? (
                <TextField fullWidth multiline rows={6} size="small" label="Experience & Education"
                  placeholder="Add your work experience and education details (one entry per line)..."
                  value={form.experience || ''}
                  onChange={e => setForm(prev => ({ ...prev, experience: e.target.value }))}
                  sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
              ) : (
                <Box>
                  {(employee?.experience || '').split('\n').filter(l => l.trim() && !l.startsWith('EDU|')).map((line, i, arr) => (
                    <Box key={i} sx={{ display: 'flex', gap: 1.5, py: 1.25, borderBottom: i < arr.length - 1 ? '1px solid #f3f4f6' : 'none' }}>
                      <Box sx={{ width: 32, height: 32, borderRadius: 1.5, bgcolor: '#f0fdf4', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, mt: 0.25 }}>
                        <WorkIcon sx={{ fontSize: 15, color: '#16a34a' }} />
                      </Box>
                      <Typography sx={{ fontSize: 13.5, color: '#374151', lineHeight: 1.6, flex: 1 }}>{line}</Typography>
                    </Box>
                  ))}
                  {!(employee?.experience || '').split('\n').some(l => l.trim() && !l.startsWith('EDU|')) && (
                    <Typography sx={{ fontSize: 13, color: '#9ca3af', textAlign: 'center', py: 4 }}>No experience details added yet</Typography>
                  )}
                </Box>
              )}
            </W>
          </Box>
        )}

        {/* ─────────── TAB 2: SKILLS ─────────── */}
        {activeTab === 2 && (
          <Box sx={{ mt: 2 }}>
            <W title="Skills">
              <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
                {!editing ? (
                  <Button startIcon={<EditIcon sx={{ fontSize: 14 }} />} size="small" onClick={() => setEditing(true)}
                    sx={{ textTransform: 'none', fontWeight: 600, color: '#0891b2', borderColor: '#0891b2', borderRadius: 2 }} variant="outlined">Edit Skills</Button>
                ) : (
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button size="small" onClick={() => { setEditing(false); setForm(employee); }}
                      sx={{ textTransform: 'none', borderRadius: 2 }}>Cancel</Button>
                    <Button variant="contained" size="small" onClick={handleSave}
                      sx={{ bgcolor: '#0891b2', '&:hover': { bgcolor: '#0e7490' }, textTransform: 'none', borderRadius: 2 }}>Save</Button>
                  </Box>
                )}
              </Box>
              {editing ? (
                <TextField fullWidth size="small" multiline rows={3} label="Skills (comma-separated)"
                  value={form.skills || ''}
                  onChange={e => setForm(prev => ({ ...prev, skills: e.target.value }))}
                  helperText="e.g., React, Java, Spring Boot, MySQL"
                  sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />
              ) : (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                  {skillsList.map((s, i) => {
                    const palette = [['#e0f2fe','#0369a1'],['#f0fdf4','#15803d'],['#fef3c7','#b45309'],['#f5f3ff','#7c3aed'],['#fff7ed','#c2410c'],['#fdf4ff','#9333ea'],['#ecfdf5','#047857'],['#fff1f2','#be123c']];
                    const [bg, col] = palette[i % palette.length];
                    return <Chip key={i} label={s} sx={{ height: 28, fontSize: 12.5, fontWeight: 600, bgcolor: bg, color: col }} />;
                  })}
                  {skillsList.length === 0 && <Typography sx={{ color: '#9ca3af', fontSize: 13 }}>No skills added yet</Typography>}
                </Box>
              )}
            </W>
          </Box>
        )}

        {/* ─────────── TAB 3: PERFORMANCE ─────────── */}
        {activeTab === 3 && (
          <Box sx={{ mt: 2 }}>
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <W title="Performance Trend">
                  <Box sx={{ height: 220 }}>
                    {performance.length > 0 ? <Bar data={perfChart} options={perfOpts} /> : (
                      <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Typography sx={{ color: '#9ca3af', fontSize: 13 }}>No performance data</Typography>
                      </Box>
                    )}
                  </Box>
                </W>
              </Grid>
              {performance.map((p, i) => (
                <Grid item xs={12} sm={6} md={4} key={p.id}>
                  <W title={p.reviewDate ? new Date(p.reviewDate).toLocaleDateString('en-US', { month: 'long', year: 'numeric' }) : `Review ${i + 1}`}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Box sx={{ width: 46, height: 46, borderRadius: 2, bgcolor: '#eef2ff', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                        <Typography sx={{ fontSize: 18, fontWeight: 800, color: '#6366f1' }}>{p.rating}</Typography>
                      </Box>
                      <Box>
                        <Box sx={{ display: 'flex', mb: 0.25 }}>
                          {[1,2,3,4,5].map(s => (
                            <Box key={s} sx={{ fontSize: 13, color: s <= (p.rating || 0) ? '#f59e0b' : '#e5e7eb' }}>★</Box>
                          ))}
                        </Box>
                        <Typography sx={{ fontSize: 12, color: '#374151', fontWeight: 600 }}>{p.reviewerName || 'Manager'}</Typography>
                        {p.comments && <Typography sx={{ fontSize: 11.5, color: '#6b7280', mt: 0.25, lineHeight: 1.4 }}>{p.comments.substring(0, 80)}{p.comments.length > 80 ? '…' : ''}</Typography>}
                      </Box>
                    </Box>
                  </W>
                </Grid>
              ))}
            </Grid>
          </Box>
        )}

      </Box>
    </Box>
  );
};

export default ProfilePage;
