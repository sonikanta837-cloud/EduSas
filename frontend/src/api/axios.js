import axios from 'axios';

const BASE = process.env.REACT_APP_API_URL || '/api';

const api = axios.create({
  baseURL: BASE,
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let isRefreshing = false;
let pendingQueue = [];

const processQueue = (error, token = null) => {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error); else resolve(token);
  });
  pendingQueue = [];
};

/**
 * Shared token refresh function used by both the Axios interceptor and
 * native fetch() calls (interviewApi multipart uploads).
 *
 * - If a refresh is already in flight, queues the caller and returns when done.
 * - On success: updates localStorage, resolves all queued callers, returns new token.
 * - On failure: clears session, redirects to /login.
 */
export async function refreshAccessToken() {
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      pendingQueue.push({ resolve, reject });
    });
  }

  isRefreshing = true;
  try {
    const refreshToken = localStorage.getItem('refreshToken');
    const res = await axios.post(`${BASE}/auth/refresh`, null, { params: { refreshToken } });
    const newToken = res.data.accessToken;
    localStorage.setItem('accessToken', newToken);
    localStorage.setItem('refreshToken', res.data.refreshToken);
    // Keep the stored user profile in sync with the server's current view (role may have changed)
    if (res.data.role) {
      try {
        const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
        localStorage.setItem('user', JSON.stringify({ ...currentUser, ...res.data }));
      } catch { /* ignore */ }
    }
    processQueue(null, newToken);
    return newToken;
  } catch (err) {
    processQueue(err);
    localStorage.clear();
    window.location.href = '/login';
    throw err;
  } finally {
    isRefreshing = false;
  }
}

api.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true;
      try {
        const newToken = await refreshAccessToken();
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      } catch (err) {
        return Promise.reject(err);
      }
    }
    return Promise.reject(error);
  }
);

export default api;
