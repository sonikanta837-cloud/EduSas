import React, { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import { useSearchParams } from 'react-router-dom';
import {
  Box, Card, Typography, Button, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Chip, Dialog,
  DialogTitle, DialogContent, DialogActions, TextField,
  CircularProgress, Tabs, Tab, IconButton, Tooltip,
} from '@mui/material';
import AddIcon      from '@mui/icons-material/Add';
import CheckIcon    from '@mui/icons-material/Check';
import CloseIcon    from '@mui/icons-material/Close';
import HistoryIcon  from '@mui/icons-material/History';
import dayjs from 'dayjs';
import { workingHoursCorrectionApi } from '../api/workingHoursCorrectionApi';
import { employeeApi } from '../api/employeeApi';
import { toast } from 'react-toastify';
import CorrectionRequestDialog from '../components/CorrectionRequestDialog';

const statusColors = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'error' };

const REASON_LABELS = {
  FORGOT_START_BREAK: 'Forgot to Start Break',
  FORGOT_END_BREAK: 'Forgot to End Break',
  APPROVED_OVERTIME: 'Approved Overtime',
  CLIENT_REQUIREMENT: 'Client Requirement',
  PRODUCTION_SUPPORT: 'Production Support',
  EMERGENCY_WORK: 'Emergency Work',
  MEETING_EXTENDED: 'Meeting Extended',
  OTHER: 'Other',
};

const BREAK_REASONS = ['FORGOT_START_BREAK', 'FORGOT_END_BREAK'];

const fmtMinutes = (mins) => {
  if (mins == null) return '—';
  const h = Math.floor(mins / 60);
  const m = Math.round(mins % 60);
  return `${h}h ${String(m).padStart(2, '0')}m`;
};

const fmtTime = (t) => (t ? dayjs(t).format('hh:mm A') : '—');

