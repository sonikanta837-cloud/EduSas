import api from './axios';

export const workReportApi = {
  getTeamReports: (start, end) =>
    api.get('/work-reports', { params: { start, end } }),

  getMyReports: (start, end) =>
    api.get('/work-reports/my', { params: { start, end } }),

  generateReports: (date) =>
    api.post('/work-reports/generate', null, { params: { date } }),

  generateReportsForRange: (start, end) =>
    api.post('/work-reports/generate/range', null, { params: { start, end } }),

  exportCsv: (start, end) =>
    api.get('/work-reports/export', { params: { start, end }, responseType: 'blob' }),
};
