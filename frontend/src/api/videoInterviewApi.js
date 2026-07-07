import api from './axios';

const BASE = process.env.REACT_APP_API_URL || '/api';

// ── Authenticated (HR / Manager) ────────────────────────────────────────────

export const videoInterviewApi = {
  getAll:   ()        => api.get('/video-interview'),
  getById:  (id)      => api.get(`/video-interview/${id}`),
  create:   (data)    => api.post('/video-interview', data),
  delete:   (id)      => api.delete(`/video-interview/${id}`),
  getStats: ()        => api.get('/video-interview/stats'),
};

// ── Public (Candidate — no JWT) ─────────────────────────────────────────────

const candidateFetch = async (url, options = {}) => {
  const res = await fetch(url, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  if (!res.ok) {
    let msg = `Request failed (${res.status})`;
    try { const b = await res.json(); msg = b.message || b.error || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
};

export const candidateInterviewApi = {
  validate: (token) =>
    candidateFetch(`${BASE}/video-interview/candidate/${token}/validate`),

  start: (token) =>
    candidateFetch(`${BASE}/video-interview/candidate/${token}/start`, { method: 'POST', body: '{}' }),

  saveAnswer: (token, payload) =>
    candidateFetch(`${BASE}/video-interview/candidate/${token}/save-answer`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  logViolation: (token, payload) =>
    candidateFetch(`${BASE}/video-interview/candidate/${token}/violation`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  submit: (token) =>
    candidateFetch(`${BASE}/video-interview/candidate/${token}/submit`, { method: 'POST', body: '{}' }),

  uploadRecording: (token, blob) => {
    const fd = new FormData();
    fd.append('file', blob, `${token}.webm`);
    return fetch(`${BASE}/video-interview/candidate/${token}/recording`, {
      method: 'POST',
      body: fd,
    }).then(r => r.ok ? r.json() : null).catch(() => null);
  },
};
