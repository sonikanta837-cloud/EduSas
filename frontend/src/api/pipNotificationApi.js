import api from './axios';

export const pipNotificationApi = {
  getUnread:      ()    => api.get('/pip-notifications/unread'),
  getUnreadCount: ()    => api.get('/pip-notifications/unread-count'),
  markAsRead:     (id)  => api.post(`/pip-notifications/${id}/read`),
  markAllRead:    ()    => api.post('/pip-notifications/read-all'),
};
