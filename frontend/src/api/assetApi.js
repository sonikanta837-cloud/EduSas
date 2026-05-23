import api from './axios';

export const assetApi = {
  getAll:        ()                                    => api.get('/assets'),
  getById:       (id)                                  => api.get(`/assets/${id}`),
  getStats:      ()                                    => api.get('/assets/stats'),
  create:        (data)                                => api.post('/assets', data),
  update:        (id, data)                            => api.put(`/assets/${id}`, data),
  delete:        (id)                                  => api.delete(`/assets/${id}`),
  assign:        (id, employeeId, assignedDate, expectedReturnDate) =>
    api.patch(`/assets/${id}/assign`, null, { params: { employeeId, assignedDate, expectedReturnDate } }),
  returnAsset:   (id)                                  => api.patch(`/assets/${id}/return`),
  updateStatus:  (id, status)                          => api.patch(`/assets/${id}/status`, null, { params: { status } }),
};
