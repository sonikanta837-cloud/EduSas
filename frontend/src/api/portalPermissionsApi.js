import api from './axios';

export const portalPermissionsApi = {
  getMine: () => api.get('/portal-permissions/me'),
  checkAccess: (portalId) => api.get(`/portal-permissions/check/${portalId}`),
  getAllRoles: () => api.get('/portal-permissions/roles'),
  updateRole: (role, portalIds) =>
    api.put(`/portal-permissions/roles/${role}`, { portalIds }),
};
