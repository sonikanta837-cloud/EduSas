import api from './axios';

export const pipApi = {
  // List / detail
  getAll:            ()           => api.get('/pip'),
  getByEmployee:     (empId)      => api.get(`/pip/employee/${empId}`),
  getDetail:         (id)         => api.get(`/pip/${id}`),

  // PIP CRUD
  create:            (data)       => api.post('/pip', data),
  update:            (id, data)   => api.put(`/pip/${id}`, data),
  remove:            (id)         => api.delete(`/pip/${id}`),

  // Goals
  addGoal:           (id, data)   => api.post(`/pip/${id}/goals`, data),
  updateGoal:        (id, gId, d) => api.put(`/pip/${id}/goals/${gId}`, d),
  deleteGoal:        (id, gId)    => api.delete(`/pip/${id}/goals/${gId}`),

  // Weekly reviews
  addReview:         (id, data)   => api.post(`/pip/${id}/reviews`, data),

  // Comments
  addComment:        (id, content) => api.post(`/pip/${id}/comments`, { content }),

  // Outcome
  setOutcome:        (id, data)   => api.put(`/pip/${id}/outcome`, data),
};
