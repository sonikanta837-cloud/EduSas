import api from './axios';

export const resourceApi = {
  getAll: () => api.get('/resources'),

  upload: (file, title, category, subCategory, description) => {
    const form = new FormData();
    form.append('file', file);
    if (title)       form.append('title',       title);
    if (category)    form.append('category',    category);
    if (subCategory) form.append('subCategory', subCategory);
    if (description) form.append('description', description);
    return api.post('/resources/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  update:   (id, data) => api.patch(`/resources/${id}`, data),
  toggle:   (id)       => api.patch(`/resources/${id}/toggle`),
  delete:   (id)       => api.delete(`/resources/${id}`),

  // download — browser saves the file
  download: (id) => api.get(`/resources/download/${id}`, { responseType: 'blob' }),

  // view — fetch as blob then open in a new tab (handles JWT auth)
  view: (id) => api.get(`/resources/view/${id}`, { responseType: 'blob' }),
};

export const faqApi = {
  getAll:  (all = false) => api.get('/faqs', { params: { all } }),
  create:  (data)        => api.post('/faqs', data),
  update:  (id, data)    => api.put(`/faqs/${id}`, data),
  toggle:  (id)          => api.patch(`/faqs/${id}/toggle`),
  delete:  (id)          => api.delete(`/faqs/${id}`),
};
