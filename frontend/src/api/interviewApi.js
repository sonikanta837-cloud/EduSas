import api, { refreshAccessToken } from './axios';

const BASE = process.env.REACT_APP_API_URL || '/api';

/**
 * Multipart POST via native fetch() — required because Axios + FormData + a default
 * Content-Type header causes Spring Boot's multipart parser to fail (no boundary).
 *
 * On 401 it calls refreshAccessToken() which coordinates with the shared Axios queue:
 * only one refresh fires even when multiple requests (Axios + fetch) all expire together.
 */
const fetchMultipart = async (url, formData) => {
  const doFetch = (token) =>
    fetch(url, {
      method: 'POST',
      // No Content-Type — browser sets multipart/form-data + boundary automatically
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });

  let res = await doFetch(localStorage.getItem('accessToken'));

  if (res.status === 401) {
    const newToken = await refreshAccessToken();
    res = await doFetch(newToken);
  }

  if (!res.ok) {
    // Try to extract a server-provided message; fall back to a status-specific default.
    let message;
    try {
      const body = await res.json();
      message = body.message || body.error || `Server error (${res.status})`;
    } catch {
      message = res.status === 403
        ? 'Access denied — you do not have permission for this action'
        : res.status === 401
          ? 'Session expired — please log in again'
          : `Upload failed (HTTP ${res.status})`;
    }
    return Promise.reject(new Error(message));
  }
  return res.json();
};

export const interviewApi = {
  // ── CV Bank ────────────────────────────────────────────────────────────────

  /**
   * Step 1: Upload file immediately, get back parsed fields + resumePath.
   * Call this the instant the user selects a file — before they fill the form.
   */
  storeResume: (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return fetchMultipart(`${BASE}/interviews/candidates/store-resume`, fd);
  },

  /** Sends browser-OCR'd text to backend for regex field parsing. */
  parseText: (text) => api.post('/interviews/candidates/parse-text', { text }),

  /** Legacy parse-only (no file saved). Kept for compatibility. */
  parseResume: (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return fetchMultipart(`${BASE}/interviews/candidates/parse-resume`, fd);
  },

  /**
   * Step 2: Create candidate record with pre-stored file reference.
   * Sends form fields + the resumePath returned by storeResume.
   * No file re-upload.
   */
  uploadCandidate: (formData) =>
    fetchMultipart(`${BASE}/interviews/candidates`, formData),

  getAllCandidates: () => api.get('/interviews/candidates'),
  getCandidate:    (id) => api.get(`/interviews/candidates/${id}`),
  updateCandidate: (id, data) => api.put(`/interviews/candidates/${id}`, data),
  deleteCandidate: (id) => api.delete(`/interviews/candidates/${id}`),
  rejectFromCvBank: (id) => api.post(`/interviews/candidates/${id}/reject`),
  openHrScreening:  (id) => api.post(`/interviews/candidates/${id}/open-hr`),
  getResumeUrl: (id) => `${BASE}/interviews/candidates/${id}/resume`,

  /**
   * Checks if a candidate already exists (email → phone → name priority).
   * Returns the existing candidate DTO when a match is found, or null/falsy when clear.
   */
  checkDuplicate: (data) => api.post('/interviews/candidates/check-duplicate', data)
    .catch(err => {
      if (err?.response?.status === 204) return null;
      throw err;
    }),

  /** Archives the old CV and stores the new one as the active version. */
  replaceResume: (id, formData) =>
    fetchMultipart(`${BASE}/interviews/candidates/${id}/replace-resume`, formData),

  // ── HR Screening ───────────────────────────────────────────────────────────
  saveHrScreening:   (id, data) => api.post(`/interviews/candidates/${id}/hr-screening`, data),
  submitHrDecision:  (id, data) => api.post(`/interviews/candidates/${id}/hr-screening/decision`, data),

  // ── Technical ──────────────────────────────────────────────────────────────
  getMyAssignments:         () => api.get('/interviews/my-assignments'),
  getMyRounds:              () => api.get('/interviews/my-rounds'),
  getDirectors:             () => api.get('/interviews/directors'),
  assignTechnicalInterview: (id, data) => api.post(`/interviews/candidates/${id}/technical`, data),
  submitTechnicalFeedback:  (id, data) => api.post(`/interviews/technical/${id}/feedback`, data),

  // ── Technical Video Interview ──────────────────────────────────────────────
  generateInterviewLink:    (id, data) => api.post(`/interviews/technical/${id}/generate-link`, data),
  getManagerRoom:           (id)       => api.get(`/interviews/technical/${id}/room`),
  submitManagerEvaluation:  (id, data) => api.post(`/interviews/technical/${id}/evaluate`, data),

  // WebRTC signaling — manager side
  saveAnswerSdp:   (id, sdp) => api.post(`/interviews/technical/${id}/signal/answer`, { sdp }),
  getOffer:        (id)      => api.get(`/interviews/technical/${id}/signal/offer`),
  addManagerIce:   (id, ice) => api.post(`/interviews/technical/${id}/signal/ice`, { ice }),
  getCandidateIce: (id)      => api.get(`/interviews/technical/${id}/signal/ice/candidate`),

  // ── Final Round (legacy form) ──────────────────────────────────────────────
  saveFinalRound:      (id, data) => api.post(`/interviews/candidates/${id}/final`, data),
  submitFinalDecision: (id, data) => api.post(`/interviews/final/${id}/decision`, data),

  // ── Final Round Video Interview ────────────────────────────────────────────
  generateFinalInterviewLink:   (candidateId, data) => api.post(`/interviews/candidates/${candidateId}/final/generate-link`, data),
  getDirectorRoom:              (id)       => api.get(`/interviews/final/${id}/director-room`),
  submitDirectorEvaluation:     (id, data) => api.post(`/interviews/final/${id}/evaluate`, data),

  // WebRTC signaling — director side
  saveFinalAnswerSdp:   (id, sdp) => api.post(`/interviews/final/${id}/signal/answer`, { sdp }),
  getFinalOffer:        (id)      => api.get(`/interviews/final/${id}/signal/offer`),
  addDirectorIce:       (id, ice) => api.post(`/interviews/final/${id}/signal/ice`, { ice }),
  getCandidateFinalIce: (id)      => api.get(`/interviews/final/${id}/signal/ice/candidate`),

  // ── Stats ──────────────────────────────────────────────────────────────────
  getStats: () => api.get('/interviews/stats'),
};
