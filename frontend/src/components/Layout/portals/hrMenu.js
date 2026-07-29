import React from 'react';
import DashboardIcon          from '@mui/icons-material/Dashboard';
import PeopleIcon             from '@mui/icons-material/People';
import AccountTreeIcon        from '@mui/icons-material/AccountTree';
import HowToRegIcon           from '@mui/icons-material/HowToReg';
import AccessTimeIcon         from '@mui/icons-material/AccessTime';
import BeachAccessIcon        from '@mui/icons-material/BeachAccess';
import CelebrationIcon        from '@mui/icons-material/Celebration';
import BarChartIcon           from '@mui/icons-material/BarChart';
import InventoryIcon          from '@mui/icons-material/Inventory';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import GroupsIcon             from '@mui/icons-material/Groups';
import EventAvailableIcon     from '@mui/icons-material/EventAvailable';
import SummarizeIcon          from '@mui/icons-material/Summarize';
import RuleIcon               from '@mui/icons-material/Rule';

const ALL_ROLES = ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE'];

// Sidebar modules for the HR portal — the core employee-management workspace.
// Same shape consumed by Sidebar.jsx for every portal: `type: 'item'` renders a
// standalone link, `type: 'group'` renders an accordion parent with `children`.
export const hrMenu = [
  {
    type: 'item',
    label: 'Dashboard',
    path: '/dashboard',
    icon: <DashboardIcon />,
    roles: ALL_ROLES,
  },
  {
    type: 'group',
    id: 'employee-management',
    label: 'Employee Management',
    icon: <GroupsIcon />,
    children: [
      { label: 'Employees',    path: '/employees', icon: <PeopleIcon />,      roles: ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER'] },
      { label: 'Organisation', path: '/org-chart',  icon: <AccountTreeIcon />, roles: ALL_ROLES },
    ],
  },
  {
    type: 'group',
    id: 'attendance-management',
    label: 'Attendance Management',
    icon: <EventAvailableIcon />,
    children: [
      { label: 'Attendance',   path: '/attendance',   icon: <HowToRegIcon />,    roles: ALL_ROLES },
      { label: 'Timesheets',   path: '/timesheets',   icon: <AccessTimeIcon />,  roles: ALL_ROLES },
      { label: 'Leaves',       path: '/leaves',       icon: <BeachAccessIcon />, roles: ALL_ROLES },
      { label: 'Working Hours Corrections', path: '/working-hours-corrections', icon: <RuleIcon />, roles: ALL_ROLES },
      { label: 'Holidays',     path: '/holidays',     icon: <CelebrationIcon />, roles: ALL_ROLES },
      { label: 'Timesheet Master Data', path: '/timesheet-master-data', icon: <InventoryIcon />, roles: ['ADMIN', 'DIRECTOR', 'HR'] },
    ],
  },
  {
    type: 'group',
    id: 'reports-resources',
    label: 'Reports & Resources',
    icon: <SummarizeIcon />,
    children: [
      { label: 'Reports',   path: '/reports',   icon: <BarChartIcon />,  roles: ALL_ROLES },
      { label: 'Resources', path: '/resources', icon: <InventoryIcon />, roles: ALL_ROLES },
    ],
  },
  {
    type: 'group',
    id: 'administration',
    label: 'Administration',
    icon: <AdminPanelSettingsIcon />,
    children: [
      { label: 'Roles & Permissions', path: '/roles-permissions', icon: <AdminPanelSettingsIcon />, roles: ['ADMIN', 'DIRECTOR'], superUserOnly: true },
    ],
  },
];

export default hrMenu;
