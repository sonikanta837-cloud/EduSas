import React from 'react';
import StarIcon           from '@mui/icons-material/Star';
import AssignmentLateIcon from '@mui/icons-material/AssignmentLate';

const ALL_ROLES = ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE'];

// Sidebar modules for the Tandem portal — performance tracking and reviews.
export const tandemMenu = [
  {
    type: 'item',
    label: 'Performance Review',
    path: '/performance',
    icon: <StarIcon />,
    roles: ALL_ROLES,
  },
  {
    type: 'item',
    label: 'Performance Improvement',
    path: '/performance-pip',
    icon: <AssignmentLateIcon />,
    roles: ALL_ROLES,
  },
];

export default tandemMenu;
