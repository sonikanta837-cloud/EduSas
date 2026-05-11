import api from './axios';

export const timesheetEntryApi = {
  getMonthly: (empId, year, month) =>
    api.get(`/timesheets/entries/employee/${empId}`, { params: { year, month } }),
  save: (dto) => api.post('/timesheets/entries', dto),
  delete: (id) => api.delete(`/timesheets/entries/${id}`),
  deleteProject: (empId, projectName, taskName) =>
    api.delete('/timesheets/entries/project', { params: { empId, projectName, taskName } }),
};
