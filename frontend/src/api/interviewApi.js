import api from './axios';

export const interviewApi = {
  // Candidates
  createCandidate: (data) => api.post('/interviews/candidates', data),
  getAllCandidates: () => api.get('/interviews/candidates'),
  getCandidate: (id) => api.get(`/interviews/candidates/${id}`),
  updateCandidate: (id, data) => api.put(`/interviews/candidates/${id}`, data),
  deleteCandidate: (id) => api.delete(`/interviews/candidates/${id}`),
  updateCandidateStatus: (id, status) => api.put(`/interviews/candidates/${id}/status`, { status }),

  // Rounds
  scheduleRound: (candidateId, data) => api.post(`/interviews/candidates/${candidateId}/rounds`, data),
  getRoundsForCandidate: (candidateId) => api.get(`/interviews/candidates/${candidateId}/rounds`),
  getRound: (id) => api.get(`/interviews/rounds/${id}`),
  getMyRounds: () => api.get('/interviews/my-rounds'),
  updateRound: (id, data) => api.put(`/interviews/rounds/${id}`, data),
  startRound: (id) => api.put(`/interviews/rounds/${id}/start`),
  cancelRound: (id) => api.delete(`/interviews/rounds/${id}`),

  // Feedback
  submitFeedback: (roundId, data) => api.post(`/interviews/rounds/${roundId}/feedback`, data),
  updateFeedback: (feedbackId, data) => api.put(`/interviews/feedback/${feedbackId}`, data),
  getFeedbackForRound: (roundId) => api.get(`/interviews/rounds/${roundId}/feedback`),

  // Stats & Audit
  getStats: () => api.get('/interviews/stats'),
  getAuditLogs: () => api.get('/interviews/audit-logs'),
  getAuditLogsForCandidate: (id) => api.get(`/interviews/candidates/${id}/audit-logs`),

  // Notifications
  getMyNotifications: () => api.get('/interviews/notifications'),
  getUnreadCount: () => api.get('/interviews/notifications/unread-count'),
  markAllRead: () => api.post('/interviews/notifications/mark-read'),

  // Calendar (.ics) download — use downloadIcs() helper in the component (needs auth header)
  getRoundIcsUrl: (id) => `${process.env.REACT_APP_API_URL || '/api'}/interviews/rounds/${id}/ics`,
};
