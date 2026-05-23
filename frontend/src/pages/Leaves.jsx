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
import AddIcon from '@mui/icons-material/Add';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { leaveApi } from '../api/leaveApi';
import { employeeApi } from '../api/employeeApi';
import { toast } from 'react-toastify';

const statusColors = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'error' };

// Fixed quota (days) per leave type — keyed by the exact leaveType string stored in DB
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
  const [searchParams, setSearchParams] = useSearchParams();
  const tab    = parseInt(searchParams.get('tab') || '0', 10);
  const setTab = (v) => setSearchParams({ tab: v }, { replace: false });
  const [form, setForm] = useState({ leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' });
  const [editOpen, setEditOpen] = useState(false);
  const [editingLeave, setEditingLeave] = useState(null);
  const [editForm, setEditForm] = useState({ leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' });
  const [deleteDialogId, setDeleteDialogId] = useState(null);
  const isManagerOrAdmin = ['ADMIN', 'MANAGER', 'ASSISTANT_MANAGER'].includes(user?.role);

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
        if (user?.role === 'ADMIN') setLeaves(await leaveApi.getAll());
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
  }, [user, tab]);

  const refreshMyLeaves = async () => {
    if (!myEmployee) return;
    const mine = await leaveApi.getMyLeaves(myEmployee.id);
    setMyLeaves(mine);
  };

  const handleApply = async () => {
    try {
      await leaveApi.apply(myEmployee.id, form);
      toast.success('Leave applied successfully!');
      setApplyOpen(false);
      setForm({ leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' });
      await Promise.all([fetchLeaves(myEmployee), refreshMyLeaves()]);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to apply leave');
    }
  };

  const handleProcess = async (leaveId, status) => {
    try {
      await leaveApi.processLeave(leaveId, myEmployee.id, status, '');
      toast.success(`Leave ${status.toLowerCase()}`);
      await fetchLeaves(myEmployee);
    } catch {
      toast.error('Failed to process leave');
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
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setApplyOpen(true)}>
          Apply Leave
        </Button>
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
              {leaves.length === 0 ? (
                <TableRow><TableCell colSpan={8} align="center">No leave records found</TableCell></TableRow>
              ) : leaves.map((l) => (
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
                          onClick={() => handleProcess(l.id, 'APPROVED')}>Approve</Button>
                        <Button size="small" color="error" startIcon={<CloseIcon />}
                          onClick={() => handleProcess(l.id, 'REJECTED')}>Reject</Button>
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
            <TextField label="Reason" value={form.reason}
              onChange={(e) => setForm({ ...form, reason: e.target.value })}
              multiline rows={3} fullWidth />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setApplyOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleApply}>Submit</Button>
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
