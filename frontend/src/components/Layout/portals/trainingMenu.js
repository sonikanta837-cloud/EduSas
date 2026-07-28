import React from 'react';
import SchoolIcon from '@mui/icons-material/School';

const ALL_ROLES = ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE'];

// Sidebar modules for the Training portal — learning content.
export const trainingMenu = [
  {
    type: 'item',
    label: 'Courses',
    path: '/courses',
    icon: <SchoolIcon />,
    roles: ALL_ROLES,
  },
];

export default trainingMenu;
