import axiosInstance from './axios';

export const settingsApi = {
  getBreakAlertSettings:          ()     => axiosInstance.get('/settings/break-alert'),
  saveBreakAlertSettings:         (data) => axiosInstance.put('/settings/break-alert', data),
  getWorkReportEmailSettings:     ()     => axiosInstance.get('/settings/work-report-email'),
  saveWorkReportEmailSettings:    (data) => axiosInstance.put('/settings/work-report-email', data),
  getAttendanceAuditSettings:     ()     => axiosInstance.get('/settings/attendance-audit'),
  saveAttendanceAuditSettings:    (data) => axiosInstance.put('/settings/attendance-audit', data),
  getPerformanceThreshold:        ()     => axiosInstance.get('/settings/performance-threshold'),
  savePerformanceThreshold:       (data) => axiosInstance.put('/settings/performance-threshold', data),
};
