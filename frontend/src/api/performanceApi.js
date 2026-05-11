import api from './axios';

export const performanceApi = {
  create: (data) => api.post('/performance', data),
  getByEmployee: (empId) => api.get(`/performance/employee/${empId}`),
  getByReviewer: (reviewerId) => api.get(`/performance/reviewer/${reviewerId}`),
  getAll: () => api.get('/performance'),
  getAverage: (empId) => api.get(`/performance/employee/${empId}/average`),
};
