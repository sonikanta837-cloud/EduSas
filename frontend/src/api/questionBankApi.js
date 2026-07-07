import api from './axios';

export const questionBankApi = {
  getAll:          ()        => api.get('/interview-questions'),
  getById:         (id)      => api.get(`/interview-questions/${id}`),
  create:          (data)    => api.post('/interview-questions', data),
  update:          (id, data)=> api.put(`/interview-questions/${id}`, data),
  delete:          (id)      => api.delete(`/interview-questions/${id}`),
  toggleStatus:    (id)      => api.patch(`/interview-questions/${id}/toggle-status`),
  getTechnologies: ()        => api.get('/interview-questions/technologies'),
  getCategories:   ()        => api.get('/interview-questions/categories'),
  getStats:        ()        => api.get('/interview-questions/stats'),
  exportExcel: () => {
    const token = localStorage.getItem('accessToken');
    const BASE = process.env.REACT_APP_API_URL || '/api';
    return fetch(`${BASE}/interview-questions/export`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  },
  importExcel: (file) => {
    const fd = new FormData();
    fd.append('file', file);
    const token = localStorage.getItem('accessToken');
    const BASE = process.env.REACT_APP_API_URL || '/api';
    return fetch(`${BASE}/interview-questions/import`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: fd,
    }).then(r => r.json());
  },
};
