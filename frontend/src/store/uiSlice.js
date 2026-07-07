import { createSlice } from '@reduxjs/toolkit';

const uiSlice = createSlice({
  name: 'ui',
  initialState: {
    sidebarOpen: true,
    mobileSidebarOpen: false,
  },
  reducers: {
    toggleSidebar: (state) => { state.sidebarOpen = !state.sidebarOpen; },
    setSidebarOpen: (state, action) => { state.sidebarOpen = action.payload; },
    toggleMobileSidebar: (state) => { state.mobileSidebarOpen = !state.mobileSidebarOpen; },
    setMobileSidebarOpen: (state, action) => { state.mobileSidebarOpen = action.payload; },
  },
});

export const { toggleSidebar, setSidebarOpen, toggleMobileSidebar, setMobileSidebarOpen } = uiSlice.actions;
export default uiSlice.reducer;
