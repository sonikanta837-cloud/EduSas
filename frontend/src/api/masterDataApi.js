import api from './axios';

export const masterDataApi = {
  list:   (type, activeOnly = true) => api.get(`/timesheet-master/${type}`, { params: { activeOnly } }),
  create: (type, data)               => api.post(`/timesheet-master/${type}`, data),
  update: (id, data)                 => api.put(`/timesheet-master/${id}`, data),
};
