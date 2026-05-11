import api from './axios';

export const employeeApi = {
  getAll: (search) => api.get('/employees', { params: search ? { search } : {} }),
  getById: (id) => api.get(`/employees/${id}`),
  getByUserId: (userId) => api.get(`/employees/user/${userId}`),
  getTeam: (managerId) => api.get(`/employees/manager/${managerId}/team`),
  getOrgChart: () => api.get('/employees/org-chart'),
  create: (data) => api.post('/employees', data),
  update: (id, data) => api.put(`/employees/${id}`, data),
  toggleStatus: (id) => api.patch(`/employees/${id}/toggle-status`),
  delete: (id) => api.delete(`/employees/${id}`),
};
