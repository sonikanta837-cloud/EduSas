import React from 'react';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs }         from '@mui/x-date-pickers/AdapterDayjs';
import { TimePicker }           from '@mui/x-date-pickers/TimePicker';
import dayjs from 'dayjs';

// "HH:mm" 24-hour string — matches the old <input type="time"> value shape this
// component replaces, so callers combining it with a separate date field don't change.
const VALUE_FORMAT = 'HH:mm';

/**
 * Standardized time-only picker, sibling to AppDateTimePicker. Auto-closes as soon
 * as a valid time is picked (closeOnSelect) instead of the native <input type="time">
 * clock overlay, which on some browsers stays open until the user clicks away.
 */
const AppTimePicker = ({
  label,
  value,
  onChange,
  disabled,
  size = 'small',
  fullWidth = true,
  sx,
  slotProps,
  ...rest
}) => {
  const dayjsValue = value ? dayjs(value, VALUE_FORMAT) : null;

  const commit = (newValue) => {
    onChange(newValue && newValue.isValid() ? newValue.format(VALUE_FORMAT) : '');
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <TimePicker
        label={label}
        value={dayjsValue}
        onChange={commit}
        onAccept={commit}
        format="hh:mm A"
        closeOnSelect
        disabled={disabled}
        slotProps={{ textField: { size, fullWidth, sx }, ...slotProps }}
        {...rest}
      />
    </LocalizationProvider>
  );
};

export default AppTimePicker;