const WorkingHoursCorrectionsPage = () => {
  const { user } = useSelector((s) => s.auth);
  const [myEmployee, setMyEmployee] = useState(null);
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [applyOpen, setApplyOpen] = useState(false);
  const [processingId, setProcessingId] = useState(null);
  const [commentDrafts, setCommentDrafts] = useState({});
  const [auditDialog, setAuditDialog] = useState({ open: false, requestId: null, entries: [] });
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = parseInt(searchParams.get('tab') || '0', 10);
  const setTab = (v) => setSearchParams({ tab: v }, { replace: false });

  const isManagerOrAdmin = ['ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER', 'HR'].includes(user?.role);
  const canAct = ['ADMIN', 'DIRECTOR', 'MANAGER', 'ASSISTANT_MANAGER'].includes(user?.role);

  const fetchRequests = async (emp) => {
    if (!emp) return;
    try {
      if (tab === 0) {
        setRequests(await workingHoursCorrectionApi.getMy(emp.id));
      } else if (['ADMIN', 'DIRECTOR', 'HR'].includes(user?.role)) {
        setRequests(await workingHoursCorrectionApi.getAll());
      } else {
        setRequests(await workingHoursCorrectionApi.getForManager(emp.id));
      }
    } catch {
      toast.error('Failed to load correction requests');
    }
  };

  useEffect(() => {
    employeeApi.getByUserId(user.userId).then(async (emp) => {
      setMyEmployee(emp);
      await fetchRequests(emp);
    }).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, tab]);

  const handleProcess = async (requestId, status) => {
    setProcessingId(requestId);
    try {
      await workingHoursCorrectionApi.processRequest(requestId, status, commentDrafts[requestId] || '');
      toast.success(`Request ${status.toLowerCase()}`);
      await fetchRequests(myEmployee);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to process request');
    } finally {
      setProcessingId(null);
    }
  };

  const openAuditTrail = async (requestId) => {
    try {
      const entries = await workingHoursCorrectionApi.getAuditTrail(requestId);
      setAuditDialog({ open: true, requestId, entries });
    } catch {
      toast.error('Failed to load audit trail');
    }
  };

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}><CircularProgress /></Box>;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" fontWeight={700}>Working Hours Corrections</Typography>
        {tab === 0 && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setApplyOpen(true)}>
            Request Correction
          </Button>
        )}
      </Box>

      {isManagerOrAdmin && (
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab label="My Requests" />
          <Tab label={['ADMIN', 'DIRECTOR', 'HR'].includes(user?.role) ? 'All Requests' : 'Team Requests'} />
        </Tabs>
      )}

      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8f9fa' }}>
                {tab === 1 && <TableCell>Employee</TableCell>}
                <TableCell>Date</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Original</TableCell>
                <TableCell>Requested</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {requests.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={tab === 1 ? 7 : 6} align="center" sx={{ py: 4, color: '#94a3b8' }}>
                    No correction requests found
                  </TableCell>
                </TableRow>
              ) : requests.map((r) => (
                <TableRow key={r.id} hover
                  sx={r.status === 'PENDING' && tab === 1 ? { bgcolor: '#fffbeb', '&:hover': { bgcolor: '#fef3c7' } } : {}}>
                  {tab === 1 && (
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{r.employeeName}</Typography>
                      <Typography variant="caption" color="text.secondary">{r.employeeCode}</Typography>
                    </TableCell>
                  )}
                  <TableCell>{r.workDate}</TableCell>
                  <TableCell>
                    <Chip label={REASON_LABELS[r.reason] || r.reason} size="small" variant="outlined" />
                    {r.reasonComments && (
                      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5, maxWidth: 220 }}>
                        {r.reasonComments}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{fmtMinutes(r.originalWorkingMinutes)}</Typography>
                    <Typography variant="caption" color="text.secondary">{r.originalStatus}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {fmtMinutes(r.status === 'APPROVED' ? r.finalWorkingMinutes : r.requestedWorkingMinutes)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" display="block">
                      {r.status === 'APPROVED' ? r.finalStatus : r.requestedStatus}
                    </Typography>
                    {BREAK_REASONS.includes(r.reason) && (r.requestedBreakStartTime || r.requestedBreakEndTime) && (
                      <Typography variant="caption" sx={{ display: 'block', mt: 0.5, color: '#c2410c', fontWeight: 600, maxWidth: 200 }}>
                        Requested break: {fmtTime(r.requestedBreakStartTime)} – {fmtTime(r.requestedBreakEndTime)}
                      </Typography>
                    )}
                    {r.reason === 'APPROVED_OVERTIME' && r.overtimeRemarks && (
                      <Typography variant="caption" sx={{ display: 'block', mt: 0.5, color: '#7c3aed', fontWeight: 600, maxWidth: 200 }}>
                        Overtime: {r.overtimeRemarks}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip label={r.status} size="small" color={statusColors[r.status] || 'default'} />
                    {r.managerComment && (
                      <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5, maxWidth: 200 }}>
                        "{r.managerComment}"
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 160 }}>
                      {tab === 1 && canAct && r.status === 'PENDING' && (
                        <>
                          <TextField
                            size="small" placeholder="Comment (optional)"
                            value={commentDrafts[r.id] || ''}
                            onChange={(e) => setCommentDrafts({ ...commentDrafts, [r.id]: e.target.value })}
                          />
                          <Box sx={{ display: 'flex', gap: 0.5 }}>
                            <Button size="small" color="success" startIcon={<CheckIcon />}
                              disabled={processingId === r.id}
                              onClick={() => handleProcess(r.id, 'APPROVED')}>
                              Approve
                            </Button>
                            <Button size="small" color="error" startIcon={<CloseIcon />}
                              disabled={processingId === r.id}
                              onClick={() => handleProcess(r.id, 'REJECTED')}>
                              Reject
                            </Button>
                          </Box>
                        </>
                      )}
                      <Tooltip title="View Audit Trail">
                        <IconButton size="small" onClick={() => openAuditTrail(r.id)}>
                          <HistoryIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <CorrectionRequestDialog
        open={applyOpen}
        employeeId={myEmployee?.id}
        onClose={() => setApplyOpen(false)}
        onSubmitted={() => fetchRequests(myEmployee)}
      />

      {/* Audit Trail Dialog */}
      <Dialog open={auditDialog.open} onClose={() => setAuditDialog({ open: false, requestId: null, entries: [] })} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>Audit Trail</DialogTitle>
        <DialogContent>
          {auditDialog.entries.length === 0 ? (
            <Typography color="text.secondary">No audit history available.</Typography>
          ) : (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
              {auditDialog.entries.map((e) => (
                <Box key={e.id} sx={{ borderLeft: '3px solid #14b8a6', pl: 2 }}>
                  <Typography variant="body2" fontWeight={700}>{e.action}</Typography>
                  <Typography variant="caption" color="text.secondary" display="block">
                    {e.performedByName || 'System'} · {e.createdAt ? new Date(e.createdAt).toLocaleString() : ''}
                  </Typography>
                  <Typography variant="body2" sx={{ mt: 0.5 }}>{e.details}</Typography>
                </Box>
              ))}
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setAuditDialog({ open: false, requestId: null, entries: [] })}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default WorkingHoursCorrectionsPage;
