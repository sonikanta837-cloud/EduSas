import React, { useEffect, useState, useCallback } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, Button, Grid,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  CircularProgress, Rating, Avatar, Autocomplete,
  FormControl, InputLabel, Select, MenuItem, Tabs, Tab,
  InputAdornment, OutlinedInput, IconButton, Divider, Menu,
} from '@mui/material';
import AddIcon            from '@mui/icons-material/Add';
import SearchIcon         from '@mui/icons-material/Search';
import RefreshIcon        from '@mui/icons-material/Refresh';
import ViewModuleIcon     from '@mui/icons-material/ViewModule';
import ViewListIcon       from '@mui/icons-material/ViewList';
import MoreVertIcon       from '@mui/icons-material/MoreVert';
import CalendarTodayIcon  from '@mui/icons-material/CalendarToday';
import EmojiEventsIcon    from '@mui/icons-material/EmojiEvents';
import AccessTimeIcon     from '@mui/icons-material/AccessTime';
import AssignmentIcon     from '@mui/icons-material/Assignment';
import StarRoundedIcon    from '@mui/icons-material/StarRounded';
import ChevronLeftIcon    from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon   from '@mui/icons-material/ChevronRight';
import DeleteOutlineIcon  from '@mui/icons-material/DeleteOutline';
import EditOutlinedIcon   from '@mui/icons-material/EditOutlined';
import { performanceApi }  from '../api/performanceApi';
import { employeeApi }     from '../api/employeeApi';
import { toast }           from 'react-toastify';
import PerformancePipTab   from './PerformancePipTab';

/* ─── constants ─── */
const BLANK_FORM = {
  employeeId: null, rating: 3, comments: '', strengths: '',
  areasOfImprovement: '', reviewDate: new Date().toISOString().split('T')[0], reviewPeriod: '',
};

const buildQuarterOptions = () => {
  const now = new Date();
  const yr  = now.getFullYear();
  const cq  = Math.ceil((now.getMonth() + 1) / 3);
  const opts = [];
  // Only current year, up to and including the current quarter
  for (let q = 1; q <= cq; q++) opts.push(`Q${q} ${yr}`);
  return opts; // oldest → newest
};
const QUARTER_OPTIONS = buildQuarterOptions();
const CURRENT_QUARTER = QUARTER_OPTIONS[QUARTER_OPTIONS.length - 1];
const ROWS_PER_PAGE   = 6;

/* ─── helpers ─── */
const AVATAR_PALETTE = [
  { bg: '#ede9fe', color: '#7c3aed' },
  { bg: '#fce7f3', color: '#be185d' },
  { bg: '#d1fae5', color: '#065f46' },
  { bg: '#fef3c7', color: '#92400e' },
  { bg: '#dbeafe', color: '#1e40af' },
  { bg: '#ffedd5', color: '#9a3412' },
  { bg: '#e0f2fe', color: '#0369a1' },
  { bg: '#f3e8ff', color: '#6b21a8' },
];

const avatarColor = (name = '') => {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = name.charCodeAt(i) + ((h << 5) - h);
  return AVATAR_PALETTE[Math.abs(h) % AVATAR_PALETTE.length];
};

const initials = (name = '') =>
  name.split(' ').filter(Boolean).map(w => w[0]).join('').slice(0, 2).toUpperCase();

const fmtDate = (d) => {
  if (!d) return '';
  return new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
};

const ratingInfo = (r) => {
  if (r >= 4.5) return { label: 'Excellent',  color: '#059669', bg: '#ecfdf5', border: '#6ee7b7', bar: '#10b981' };
  if (r >= 4)   return { label: 'Very Good',  color: '#16a34a', bg: '#f0fdf4', border: '#86efac', bar: '#22c55e' };
  if (r >= 3.5) return { label: 'Good',       color: '#16a34a', bg: '#f0fdf4', border: '#86efac', bar: '#22c55e' };
  if (r >= 2.5) return { label: 'Average',    color: '#d97706', bg: '#fffbeb', border: '#fde68a', bar: '#f59e0b' };
  if (r >= 1.5) return { label: 'Below Avg',  color: '#ea580c', bg: '#fff7ed', border: '#fed7aa', bar: '#f97316' };
  return               { label: 'Poor',       color: '#dc2626', bg: '#fef2f2', border: '#fca5a5', bar: '#ef4444' };
};

/* ─── helpers ─── */
// eslint-disable-next-line no-unused-vars
const splitItems = (text) =>
  text ? text.split(/[,\n]+/).map(s => s.trim()).filter(Boolean) : [];

// eslint-disable-next-line no-unused-vars
const assessmentInfo = (rating) => {
  if (rating >= 4.5) return { label: 'Excellent Performance',   desc: 'Consistently exceeds expectations and delivers exceptional results' };
  if (rating >= 4)   return { label: 'Very Good Performance',   desc: 'Exceeds expectations in most areas of performance' };
  if (rating >= 3.5) return { label: 'Good Performance',        desc: 'Meets expectations and delivers consistent results' };
  if (rating >= 2.5) return { label: 'Average Performance',     desc: 'Meets most expectations with some areas for improvement' };
  if (rating >= 1.5) return { label: 'Below Average',           desc: 'Partially meets expectations, with room for improvement' };
  return               { label: 'Needs Improvement',            desc: 'Below expectations, requires significant improvement' };
};

const getTrend = (revs) => {
  if (revs.length === 0) return { label: '—',            icon: '—',  color: '#94a3b8', bg: '#f8fafc' };
  if (revs.length === 1) return { label: 'First Review', icon: '★',  color: '#6366f1', bg: '#eef2ff' };
  const diff = (revs[0].rating || 0) - (revs[1].rating || 0);
  if (diff > 0) return { label: 'Improving', icon: '↗', color: '#16a34a', bg: '#f0fdf4' };
  if (diff < 0) return { label: 'Declining',  icon: '↘', color: '#dc2626', bg: '#fef2f2' };
  return               { label: 'Steady',     icon: '→', color: '#d97706', bg: '#fffbeb' };
};

/* ─── sub-components ─── */
const StatCard = ({ icon, label, value, sub, accent, extra }) => (
  <Box sx={{
    bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px',
    boxShadow: '0 2px 8px rgba(0,0,0,0.05)', p: 2.5,
    display: 'flex', alignItems: 'center', gap: 2, height: '100%',
  }}>
    <Box sx={{
      width: 52, height: 52, borderRadius: '14px',
      bgcolor: `${accent}18`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
    }}>
      {React.cloneElement(icon, { sx: { fontSize: 26, color: accent } })}
    </Box>
    <Box sx={{ flex: 1 }}>
      <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.5px', mb: .3 }}>
        {label}
      </Typography>
      <Typography sx={{ fontSize: 26, fontWeight: 800, color: '#0f172a', lineHeight: 1, mb: .3 }}>{value}</Typography>
      {extra || <Typography sx={{ fontSize: 12.5, color: '#64748b' }}>{sub}</Typography>}
    </Box>
  </Box>
);

