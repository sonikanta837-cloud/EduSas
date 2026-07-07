import React from 'react';
import DashboardIcon          from '@mui/icons-material/Dashboard';
import PeopleIcon             from '@mui/icons-material/People';
import AccountTreeIcon        from '@mui/icons-material/AccountTree';
import HowToRegIcon           from '@mui/icons-material/HowToReg';
import AccessTimeIcon         from '@mui/icons-material/AccessTime';
import AssessmentIcon         from '@mui/icons-material/Assessment';
import BeachAccessIcon        from '@mui/icons-material/BeachAccess';
import CelebrationIcon        from '@mui/icons-material/Celebration';
import SchoolIcon             from '@mui/icons-material/School';
import StarIcon               from '@mui/icons-material/Star';
import BarChartIcon           from '@mui/icons-material/BarChart';
import InventoryIcon          from '@mui/icons-material/Inventory';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import PersonIcon             from '@mui/icons-material/Person';
import WorkHistoryIcon        from '@mui/icons-material/WorkHistory';
import QuizIcon                from '@mui/icons-material/Quiz';
import GroupsIcon             from '@mui/icons-material/Groups';
import EventAvailableIcon     from '@mui/icons-material/EventAvailable';
import EmojiEventsIcon        from '@mui/icons-material/EmojiEvents';
import BadgeIcon              from '@mui/icons-material/Badge';
import SummarizeIcon          from '@mui/icons-material/Summarize';

const ALL_ROLES = ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE'];

// Reusable nav configuration — add/remove modules or submenu items here only.
// `type: 'item'` renders a standalone top-level link.
// `type: 'group'` renders an accordion parent with `children` submenu items.
export const navConfig = [
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
      { label: 'Work Reports', path: '/work-reports', icon: <AssessmentIcon />, roles: ALL_ROLES },
      { label: 'Leaves',       path: '/leaves',       icon: <BeachAccessIcon />, roles: ALL_ROLES },
      { label: 'Holidays',     path: '/holidays',     icon: <CelebrationIcon />, roles: ALL_ROLES },
    ],
  },
  {
    type: 'group',
    id: 'learning-performance',
    label: 'Learning & Performance',
    icon: <EmojiEventsIcon />,
    children: [
      { label: 'Courses',     path: '/courses',     icon: <SchoolIcon />, roles: ALL_ROLES },
      { label: 'Performance', path: '/performance', icon: <StarIcon />,   roles: ALL_ROLES },
    ],
  },
  {
    type: 'group',
    id: 'recruitment',
    label: 'Recruitment',
    icon: <BadgeIcon />,
    children: [
      { label: 'Interviews',     path: '/interviews',     icon: <WorkHistoryIcon />, roles: ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE'] },
      { label: 'Question Bank',  path: '/question-bank',  icon: <QuizIcon />,        roles: ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER'] },
    ],
  },
  {
    type: 'group',
    id: 'reports-resources',
    label: 'Reports & Resources',
    icon: <SummarizeIcon />,
    children: [
      { label: 'Reports',   path: '/reports',   icon: <BarChartIcon />,  roles: ['ADMIN', 'DIRECTOR'] },
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
      { label: 'My Profile',          path: '/profile',           icon: <PersonIcon />,             roles: ALL_ROLES },
    ],
  },
];
