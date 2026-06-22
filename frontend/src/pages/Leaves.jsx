import React, { useEffect, useState, useMemo } from 'react';
import { useSelector } from 'react-redux';
import { useSearchParams } from 'react-router-dom';
import {
  Box, Card, Typography, Button, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Chip, Dialog,
  DialogTitle, DialogContent, DialogActions, TextField, MenuItem,
  Select, FormControl, InputLabel, CircularProgress, Tabs, Tab,
  IconButton, Tooltip, LinearProgress,
} from '@mui/material';
import AddIcon          from '@mui/icons-material/Add';
import CheckIcon        from '@mui/icons-material/Check';
import CloseIcon        from '@mui/icons-material/Close';
import EditIcon         from '@mui/icons-material/Edit';
import DeleteIcon       from '@mui/icons-material/Delete';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import FilterAltOffIcon  from '@mui/icons-material/FilterAltOff';
import PeopleAltIcon     from '@mui/icons-material/PeopleAlt';
import { leaveApi } from '../api/leaveApi';
import { employeeApi } from '../api/employeeApi';
import { holidayApi } from '../api/holidayApi';
import { toast } from 'react-toastify';

const statusColors = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'error' };

// Fixed quota (days) per leave type — keyed by the exact leaveType string stored in DB
// eslint-disable-next-line no-unused-vars
const LEAVE_QUOTA_MAP = {
  'ANNUAL':         { label: 'Annual Leave',        total: 21, color: '#14b8a6', bg: '#f0fdfa' },
  'Annual Leave':   { label: 'Annual Leave',        total: 21, color: '#14b8a6', bg: '#f0fdfa' },
  'CASUAL':         { label: 'Casual Leave',        total: 12, color: '#6366f1', bg: '#ede9fe' },
  'Casual Leave':   { label: 'Casual Leave',        total: 12, color: '#6366f1', bg: '#ede9fe' },
  'SICK':           { label: 'Sick Leave',          total: 12, color: '#f59e0b', bg: '#fef3c7' },
  'Sick Leave':     { label: 'Sick Leave',          total: 12, color: '#f59e0b', bg: '#fef3c7' },
  'LOP':            { label: 'LOP',                 total: null, color: '#ef4444', bg: '#fee2e2' },
  'LWP':            { label: 'LWP',                 total: null, color: '#ef4444', bg: '#fee2e2' },
};

