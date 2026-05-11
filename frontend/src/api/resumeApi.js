import api from './axios';

export const resumeApi = {
  parse: (file) => {
    const form = new FormData();
    form.append('file', file);
    return api.post('/resume/parse', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};
