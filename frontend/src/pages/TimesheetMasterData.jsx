import React, { useEffect, useState, useCallback } from 'react';
import {
  Box, Typography, Tabs, Tab, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, Paper, Switch, IconButton, TextField, Button,
  Dialog, DialogTitle, DialogContent, DialogActions, CircularProgress, Chip,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import AddIcon from '@mui/icons-material/Add';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import dayjs from 'dayjs';
import { masterDataApi } from '../api/masterDataApi';
import { toast } from 'react-toastify';

const TYPES = [
  { value: 'CLIENT',    label: 'Clients' },
  { value: 'JOB',        label: 'Jobs' },
  { value: 'JOB_TYPE',   label: 'Job Types' },
  { value: 'PERIOD_END', label: 'Period Ends' },
];

const TimesheetMasterDataPage = () => {
  const [tab, setTab] = useState('CLIENT');
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editRow, setEditRow] = useState(null); // row being edited
  const [editValue, setEditValue] = useState('');
  const [editDate, setEditDate] = useState(null);
  const [editActive, setEditActive] = useState(true);
  const [addOpen, setAddOpen] = useState(false);
  const [addValue, setAddValue] = useState('');
  const [addDate, setAddDate] = useState(null);
  const [saving, setSaving] = useState(false);

  const isPeriodEnd = tab === 'PERIOD_END';

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await masterDataApi.list(tab, false);
      setRows(Array.isArray(data) ? data : []);
    } catch {
      toast.error('Failed to load master data');
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => { load(); }, [load]);

  const openEdit = (row) => {
    setEditRow(row);
    setEditValue(row.value);
    setEditDate(row.periodEndDate ? dayjs(row.periodEndDate) : null);
    setEditActive(row.active);
  };

  const handleSaveEdit = async () => {
    setSaving(true);
    try {
      const payload = isPeriodEnd
        ? { periodEndDate: editDate ? editDate.format('YYYY-MM-DD') : null, active: editActive }
        : { value: editValue.trim(), active: editActive };
      const updated = await masterDataApi.update(editRow.id, payload);
      setRows((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
      setEditRow(null);
      toast.success('Updated');
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to update');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleActive = async (row) => {
    try {
      const payload = isPeriodEnd
        ? { periodEndDate: row.periodEndDate, active: !row.active }
        : { value: row.value, active: !row.active };
      const updated = await masterDataApi.update(row.id, payload);
      setRows((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    } catch {
      toast.error('Failed to update status');
    }
  };

  const handleAdd = async () => {
    setSaving(true);
    try {
      const payload = isPeriodEnd
        ? { periodEndDate: addDate.format('YYYY-MM-DD') }
        : { value: addValue.trim() };
      const created = await masterDataApi.create(tab, payload);
      setRows((prev) => (prev.some((r) => r.id === created.id) ? prev : [...prev, created]));
      setAddOpen(false);
      setAddValue('');
      setAddDate(null);
      toast.success('Added');
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to add');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} sx={{ mb: 2 }}>Timesheet Master Data</Typography>

      <Paper sx={{ borderRadius: 2, overflow: 'hidden' }}>
        <Tabs value={tab} onChange={(e, v) => setTab(v)} sx={{ px: 2, borderBottom: '1px solid #e2e8f0' }}>
          {TYPES.map((t) => <Tab key={t.value} value={t.value} label={t.label} />)}
        </Tabs>

        <Box sx={{ p: 2 }}>
          <Button startIcon={<AddIcon />} variant="contained" size="small"
            onClick={() => setAddOpen(true)} sx={{ mb: 2 }}>
            Add {TYPES.find((t) => t.value === tab)?.label.replace(/s$/, '')}
          </Button>

          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress /></Box>
          ) : (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Value</TableCell>
                    <TableCell>Added By</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((r) => (
                    <TableRow key={r.id} hover>
                      <TableCell>{r.value}</TableCell>
                      <TableCell>{r.createdByName || '—'}</TableCell>
                      <TableCell>
                        <Chip size="small" label={r.active ? 'Active' : 'Inactive'}
                          color={r.active ? 'success' : 'default'}
                          onClick={() => handleToggleActive(r)}
                          sx={{ cursor: 'pointer' }} />
                      </TableCell>
                      <TableCell align="right">
                        <IconButton size="small" onClick={() => openEdit(r)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                  {rows.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} sx={{ textAlign: 'center', py: 3, color: '#9ca3af' }}>
                        No values yet.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Box>
      </Paper>

      {/* ── Edit dialog ── */}
      <Dialog open={!!editRow} onClose={() => setEditRow(null)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>Edit {TYPES.find((t) => t.value === tab)?.label.replace(/s$/, '')}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {isPeriodEnd ? (
              <LocalizationProvider dateAdapter={AdapterDayjs}>
                <DatePicker label="Period End Date" value={editDate} onChange={setEditDate}
                  slotProps={{ textField: { size: 'small', fullWidth: true } }} />
              </LocalizationProvider>
            ) : (
              <TextField label="Value" fullWidth value={editValue}
                onChange={(e) => setEditValue(e.target.value)} autoFocus />
            )}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Switch checked={editActive} onChange={(e) => setEditActive(e.target.checked)} />
              <Typography variant="body2">{editActive ? 'Active' : 'Inactive'}</Typography>
            </Box>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setEditRow(null)}>Cancel</Button>
          <Button variant="contained" disabled={saving} onClick={handleSaveEdit}>Save</Button>
        </DialogActions>
      </Dialog>

      {/* ── Add dialog ── */}
      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>Add {TYPES.find((t) => t.value === tab)?.label.replace(/s$/, '')}</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 1 }}>
            {isPeriodEnd ? (
              <LocalizationProvider dateAdapter={AdapterDayjs}>
                <DatePicker label="Period End Date" value={addDate} onChange={setAddDate}
                  slotProps={{ textField: { size: 'small', fullWidth: true } }} />
              </LocalizationProvider>
            ) : (
              <TextField label="Value" fullWidth value={addValue}
                onChange={(e) => setAddValue(e.target.value)} autoFocus
                onKeyDown={(e) => e.key === 'Enter' && addValue.trim() && handleAdd()} />
            )}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={saving || (isPeriodEnd ? !addDate : !addValue.trim())}
            onClick={handleAdd}>
            Add
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default TimesheetMasterDataPage;
