import api from './axios';

export const courseNotificationApi = {
  getUnread:      ()   => api.get('/course-notifications/unread'),
  getUnreadCount: ()   => api.get('/course-notifications/unread-count'),
  markAsRead:     (id) => api.post(`/course-notifications/${id}/read`),
  markAllRead:    ()   => api.post('/course-notifications/read-all'),
};
