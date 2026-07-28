import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { login, logout } from './authSlice';
import { portalPermissionsApi } from '../api/portalPermissionsApi';

const PORTAL_STORAGE_KEY = 'active_portal';

// No portal is active by default — the sidebar stays hidden until the user
// picks one from the Portal Switcher. A prior selection is restored across
// refreshes within the same session (read from localStorage below), but a
// fresh login (or logout) always clears it — see extraReducers.
const getInitialPortal = () => {
  try { return localStorage.getItem(PORTAL_STORAGE_KEY) || null; } catch { return null; }
};

// Seed from whatever the last login/refresh stored, so the switcher doesn't
// flash "no portals" before `fetchAllowedPortals` resolves on app mount.
const getInitialAllowedPortals = () => {
  try {
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    return Array.isArray(user?.allowedPortals) ? user.allowedPortals : [];
  } catch { return []; }
};

// Re-fetches the current user's portal permissions from the backend — this is
// the "permission refresh" the requirements refer to: called once per app
// mount (i.e. on browser refresh) so a grant/revoke made by an admin since the
// last login shows up without forcing the user to log out and back in.
export const fetchAllowedPortals = createAsyncThunk(
  'portal/fetchAllowedPortals',
  async (_, { rejectWithValue }) => {
    try {
      return await portalPermissionsApi.getMine();
    } catch (err) {
      return rejectWithValue(err.response?.data?.message || 'Failed to load portal permissions');
    }
  }
);

const portalSlice = createSlice({
  name: 'portal',
  initialState: {
    activePortal: getInitialPortal(),
    allowedPortals: getInitialAllowedPortals(),
  },
  reducers: {
    setActivePortal: (state, action) => {
      state.activePortal = action.payload;
      try {
        if (action.payload) localStorage.setItem(PORTAL_STORAGE_KEY, action.payload);
        else localStorage.removeItem(PORTAL_STORAGE_KEY);
      } catch { /* localStorage unavailable — non-fatal */ }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(login.fulfilled, (state, action) => {
        state.activePortal = null;
        state.allowedPortals = action.payload.allowedPortals || [];
        try { localStorage.removeItem(PORTAL_STORAGE_KEY); } catch { /* non-fatal */ }
      })
      .addCase(logout.fulfilled, (state) => {
        state.activePortal = null;
        state.allowedPortals = [];
        // authSlice's logout thunk already runs localStorage.clear() — nothing more to remove here.
      })
      .addCase(fetchAllowedPortals.fulfilled, (state, action) => {
        state.allowedPortals = action.payload || [];
        // A portal that was active but has since been revoked disappears immediately —
        // the sidebar collapses back to the "choose a portal" screen rather than
        // continuing to show a menu the role no longer has access to.
        if (state.activePortal && !state.allowedPortals.includes(state.activePortal)) {
          state.activePortal = null;
          try { localStorage.removeItem(PORTAL_STORAGE_KEY); } catch { /* non-fatal */ }
        }
      });
  },
});

export const { setActivePortal } = portalSlice.actions;
export default portalSlice.reducer;
