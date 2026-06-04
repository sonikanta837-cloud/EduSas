import api from './axios';

export const employeeApi = {
  getAll: (search) => api.get('/employees', { params: search ? { search } : {} }),
  getExEmployees: () => api.get('/employees/ex'),
  getById: (id) => api.get(`/employees/${id}`),
  getByUserId: (userId) => api.get(`/employees/user/${userId}`),
  getTeam: (managerId) => api.get(`/employees/manager/${managerId}/team`),
  getOrgChart: () => api.get('/employees/org-chart'),
  create: (data) => api.post('/employees', data),
  update: (id, data) => api.put(`/employees/${id}`, data),
  toggleStatus: (id) => api.patch(`/employees/${id}/toggle-status`),
  delete:       (id)   => api.delete(`/employees/${id}`),
  getLocations: ()     => api.get('/employees/locations'),
  getHrUsers:   ()     => api.get('/employees/hr-users'),
  assignHr: (id, hrId) => api.patch(`/employees/${id}/assign-hr`, null, { params: hrId ? { hrId } : {} }),
};
