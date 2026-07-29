import api from './axios';

export const workingHoursCorrectionApi = {
  submit: (empId, data) => api.post(`/working-hours-corrections/apply/${empId}`, data),
  getMy: (empId) => api.get(`/working-hours-corrections/employee/${empId}`),
  getPendingForManager: (managerId) => api.get(`/working-hours-corrections/manager/${managerId}/pending`),
  getForManager: (managerId) => api.get(`/working-hours-corrections/manager/${managerId}`),
  getAll: () => api.get('/working-hours-corrections'),
  processRequest: (id, status, comment) =>
    api.patch(`/working-hours-corrections/${id}/action`, { status, comment }),
  getAuditTrail: (id) => api.get(`/working-hours-corrections/${id}/audit-trail`),
};
