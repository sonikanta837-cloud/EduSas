import api from './axios';

export const courseApi = {
  getAll: () => api.get('/courses'),
  getForEmployee: (empId) => api.get(`/courses/employee/${empId}`),
  getForManager: (managerId) => api.get(`/courses/manager/${managerId}`),
  getById: (id) => api.get(`/courses/${id}`),
  create: (data, createdBy) => api.post('/courses', data, { params: { createdBy } }),
  update: (id, data) => api.put(`/courses/${id}`, data),
  delete: (id) => api.delete(`/courses/${id}`),
  enroll: (courseId, empId) => api.post(`/courses/${courseId}/enroll/${empId}`),
  assign: (courseId, employeeId, assignAll) =>
    api.post(`/courses/${courseId}/assign`, { employeeId, assignAll }),
  markWatched: (courseId, empId) => api.post(`/courses/${courseId}/watch/${empId}`),
  getExamQuestions: (courseId) => api.get(`/courses/${courseId}/exam-questions`),
  submitExamAnswers: (courseId, empId, answers) =>
    api.post(`/courses/${courseId}/exam/${empId}`, { answers }),
  createExam: (courseId, data) => api.post(`/courses/${courseId}/exam`, data),
  generateQuestions: (courseId) => api.get(`/courses/${courseId}/generate-questions`),
  getAdminExamQuestions: (courseId) => api.get(`/courses/${courseId}/admin-exam-questions`),
  getEnrolledEmployeeIds: (courseId) => api.get(`/courses/${courseId}/enrolled-employee-ids`),
  getMyCertificates: () => api.get('/certificates/my'),
  getAllCertificates: () => api.get('/certificates'),
};
