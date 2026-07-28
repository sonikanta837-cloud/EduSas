import api from './axios';

export const jobSummaryApi = {
  getMy:         (start, end)             => api.get('/job-summaries/my', { params: { start, end } }),
  getForEmployee: (employeeId, start, end) => api.get(`/job-summaries/employee/${employeeId}`, { params: { start, end } }),
};
