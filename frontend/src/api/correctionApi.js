import api from './axios';

export const correctionApi = {
  getPendingSessions: () =>
    api.get('/corrections/pending-sessions'),

  submit: ({ sessionId, requestedLogoutTime, reason, attachment }) => {
    const form = new FormData();
    form.append('sessionId', sessionId);
    form.append('requestedLogoutTime', requestedLogoutTime);
    form.append('reason', reason);
    if (attachment) form.append('attachment', attachment);
    return api.post('/corrections', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  getMy: () => api.get('/corrections/my'),

  getPending: () => api.get('/corrections/pending'),

  getPendingTeam: () => api.get('/corrections/pending/team'),

  approve: (id, logoutTime, comment) =>
    api.put(`/corrections/${id}/approve`, { logoutTime, comment }),

  reject: (id, comment) =>
    api.put(`/corrections/${id}/reject`, { comment }),

  getAuditLogs: () => api.get('/corrections/audit-logs'),
};
