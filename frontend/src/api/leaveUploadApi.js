import api from './axios';

export const leaveUploadApi = {
  upload: (file, location) => {
    const form = new FormData();
    form.append('file', file);
    if (location) form.append('location', location);
    return api.post('/leave-upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  addHoliday:           (name, date, location) => api.post('/leave-upload/holiday', { name, date, ...(location ? { location } : {}) }),
  downloadTemplate:     ()                     => api.get('/leave-upload/template', { responseType: 'blob' }),
  getHolidays:          ()                     => api.get('/leave-upload/holidays'),
  getHolidayEmployees:  (name, date)           => api.get('/leave-upload/holidays/employees', { params: { name, date } }),
};
