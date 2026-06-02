import api from './axios';

export const announcementApi = {
  getAll:        ()         => api.get('/announcements'),
  create:        (data)     => api.post('/announcements', data),
  update:        (id, data) => api.put(`/announcements/${id}`, data),
  markAsRead:    (id)       => api.post(`/announcements/${id}/view`),
  getUnread:      ()        => api.get('/announcements/unread'),
  getUnreadCount: ()        => api.get('/announcements/unread-count'),
  getViewers:    (id)       => api.get(`/announcements/${id}/viewers`),
  delete:        (id)       => api.delete(`/announcements/${id}`),
};
