import React, { useEffect, useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Box, TextField,
  MenuItem, Select, FormControl, InputLabel, Button, Typography,
} from '@mui/material';
import { workingHoursCorrectionApi } from '../api/workingHoursCorrectionApi';
import { toast } from 'react-toastify';
import AppTimePicker from './AppTimePicker';

const REASON_OPTIONS = [
  { value: 'FORGOT_START_BREAK', label: 'Forgot to Start Break' },
  { value: 'FORGOT_END_BREAK',   label: 'Forgot to End Break' },
  { value: 'APPROVED_OVERTIME',  label: 'Approved Overtime' },
  { value: 'CLIENT_REQUIREMENT', label: 'Client Requirement' },
  { value: 'PRODUCTION_SUPPORT', label: 'Production Support' },
  { value: 'EMERGENCY_WORK',     label: 'Emergency Work' },
  { value: 'MEETING_EXTENDED',   label: 'Meeting Extended' },
  { value: 'OTHER',              label: 'Other' },
];

const BREAK_REASONS = ['FORGOT_START_BREAK', 'FORGOT_END_BREAK'];

const emptyForm = (workDate) => ({
  workDate: workDate || '',
  reason: '',
  reasonComments: '',
  breakStartTime: '',
  breakEndTime: '',
  overtimeRemarks: '',
});

/**
 * Shared submission dialog for a Working Hours Correction Request — used by both
 * the Attendance page (per-day trigger) and the Working Hours Corrections page.
 */
const CorrectionRequestDialog = ({ open, onClose, employeeId, workDate, onSubmitted }) => {
  const [form, setForm] = useState(emptyForm(workDate));
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) setForm(emptyForm(workDate));
  }, [open, workDate]);

  const isBreakReason = BREAK_REASONS.includes(form.reason);
  const isOvertimeReason = form.reason === 'APPROVED_OVERTIME';
  const isOtherReason = form.reason === 'OTHER';

  const isValid = () => {
    if (!form.workDate || !form.reason) return false;
    if (isBreakReason) {
      if (!form.breakStartTime || !form.breakEndTime) return false;
      if (form.breakEndTime <= form.breakStartTime) return false;
    }
    if (isOvertimeReason && !form.overtimeRemarks?.trim()) return false;
    if (isOtherReason && !form.reasonComments?.trim()) return false;
    return true;
  };

  const handleSubmit = async () => {
    if (!isValid()) { toast.error('Please fill in all required fields'); return; }
    setSubmitting(true);
    try {
      const payload = {
        workDate: form.workDate,
        reason: form.reason,
        reasonComments: form.reasonComments || null,
        requestedBreakStartTime: isBreakReason ? `${form.workDate}T${form.breakStartTime}:00` : null,
        requestedBreakEndTime: isBreakReason ? `${form.workDate}T${form.breakEndTime}:00` : null,
        overtimeRemarks: isOvertimeReason ? form.overtimeRemarks : null,
      };
      await workingHoursCorrectionApi.submit(employeeId, payload);
      toast.success('Correction request submitted');
      onClose();
      if (onSubmitted) onSubmitted();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit correction request');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle fontWeight={700}>Working Hours Correction Request</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <TextField
            label="Date"
            type="date"
            value={form.workDate}
            onChange={(e) => setForm({ ...form, workDate: e.target.value })}
            InputLabelProps={{ shrink: true }}
            inputProps={{ max: new Date().toISOString().split('T')[0] }}
            fullWidth
          />

          <FormControl fullWidth>
            <InputLabel>Reason</InputLabel>
            <Select
              value={form.reason}
              label="Reason"
              onChange={(e) => setForm({ ...form, reason: e.target.value })}
            >
              {REASON_OPTIONS.map((r) => (
                <MenuItem key={r.value} value={r.value}>{r.label}</MenuItem>
              ))}
            </Select>
          </FormControl>

          {isBreakReason && (
            <>
              <Typography sx={{ fontSize: 12.5, color: '#64748b' }}>
                Enter the actual break time so Working Hours, Break Time, Office Time and Overtime can be recalculated.
              </Typography>
              <Box sx={{ display: 'flex', gap: 2 }}>
                <AppTimePicker
                  label="Break Start Time"
                  value={form.breakStartTime}
                  onChange={(v) => setForm({ ...form, breakStartTime: v })}
                />
                <AppTimePicker
                  label="Break End Time"
                  value={form.breakEndTime}
                  onChange={(v) => setForm({ ...form, breakEndTime: v })}
                />
              </Box>
            </>
          )}

          {isOvertimeReason && (
            <TextField
              label="Overtime Details / Remarks"
              value={form.overtimeRemarks}
              onChange={(e) => setForm({ ...form, overtimeRemarks: e.target.value })}
              multiline rows={3} fullWidth
              required
            />
          )}

          <TextField
            label={isOtherReason ? 'Comments (required)' : 'Comments'}
            value={form.reasonComments}
            onChange={(e) => setForm({ ...form, reasonComments: e.target.value })}
            multiline rows={2} fullWidth
            required={isOtherReason}
          />
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={submitting || !isValid()}>
          {submitting ? 'Submitting…' : 'Submit'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default CorrectionRequestDialog;
