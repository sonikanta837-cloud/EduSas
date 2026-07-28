import React, { useEffect, useState, useCallback } from 'react';
import {
  Autocomplete, TextField, Dialog, DialogTitle, DialogContent,
  DialogActions, Button, CircularProgress, Box, Typography,
} from '@mui/material';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { masterDataApi } from '../../api/masterDataApi';
import { toast } from 'react-toastify';

const ADD_NEW_ID = '__add_new__';
const ADD_NEW_OPTION = { id: ADD_NEW_ID, value: '➕ Add New...' };

/**
 * Search / select / add-new dropdown for the timesheet master-data types
 * (CLIENT, JOB, JOB_TYPE, PERIOD_END). Any authenticated employee can add a
 * new value directly (no approval step, per current design); renaming and
 * deactivating existing values is Admin/Director/HR-only and lives on the
 * dedicated Timesheet Master Data admin page, not here.
 *
 * The "➕ Add New..." row is always pinned as the last option (regardless of
 * search text) and opens a modal rather than being selectable itself.
 */
const MasterDataAutocomplete = ({ type, label, value, onChange, disabled, sx }) => {
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [addOpen, setAddOpen] = useState(false);
  const [addValue, setAddValue] = useState('');
  const [addDate, setAddDate] = useState(null);
  const [saving, setSaving] = useState(false);

  const isPeriodEnd = type === 'PERIOD_END';

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await masterDataApi.list(type, true);
      setOptions(Array.isArray(data) ? data : []);
    } catch {
      toast.error(`Failed to load ${label} options`);
    } finally {
      setLoading(false);
    }
  }, [type, label]);

  useEffect(() => { load(); }, [load]);

  const selected = options.find((o) => o.id === value) || null;

  const closeAddDialog = () => {
    setAddOpen(false);
    setAddValue('');
    setAddDate(null);
  };

  const trimmedValue = addValue.trim();
  const isDuplicate = isPeriodEnd
    ? !!addDate && options.some((o) => o.periodEndDate === addDate.format('YYYY-MM-DD'))
    : !!trimmedValue && options.some((o) => o.value.trim().toLowerCase() === trimmedValue.toLowerCase());
  const isMissing = isPeriodEnd ? !addDate : !trimmedValue;
  const addDisabled = saving || isMissing || isDuplicate;

  const handleAddNew = async () => {
    if (addDisabled) return;
    setSaving(true);
    try {
      const payload = isPeriodEnd
        ? { periodEndDate: addDate.format('YYYY-MM-DD') }
        : { value: trimmedValue };
      const created = await masterDataApi.create(type, payload);
      await load();
      onChange(created.id, created);
      closeAddDialog();
      toast.success(`${label} added`);
    } catch (e) {
      toast.error(e?.response?.data?.message || `Failed to add ${label}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Autocomplete
        size="small"
        sx={sx}
        disabled={disabled}
        loading={loading}
        options={options}
        value={selected}
        getOptionLabel={(o) => o.value || ''}
        isOptionEqualToValue={(o, v) => o.id === v.id}
        filterOptions={(opts, state) => {
          const q = state.inputValue.trim().toLowerCase();
          const filtered = q ? opts.filter((o) => o.value.toLowerCase().includes(q)) : opts;
          return [...filtered, ADD_NEW_OPTION];
        }}
        renderOption={(props, option) => {
          const { key, ...rest } = props;
          if (option.id === ADD_NEW_ID) {
            return (
              <li key={key} {...rest} style={{ fontWeight: 600, color: '#1976d2', borderTop: '1px solid #e2e8f0' }}>
                {option.value}
              </li>
            );
          }
          return <li key={key} {...rest}>{option.value}</li>;
        }}
        onChange={(e, newValue) => {
          if (!newValue) { onChange(null, null); return; }
          if (newValue.id === ADD_NEW_ID) {
            setAddOpen(true);
            return;
          }
          onChange(newValue.id, newValue);
        }}
        renderInput={(params) => (
          <TextField
            {...params}
            label={label}
            InputProps={{
              ...params.InputProps,
              endAdornment: (
                <>
                  {loading || saving ? <CircularProgress size={16} /> : null}
                  {params.InputProps.endAdornment}
                </>
              ),
            }}
          />
        )}
      />

      <Dialog open={addOpen} onClose={closeAddDialog} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>Add New {label}</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 1 }}>
            {isPeriodEnd ? (
              <LocalizationProvider dateAdapter={AdapterDayjs}>
                <DatePicker
                  label="Period End Date"
                  value={addDate}
                  onChange={setAddDate}
                  slotProps={{ textField: { size: 'small', fullWidth: true, autoFocus: true } }}
                />
              </LocalizationProvider>
            ) : (
              <TextField
                label={label}
                fullWidth
                size="small"
                value={addValue}
                onChange={(e) => setAddValue(e.target.value)}
                autoFocus
                error={isDuplicate}
                onKeyDown={(e) => e.key === 'Enter' && handleAddNew()}
              />
            )}
            {isDuplicate && (
              <Typography variant="caption" color="error" sx={{ mt: 0.5, display: 'block' }}>
                This {label.toLowerCase()} already exists.
              </Typography>
            )}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={closeAddDialog}>Cancel</Button>
          <Button variant="contained" disabled={addDisabled} onClick={handleAddNew}>
            {saving ? <CircularProgress size={18} /> : 'Add'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default MasterDataAutocomplete;
