import React from 'react';
import WorkHistoryIcon from '@mui/icons-material/WorkHistory';
import QuizIcon        from '@mui/icons-material/Quiz';

const ALL_ROLES         = ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE'];
const REVIEWER_ROLES    = ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER'];

// Sidebar modules for the Interview portal — recruitment and hiring pipeline.
export const interviewMenu = [
  {
    type: 'item',
    label: 'Interviews',
    path: '/interviews',
    icon: <WorkHistoryIcon />,
    roles: ALL_ROLES,
  },
  {
    type: 'item',
    label: 'Question Bank',
    path: '/question-bank',
    icon: <QuizIcon />,
    roles: REVIEWER_ROLES,
  },
];

export default interviewMenu;
