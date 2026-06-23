import React, { useEffect, useState, useCallback } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, Chip, CircularProgress, Paper, Button,
  Table, TableBody, TableCell, TableHead, TableRow,
  Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Divider, Tooltip, IconButton,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import CancelOutlinedIcon     from '@mui/icons-material/CancelOutlined';
import HistoryIcon            from '@mui/icons-material/History';
import dayjs from 'dayjs';
import { correctionApi } from '../api/correctionApi';
import { toast } from 'react-toastify';

const fmtTime = (t) => (t ? String(t).substring(0, 5) : '—');

const STATUS_CFG = {
  PENDING_MANAGER_APPROVAL: { label: 'Pending', bg: '#fff7ed', color: '#c2410c' },
  APPROVED:                  { label: 'Approved', bg: '#dcfce7', color: '#16a34a' },
  REJECTED:                  { label: 'Rejected',  bg: '#fee2e2', color: '#dc2626' },
};

const CorrectionHistory = () => {
  const { user } = useSelector((s) => s.auth);
  const isManagerOrAbove = ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER'].includes(user?.role);

  const [myRequests,   setMyRequests]   = useState([]);
  const [teamRequests, setTeamRequests] = useState([]);
  const [auditLogs,    setAuditLogs]    = useState([]);
  const [loading,      setLoading]      = useState(true);
  const [showAudit,    setShowAudit]    = useState(false);

  // Review dialog
  const [reviewOpen,       setReviewOpen]       = useState(false);
  const [reviewTarget,     setReviewTarget]     = useState(null);
  const [reviewAction,     setReviewAction]     = useState('approve');
  const [reviewForm,       setReviewForm]       = useState({ logoutTime: '', comment: '' });
  const [reviewSubmitting, setReviewSubmitting] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [my, team] = await Promise.all([
        correctionApi.getMy(),
        isManagerOrAbove
          ? (user?.role === 'MANAGER' || user?.role === 'ASSISTANT_MANAGER'
              ? correctionApi.getPendingTeam()
              : correctionApi.getPending())
          : Promise.resolve([]),
      ]);
      setMyRequests(Array.isArray(my) ? my : []);
      setTeamRequests(Array.isArray(team) ? team : []);
    } catch {
      toast.error('Failed to load correction requests');
    } finally {
      setLoading(false);
    }
  }, [isManagerOrAbove, user?.role]);

  useEffect(() => { loadData(); }, [loadData]);

  useEffect(() => {
    if (!isManagerOrAbove || !showAudit) return;
    correctionApi.getAuditLogs().then(setAuditLogs).catch(() => {});
  }, [isManagerOrAbove, showAudit]);

  const openReview = (req, action) => {
    setReviewTarget(req);
    setReviewAction(action);
    setReviewForm({
      logoutTime: action === 'approve' ? (req.requestedLogoutTime?.slice(0, 5) || '') : '',
      comment: '',
    });
    setReviewOpen(true);
  };

  const handleReview = async () => {
    setReviewSubmitting(true);
    try {
      if (reviewAction === 'approve') {
        if (!reviewForm.logoutTime) { toast.error('Logout time is required'); setReviewSubmitting(false); return; }
        await correctionApi.approve(reviewTarget.id, reviewForm.logoutTime, reviewForm.comment);
        toast.success('Correction approved — attendance updated');
      } else {
        await correctionApi.reject(reviewTarget.id, reviewForm.comment);
        toast.success('Correction request rejected');
      }
      setReviewOpen(false);
      loadData();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to process request');
    } finally {
      setReviewSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={3}>Timesheet Correction History</Typography>

      {/* ── My Requests — hidden for pure admin accounts ── */}
      {user?.role !== 'ADMIN' && user?.role !== 'DIRECTOR' && <Paper sx={{ mb: 3, overflow: 'hidden', border: '1px solid #e2e8f0' }}>
        <Box sx={{ px: 2.5, py: 1.75, bgcolor: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
          <Typography fontWeight={700} fontSize={14} color="#0f172a">My Correction Requests</Typography>
        </Box>
        {myRequests.length === 0 ? (
          <Box sx={{ py: 5, textAlign: 'center', color: '#94a3b8', fontSize: 13 }}>
            No correction requests submitted yet
          </Box>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                {['#', 'Date', 'First Login', 'Requested Logout', 'Reason', 'Submitted', 'Status', 'Resolved By', 'Remarks'].map(h => (
                  <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12, color: '#374151', py: 1.25 }}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {myRequests.map(r => {
                const cfg = STATUS_CFG[r.status] || STATUS_CFG.PENDING_MANAGER_APPROVAL;
                return (
                  <TableRow key={r.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                    <TableCell sx={{ fontSize: 12, color: '#94a3b8' }}>#{r.id}</TableCell>
                    <TableCell sx={{ fontSize: 13, fontWeight: 600 }}>
                      {dayjs(r.workDate).format('DD MMM YYYY')}
                    </TableCell>
                    <TableCell sx={{ fontSize: 13 }}>{fmtTime(r.firstLoginTime || r.loginTime)}</TableCell>
                    <TableCell sx={{ fontSize: 13 }}>{fmtTime(r.requestedLogoutTime)}</TableCell>
                    <TableCell sx={{ maxWidth: 200 }}>
                      <Typography noWrap sx={{ fontSize: 12, color: '#64748b', maxWidth: 200 }}>{r.reason}</Typography>
                    </TableCell>
                    <TableCell sx={{ fontSize: 12, color: '#94a3b8', whiteSpace: 'nowrap' }}>
                      {r.createdAt ? dayjs(r.createdAt).format('DD MMM, HH:mm') : '—'}
                    </TableCell>
                    <TableCell>
                      <Chip label={cfg.label} size="small"
                        sx={{ fontSize: 11, fontWeight: 700, bgcolor: cfg.bg, color: cfg.color }} />
                    </TableCell>
                    <TableCell sx={{ fontSize: 12, color: '#64748b' }}>{r.resolvedBy || '—'}</TableCell>
                    <TableCell sx={{ fontSize: 12, color: '#64748b', maxWidth: 160 }}>
                      <Typography noWrap sx={{ fontSize: 12, maxWidth: 160 }}>{r.resolverComment || '—'}</Typography>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </Paper>}

      {/* ── Manager / HR: Pending Team Requests ── */}
      {isManagerOrAbove && (
        <Paper sx={{ mb: 3, overflow: 'hidden', border: '1px solid #e2e8f0' }}>
          <Box sx={{ px: 2.5, py: 1.75, bgcolor: '#f8fafc', borderBottom: '1px solid #e2e8f0',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Typography fontWeight={700} fontSize={14} color="#0f172a">
                Pending Correction Requests
              </Typography>
              {teamRequests.length > 0 && (
                <Chip label={teamRequests.length} size="small"
                  sx={{ bgcolor: '#fff7ed', color: '#c2410c', fontWeight: 700, fontSize: 11, height: 20 }} />
              )}
            </Box>
            <Button size="small" variant="outlined" startIcon={<HistoryIcon sx={{ fontSize: 14 }} />}
              onClick={() => setShowAudit(v => !v)}
              sx={{ textTransform: 'none', fontSize: 12, borderRadius: '8px' }}>
              {showAudit ? 'Hide Audit Log' : 'View Audit Log'}
            </Button>
          </Box>

          {teamRequests.length === 0 ? (
            <Box sx={{ py: 5, textAlign: 'center', color: '#94a3b8', fontSize: 13 }}>
              No pending correction requests
            </Box>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: '#f8fafc' }}>
                  {['Employee', 'Date', 'First Login', 'Requested Logout', 'Reason', 'Submitted', 'Actions'].map(h => (
                    <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12, color: '#374151', py: 1.25 }}>{h}</TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {teamRequests.map(r => (
                  <TableRow key={r.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                    <TableCell>
                      <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{r.employeeName}</Typography>
                      <Typography sx={{ fontSize: 11, color: '#94a3b8' }}>{r.department}</Typography>
                    </TableCell>
                    <TableCell sx={{ fontSize: 13, fontWeight: 600 }}>
                      {dayjs(r.workDate).format('DD MMM YYYY')}
                    </TableCell>
                    <TableCell sx={{ fontSize: 13 }}>{fmtTime(r.firstLoginTime || r.loginTime)}</TableCell>
                    <TableCell sx={{ fontSize: 13 }}>{fmtTime(r.requestedLogoutTime)}</TableCell>
                    <TableCell sx={{ maxWidth: 180 }}>
                      <Typography noWrap sx={{ fontSize: 12, color: '#64748b', maxWidth: 180 }}>{r.reason}</Typography>
                    </TableCell>
                    <TableCell sx={{ fontSize: 12, color: '#94a3b8', whiteSpace: 'nowrap' }}>
                      {r.createdAt ? dayjs(r.createdAt).format('DD MMM, HH:mm') : '—'}
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', gap: 0.75 }}>
                        <Tooltip title="Approve">
                          <IconButton size="small" onClick={() => openReview(r, 'approve')}
                            sx={{ color: '#16a34a', bgcolor: '#f0fdf4', '&:hover': { bgcolor: '#dcfce7' } }}>
                            <CheckCircleOutlineIcon sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Reject">
                          <IconButton size="small" onClick={() => openReview(r, 'reject')}
                            sx={{ color: '#dc2626', bgcolor: '#fef2f2', '&:hover': { bgcolor: '#fee2e2' } }}>
                            <CancelOutlinedIcon sx={{ fontSize: 17 }} />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          {/* Audit Log */}
          {showAudit && (
            <>
              <Divider />
              <Box sx={{ px: 2.5, py: 1.5, bgcolor: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                <Typography fontWeight={700} fontSize={13} color="#374151">Audit Log</Typography>
              </Box>
              {auditLogs.length === 0 ? (
                <Box sx={{ py: 3, textAlign: 'center', color: '#94a3b8', fontSize: 13 }}>No audit entries</Box>
              ) : (
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: '#f8fafc' }}>
                      {['Employee', 'Date', 'Action', 'Req. Logout', 'Approved Logout', 'Performed By', 'Remarks', 'Time'].map(h => (
                        <TableCell key={h} sx={{ fontWeight: 700, fontSize: 11.5, color: '#374151', py: 1 }}>{h}</TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {auditLogs.slice(0, 100).map(log => (
                      <TableRow key={log.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                        <TableCell sx={{ fontSize: 12 }}>{log.employeeName}</TableCell>
                        <TableCell sx={{ fontSize: 12 }}>{log.workDate}</TableCell>
                        <TableCell>
                          <Chip label={log.action} size="small" sx={{
                            fontSize: 10, fontWeight: 700,
                            bgcolor: log.action === 'APPROVED' ? '#dcfce7' : log.action === 'REJECTED' ? '#fee2e2' : '#fff7ed',
                            color:   log.action === 'APPROVED' ? '#16a34a' : log.action === 'REJECTED' ? '#dc2626' : '#c2410c',
                          }} />
                        </TableCell>
                        <TableCell sx={{ fontSize: 12, color: '#64748b' }}>{fmtTime(log.requestedLogoutTime)}</TableCell>
                        <TableCell sx={{ fontSize: 12, color: '#16a34a', fontWeight: 600 }}>
                          {log.approvedLogoutTime ? fmtTime(log.approvedLogoutTime) : '—'}
                        </TableCell>
                        <TableCell sx={{ fontSize: 12, color: '#64748b' }}>{log.approverName || '—'}</TableCell>
                        <TableCell sx={{ maxWidth: 160 }}>
                          <Typography noWrap sx={{ fontSize: 11.5, color: '#64748b', maxWidth: 160 }}>{log.remarks || '—'}</Typography>
                        </TableCell>
                        <TableCell sx={{ fontSize: 11.5, color: '#94a3b8', whiteSpace: 'nowrap' }}>
                          {log.actionDateTime ? dayjs(log.actionDateTime).format('DD MMM, HH:mm') : '—'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </>
          )}
        </Paper>
      )}

      {/* Review Dialog */}
      <Dialog open={reviewOpen} onClose={() => !reviewSubmitting && setReviewOpen(false)}
        maxWidth="xs" fullWidth PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16, pb: 0 }}>
          {reviewAction === 'approve' ? 'Approve Correction Request' : 'Reject Correction Request'}
        </DialogTitle>
        <Divider sx={{ mt: 1.5 }} />
        <DialogContent sx={{ pt: 2.5 }}>
          {reviewTarget && (
            <Box sx={{ bgcolor: '#f8fafc', borderRadius: '10px', p: 1.5, mb: 2 }}>
              <Typography sx={{ fontSize: 12.5, color: '#374151' }}>
                <strong>{reviewTarget.employeeName}</strong> — {dayjs(reviewTarget.workDate).format('DD MMM YYYY')}
              </Typography>
              <Typography sx={{ fontSize: 12, color: '#64748b', mt: 0.25 }}>
                Login: {fmtTime(reviewTarget.loginTime)} &nbsp;|&nbsp; Requested: {fmtTime(reviewTarget.requestedLogoutTime)}
              </Typography>
              <Typography sx={{ fontSize: 12, color: '#64748b', mt: 0.25 }}>
                Reason: {reviewTarget.reason}
              </Typography>
            </Box>
          )}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {reviewAction === 'approve' && (
              <TextField label="Approved Logout Time *" type="time" size="small" fullWidth
                InputLabelProps={{ shrink: true }}
                value={reviewForm.logoutTime}
                onChange={e => setReviewForm(f => ({ ...f, logoutTime: e.target.value }))}
                helperText="Working hours will be recalculated automatically" />
            )}
            <TextField
              label={reviewAction === 'approve' ? 'Comment (optional)' : 'Reason for Rejection *'}
              size="small" fullWidth multiline rows={2}
              value={reviewForm.comment}
              onChange={e => setReviewForm(f => ({ ...f, comment: e.target.value }))} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={() => setReviewOpen(false)} disabled={reviewSubmitting}
            sx={{ textTransform: 'none', borderRadius: '8px' }}>Cancel</Button>
          <Button variant="contained" onClick={handleReview} disabled={reviewSubmitting}
            sx={{
              textTransform: 'none', borderRadius: '8px', minWidth: 120,
              bgcolor: reviewAction === 'approve' ? '#16a34a' : '#dc2626',
              '&:hover': { bgcolor: reviewAction === 'approve' ? '#15803d' : '#b91c1c' },
            }}>
            {reviewSubmitting
              ? <CircularProgress size={18} color="inherit" />
              : reviewAction === 'approve' ? 'Approve & Update' : 'Reject'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CorrectionHistory;
