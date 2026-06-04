import api from './axios';

export const holidayApi = {
  getAll:       (year)     => api.get('/holidays', { params: year ? { year } : {} }),
  getMy:        (year)     => api.get('/holidays/my', { params: year ? { year } : {} }),
  getYears:     ()         => api.get('/holidays/years'),
  create:       (data)     => api.post('/holidays', data),
  update:       (id, data) => api.put(`/holidays/${id}`, data),
  delete:       (id)       => api.delete(`/holidays/${id}`),
  bulkCreate:   (list)     => api.post('/holidays/bulk', list),
};