const LeavesPage = () => {
  const { user } = useSelector((s) => s.auth);
  const [myEmployee, setMyEmployee] = useState(null);
  const [leaves, setLeaves] = useState([]);
  const [myLeaves, setMyLeaves] = useState([]);
  const [loading, setLoading] = useState(true);
  const [applyOpen, setApplyOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [processingId, setProcessingId] = useState(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const tab    = parseInt(searchParams.get('tab') || '0', 10);
  const setTab = (v) => {
    setSearchParams({ tab: v }, { replace: false });
    if (v !== 1) { setFilterFrom(''); setFilterTo(''); setStatusFilter(''); }
  };
  const [form, setForm] = useState({ leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' });
  const [rangeHolidays, setRangeHolidays] = useState([]);
  const [editOpen, setEditOpen] = useState(false);
  const [editingLeave, setEditingLeave] = useState(null);
  const [editForm, setEditForm] = useState({ leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' });
  const [deleteDialogId, setDeleteDialogId] = useState(null);
  const isManagerOrAdmin = ['ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR'].includes(user?.role);

  /* ── date range filter (Team Leaves tab) ── */
  const [filterFrom,   setFilterFrom]   = useState('');
  const [filterTo,     setFilterTo]     = useState('');
  const [statusFilter, setStatusFilter] = useState('');


  /* A leave overlaps [filterFrom, filterTo] when:
     leave.startDate <= filterTo  AND  leave.endDate >= filterFrom */
  const displayedLeaves = useMemo(() => {
    if (tab !== 1) return leaves;
    let rows = leaves;
    if (filterFrom || filterTo) {
      rows = rows.filter(l => {
        if (!l.startDate || !l.endDate) return false;
        const afterFrom = !filterFrom || l.endDate   >= filterFrom;
        const beforeTo  = !filterTo   || l.startDate <= filterTo;
        return afterFrom && beforeTo;
      });
    }
    if (statusFilter) rows = rows.filter(l => l.status === statusFilter);
    return rows;
  }, [leaves, tab, filterFrom, filterTo, statusFilter]);

  const hasFilter = filterFrom || filterTo || statusFilter;

  const onLeaveCount = useMemo(() => {
    if (tab !== 1 || (!filterFrom && !filterTo)) return null;
    return displayedLeaves.filter(l => l.status === 'APPROVED').length;
  }, [displayedLeaves, tab, filterFrom, filterTo]);

  const currentYear = new Date().getFullYear();

  const leaveBalance = useMemo(() => {
    let annualUsed = 0;
    myLeaves.forEach((l) => {
      if (l.status === 'APPROVED' && l.leaveType && l.leaveType !== 'Public Holiday') {
        const yr = l.startDate ? new Date(l.startDate).getFullYear() : null;
        if (yr === currentYear) {
          annualUsed += (l.totalDays || 1);
        }
      }
    });
    const total     = 21;
    const remaining = Math.max(0, total - annualUsed);
    return [{
      type:      'ANNUAL',
      label:     'Annual Leave',
      total,
      used:      annualUsed,
      remaining,
      color:     '#14b8a6',
      bg:        '#f0fdfa',
    }];
  }, [myLeaves, currentYear]);

  const fetchLeaves = async (emp) => {
    if (!emp) return;
    try {
      if (tab === 0) {
        setLeaves(await leaveApi.getMyLeaves(emp.id));
      } else {
        if (['ADMIN', 'HR'].includes(user?.role)) setLeaves(await leaveApi.getAll());
        else setLeaves(await leaveApi.getLeavesForManager(emp.id));
      }
    } catch {
      toast.error('Failed to load leaves');
    }
  };

  useEffect(() => {
    employeeApi.getByUserId(user.userId).then(async (emp) => {
      setMyEmployee(emp);
      const [, mine] = await Promise.allSettled([fetchLeaves(emp), leaveApi.getMyLeaves(emp.id)]);
      if (mine.status === 'fulfilled') setMyLeaves(mine.value);
    }).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, tab]);

  const refreshMyLeaves = async () => {
    if (!myEmployee) return;
    const mine = await leaveApi.getMyLeaves(myEmployee.id);
    setMyLeaves(mine);
  };

  // Fetch holidays in the selected date range for the apply-leave dialog
  useEffect(() => {
    if (!form.startDate || !form.endDate || form.endDate < form.startDate) {
      setRangeHolidays([]);
      return;
    }
    const year = new Date(form.startDate).getFullYear();
    holidayApi.getMy(year).then(all => {
      const inRange = all.filter(h => h.active && h.date >= form.startDate && h.date <= form.endDate);
      setRangeHolidays(inRange);
    }).catch(() => {});
  }, [form.startDate, form.endDate]);

  const handleApply = async () => {
    setSubmitting(true);
    try {
      await leaveApi.apply(myEmployee.id, form);
      toast.success('Leave applied successfully!');
      setApplyOpen(false);
      setForm({ leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' });
      await Promise.all([fetchLeaves(myEmployee), refreshMyLeaves()]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to apply leave');
    } finally {
      setSubmitting(false);
    }
  };

  const handleProcess = async (leaveId, status) => {
    setProcessingId(leaveId);
    try {
      await leaveApi.processLeave(leaveId, myEmployee.id, status, '');
      toast.success(`Leave ${status.toLowerCase()}`);
      await fetchLeaves(myEmployee);
    } catch {
      toast.error('Failed to process leave');
    } finally {
      setProcessingId(null);
    }
  };

  const openEdit = (leave) => {
    setEditingLeave(leave);
    setEditForm({ leaveType: leave.leaveType, startDate: leave.startDate, endDate: leave.endDate, reason: leave.reason });
    setEditOpen(true);
  };

  const handleEdit = async () => {
    try {
      await leaveApi.update(editingLeave.id, myEmployee.id, editForm);
      toast.success('Leave updated successfully');
      setEditOpen(false);
      setEditingLeave(null);
      await Promise.all([fetchLeaves(myEmployee), refreshMyLeaves()]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update leave');
    }
  };

  const handleDelete = async () => {
    try {
      await leaveApi.delete(deleteDialogId);
      toast.success('Leave deleted');
      setDeleteDialogId(null);
      await Promise.all([fetchLeaves(myEmployee), refreshMyLeaves()]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to delete leave');
    }
  };

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}><CircularProgress /></Box>;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" fontWeight={700}>Leave Management</Typography>
        {tab === 0 && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setApplyOpen(true)}>
            Apply Leave
          </Button>
        )}
      </Box>

      {/* ── Leave Balance Cards ── */}
      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        {leaveBalance.map((b) => (
          <Box key={b.type} sx={{
            flex: '1 1 160px', minWidth: 150, maxWidth: 220,
            bgcolor: b.bg, border: `1px solid ${b.color}22`,
            borderRadius: 2.5, px: 2.5, py: 2,
          }}>
            <Typography fontSize={12} fontWeight={600} color={b.color} sx={{ mb: 0.5, textTransform: 'uppercase', letterSpacing: 0.4 }}>
              {b.label}
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5, mb: 1 }}>
              <Typography fontSize={28} fontWeight={800} color={b.color} lineHeight={1}>
                {b.remaining != null ? b.remaining : b.used}
              </Typography>
              {b.total != null && (
                <Typography fontSize={12} color="text.secondary">/ {b.total}</Typography>
              )}
            </Box>
            {b.total != null ? (
              <LinearProgress
                variant="determinate"
                value={b.total > 0 ? Math.min(100, (b.used / b.total) * 100) : 0}
                sx={{
                  height: 5, borderRadius: 4, bgcolor: `${b.color}22`,
                  '& .MuiLinearProgress-bar': { bgcolor: b.color, borderRadius: 4 },
                }}
              />
            ) : (
              <Box sx={{ height: 5, borderRadius: 4, bgcolor: `${b.color}22` }} />
            )}
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.75 }}>
              <Typography fontSize={11} color="text.secondary">Used: <strong>{b.used}</strong></Typography>
              {b.remaining != null
                ? <Typography fontSize={11} color="text.secondary">Left: <strong>{b.remaining}</strong></Typography>
                : <Typography fontSize={11} color="text.secondary">days</Typography>
              }
            </Box>
          </Box>
        ))}
      </Box>

      {isManagerOrAdmin && (
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab label="My Leaves" />
          <Tab label="Team Leaves" />
        </Tabs>
      )}

      {/* ── Date range filter bar — Team Leaves only ── */}
      {tab === 1 && (
        <Box sx={{
          mb: 2, p: 2,
          bgcolor: '#fff', border: '1px solid #f1f5f9', borderRadius: '12px',
          boxShadow: '0 1px 6px rgba(0,0,0,0.05)',
        }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>

            <CalendarTodayIcon sx={{ fontSize: 17, color: '#64748b' }} />

            {/* From date */}
            <TextField
              label="From Date"
              type="date"
              size="small"
              value={filterFrom}
              onChange={e => {
                setFilterFrom(e.target.value);
                /* auto-clear To if it's before the new From */
                if (filterTo && e.target.value && filterTo < e.target.value) setFilterTo('');
              }}
              inputProps={{ max: filterTo || undefined }}
              InputLabelProps={{ shrink: true }}
              sx={{ minWidth: 160, '& .MuiOutlinedInput-root': { borderRadius: '8px' } }}
            />

            <Typography sx={{ fontSize: 13, color: '#94a3b8', fontWeight: 500 }}>to</Typography>

            {/* To date */}
            <TextField
              label="To Date"
              type="date"
              size="small"
              value={filterTo}
              onChange={e => setFilterTo(e.target.value)}
              inputProps={{ min: filterFrom || undefined }}
              InputLabelProps={{ shrink: true }}
              sx={{ minWidth: 160, '& .MuiOutlinedInput-root': { borderRadius: '8px' } }}
            />


            {/* Status filter */}
            <FormControl size="small" sx={{ minWidth: 130 }}>
              <InputLabel sx={{ fontSize: 13 }}>Status</InputLabel>
              <Select
                value={statusFilter}
                label="Status"
                onChange={e => setStatusFilter(e.target.value)}
                sx={{ borderRadius: '8px', fontSize: 13 }}
              >
                <MenuItem value="">All</MenuItem>
                <MenuItem value="APPROVED">Approved</MenuItem>
                <MenuItem value="PENDING">Pending</MenuItem>
                <MenuItem value="REJECTED">Rejected</MenuItem>
              </Select>
            </FormControl>

            {/* Clear */}
            {hasFilter && (
              <Button size="small" variant="text"
                startIcon={<FilterAltOffIcon sx={{ fontSize: 16 }} />}
                onClick={() => { setFilterFrom(''); setFilterTo(''); setStatusFilter(''); }}
                sx={{ borderRadius: '8px', textTransform: 'none', fontSize: 13, color: '#64748b', fontWeight: 600 }}
              >
                Clear
              </Button>
            )}

            {/* Live approved-count badge */}
            {onLeaveCount !== null && (
              <Box sx={{
                ml: 'auto', display: 'flex', alignItems: 'center', gap: 1,
                px: 2, py: .65, borderRadius: '20px',
                bgcolor: onLeaveCount > 0 ? '#f0fdfa' : '#f8fafc',
                border: `1px solid ${onLeaveCount > 0 ? '#99f6e4' : '#e2e8f0'}`,
              }}>
                <PeopleAltIcon sx={{ fontSize: 16, color: onLeaveCount > 0 ? '#14b8a6' : '#94a3b8' }} />
                <Typography sx={{ fontSize: 13, fontWeight: 700, color: onLeaveCount > 0 ? '#0f766e' : '#64748b' }}>
                  {onLeaveCount} approved
                </Typography>
                {filterFrom && filterTo && filterFrom !== filterTo && (
                  <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>
                    · {new Date(filterFrom + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}
                    {' – '}
                    {new Date(filterTo + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
                  </Typography>
                )}
                {filterFrom && (!filterTo || filterFrom === filterTo) && (
                  <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>
                    · {new Date((filterTo || filterFrom) + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
                  </Typography>
                )}
              </Box>
            )}
          </Box>

          {/* Range summary label */}
          {(filterFrom || filterTo) && (
            <Box sx={{ mt: 1.25, display: 'flex', alignItems: 'center', gap: .75 }}>
              <Typography sx={{ fontSize: 12.5, color: '#64748b' }}>
                Showing leaves overlapping
                <strong style={{ color: '#0f172a', marginLeft: 4 }}>
                  {filterFrom
                    ? new Date(filterFrom + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
                    : '(any start)'}
                </strong>
                {' → '}
                <strong style={{ color: '#0f172a' }}>
                  {filterTo
                    ? new Date(filterTo + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
                    : '(any end)'}
                </strong>
                <Typography component="span" sx={{ ml: 1, fontSize: 12, color: '#94a3b8' }}>
                  ({displayedLeaves.length} record{displayedLeaves.length !== 1 ? 's' : ''})
                </Typography>
              </Typography>
            </Box>
          )}
        </Box>
      )}

      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8f9fa' }}>
                {tab === 1 && <TableCell>Employee</TableCell>}
                <TableCell>Type</TableCell>
                <TableCell>From</TableCell>
                <TableCell>To</TableCell>
                <TableCell>Days</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {displayedLeaves.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4, color: '#94a3b8' }}>
                    {(filterFrom || filterTo)
                    ? `No leave records found for the selected date range`
                    : 'No leave records found'}
                  </TableCell>
                </TableRow>
              ) : displayedLeaves.map((l) => (
                <TableRow key={l.id} hover
                  sx={l.status === 'PENDING' && tab === 1 ? { bgcolor: '#fffbeb', '&:hover': { bgcolor: '#fef3c7' } } : {}}>
                  {tab === 1 && (
                    <TableCell>
                      <Box>
                        <Typography variant="body2" fontWeight={600}>{l.employeeName}</Typography>
                        {l.status === 'PENDING' && (
                          <Typography variant="caption" color="text.secondary">{l.appliedAt ? new Date(l.appliedAt).toLocaleString() : ''}</Typography>
                        )}
                      </Box>
                    </TableCell>
                  )}
                  <TableCell><Chip label={l.leaveType} size="small" variant="outlined" /></TableCell>
                  <TableCell>{l.startDate}</TableCell>
                  <TableCell>{l.endDate}</TableCell>
                  <TableCell>{l.totalDays}</TableCell>
                  <TableCell sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {l.reason}
                  </TableCell>
                  <TableCell>
                    <Chip label={l.status} size="small" color={statusColors[l.status] || 'default'} />
                  </TableCell>
                  <TableCell>
                    {tab === 0 && l.status === 'PENDING' && (
                      <>
                        <Tooltip title="Edit">
                          <IconButton size="small" color="primary" onClick={() => openEdit(l)}>
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Delete">
                          <IconButton size="small" color="error" onClick={() => setDeleteDialogId(l.id)}>
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </>
                    )}
                    {tab === 1 && l.status === 'PENDING' && (
                      <>
                        <Button size="small" color="success" startIcon={<CheckIcon />}
                          disabled={processingId === l.id}
                          onClick={() => handleProcess(l.id, 'APPROVED')}>
                          {processingId === l.id ? 'Approving…' : 'Approve'}
                        </Button>
                        <Button size="small" color="error" startIcon={<CloseIcon />}
                          disabled={processingId === l.id}
                          onClick={() => handleProcess(l.id, 'REJECTED')}>
                          {processingId === l.id ? 'Rejecting…' : 'Reject'}
                        </Button>
                      </>
                    )}
                    {l.status !== 'PENDING' && <Typography variant="caption" color="text.secondary">—</Typography>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <Dialog open={applyOpen} onClose={() => setApplyOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>Apply for Leave</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <FormControl fullWidth>
              <InputLabel>Leave Type</InputLabel>
              <Select value={form.leaveType} label="Leave Type" onChange={(e) => setForm({ ...form, leaveType: e.target.value })}>
                {[
                  { value: 'ANNUAL', label: 'Annual Leave' },
                  { value: 'LOP',    label: 'LOP (Loss of Pay)' },
                  { value: 'LWP',    label: 'LWP (Leave Without Pay)' },
                ].map(t => (
                  <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField label="Start Date" type="date" value={form.startDate}
              onChange={(e) => setForm({ ...form, startDate: e.target.value })}
              InputLabelProps={{ shrink: true }} fullWidth />
            <TextField label="End Date" type="date" value={form.endDate}
              onChange={(e) => setForm({ ...form, endDate: e.target.value })}
              InputLabelProps={{ shrink: true }} fullWidth />

            {/* Holiday info for selected range */}
            {form.startDate && form.endDate && form.endDate >= form.startDate && (
              <Box sx={{ bgcolor: rangeHolidays.length ? '#fffbeb' : '#f0fdf4', border: `1px solid ${rangeHolidays.length ? '#fde68a' : '#bbf7d0'}`, borderRadius: '10px', px: 2, py: 1.5 }}>
                {rangeHolidays.length > 0 ? (
                  <>
                    <Typography sx={{ fontSize: 12.5, fontWeight: 700, color: '#92400e', mb: 0.75 }}>
                      🎉 {rangeHolidays.length} holiday{rangeHolidays.length > 1 ? 's' : ''} in this range — automatically excluded from deduction
                    </Typography>
                    {rangeHolidays.map(h => (
                      <Box key={h.id} sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.25 }}>
                        <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: h.holidayType === 'PUBLIC_HOLIDAY' ? '#ef4444' : h.holidayType === 'RESTRICTED_HOLIDAY' ? '#f59e0b' : '#3b82f6', flexShrink: 0 }} />
                        <Typography sx={{ fontSize: 12, color: '#78350f' }}>
                          {new Date(h.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' })} — {h.name}
                        </Typography>
                      </Box>
                    ))}
                  </>
                ) : (
                  <Typography sx={{ fontSize: 12.5, color: '#15803d' }}>✓ No holidays in this range — all working days will be deducted</Typography>
                )}
              </Box>
            )}

            <TextField label="Reason" value={form.reason}
              onChange={(e) => setForm({ ...form, reason: e.target.value })}
              multiline rows={3} fullWidth />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setApplyOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleApply} disabled={submitting}>
            {submitting ? 'Submitting…' : 'Submit'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Leave Dialog */}
      <Dialog open={editOpen} onClose={() => setEditOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>Edit Leave Request</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <FormControl fullWidth>
              <InputLabel>Leave Type</InputLabel>
              <Select value={editForm.leaveType} label="Leave Type" onChange={(e) => setEditForm({ ...editForm, leaveType: e.target.value })}>
                {[
                  { value: 'ANNUAL', label: 'Annual Leave' },
                  { value: 'LOP',    label: 'LOP (Loss of Pay)' },
                  { value: 'LWP',    label: 'LWP (Leave Without Pay)' },
                ].map(t => (
                  <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField label="Start Date" type="date" value={editForm.startDate}
              onChange={(e) => setEditForm({ ...editForm, startDate: e.target.value })}
              InputLabelProps={{ shrink: true }} fullWidth />
            <TextField label="End Date" type="date" value={editForm.endDate}
              onChange={(e) => setEditForm({ ...editForm, endDate: e.target.value })}
              InputLabelProps={{ shrink: true }} fullWidth />
            <TextField label="Reason" value={editForm.reason}
              onChange={(e) => setEditForm({ ...editForm, reason: e.target.value })}
              multiline rows={3} fullWidth />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleEdit}>Save Changes</Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={Boolean(deleteDialogId)} onClose={() => setDeleteDialogId(null)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>Delete Leave Request</DialogTitle>
        <DialogContent>
          <Typography>Are you sure you want to delete this leave request? This action cannot be undone.</Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDeleteDialogId(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete}>Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default LeavesPage;
