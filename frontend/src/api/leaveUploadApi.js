import api from './axios';

export const leaveUploadApi = {
  upload: (file) => {
    const form = new FormData();
    form.append('file', file);
    return api.post('/leave-upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  downloadTemplate: () =>
    api.get('/leave-upload/template', { responseType: 'blob' }),
  getHolidays: () =>
    api.get('/leave-upload/holidays'),
};
