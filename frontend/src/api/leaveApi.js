import api from './axios';

export const leaveApi = {
  apply: (empId, data) => api.post(`/leaves/apply/${empId}`, data),
  getMyLeaves: (empId) => api.get(`/leaves/employee/${empId}`),
  getForManager: (managerId) => api.get(`/leaves/manager/${managerId}`),
  getLeavesForManager: (managerId) => api.get(`/leaves/manager/${managerId}`),
  getPendingForManager: (managerId) => api.get(`/leaves/manager/${managerId}/pending`),
  getAll: () => api.get('/leaves'),
  processLeave: (leaveId, managerId, status, comment) =>
    api.patch(`/leaves/${leaveId}/action`, { status, comment }, { params: { managerId } }),
  update: (id, employeeId, data) => api.put(`/leaves/${id}`, data, { params: { employeeId } }),
  delete: (id) => api.delete(`/leaves/${id}`),
  getPublicHolidays: () => api.get('/leaves/public-holidays'),
};
