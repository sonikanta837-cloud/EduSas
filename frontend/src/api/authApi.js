import api from './axios';

export const authApi = {
  login: (credentials) => api.post('/auth/login', credentials),
  logout: () => api.post('/auth/logout'),
  register: (data) => api.post('/auth/register', data),
  refresh: (token) => api.post('/auth/refresh', null, { params: { refreshToken: token } }),
};
