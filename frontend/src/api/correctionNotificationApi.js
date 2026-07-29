import api from './axios';

export const correctionNotificationApi = {
  getUnread:      ()    => api.get('/correction-notifications/unread'),
  getUnreadCount: ()    => api.get('/correction-notifications/unread-count'),
  markAsRead:     (id)  => api.post(`/correction-notifications/${id}/read`),
  markAllRead:    ()    => api.post('/correction-notifications/read-all'),
};
