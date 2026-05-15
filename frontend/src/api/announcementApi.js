import api from './axios';

export const announcementApi = {
  getAll:  ()         => api.get('/announcements'),
  create:  (data)     => api.post('/announcements', data),
  delete:  (id)       => api.delete(`/announcements/${id}`),
};
