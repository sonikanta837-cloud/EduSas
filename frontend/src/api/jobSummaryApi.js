import api from './axios';

export const jobSummaryApi = {
  getMy:          (start, end)             => api.get('/job-summaries/my', { params: { start, end } }),
  getForEmployee: (employeeId, start, end) => api.get(`/job-summaries/employee/${employeeId}`, { params: { start, end } }),
  getTeam:        (start, end)             => api.get('/job-summaries', { params: { start, end } }),
  exportCsv:      (start, end)             => api.get('/job-summaries/export', { params: { start, end }, responseType: 'blob' }),
  generateRange:  (start, end)             => api.post('/job-summaries/generate/range', null, { params: { start, end } }),
};
