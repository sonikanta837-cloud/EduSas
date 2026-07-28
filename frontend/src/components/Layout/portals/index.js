import React from 'react';
import GroupsIcon           from '@mui/icons-material/Groups';
import SchoolIcon           from '@mui/icons-material/School';
import InsightsIcon         from '@mui/icons-material/Insights';
import RecordVoiceOverIcon  from '@mui/icons-material/RecordVoiceOver';
import { hrMenu }        from './hrMenu';
import { trainingMenu }  from './trainingMenu';
import { tandemMenu }    from './tandemMenu';
import { interviewMenu } from './interviewMenu';

// Single source of truth for every portal available in the sidebar switcher
// and the portal-selection welcome screen. To add a new portal: create its
// `<name>Menu.js` config next to these, then register it here — Sidebar.jsx
// and PortalWelcome.jsx both read this list and need no changes.
export const portalRegistry = [
  {
    id: 'hr',
    label: 'CRM',
    description: 'Manage employees, attendance, leaves, and org-wide operations.',
    dashboardPath: '/dashboard',
    icon: <GroupsIcon />,
    menu: hrMenu,
  },
  {
    id: 'training',
    label: 'Training Portal',
    description: 'Browse courses, track learning progress, and manage performance reviews.',
    dashboardPath: '/courses',
    icon: <SchoolIcon />,
    menu: trainingMenu,
  },
  {
    id: 'tandem',
    label: 'Tandem',
    description: 'Track performance reviews and manage employee growth.',
    dashboardPath: '/performance',
    icon: <InsightsIcon />,
    menu: tandemMenu,
  },
  {
    id: 'interview',
    label: 'Interview Portal',
    description: 'Schedule interviews, manage the question bank, and track candidates.',
    dashboardPath: '/interviews',
    icon: <RecordVoiceOverIcon />,
    menu: interviewMenu,
  },
];

// No default — `id` may be null/unrecognized when no portal has been chosen yet.
export const getPortalById = (id) =>
  portalRegistry.find((p) => p.id === id) || null;

const flattenMenuPaths = (menu) =>
  menu.flatMap((entry) => (entry.type === 'group' ? entry.children : [entry])).map((e) => e.path);

// Resolves which registered portal "owns" a given route, so direct navigation
// (typed URL, bookmark, refresh) into a portal's pages can be authorized the
// same way clicking its switcher button is. Returns null for routes that
// aren't part of any portal (shared pages like /profile, /settings).
export const findOwningPortal = (pathname) =>
  portalRegistry.find((portal) =>
    flattenMenuPaths(portal.menu).some(
      (path) => pathname === path || pathname.startsWith(path + '/')
    )
  ) || null;
