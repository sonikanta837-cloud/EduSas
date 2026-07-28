import api from './axios';

export const performanceApi = {
  create: (data) => api.post('/performance', data),
  getByEmployee: (empId) => api.get(`/performance/employee/${empId}`),
  getByReviewer: (reviewerId) => api.get(`/performance/reviewer/${reviewerId}`),
  getAll: () => api.get('/performance'),
  getAverage: (empId) => api.get(`/performance/employee/${empId}/average`),
  update: (id, data) => api.put(`/performance/${id}`, data),
  delete: (id) => api.delete(`/performance/${id}`),
  exportReviews: (params) => api.get('/performance/export', { params, responseType: 'blob' }),
  getExportAuditLog: () => api.get('/performance/export/audit-log'),
};
