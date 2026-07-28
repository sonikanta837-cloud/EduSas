import React, { useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Box, Typography, Button, TextField, CircularProgress,
  Chip, Divider, LinearProgress, Select, MenuItem, FormControl, InputLabel,
} from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import AttachFileIcon    from '@mui/icons-material/AttachFile';
import AccessTimeIcon    from '@mui/icons-material/AccessTime';
import dayjs from 'dayjs';
import { correctionApi } from '../api/correctionApi';
import { toast } from 'react-toastify';

const fmtTime = (t) => (t ? String(t).substring(0, 5) : '--:--');

const HOURS   = Array.from({ length: 12 }, (_, i) => i + 1);
const MINUTES = Array.from({ length: 60 }, (_, i) => i);

const to24Hour = (hour12, period) => {
  const h = Number(hour12) % 12;
  return period === 'PM' ? h + 12 : h;
};

const buildTimeString = (hour, minute, period) => {
  if (!hour || minute === '' || minute == null || !period) return '';
  const h24 = to24Hour(hour, period);
  return `${String(h24).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
};

const emptyForm = () => ({ hour: '', minute: '', period: '', reason: '' });

const CorrectionPopup = ({ sessions, onAllDone }) => {
  const [index,      setIndex]      = useState(0);
  const [form,       setForm]       = useState(emptyForm);
  const [attachment, setAttachment] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const total   = sessions.length;
  const current = sessions[index];
  if (!current) return null;

  const progress = Math.round((index / total) * 100);
  const logoutTime = buildTimeString(form.hour, form.minute, form.period);

  const handleSubmit = async () => {
    if (!logoutTime) { toast.error('Please select your actual logout time'); return; }
    if (!form.reason.trim()) { toast.error('Please provide a reason'); return; }

    setSubmitting(true);
    try {
      await correctionApi.submit({
        sessionId:          current.id,
        requestedLogoutTime: logoutTime,
        reason:             form.reason,
        attachment,
      });
      toast.success(`Correction submitted for ${dayjs(current.workDate).format('DD MMM YYYY')}`);

      if (index + 1 >= total) {
        onAllDone();
      } else {
        setIndex(i => i + 1);
        setForm(emptyForm());
        setAttachment(null);
      }
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to submit correction request');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open
      maxWidth="sm"
      fullWidth
      disableEscapeKeyDown
      PaperProps={{ sx: { borderRadius: '16px' } }}
    >
      {/* Header */}
      <DialogTitle sx={{ p: 0 }}>
        <Box sx={{ bgcolor: '#fff7ed', px: 3, py: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <WarningAmberIcon sx={{ color: '#c2410c', fontSize: 26 }} />
          <Box sx={{ flex: 1 }}>
            <Typography fontWeight={700} fontSize={16} color="#7c2d12">
              Missing Logout Detected
            </Typography>
            <Typography fontSize={12} color="#92400e" mt={0.25}>
              Please submit your actual logout time before proceeding
            </Typography>
          </Box>
          {total > 1 && (
            <Chip label={`${index + 1} of ${total}`} size="small"
              sx={{ bgcolor: '#fed7aa', color: '#9a3412', fontWeight: 700 }} />
          )}
        </Box>
        {total > 1 && (
          <LinearProgress
            variant="determinate" value={progress}
            sx={{ height: 3, bgcolor: '#fed7aa', '& .MuiLinearProgress-bar': { bgcolor: '#ea580c' } }}
          />
        )}
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ pt: 2.5, pb: 1 }}>
        {/* Session info */}
        <Box sx={{ bgcolor: '#f8fafc', borderRadius: '10px', p: 2, mb: 2.5 }}>
          <Typography fontSize={13} fontWeight={700} color="#374151" mb={1}>
            Attendance Record
          </Typography>
          <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
            <Box>
              <Typography variant="caption" color="text.secondary">Date</Typography>
              <Typography fontWeight={600} fontSize={14}>
                {dayjs(current.workDate).format('ddd, DD MMM YYYY')}
              </Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">First Login</Typography>
              <Typography fontWeight={600} fontSize={14} color="#16a34a">
                {fmtTime(current.firstLoginTime || current.loginTime)}
              </Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Logout Time</Typography>
              <Typography fontWeight={600} fontSize={14} color="#dc2626">
                Not recorded
              </Typography>
            </Box>
          </Box>
        </Box>

        {/* Form */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
              <AccessTimeIcon sx={{ fontSize: 14 }} /> Actual Logout Time *
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <FormControl size="small" sx={{ minWidth: 90 }}>
                <InputLabel id="logout-hour-label">Hour</InputLabel>
                <Select
                  labelId="logout-hour-label"
                  label="Hour"
                  value={form.hour}
                  onChange={e => setForm(f => ({ ...f, hour: e.target.value }))}
                  MenuProps={{ PaperProps: { sx: { maxHeight: 260 } } }}
                >
                  {HOURS.map(h => (
                    <MenuItem key={h} value={h}>{String(h).padStart(2, '0')}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 90 }}>
                <InputLabel id="logout-minute-label">Minute</InputLabel>
                <Select
                  labelId="logout-minute-label"
                  label="Minute"
                  value={form.minute}
                  onChange={e => setForm(f => ({ ...f, minute: e.target.value }))}
                  MenuProps={{ PaperProps: { sx: { maxHeight: 260 } } }}
                >
                  {MINUTES.map(m => (
                    <MenuItem key={m} value={m}>{String(m).padStart(2, '0')}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 90 }}>
                <InputLabel id="logout-period-label">AM/PM</InputLabel>
                <Select
                  labelId="logout-period-label"
                  label="AM/PM"
                  value={form.period}
                  onChange={e => setForm(f => ({ ...f, period: e.target.value }))}
                >
                  <MenuItem value="AM">AM</MenuItem>
                  <MenuItem value="PM">PM</MenuItem>
                </Select>
              </FormControl>
            </Box>
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
              Select the time you actually stopped working
            </Typography>
          </Box>
          <TextField
            label="Reason *"
            size="small"
            fullWidth
            multiline
            rows={3}
            placeholder="Explain why the logout was not recorded (e.g., system crashed, forgot to log out)…"
            value={form.reason}
            onChange={e => setForm(f => ({ ...f, reason: e.target.value }))}
          />
          <Box>
            <Button
              component="label"
              size="small"
              startIcon={<AttachFileIcon />}
              variant="outlined"
              sx={{ textTransform: 'none', borderRadius: '8px', fontSize: 12 }}
            >
              {attachment ? attachment.name : 'Attach Supporting Document (optional)'}
              <input
                type="file"
                hidden
                onChange={e => setAttachment(e.target.files[0] || null)}
                accept=".pdf,.jpg,.jpeg,.png"
              />
            </Button>
            {attachment && (
              <Button size="small" sx={{ ml: 1, fontSize: 11, color: '#dc2626' }}
                onClick={() => setAttachment(null)}>Remove</Button>
            )}
          </Box>
        </Box>
      </DialogContent>

      <Divider sx={{ mt: 1.5 }} />

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Typography variant="caption" color="text.disabled" sx={{ flex: 1 }}>
          This request will be sent to your reporting manager for approval.
        </Typography>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={submitting || !logoutTime || !form.reason.trim()}
          sx={{
            textTransform: 'none', borderRadius: '8px', minWidth: 130,
            bgcolor: '#c2410c', '&:hover': { bgcolor: '#9a3412' },
          }}
        >
          {submitting
            ? <CircularProgress size={18} color="inherit" />
            : index + 1 < total ? 'Submit & Next' : 'Submit Request'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default CorrectionPopup;
