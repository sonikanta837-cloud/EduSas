import React, { useEffect, useState, useCallback } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Card, CardContent, Typography, Button, Grid, Chip,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  CircularProgress, Rating, Avatar, Autocomplete,
  FormControl, InputLabel, Select, MenuItem,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { performanceApi } from '../api/performanceApi';
import { employeeApi } from '../api/employeeApi';
import { toast } from 'react-toastify';

const BLANK_FORM = {
  employeeId: null,
  rating: 3,
  comments: '',
  strengths: '',
  areasOfImprovement: '',
  reviewDate: new Date().toISOString().split('T')[0],
  reviewPeriod: '',
};

// Generate Q1–Q4 for previous year and current year
const buildQuarterOptions = () => {
  const yr = new Date().getFullYear();
  const opts = [];
  for (let y = yr - 1; y <= yr; y++) {
    for (let q = 1; q <= 4; q++) opts.push(`Q${q} ${y}`);
  }
  return opts;
};
const QUARTER_OPTIONS = buildQuarterOptions();

const PerformancePage = () => {
  const { user } = useSelector((s) => s.auth);
  const isAdmin   = user?.role === 'ADMIN';
  const isManager = user?.role === 'MANAGER';

  const [myEmployee,   setMyEmployee]   = useState(null);
  const [reviews,      setReviews]      = useState([]);
  const [avgRating,    setAvgRating]    = useState(null);
  const [loading,      setLoading]      = useState(true);
  const [addOpen,      setAddOpen]      = useState(false);
  const [form,         setForm]         = useState(BLANK_FORM);
  const [empOptions,   setEmpOptions]   = useState([]);   // available employees for the picker
  const [empLoading,   setEmpLoading]   = useState(false);
  const [submitting,   setSubmitting]   = useState(false);

  // ── Initial load ────────────────────────────────────────────────────────────
  useEffect(() => {
    employeeApi.getByUserId(user.userId)
      .then(async (emp) => {
        setMyEmployee(emp);
        const [revs, avg] = await Promise.all([
          performanceApi.getByEmployee(emp.id).catch(() => []),
          performanceApi.getAverage(emp.id).catch(() => ({ averageRating: 0 })),
        ]);
        setReviews(revs);
        setAvgRating(avg?.averageRating || 0);
      })
      .catch(() => toast.error('Failed to load performance data'))
      .finally(() => setLoading(false));
  }, [user]);

  // ── Load reviewable employees when dialog opens ──────────────────────────────
  const openDialog = useCallback(async () => {
    setForm({ ...BLANK_FORM, reviewDate: new Date().toISOString().split('T')[0] });
    setAddOpen(true);
    if (!myEmployee) return;
    setEmpLoading(true);
    try {
      let list;
      if (isAdmin) {
        list = await employeeApi.getAll();
      } else {
        list = await employeeApi.getTeam(myEmployee.id);
      }
      // Exclude self and admins — managers/admins can only review non-admin employees
      setEmpOptions((list || []).filter((e) => e.id !== myEmployee.id && e.role !== 'ADMIN'));
    } catch {
      toast.error('Failed to load employees');
    } finally {
      setEmpLoading(false);
    }
  }, [myEmployee, isAdmin]);

  // ── Submit ──────────────────────────────────────────────────────────────────
  const handleCreate = async () => {
    if (!form.employeeId) { toast.error('Please select an employee'); return; }
    if (!form.reviewPeriod) { toast.error('Please select a review period'); return; }
    setSubmitting(true);
    try {
      const payload = {
        ...form,
        reviewerId: myEmployee.id,
        employeeId: form.employeeId,
      };
      await performanceApi.create(payload);
      toast.success('Review submitted!');
      setAddOpen(false);
      const revs = await performanceApi.getByEmployee(myEmployee.id);
      setReviews(revs);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit review');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
      <CircularProgress />
    </Box>
  );

  const ratingColor = (r) => r >= 4 ? 'success' : r >= 3 ? 'warning' : 'error';

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" fontWeight={700}>Performance Reviews</Typography>
        {(isAdmin || isManager) && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={openDialog}>
            New Review
          </Button>
        )}
      </Box>

      {/* Average rating summary */}
      {avgRating > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 3 }}>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h2" fontWeight={700} color="primary">
                  {avgRating.toFixed(1)}
                </Typography>
                <Typography variant="body2" color="text.secondary">Avg. Rating</Typography>
              </Box>
              <Box>
                <Rating value={avgRating} readOnly precision={0.5} size="large" />
                <Typography variant="body2" color="text.secondary">
                  Based on {reviews.length} review{reviews.length !== 1 ? 's' : ''}
                </Typography>
              </Box>
            </Box>
          </CardContent>
        </Card>
      )}

      {/* Reviews grid */}
      <Grid container spacing={2}>
        {reviews.length === 0 ? (
          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Typography align="center" color="text.secondary" py={3}>
                  No performance reviews yet
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ) : reviews.map((r) => (
          <Grid item xs={12} md={6} key={r.id}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1.5 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                    <Avatar sx={{ width: 36, height: 36, bgcolor: '#1976d2', fontSize: '0.85rem' }}>
                      {r.reviewerName?.charAt(0)}
                    </Avatar>
                    <Box>
                      <Typography variant="subtitle2" fontWeight={600}>{r.reviewerName}</Typography>
                      <Typography variant="caption" color="text.secondary">{r.reviewPeriod}</Typography>
                    </Box>
                  </Box>
                  <Chip label={`${r.rating}/5`} size="small" color={ratingColor(r.rating)} />
                </Box>
                <Rating value={r.rating} readOnly size="small" sx={{ mb: 1 }} />
                {r.comments && (
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1, fontStyle: 'italic' }}>
                    "{r.comments}"
                  </Typography>
                )}
                {r.strengths && (
                  <Box sx={{ mb: 0.5 }}>
                    <Typography variant="caption" fontWeight={600} color="success.main">Strengths: </Typography>
                    <Typography variant="caption">{r.strengths}</Typography>
                  </Box>
                )}
                {r.areasOfImprovement && (
                  <Box>
                    <Typography variant="caption" fontWeight={600} color="warning.main">Improve: </Typography>
                    <Typography variant="caption">{r.areasOfImprovement}</Typography>
                  </Box>
                )}
                <Typography variant="caption" color="text.secondary" display="block" mt={1}>
                  {r.reviewDate}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* New Review Dialog */}
      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>Submit Performance Review</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>

            {/* Employee name picker */}
            <Autocomplete
              options={empOptions}
              getOptionLabel={(emp) => emp.fullName || ''}
              loading={empLoading}
              value={empOptions.find((e) => e.id === form.employeeId) || null}
              onChange={(_, emp) => setForm({ ...form, employeeId: emp ? emp.id : null })}
              renderOption={(props, emp) => (
                <Box component="li" {...props} key={emp.id}>
                  <Box>
                    <Typography variant="body2" fontWeight={600}>{emp.fullName}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {emp.position || emp.role} · {emp.department}
                    </Typography>
                  </Box>
                </Box>
              )}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Employee"
                  required
                  InputProps={{
                    ...params.InputProps,
                    endAdornment: (
                      <>
                        {empLoading ? <CircularProgress color="inherit" size={16} /> : null}
                        {params.InputProps.endAdornment}
                      </>
                    ),
                  }}
                />
              )}
              fullWidth
            />

            {/* Rating */}
            <Box>
              <Typography variant="body2" gutterBottom fontWeight={500}>Rating</Typography>
              <Rating
                value={form.rating}
                onChange={(_, v) => setForm({ ...form, rating: v })}
                size="large"
              />
            </Box>

            {/* Review period — quarter dropdown */}
            <FormControl fullWidth required>
              <InputLabel>Review Period</InputLabel>
              <Select
                value={form.reviewPeriod}
                label="Review Period"
                onChange={(e) => setForm({ ...form, reviewPeriod: e.target.value })}
              >
                {QUARTER_OPTIONS.map((q) => (
                  <MenuItem key={q} value={q}>{q}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <TextField
              label="Comments"
              value={form.comments}
              onChange={(e) => setForm({ ...form, comments: e.target.value })}
              multiline rows={2} fullWidth
            />
            <TextField
              label="Strengths"
              value={form.strengths}
              onChange={(e) => setForm({ ...form, strengths: e.target.value })}
              multiline rows={2} fullWidth
            />
            <TextField
              label="Areas of Improvement"
              value={form.areasOfImprovement}
              onChange={(e) => setForm({ ...form, areasOfImprovement: e.target.value })}
              multiline rows={2} fullWidth
            />
            <TextField
              label="Review Date"
              type="date"
              value={form.reviewDate}
              onChange={(e) => setForm({ ...form, reviewDate: e.target.value })}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setAddOpen(false)} disabled={submitting}>Cancel</Button>
          <Button variant="contained" onClick={handleCreate} disabled={submitting}>
            {submitting ? <CircularProgress size={20} sx={{ mr: 1 }} /> : null}
            Submit Review
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PerformancePage;
