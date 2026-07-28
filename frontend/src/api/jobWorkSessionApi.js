import api from './axios';

export const jobWorkSessionApi = {
  login:      (data)                    => api.post('/job-sessions/login', data),
  logout:     (employeeId)              => api.post(`/job-sessions/logout/${employeeId}`),
  switchJob:  (data)                    => api.post('/job-sessions/switch', data),
  startBreak: (employeeId)              => api.post(`/job-sessions/break/start/${employeeId}`),
  endBreak:   (employeeId)              => api.post(`/job-sessions/break/end/${employeeId}`),
  getToday:   (employeeId)              => api.get(`/job-sessions/today/${employeeId}`),
  getRange:   (employeeId, start, end)  => api.get(`/job-sessions/range/${employeeId}`, { params: { start, end } }),
  getWorkReport: (start, end)           => api.get('/job-sessions/work-report', { params: { start, end } }),
};
