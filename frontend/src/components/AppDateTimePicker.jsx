import React from 'react';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs }         from '@mui/x-date-pickers/AdapterDayjs';
import { DateTimePicker }       from '@mui/x-date-pickers/DateTimePicker';
import dayjs from 'dayjs';

// Local (non-UTC) datetime string format shared with the old <input type="datetime-local">
// fields this component replaces, so callers and API payloads (`new Date(value)`) don't change.
const VALUE_FORMAT = 'YYYY-MM-DDTHH:mm';

/**
 * Standardized DateTime picker used everywhere in the app that needs a combined
 * date + time value. Auto-closes as soon as a valid date and time are both picked
 * (closeOnSelect), on both desktop and mobile, and commits the final value on
 * accept so validation/form state update right after the picker closes.
 */
const AppDateTimePicker = ({
  label,
  value,
  onChange,
  minDateTime,
  maxDateTime,
  disabled,
  size = 'small',
  fullWidth = true,
  sx,
  slotProps,
  ...rest
}) => {
  const dayjsValue = value ? dayjs(value) : null;

  const commit = (newValue) => {
    onChange(newValue && newValue.isValid() ? newValue.format(VALUE_FORMAT) : '');
  };

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <DateTimePicker
        label={label}
        value={dayjsValue}
        onChange={commit}
        onAccept={commit}
        format="MM/DD/YYYY hh:mm A"
        closeOnSelect
        disabled={disabled}
        minDateTime={minDateTime ? dayjs(minDateTime) : undefined}
        maxDateTime={maxDateTime ? dayjs(maxDateTime) : undefined}
        slotProps={{ textField: { size, fullWidth, sx }, ...slotProps }}
        {...rest}
      />
    </LocalizationProvider>
  );
};

export default AppDateTimePicker;
