import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import uiReducer from './uiSlice';
import portalReducer from './portalSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    ui: uiReducer,
    portal: portalReducer,
  },
});

export default store;