const ReviewCard = ({ r, isAdmin, isManager, myEmployeeId, onViewDetails, onEdit, onDelete }) => {
  const [anchorEl, setAnchorEl] = useState(null);
  const menuOpen  = Boolean(anchorEl);
  const info      = ratingInfo(r.rating ?? 0);
  const avColor   = avatarColor(r.employeeName || '');
  const pct       = Math.min(((r.rating ?? 0) / 5) * 100, 100);
  const canDelete = isAdmin || isManager || r.reviewerId === myEmployeeId;

  return (
    <Box sx={{
      bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px',
      boxShadow: '0 2px 8px rgba(0,0,0,0.05)', p: 2.5, height: '100%',
      display: 'flex', flexDirection: 'column',
      transition: 'box-shadow .2s, transform .2s',
      '&:hover': { boxShadow: '0 6px 20px rgba(0,0,0,0.09)', transform: 'translateY(-2px)' },
    }}>
      {/* Header row */}
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, mb: 1.5 }}>
        <Avatar sx={{ width: 46, height: 46, bgcolor: avColor.bg, color: avColor.color, fontSize: '0.88rem', fontWeight: 700, flexShrink: 0 }}>
          {initials(r.employeeName || r.reviewerName || '')}
        </Avatar>
        <Box sx={{ flex: 1, overflow: 'hidden', minWidth: 0 }}>
          <Typography sx={{ fontSize: 14.5, fontWeight: 700, color: '#0f172a' }} noWrap>
            {r.employeeName || 'Unknown'}
          </Typography>
          <Typography sx={{ fontSize: 12.5, color: '#64748b', lineHeight: 1.4 }}>
            Reviewed by: {r.reviewerName || '—'}
          </Typography>
          <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>{r.reviewPeriod || '—'}</Typography>
        </Box>
        {/* Rating badge */}
        <Box sx={{ flexShrink: 0, textAlign: 'center' }}>
          <Box sx={{ width: 54, height: 54, borderRadius: '50%', bgcolor: info.bg, border: `2px solid ${info.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Typography sx={{ fontSize: 12.5, fontWeight: 800, color: info.color, lineHeight: 1 }}>
              {r.rating}/5
            </Typography>
          </Box>
          <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: info.color, mt: .4 }}>{info.label}</Typography>
        </Box>
      </Box>

      {/* Progress bar */}
      <Box sx={{ height: 5, bgcolor: '#f1f5f9', borderRadius: 4, overflow: 'hidden', mb: 1.75 }}>
        <Box sx={{ height: '100%', width: `${pct}%`, bgcolor: info.bar, borderRadius: 4, transition: 'width .5s ease' }} />
      </Box>

      {/* Comment */}
      {r.comments && (
        <Typography sx={{ fontSize: 13, color: '#64748b', fontStyle: 'italic', mb: 1.25, lineHeight: 1.5 }}>
          "{r.comments}"
        </Typography>
      )}

      {/* Strengths / Improve */}
      {r.strengths && (
        <Box sx={{ mb: .6 }}>
          <Typography component="span" sx={{ fontSize: 12.5, fontWeight: 700, color: '#16a34a' }}>Strengths: </Typography>
          <Typography component="span" sx={{ fontSize: 12.5, color: '#374151' }}>{r.strengths}</Typography>
        </Box>
      )}
      {r.areasOfImprovement && (
        <Box sx={{ mb: 1.5 }}>
          <Typography component="span" sx={{ fontSize: 12.5, fontWeight: 700, color: '#d97706' }}>Improve: </Typography>
          <Typography component="span" sx={{ fontSize: 12.5, color: '#374151' }}>{r.areasOfImprovement}</Typography>
        </Box>
      )}

      <Box sx={{ flex: 1 }} />

      {/* Footer */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 1.5, pt: 1.5, borderTop: '1px solid #f8fafc' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: .75 }}>
          <CalendarTodayIcon sx={{ fontSize: 13, color: '#94a3b8' }} />
          <Typography sx={{ fontSize: 12, color: '#64748b' }}>{fmtDate(r.reviewDate)}</Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: .75 }}>
          <Button
            size="small" variant="outlined"
            onClick={() => onViewDetails(r)}
            sx={{ fontSize: 12, py: .45, px: 1.5, borderRadius: '8px', borderColor: '#1e3a5f', color: '#1e3a5f', fontWeight: 600, '&:hover': { bgcolor: 'rgba(30,58,95,0.06)', borderColor: '#152d4a' } }}
          >
            View Details
          </Button>
          {canDelete && (
            <IconButton
              size="small"
              onClick={e => setAnchorEl(e.currentTarget)}
              sx={{ color: '#94a3b8', '&:hover': { bgcolor: '#f8fafc', color: '#374151' } }}
            >
              <MoreVertIcon sx={{ fontSize: 18 }} />
            </IconButton>
          )}
          <Menu
            anchorEl={anchorEl}
            open={menuOpen}
            onClose={() => setAnchorEl(null)}
            PaperProps={{ sx: { borderRadius: '10px', boxShadow: '0 4px 20px rgba(0,0,0,0.12)', border: '1px solid #f1f5f9', minWidth: 160 } }}
            transformOrigin={{ horizontal: 'right', vertical: 'top' }}
            anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
          >
            {canDelete && (
              <MenuItem
                onClick={() => { setAnchorEl(null); onEdit(r); }}
                sx={{ fontSize: 13.5, gap: 1.25, py: 1.1, color: '#374151' }}
              >
                <EditOutlinedIcon sx={{ fontSize: 17, color: '#64748b' }} />
                Edit Review
              </MenuItem>
            )}
            {canDelete && (
              <MenuItem
                onClick={() => { setAnchorEl(null); onDelete(r); }}
                sx={{ fontSize: 13.5, gap: 1.25, py: 1.1, color: '#dc2626' }}
              >
                <DeleteOutlineIcon sx={{ fontSize: 17, color: '#dc2626' }} />
                Delete Review
              </MenuItem>
            )}
          </Menu>
        </Box>
      </Box>
    </Box>
  );
};

/* ════════════════════════════════════════════════════
   PAGE
════════════════════════════════════════════════════ */
const PerformancePage = () => {
  const { user } = useSelector((s) => s.auth);
  const isAdmin   = user?.role === 'ADMIN' || user?.role === 'DIRECTOR';
  const isManager = user?.role === 'MANAGER';
  const isAsst    = user?.role === 'ASSISTANT_MANAGER';
  const isMgr     = isManager || isAsst;
  const isEmp     = !isAdmin && !isMgr;
  const canReview = isAdmin || isMgr;

  const [myEmployee,  setMyEmployee]  = useState(null);
  const [reviews,     setReviews]     = useState([]);
  const [allEmps,     setAllEmps]     = useState([]);
  const [loading,     setLoading]     = useState(true);
  const [addOpen,      setAddOpen]      = useState(false);
  const [detailOpen,   setDetailOpen]   = useState(false);
  const [selectedRev,  setSelectedRev]  = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting,     setDeleting]     = useState(false);
  const [editOpen,     setEditOpen]     = useState(false);
  const [editTarget,   setEditTarget]   = useState(null);
  const [editForm,     setEditForm]     = useState(BLANK_FORM);
  const [updating,     setUpdating]     = useState(false);
  const [form,        setForm]        = useState(BLANK_FORM);
  const [empOptions,  setEmpOptions]  = useState([]);
  const [empLoading,  setEmpLoading]  = useState(false);
  const [submitting,  setSubmitting]  = useState(false);

  /* top-level tab: 0 = Reviews, 1 = PIP */
  const [mainTab, setMainTab] = useState(0);

  /* filter state */
  const [searchText, setSearchText] = useState('');
  const [quarterFlt, setQuarterFlt] = useState('');
  const [ratingFlt,  setRatingFlt]  = useState('');
  const [deptFlt,    setDeptFlt]    = useState('');
  const [tab,        setTab]        = useState(0);
  const [sortBy,     setSortBy]     = useState('recent');
  const [viewMode,   setViewMode]   = useState('grid');
  const [page,       setPage]       = useState(0);

  /* ── initial load ── */
  useEffect(() => {
    employeeApi.getByUserId(user.userId)
      .then(async (emp) => {
        setMyEmployee(emp);
        if (isAdmin) {
          const [revs, empsData] = await Promise.all([
            performanceApi.getAll().catch(() => []),
            employeeApi.getAll().catch(() => []),
          ]);
          const empsArr = Array.isArray(empsData) ? empsData : [];
          setAllEmps(empsArr);
          const activeIds = new Set(empsArr.filter(e => e.active !== false).map(e => e.id));
          setReviews((Array.isArray(revs) ? revs : []).filter(r => activeIds.has(r.employeeId)));
        } else if (isMgr) {
          // Managers/Asst Managers: only see direct reports' reviews + their own received
          const [revs, teamData] = await Promise.all([
            performanceApi.getAll().catch(() => []),
            employeeApi.getTeam(emp.id).catch(() => []),
          ]);
          const teamArr = (Array.isArray(teamData) ? teamData : []).filter(e => e.active !== false);
          setAllEmps(teamArr);
          const teamIds = new Set(teamArr.map(e => e.id));
          setReviews((Array.isArray(revs) ? revs : []).filter(r =>
            teamIds.has(r.employeeId) ||  // reviews for any team member
            r.reviewerId === emp.id      ||  // reviews GIVEN by this manager (regardless of recipient)
            r.employeeId === emp.id          // reviews RECEIVED by this manager
          ));
        } else {
          // Employee: received reviews + reviews given by others about them
          const [receivedRevs, givenRevs] = await Promise.all([
            performanceApi.getByEmployee(emp.id).catch(() => []),
            performanceApi.getByReviewer(emp.id).catch(() => []),
          ]);
          const received = Array.isArray(receivedRevs) ? receivedRevs : [];
          const given    = Array.isArray(givenRevs)    ? givenRevs    : [];
          // Merge and deduplicate by id — employees primarily see reviews received
          const merged = [...received];
          given.forEach(r => { if (!merged.find(x => x.id === r.id)) merged.push(r); });
          setAllEmps([]);
          setReviews(merged);
        }
      })
      .catch(() => toast.error('Failed to load performance data'))
      .finally(() => setLoading(false));
  }, [user]); // eslint-disable-line

  /* ── open add dialog ── */
  const openDialog = useCallback(async () => {
    setForm({ ...BLANK_FORM, reviewDate: new Date().toISOString().split('T')[0], reviewPeriod: '' });
    setAddOpen(true);
    if (!myEmployee) return;
    setEmpLoading(true);
    try {
      const list = isAdmin
        ? await employeeApi.getAll()
        : await employeeApi.getTeam(myEmployee.id);
      setEmpOptions((list || []).filter(e => e.id !== myEmployee.id && e.role !== 'ADMIN' && e.role !== 'DIRECTOR' && e.active !== false));
    } catch {
      toast.error('Failed to load employees');
    } finally {
      setEmpLoading(false);
    }
  }, [myEmployee, isAdmin]);

  /* ── submit review ── */
  const handleCreate = async () => {
    if (!form.employeeId)         { toast.error('Please select an employee'); return; }
    if (!form.reviewPeriod)       { toast.error('Please select a review period'); return; }
    if (!form.comments?.trim())   { toast.error('Comments are required'); return; }

    const duplicate = reviews.find(r => r.employeeId === form.employeeId && r.reviewPeriod === form.reviewPeriod);
    if (duplicate) {
      const empName = empOptions.find(e => e.id === form.employeeId)?.fullName || 'This employee';
      toast.error(`${empName} already has a review for ${form.reviewPeriod}`);
      return;
    }

    setSubmitting(true);
    try {
      await performanceApi.create({ ...form, reviewerId: myEmployee.id });
      toast.success('Review submitted!');
      setAddOpen(false);
      if (isAdmin) {
        const [revs, empsData] = await Promise.all([
          performanceApi.getAll().catch(() => []),
          employeeApi.getAll().catch(() => []),
        ]);
        const empsArr = Array.isArray(empsData) ? empsData : [];
        if (empsArr.length) setAllEmps(empsArr);
        const activeIds = new Set(empsArr.filter(e => e.active !== false).map(e => e.id));
        setReviews((Array.isArray(revs) ? revs : []).filter(r => activeIds.has(r.employeeId)));
      } else if (isMgr) {
        const [revs, teamData] = await Promise.all([
          performanceApi.getAll().catch(() => []),
          employeeApi.getTeam(myEmployee.id).catch(() => []),
        ]);
        const teamArr = (Array.isArray(teamData) ? teamData : []).filter(e => e.active !== false);
        setAllEmps(teamArr);
        const teamIds = new Set(teamArr.map(e => e.id));
        setReviews((Array.isArray(revs) ? revs : []).filter(r =>
          teamIds.has(r.employeeId) ||
          r.reviewerId === myEmployee.id ||
          r.employeeId === myEmployee.id
        ));
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit review');
    } finally {
      setSubmitting(false);
    }
  };

  /* ── update review ── */
  const handleUpdate = async () => {
    if (!editForm.reviewPeriod)      { toast.error('Please select a review period'); return; }
    if (!editForm.comments?.trim())  { toast.error('Comments are required'); return; }
    setUpdating(true);
    try {
      const updated = await performanceApi.update(editTarget.id, editForm);
      toast.success('Review updated!');
      setReviews(prev => prev.map(r => r.id === editTarget.id ? { ...r, ...updated } : r));
      setEditOpen(false);
      setEditTarget(null);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update review');
    } finally {
      setUpdating(false);
    }
  };

  /* ── delete review ── */
  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await performanceApi.delete(deleteTarget.id);
      toast.success('Review deleted');
      setReviews(prev => prev.filter(r => r.id !== deleteTarget.id));
    } catch {
      toast.error('Failed to delete review');
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  };

  /* ── derived stats ── */
  const avgRating = reviews.length
    ? reviews.reduce((s, r) => s + (r.rating || 0), 0) / reviews.length
    : 0;

  const now      = new Date();
  const currentQ = `Q${Math.ceil((now.getMonth() + 1) / 3)} ${now.getFullYear()}`;

  /* top performer: (1) avg rating desc, (2) review count desc, (3) most recent review date desc, (4) name asc */
  const empRatingMap = {};
  reviews.forEach(r => {
    if (!r.employeeId) return;
    if (!empRatingMap[r.employeeId])
      empRatingMap[r.employeeId] = { name: r.employeeName || '', total: 0, count: 0, latestDate: null };
    empRatingMap[r.employeeId].total += r.rating || 0;
    empRatingMap[r.employeeId].count++;
    const d = r.reviewDate ? new Date(r.reviewDate) : null;
    if (d && (!empRatingMap[r.employeeId].latestDate || d > empRatingMap[r.employeeId].latestDate))
      empRatingMap[r.employeeId].latestDate = d;
  });
  const topPerfEntry = Object.values(empRatingMap)
    .map(d => ({ name: d.name, avg: d.count > 0 ? d.total / d.count : 0, count: d.count, latestDate: d.latestDate }))
    .sort((a, b) => {
      if (Math.abs(b.avg - a.avg) > 0.001) return b.avg - a.avg;
      if (b.count !== a.count) return b.count - a.count;
      if (a.latestDate && b.latestDate && a.latestDate - b.latestDate !== 0) return b.latestDate - a.latestDate;
      return a.name.localeCompare(b.name);
    })[0];

  const reviewedThisQ = new Set(reviews.filter(r => r.reviewPeriod === currentQ).map(r => r.employeeId));
  const pendingCount  = canReview
    ? allEmps.filter(e => e.active !== false && e.id !== myEmployee?.id && !reviewedThisQ.has(e.id)).length
    : 0;

  /* ── departments for filter ── */
  const departments = [...new Set(allEmps.filter(e => e.department).map(e => e.department))].sort();
  const empDeptMap  = Object.fromEntries(allEmps.map(e => [e.id, e.department || '']));

  /* ── quarter filter options: current year only, newest first ── */
  const existingQuarters = [...QUARTER_OPTIONS].reverse();

  /* ── filtered reviews ── */
  const teamIds = new Set(allEmps.map(e => e.id));
  const tabReviews = (() => {
    if (isEmp) {
      return reviews.filter(r => r.employeeId === myEmployee?.id); // received only
    }
    if (isMgr) {
      if (tab === 0) return reviews.filter(r => teamIds.has(r.employeeId));      // team only
      if (tab === 1) return reviews.filter(r => r.employeeId === myEmployee?.id); // my received
      if (tab === 2) return reviews.filter(r => r.reviewerId === myEmployee?.id); // given by me
      return reviews;
    }
    // Admin
    if (tab === 1) return reviews.filter(r => r.employeeId === myEmployee?.id);
    if (tab === 2) return reviews.filter(r => r.reviewerId === myEmployee?.id);
    return reviews;
  })();

  const filteredReviews = tabReviews
    .filter(r => {
      const q = searchText.toLowerCase();
      if (q && !(r.employeeName || '').toLowerCase().includes(q) && !(r.reviewerName || '').toLowerCase().includes(q)) return false;
      if (quarterFlt && r.reviewPeriod !== quarterFlt) return false;
      if (ratingFlt) {
        const min = parseFloat(ratingFlt);
        if ((r.rating || 0) < min) return false;
        if (min < 5 && (r.rating || 0) >= min + 1) return false;
      }
      if (deptFlt && empDeptMap[r.employeeId] !== deptFlt) return false;
      return true;
    })
    .sort((a, b) => {
      if (sortBy === 'highest') return (b.rating || 0) - (a.rating || 0);
      if (sortBy === 'lowest')  return (a.rating || 0) - (b.rating || 0);
      return new Date(b.reviewDate || 0) - new Date(a.reviewDate || 0);
    });

  const totalPages   = Math.ceil(filteredReviews.length / ROWS_PER_PAGE);
  const pagedReviews = filteredReviews.slice(page * ROWS_PER_PAGE, (page + 1) * ROWS_PER_PAGE);

  const resetFilters = () => { setSearchText(''); setQuarterFlt(''); setRatingFlt(''); setDeptFlt(''); setPage(0); };

  /* ── loading ── */
  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 360 }}>
      <CircularProgress sx={{ color: '#14b8a6' }} />
    </Box>
  );

  /* ── render ── */
  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh' }}>

      {/* Page header */}
      <Box sx={{ mb: 2 }}>
        <Typography sx={{ fontSize: 26, fontWeight: 800, color: '#0f172a', lineHeight: 1.2 }}>
          Performance
        </Typography>
        <Typography sx={{ fontSize: 13.5, color: '#64748b', mt: .4 }}>
          {isAdmin ? 'Track and manage all employee performance reviews and improvement plans.'
           : isManager ? 'Track and review performance of your direct reports.'
           : isAsst    ? 'Track and review performance of your assigned team members.'
           :             'View your performance ratings, feedback, and improvement plans.'}
        </Typography>
      </Box>

      {/* Top-level module tabs */}
      <Box sx={{ bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px', boxShadow: '0 2px 8px rgba(0,0,0,0.04)', mb: 3, overflow: 'hidden' }}>
        <Tabs
          value={mainTab}
          onChange={(_, v) => setMainTab(v)}
          TabIndicatorProps={{ style: { backgroundColor: '#1e3a5f', height: 3 } }}
          sx={{
            px: 2,
            '& .MuiTab-root': { fontSize: 14, fontWeight: 600, textTransform: 'none', color: '#64748b', minHeight: 50, '&.Mui-selected': { color: '#1e3a5f' } },
          }}
        >
          <Tab label="Performance Reviews" />
          <Tab label="Improvement Plans (PIP)" />
        </Tabs>
      </Box>

      {/* ── PIP tab ── */}
      {mainTab === 1 && (
        <PerformancePipTab myEmployee={myEmployee} />
      )}

      {/* ── Performance Reviews tab ── */}
      {mainTab === 0 && (<>

      {/* ── Filter bar (Admin / Manager only — employee has its own) ── */}
      {!isEmp && <Box sx={{
        bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px',
        boxShadow: '0 2px 8px rgba(0,0,0,0.05)', p: 1.75, mb: 3,
        display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap',
      }}>
        <OutlinedInput
          placeholder={isEmp ? 'Search by reviewer or period…' : 'Search by employee name…'}
          value={searchText}
          onChange={e => { setSearchText(e.target.value); setPage(0); }}
          startAdornment={<InputAdornment position="start"><SearchIcon sx={{ fontSize: 19, color: '#94a3b8' }} /></InputAdornment>}
          sx={{
            height: 40, fontSize: 13.5, borderRadius: '10px', minWidth: 230, flex: '1 1 200px',
            '& fieldset': { borderColor: '#e2e8f0' }, '&:hover fieldset': { borderColor: '#cbd5e1' },
          }}
        />

        <Select
          value={quarterFlt} onChange={e => { setQuarterFlt(e.target.value); setPage(0); }}
          displayEmpty renderValue={v => v || 'All Quarters'}
          sx={{ height: 40, fontSize: 13.5, borderRadius: '10px', minWidth: 145, '& fieldset': { borderColor: '#e2e8f0' } }}
        >
          <MenuItem value="">All Quarters</MenuItem>
          {existingQuarters.map(q => <MenuItem key={q} value={q} sx={{ fontSize: 13.5 }}>{q}</MenuItem>)}
        </Select>

        <Select
          value={ratingFlt} onChange={e => { setRatingFlt(e.target.value); setPage(0); }}
          displayEmpty renderValue={v => v ? `Rating ${v}+` : 'All Ratings'}
          sx={{ height: 40, fontSize: 13.5, borderRadius: '10px', minWidth: 135, '& fieldset': { borderColor: '#e2e8f0' } }}
        >
          <MenuItem value="">All Ratings</MenuItem>
          <MenuItem value="5"  sx={{ fontSize: 13.5 }}>Excellent (5)</MenuItem>
          <MenuItem value="4"  sx={{ fontSize: 13.5 }}>Very Good (4+)</MenuItem>
          <MenuItem value="3"  sx={{ fontSize: 13.5 }}>Good (3+)</MenuItem>
          <MenuItem value="2"  sx={{ fontSize: 13.5 }}>Average (2+)</MenuItem>
          <MenuItem value="1"  sx={{ fontSize: 13.5 }}>Any Rating</MenuItem>
        </Select>

        {!isEmp && departments.length > 0 && (
          <Select
            value={deptFlt} onChange={e => { setDeptFlt(e.target.value); setPage(0); }}
            displayEmpty renderValue={v => v || 'All Departments'}
            sx={{ height: 40, fontSize: 13.5, borderRadius: '10px', minWidth: 155, '& fieldset': { borderColor: '#e2e8f0' } }}
          >
            <MenuItem value="">All Departments</MenuItem>
            {departments.map(d => <MenuItem key={d} value={d} sx={{ fontSize: 13.5 }}>{d}</MenuItem>)}
          </Select>
        )}

        <Button
          variant="outlined" onClick={resetFilters}
          startIcon={<RefreshIcon sx={{ fontSize: 17 }} />}
          sx={{
            height: 40, borderRadius: '10px', borderColor: '#e2e8f0', color: '#64748b',
            fontSize: 13.5, fontWeight: 600, px: 2,
            '&:hover': { borderColor: '#cbd5e1', bgcolor: '#f8fafc' },
          }}
        >
          Reset
        </Button>

        {canReview && (
          <Button
            variant="contained" onClick={openDialog}
            startIcon={<AddIcon />}
            sx={{
              height: 40, borderRadius: '10px', bgcolor: '#1e3a5f',
              '&:hover': { bgcolor: '#152d4a' }, fontSize: 13.5, fontWeight: 700, px: 2.5,
              boxShadow: '0 4px 12px rgba(30,58,95,0.3)', ml: 'auto',
            }}
          >
            New Review
          </Button>
        )}
      </Box>}

      {/* ══════════════════════════════════════════
           EMPLOYEE: MY PERFORMANCE REVIEWS
      ══════════════════════════════════════════ */}
      {isEmp && (() => {
        const myRevs = reviews
          .filter(r => r.employeeId === myEmployee?.id)
          .sort((a, b) => new Date(b.reviewDate) - new Date(a.reviewDate));

        const filteredRevs = myRevs.filter(r => {
          const q = searchText.toLowerCase();
          if (q && !(r.reviewerName || '').toLowerCase().includes(q) && !(r.reviewPeriod || '').toLowerCase().includes(q)) return false;
          if (quarterFlt && r.reviewPeriod !== quarterFlt) return false;
          return true;
        });

        const totalPages   = Math.ceil(filteredRevs.length / ROWS_PER_PAGE);
        const pagedRevs    = filteredRevs.slice(page * ROWS_PER_PAGE, (page + 1) * ROWS_PER_PAGE);
        const avgEmpRating = myRevs.length ? myRevs.reduce((s, r) => s + (r.rating || 0), 0) / myRevs.length : 0;
        const latestReview = myRevs[0];
        const latestInfo   = latestReview ? ratingInfo(latestReview.rating ?? 0) : null;
        const trend        = getTrend(myRevs);

        return (
          <Box>

            {/* ── 4 stat cards ── */}
            <Grid container spacing={2} sx={{ mb: 3 }}>
              {/* Latest */}
              <Grid item xs={12} sm={6} md={3}>
                <Box sx={{ bgcolor: '#fff', border: `1px solid ${latestInfo ? latestInfo.border : '#e2e8f0'}`, borderRadius: '14px', p: 2.5, boxShadow: '0 1px 6px rgba(0,0,0,0.04)', height: '100%' }}>
                  <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px', mb: 1 }}>Latest Rating</Typography>
                  {latestReview && latestInfo ? (
                    <>
                      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: .5, mb: .5 }}>
                        <Typography sx={{ fontSize: 32, fontWeight: 800, color: latestInfo.color, lineHeight: 1 }}>{latestReview.rating}</Typography>
                        <Typography sx={{ fontSize: 14, color: '#94a3b8' }}>.0 ★</Typography>
                      </Box>
                      <Box sx={{ display: 'inline-block', px: 1.25, py: .3, borderRadius: '6px', bgcolor: latestInfo.bg }}>
                        <Typography sx={{ fontSize: 11, fontWeight: 700, color: latestInfo.color }}>{latestInfo.label}</Typography>
                      </Box>
                    </>
                  ) : (
                    <Typography sx={{ fontSize: 13, color: '#94a3b8' }}>No reviews yet</Typography>
                  )}
                </Box>
              </Grid>

              {/* Avg Rating */}
              <Grid item xs={12} sm={6} md={3}>
                <Box sx={{ bgcolor: '#fff', border: '1px solid #e2e8f0', borderRadius: '14px', p: 2.5, boxShadow: '0 1px 6px rgba(0,0,0,0.04)', height: '100%' }}>
                  <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px', mb: 1 }}>Avg Rating</Typography>
                  <Box sx={{ display: 'flex', alignItems: 'baseline', gap: .5, mb: .75 }}>
                    <Typography sx={{ fontSize: 32, fontWeight: 800, color: '#0f172a', lineHeight: 1 }}>{avgEmpRating.toFixed(1)}</Typography>
                    <Typography sx={{ fontSize: 14, color: '#94a3b8' }}>/5</Typography>
                  </Box>
                  <Rating value={avgEmpRating} readOnly precision={0.5} size="small"
                    sx={{ '& .MuiRating-iconFilled': { color: '#f59e0b' }, '& .MuiRating-iconEmpty': { color: '#e2e8f0' } }} />
                </Box>
              </Grid>

              {/* Total Reviews */}
              <Grid item xs={12} sm={6} md={3}>
                <Box sx={{ bgcolor: '#fff', border: '1px solid #e2e8f0', borderRadius: '14px', p: 2.5, boxShadow: '0 1px 6px rgba(0,0,0,0.04)', height: '100%' }}>
                  <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px', mb: 1 }}>Reviews</Typography>
                  <Typography sx={{ fontSize: 32, fontWeight: 800, color: '#0f172a', lineHeight: 1, mb: .75 }}>{myRevs.length}</Typography>
                  <Typography sx={{ fontSize: 12, color: '#64748b' }}>
                    {myRevs.length === 0 ? 'No reviews yet'
                      : myRevs.length === 1 ? 'Total review'
                      : 'Total reviews'}
                  </Typography>
                </Box>
              </Grid>

              {/* Trend */}
              <Grid item xs={12} sm={6} md={3}>
                <Box sx={{ bgcolor: trend.bg || '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '14px', p: 2.5, boxShadow: '0 1px 6px rgba(0,0,0,0.04)', height: '100%' }}>
                  <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px', mb: 1 }}>Trend</Typography>
                  <Typography sx={{ fontSize: 32, fontWeight: 800, color: trend.color, lineHeight: 1, mb: .75 }}>{trend.icon}</Typography>
                  <Typography sx={{ fontSize: 13, fontWeight: 700, color: trend.color }}>{trend.label}</Typography>
                </Box>
              </Grid>
            </Grid>

            {/* ── Filter bar ── */}
            <Box sx={{ bgcolor: '#fff', borderRadius: '12px', border: '1px solid #e2e8f0', px: 2, py: 1.5, mb: 3,
              display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
              <OutlinedInput
                placeholder="Search by reviewer name…"
                value={searchText}
                onChange={e => { setSearchText(e.target.value); setPage(0); }}
                startAdornment={<InputAdornment position="start"><SearchIcon sx={{ fontSize: 17, color: '#94a3b8' }} /></InputAdornment>}
                sx={{ height: 38, fontSize: 13, borderRadius: '8px', width: 240,
                  '& fieldset': { borderColor: '#e2e8f0' }, '&:hover fieldset': { borderColor: '#cbd5e1' },
                  '&.Mui-focused fieldset': { borderColor: '#1e3a5f' } }}
              />
              <Select value={quarterFlt} onChange={e => { setQuarterFlt(e.target.value); setPage(0); }}
                displayEmpty renderValue={v => v || 'All Periods'}
                sx={{ height: 38, fontSize: 13, borderRadius: '8px', minWidth: 145, '& fieldset': { borderColor: '#e2e8f0' } }}>
                <MenuItem value="">All Periods</MenuItem>
                {existingQuarters.map(q => <MenuItem key={q} value={q} sx={{ fontSize: 13 }}>{q}</MenuItem>)}
              </Select>
              {(searchText || quarterFlt) && (
                <Button onClick={() => { setSearchText(''); setQuarterFlt(''); setPage(0); }}
                  sx={{ height: 38, borderRadius: '8px', color: '#64748b', fontSize: 12.5, fontWeight: 600, textTransform: 'none', px: 1.5 }}>
                  Clear
                </Button>
              )}
              <Typography sx={{ fontSize: 12.5, color: '#94a3b8', ml: 'auto' }}>
                {filteredRevs.length} of {myRevs.length} review{myRevs.length !== 1 ? 's' : ''}
              </Typography>
            </Box>

            {/* ── Empty state ── */}
            {myRevs.length === 0 && (
              <Box sx={{ bgcolor: '#fff', borderRadius: '16px', border: '1px dashed #e2e8f0', py: 12, textAlign: 'center' }}>
                <AssignmentIcon sx={{ fontSize: 52, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1.5 }} />
                <Typography sx={{ color: '#94a3b8', fontSize: 15, fontWeight: 600 }}>No reviews yet</Typography>
                <Typography sx={{ color: '#cbd5e1', fontSize: 13, mt: .5 }}>Your manager will submit a review soon</Typography>
              </Box>
            )}

            {/* ── No results ── */}
            {myRevs.length > 0 && filteredRevs.length === 0 && (
              <Box sx={{ bgcolor: '#fff', borderRadius: '12px', border: '1px dashed #e2e8f0', py: 8, textAlign: 'center' }}>
                <SearchIcon sx={{ fontSize: 38, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1.25 }} />
                <Typography sx={{ color: '#94a3b8', fontSize: 14 }}>No reviews match your filters</Typography>
                <Button onClick={() => { setSearchText(''); setQuarterFlt(''); setPage(0); }}
                  sx={{ mt: 1.25, color: '#1e3a5f', fontSize: 13, fontWeight: 600, textTransform: 'none' }}>
                  Clear filters
                </Button>
              </Box>
            )}

            {/* ── Review cards grid ── */}
            {filteredRevs.length > 0 && (
              <>
                <Grid container spacing={2.5}>
                  {pagedRevs.map(r => (
                    <Grid item xs={12} md={6} key={r.id}>
                      <ReviewCard
                        r={r}
                        isAdmin={false}
                        isManager={false}
                        myEmployeeId={myEmployee?.id}
                        onViewDetails={rev => { setSelectedRev(rev); setDetailOpen(true); }}
                        onEdit={() => {}}
                        onDelete={() => {}}
                      />
                    </Grid>
                  ))}
                </Grid>

                {/* ── Pagination ── */}
                {filteredRevs.length > ROWS_PER_PAGE && (
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1.5 }}>
                    <Typography sx={{ fontSize: 13, color: '#94a3b8' }}>
                      {page * ROWS_PER_PAGE + 1}–{Math.min((page + 1) * ROWS_PER_PAGE, filteredRevs.length)} of {filteredRevs.length}
                    </Typography>
                    <Box sx={{ display: 'flex', gap: .75 }}>
                      <IconButton size="small" disabled={page === 0} onClick={() => setPage(p => p - 1)}
                        sx={{ width: 32, height: 32, borderRadius: '8px', border: '1px solid #e2e8f0', bgcolor: '#fff', '&.Mui-disabled': { opacity: .4 } }}>
                        <ChevronLeftIcon sx={{ fontSize: 17 }} />
                      </IconButton>
                      {Array.from({ length: totalPages }, (_, i) => (
                        <IconButton key={i} size="small" onClick={() => setPage(i)}
                          sx={{ width: 32, height: 32, borderRadius: '8px', bgcolor: page === i ? '#1e3a5f' : '#fff',
                            color: page === i ? '#fff' : '#374151', border: '1px solid', borderColor: page === i ? '#1e3a5f' : '#e2e8f0' }}>
                          <Typography sx={{ fontSize: 13, fontWeight: page === i ? 700 : 500, color: 'inherit', lineHeight: 1 }}>{i + 1}</Typography>
                        </IconButton>
                      ))}
                      <IconButton size="small" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}
                        sx={{ width: 32, height: 32, borderRadius: '8px', border: '1px solid #e2e8f0', bgcolor: '#fff', '&.Mui-disabled': { opacity: .4 } }}>
                        <ChevronRightIcon sx={{ fontSize: 17 }} />
                      </IconButton>
                    </Box>
                  </Box>
                )}
              </>
            )}

          </Box>
        );
      })()}

      {/* ── Admin / Manager: Stats + Tabs + Cards ── */}
      {!isEmp && <>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {(
          /* ── Admin / Manager stats ── */
          <>
            <Grid item xs={12} sm={6} md={3}>
              <StatCard
                icon={<AssignmentIcon />}
                label={isMgr ? 'Team Reviews' : 'Total Reviews'}
                value={isMgr ? reviews.filter(r => teamIds.has(r.employeeId)).length : reviews.length}
                sub={isMgr ? `${allEmps.length} direct report${allEmps.length !== 1 ? 's' : ''}` : 'All Time'}
                accent="#14b8a6"
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <Box sx={{ bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px', boxShadow: '0 2px 8px rgba(0,0,0,0.05)', p: 2.5, display: 'flex', alignItems: 'center', gap: 2, height: '100%' }}>
                <Box sx={{ width: 52, height: 52, borderRadius: '14px', bgcolor: '#fffbeb', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <StarRoundedIcon sx={{ fontSize: 27, color: '#f59e0b' }} />
                </Box>
                <Box>
                  <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.5px', mb: .3 }}>
                    {isMgr ? 'Team Avg Rating' : 'Average Rating'}
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'baseline', gap: .5, mb: .35 }}>
                    <Typography sx={{ fontSize: 26, fontWeight: 800, color: '#0f172a', lineHeight: 1 }}>{avgRating.toFixed(1)}</Typography>
                    <Typography sx={{ fontSize: 14, color: '#94a3b8' }}>/ 5</Typography>
                  </Box>
                  <Rating value={avgRating} readOnly precision={0.5} size="small" sx={{ '& .MuiRating-iconFilled': { color: '#f59e0b' } }} />
                </Box>
              </Box>
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <StatCard
                icon={<EmojiEventsIcon />}
                label={isMgr ? 'Top in Team' : 'Top Performer'}
                value={topPerfEntry?.name || '—'}
                sub={topPerfEntry ? `Avg ${topPerfEntry.avg.toFixed(1)}/5 · ${topPerfEntry.count} review${topPerfEntry.count !== 1 ? 's' : ''}` : 'No reviews yet'}
                accent="#f59e0b"
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <StatCard icon={<AccessTimeIcon />} label={isMgr ? 'Pending This Quarter' : 'Not Reviewed Yet'} value={pendingCount} sub={`${isMgr ? 'Team members' : 'Employees'} · ${currentQ}`} accent="#6366f1" />
            </Grid>
          </>
        )}
      </Grid>

      {/* ── Tabs + Sort + View ── */}
      <Box sx={{
        bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px',
        boxShadow: '0 2px 8px rgba(0,0,0,0.05)', mb: 2.5, overflow: 'hidden',
      }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 2, borderBottom: '1px solid #f1f5f9', flexWrap: 'wrap', gap: 1 }}>
          <Tabs
            value={tab}
            onChange={(_, v) => { setTab(v); setPage(0); }}
            TabIndicatorProps={{ style: { backgroundColor: '#14b8a6', height: 2.5 } }}
            sx={{
              minHeight: 52,
              '& .MuiTab-root': {
                fontSize: 13.5, fontWeight: 600, textTransform: 'none',
                color: '#64748b', minHeight: 52, px: 2,
                '&.Mui-selected': { color: '#14b8a6' },
              },
            }}
          >
            {isEmp ? (
              <Tab label="My Reviews" />
            ) : isMgr ? (
              [
                <Tab key="t" label={`Team Reviews (${reviews.filter(r => teamIds.has(r.employeeId)).length})`} />,
                <Tab key="m" label="My Reviews" />,
                <Tab key="g" label="Given by Me" />,
              ]
            ) : (
              [
                <Tab key="a" label="All Reviews" />,
                <Tab key="r" label="Received by Me" />,
                <Tab key="g" label="Given by Me" />,
              ]
            )}
          </Tabs>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, py: 1 }}>
            <Typography sx={{ fontSize: 13, color: '#64748b', fontWeight: 500 }}>Sort by:</Typography>
            <Select
              value={sortBy} onChange={e => setSortBy(e.target.value)}
              sx={{ height: 34, fontSize: 13, borderRadius: '8px', minWidth: 140, '& fieldset': { borderColor: '#e2e8f0' } }}
            >
              <MenuItem value="recent"  sx={{ fontSize: 13 }}>Most Recent</MenuItem>
              <MenuItem value="highest" sx={{ fontSize: 13 }}>Highest Rating</MenuItem>
              <MenuItem value="lowest"  sx={{ fontSize: 13 }}>Lowest Rating</MenuItem>
            </Select>
            {/* Grid / List toggle */}
            {(['grid', 'list']).map((mode) => (
              <IconButton
                key={mode} size="small"
                onClick={() => setViewMode(mode)}
                sx={{
                  width: 32, height: 32, borderRadius: '8px',
                  bgcolor: viewMode === mode ? '#14b8a6' : '#f8fafc',
                  color: viewMode === mode ? '#fff' : '#64748b',
                  border: '1px solid',
                  borderColor: viewMode === mode ? '#14b8a6' : '#e2e8f0',
                  '&:hover': { bgcolor: viewMode === mode ? '#0d9488' : '#f1f5f9' },
                }}
              >
                {mode === 'grid'
                  ? <ViewModuleIcon sx={{ fontSize: 17 }} />
                  : <ViewListIcon  sx={{ fontSize: 17 }} />
                }
              </IconButton>
            ))}
          </Box>
        </Box>
      </Box>

      {/* ── Review cards ── */}
      {filteredReviews.length === 0 ? (
        <Box sx={{
          bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '14px',
          py: 9, textAlign: 'center',
        }}>
          <AssignmentIcon sx={{ fontSize: 52, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1.5 }} />
          <Typography sx={{ color: '#94a3b8', fontSize: 15 }}>
            {isEmp ? 'No performance reviews received yet'
             : isMgr ? 'No reviews found for your team'
             : 'No reviews found'}
          </Typography>
          {(searchText || quarterFlt || ratingFlt || deptFlt) && (
            <Button onClick={resetFilters} sx={{ mt: 1.5, color: '#14b8a6', fontSize: 13, fontWeight: 600, textTransform: 'none' }}>
              Clear filters
            </Button>
          )}
        </Box>
      ) : (
        <Grid container spacing={2}>
          {pagedReviews.map(r => (
            <Grid item xs={12} md={viewMode === 'list' ? 12 : 6} lg={viewMode === 'list' ? 12 : 4} key={r.id}>
              <ReviewCard
                r={r}
                isAdmin={isAdmin}
                isManager={isManager}
                myEmployeeId={myEmployee?.id}
                onViewDetails={rev => { setSelectedRev(rev); setDetailOpen(true); }}
                onEdit={rev => { setEditForm({ employeeId: rev.employeeId, reviewerId: rev.reviewerId, rating: rev.rating, comments: rev.comments || '', strengths: rev.strengths || '', areasOfImprovement: rev.areasOfImprovement || '', reviewDate: rev.reviewDate || new Date().toISOString().split('T')[0], reviewPeriod: rev.reviewPeriod || '' }); setEditTarget(rev); setEditOpen(true); }}
                onDelete={rev => setDeleteTarget(rev)}
              />
            </Grid>
          ))}
        </Grid>
      )}

      {/* ── Pagination ── */}
      {filteredReviews.length > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 3, flexWrap: 'wrap', gap: 1.5 }}>
          <Typography sx={{ fontSize: 13, color: '#64748b' }}>
            Showing {page * ROWS_PER_PAGE + 1} to {Math.min((page + 1) * ROWS_PER_PAGE, filteredReviews.length)} of {filteredReviews.length} reviews
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: .75 }}>
            <IconButton
              size="small" disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
              sx={{ width: 32, height: 32, borderRadius: '8px', border: '1px solid #e2e8f0', bgcolor: '#fff', '&.Mui-disabled': { opacity: .4 } }}
            >
              <ChevronLeftIcon sx={{ fontSize: 18 }} />
            </IconButton>
            {Array.from({ length: totalPages }, (_, i) => (
              <IconButton
                key={i} size="small"
                onClick={() => setPage(i)}
                sx={{
                  width: 32, height: 32, borderRadius: '8px',
                  bgcolor: page === i ? '#14b8a6' : '#fff',
                  color: page === i ? '#fff' : '#374151',
                  border: '1px solid',
                  borderColor: page === i ? '#14b8a6' : '#e2e8f0',
                  fontWeight: page === i ? 700 : 500,
                  '&:hover': { bgcolor: page === i ? '#0d9488' : '#f8fafc' },
                }}
              >
                <Typography sx={{ fontSize: 13, fontWeight: page === i ? 700 : 500, color: 'inherit', lineHeight: 1 }}>{i + 1}</Typography>
              </IconButton>
            ))}
            <IconButton
              size="small" disabled={page >= totalPages - 1}
              onClick={() => setPage(p => p + 1)}
              sx={{ width: 32, height: 32, borderRadius: '8px', border: '1px solid #e2e8f0', bgcolor: '#fff', '&.Mui-disabled': { opacity: .4 } }}
            >
              <ChevronRightIcon sx={{ fontSize: 18 }} />
            </IconButton>
          </Box>
        </Box>
      )}
      </>}

      {/* ── Edit Review Dialog ── */}
      <Dialog open={editOpen} onClose={() => { setEditOpen(false); setEditTarget(null); }} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 17 }}>Edit Performance Review</DialogTitle>
        <Divider />
        <DialogContent>
          {editTarget && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
              {/* Employee (read-only) */}
              <Box sx={{ p: 1.5, borderRadius: '10px', bgcolor: '#f8fafc', border: '1px solid #e2e8f0', display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <Avatar sx={{ width: 36, height: 36, bgcolor: avatarColor(editTarget.employeeName || '').bg, color: avatarColor(editTarget.employeeName || '').color, fontSize: '0.82rem', fontWeight: 700 }}>
                  {initials(editTarget.employeeName || '')}
                </Avatar>
                <Box>
                  <Typography sx={{ fontSize: 14, fontWeight: 700, color: '#0f172a' }}>{editTarget.employeeName}</Typography>
                  <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>Employee · review cannot be reassigned</Typography>
                </Box>
              </Box>
              <Box>
                <Typography variant="body2" gutterBottom fontWeight={500}>Rating</Typography>
                <Rating value={editForm.rating} onChange={(_, v) => setEditForm(f => ({ ...f, rating: v }))} size="large" />
              </Box>
              <FormControl fullWidth required>
                <InputLabel>Review Period</InputLabel>
                <Select value={editForm.reviewPeriod} label="Review Period" onChange={e => setEditForm(f => ({ ...f, reviewPeriod: e.target.value }))}>
                  {[...QUARTER_OPTIONS].reverse().map(q => (
                    <MenuItem key={q} value={q}>{q}{q === CURRENT_QUARTER ? ' (Current)' : ''}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField label="Comments"             value={editForm.comments}            onChange={e => setEditForm(f => ({ ...f, comments: e.target.value }))}            multiline rows={2} fullWidth />
              <TextField label="Strengths"            value={editForm.strengths}           onChange={e => setEditForm(f => ({ ...f, strengths: e.target.value }))}           multiline rows={2} fullWidth />
              <TextField label="Areas of Improvement" value={editForm.areasOfImprovement} onChange={e => setEditForm(f => ({ ...f, areasOfImprovement: e.target.value }))} multiline rows={2} fullWidth />
              <TextField label="Review Date" type="date" value={editForm.reviewDate} onChange={e => setEditForm(f => ({ ...f, reviewDate: e.target.value }))} InputLabelProps={{ shrink: true }} fullWidth />
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => { setEditOpen(false); setEditTarget(null); }} disabled={updating} sx={{ borderRadius: '10px', fontSize: 13.5, fontWeight: 600, textTransform: 'none' }}>Cancel</Button>
          <Button
            variant="contained" onClick={handleUpdate}
            disabled={updating || !editForm.reviewPeriod || !editForm.comments?.trim()}
            sx={{ borderRadius: '10px', bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' }, fontSize: 13.5, fontWeight: 700, textTransform: 'none' }}
          >
            {updating ? <CircularProgress size={18} sx={{ mr: 1, color: '#fff' }} /> : null}
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Delete Confirmation Dialog ── */}
      <Dialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} maxWidth="xs" fullWidth PaperProps={{ sx: { borderRadius: '14px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16, pb: 1 }}>Delete Review?</DialogTitle>
        <Divider />
        <DialogContent sx={{ pt: 2 }}>
          <Typography sx={{ fontSize: 14, color: '#374151' }}>
            This will permanently delete the review for <strong>{deleteTarget?.employeeName}</strong> ({deleteTarget?.reviewPeriod}). This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDeleteTarget(null)} disabled={deleting} sx={{ borderRadius: '10px', fontSize: 13.5, fontWeight: 600, textTransform: 'none', color: '#64748b' }}>
            Cancel
          </Button>
          <Button
            variant="contained" onClick={handleDelete} disabled={deleting}
            sx={{ borderRadius: '10px', bgcolor: '#dc2626', '&:hover': { bgcolor: '#b91c1c' }, fontSize: 13.5, fontWeight: 700, textTransform: 'none' }}
          >
            {deleting ? <CircularProgress size={16} sx={{ mr: 1, color: '#fff' }} /> : null}
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Detail Dialog ── */}
      <Dialog open={detailOpen} onClose={() => setDetailOpen(false)} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 17, pb: 1.5 }}>Review Details</DialogTitle>
        <Divider />
        {selectedRev && (() => {
          const info    = ratingInfo(selectedRev.rating ?? 0);
          const avColor = avatarColor(selectedRev.employeeName || '');
          return (
            <DialogContent sx={{ pt: 2.5 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2.5 }}>
                <Avatar sx={{ width: 52, height: 52, bgcolor: avColor.bg, color: avColor.color, fontSize: '1rem', fontWeight: 700 }}>
                  {initials(selectedRev.employeeName || '')}
                </Avatar>
                <Box sx={{ flex: 1 }}>
                  <Typography sx={{ fontSize: 16, fontWeight: 700, color: '#0f172a' }}>{selectedRev.employeeName}</Typography>
                  <Typography sx={{ fontSize: 13, color: '#64748b' }}>Reviewed by: {selectedRev.reviewerName}</Typography>
                  <Typography sx={{ fontSize: 12.5, color: '#94a3b8' }}>{selectedRev.reviewPeriod} · {fmtDate(selectedRev.reviewDate)}</Typography>
                </Box>
                <Box sx={{ textAlign: 'center', flexShrink: 0 }}>
                  <Box sx={{ width: 60, height: 60, borderRadius: '50%', bgcolor: info.bg, border: `2px solid ${info.border}`, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                    <Typography sx={{ fontSize: 13.5, fontWeight: 800, color: info.color, lineHeight: 1 }}>{selectedRev.rating}/5</Typography>
                  </Box>
                  <Typography sx={{ fontSize: 11, fontWeight: 700, color: info.color, mt: .5 }}>{info.label}</Typography>
                </Box>
              </Box>
              <Rating value={selectedRev.rating} readOnly precision={0.5} sx={{ mb: 2.5, '& .MuiRating-iconFilled': { color: '#f59e0b' } }} />
              {selectedRev.comments && (
                <Box sx={{ bgcolor: '#f8fafc', borderRadius: '10px', p: 2, mb: 2 }}>
                  <Typography sx={{ fontSize: 13.5, color: '#64748b', fontStyle: 'italic', lineHeight: 1.6 }}>"{selectedRev.comments}"</Typography>
                </Box>
              )}
              {selectedRev.strengths && (
                <Box sx={{ mb: 2 }}>
                  <Typography sx={{ fontSize: 12, fontWeight: 700, color: '#16a34a', textTransform: 'uppercase', letterSpacing: '.4px', mb: .6 }}>Strengths</Typography>
                  <Typography sx={{ fontSize: 14, color: '#374151' }}>{selectedRev.strengths}</Typography>
                </Box>
              )}
              {selectedRev.areasOfImprovement && (
                <Box>
                  <Typography sx={{ fontSize: 12, fontWeight: 700, color: '#d97706', textTransform: 'uppercase', letterSpacing: '.4px', mb: .6 }}>Areas for Improvement</Typography>
                  <Typography sx={{ fontSize: 14, color: '#374151' }}>{selectedRev.areasOfImprovement}</Typography>
                </Box>
              )}
            </DialogContent>
          );
        })()}
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDetailOpen(false)} sx={{ borderRadius: '10px', fontSize: 13.5, fontWeight: 600, color: '#64748b', textTransform: 'none' }}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* ── New Review Dialog ── */}
      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 17 }}>Submit Performance Review</DialogTitle>
        <Divider />
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
            <Autocomplete
              options={empOptions}
              getOptionLabel={emp => emp.fullName || ''}
              loading={empLoading}
              value={empOptions.find(e => e.id === form.employeeId) || null}
              onChange={(_, emp) => setForm({ ...form, employeeId: emp ? emp.id : null })}
              renderOption={(props, emp) => (
                <Box component="li" {...props} key={emp.id}>
                  <Box>
                    <Typography variant="body2" fontWeight={600}>{emp.fullName}</Typography>
                    <Typography variant="caption" color="text.secondary">{emp.position || emp.role} · {emp.department}</Typography>
                  </Box>
                </Box>
              )}
              renderInput={params => (
                <TextField {...params} label="Employee" required
                  InputProps={{ ...params.InputProps, endAdornment: (<>{empLoading ? <CircularProgress color="inherit" size={16} /> : null}{params.InputProps.endAdornment}</>) }}
                />
              )}
              fullWidth
            />
            <Box>
              <Typography variant="body2" gutterBottom fontWeight={500}>Rating</Typography>
              <Rating value={form.rating} onChange={(_, v) => setForm({ ...form, rating: v })} size="large" />
            </Box>
            <FormControl fullWidth required>
              <InputLabel>Review Period</InputLabel>
              <Select value={form.reviewPeriod} label="Review Period" onChange={e => setForm({ ...form, reviewPeriod: e.target.value })}>
                {[...QUARTER_OPTIONS].reverse().map(q => (
                  <MenuItem key={q} value={q}>
                    {q}{q === CURRENT_QUARTER ? ' (Current)' : ''}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField label="Comments"             value={form.comments}            onChange={e => setForm({ ...form, comments: e.target.value })}            multiline rows={2} fullWidth />
            <TextField label="Strengths"            value={form.strengths}           onChange={e => setForm({ ...form, strengths: e.target.value })}           multiline rows={2} fullWidth />
            <TextField label="Areas of Improvement" value={form.areasOfImprovement} onChange={e => setForm({ ...form, areasOfImprovement: e.target.value })} multiline rows={2} fullWidth />
            <TextField label="Review Date" type="date" value={form.reviewDate} onChange={e => setForm({ ...form, reviewDate: e.target.value })} InputLabelProps={{ shrink: true }} fullWidth />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setAddOpen(false)} disabled={submitting} sx={{ borderRadius: '10px', fontSize: 13.5, fontWeight: 600, textTransform: 'none' }}>Cancel</Button>
          <Button
            variant="contained" onClick={handleCreate}
            disabled={submitting || !form.employeeId || !form.reviewPeriod || !form.comments?.trim()}
            sx={{ borderRadius: '10px', bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#152d4a' }, fontSize: 13.5, fontWeight: 700, textTransform: 'none' }}
          >
            {submitting ? <CircularProgress size={18} sx={{ mr: 1, color: '#fff' }} /> : null}
            Submit Review
          </Button>
        </DialogActions>
      </Dialog>

      {/* close mainTab === 0 fragment */}
      </>)}

    </Box>
  );
};

export default PerformancePage;
