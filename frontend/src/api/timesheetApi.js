import api from './axios';

export const timesheetApi = {
  checkIn: (empId) => api.post(`/timesheets/check-in/${empId}`),
  checkOut: (empId) => api.post(`/timesheets/check-out/${empId}`),
  getToday: (empId) => api.get(`/timesheets/today/${empId}`),
  getAttendance: (empId) => api.get(`/timesheets/attendance/${empId}`),
  getAttendanceByRange: (empId, start, end) =>
    api.get(`/timesheets/attendance/${empId}/range`, { params: { start, end } }),
  getByDate: (date) => api.get(`/timesheets/date/${date}`),
  getTodaySessions: (empId) => api.get(`/timesheets/sessions/today/${empId}`),
  getSessionsByRange: (empId, start, end) =>
    api.get(`/timesheets/sessions/${empId}/range`, { params: { start, end } }),
  getWorkingHoursMap: (empId, year, month) =>
    api.get(`/timesheets/working-hours/${empId}/month`, { params: { year, month } }),
  updateWorkingHours: (empId, date, hours) =>
    api.patch(`/timesheets/working-hours/${empId}/${date}`, { hours }),
};
