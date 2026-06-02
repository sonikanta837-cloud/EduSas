import api from './axios';

export const permissionsApi = {
  getAll: () => api.get('/permissions'),
  update: (userId, allowedModules) =>
    api.put(`/permissions/${userId}`, { allowedModules }),
};
