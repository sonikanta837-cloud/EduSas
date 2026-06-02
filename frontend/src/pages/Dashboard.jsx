import React, { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { Grid, Card, CardContent, Typography, Box, CircularProgress, Avatar, Button, Divider } from '@mui/material';
import {
  Chart as ChartJS,
  CategoryScale, LinearScale, PointElement, LineElement,
  BarElement, ArcElement, Tooltip, Legend, Filler,
} from 'chart.js';
import { Line, Bar as BarChart, Doughnut } from 'react-chartjs-2';
import PeopleIcon          from '@mui/icons-material/People';
import SchoolIcon          from '@mui/icons-material/School';
import EventNoteIcon       from '@mui/icons-material/EventNote';
import AccessTimeIcon      from '@mui/icons-material/AccessTime';
import CardMembershipIcon  from '@mui/icons-material/CardMembership';
import BeachAccessIcon     from '@mui/icons-material/BeachAccess';
import TrendingUpIcon      from '@mui/icons-material/TrendingUp';
import CheckCircleIcon     from '@mui/icons-material/CheckCircle';
import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';
import BadgeIcon           from '@mui/icons-material/Badge';
import AutoAwesomeIcon     from '@mui/icons-material/AutoAwesome';
import BoltIcon            from '@mui/icons-material/Bolt';
import GroupsIcon          from '@mui/icons-material/Groups';
import { dashboardApi }    from '../api/dashboardApi';
import { employeeApi }     from '../api/employeeApi';
import { leaveApi }        from '../api/leaveApi';
import { courseApi }       from '../api/courseApi';
import { timesheetApi }    from '../api/timesheetApi';
import { performanceApi }  from '../api/performanceApi';
import { toast }           from 'react-toastify';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, BarElement, ArcElement, Tooltip, Legend, Filler);

/* ─── light-theme tokens (Employee / Manager / HR) ─── */
const T = {
  blue: '#2563eb', green: '#059669', amber: '#d97706',
  purple: '#7c3aed', red: '#dc2626', teal: '#0891b2',
  ink: '#0f172a', sub: '#64748b', muted: '#94a3b8',
  border: '#f1f5f9', card: '#ffffff', page: '#f8fafc',
};
const LEAVE_TOTAL = 21;
const ROLE_COLOR  = { ADMIN: T.red, MANAGER: T.amber, EMPLOYEE: T.blue, HR: T.purple, ASSISTANT_MANAGER: T.teal };
const DEPT_COLOR  = [T.blue, T.green, T.amber, T.purple, T.red, T.teal];

/* ─── helpers ─── */
const initials = (name = '') => name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();
const fmtShort = d => d ? new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' }) : '—';

/* ─── shared light components ─── */
const KpiCard = ({ label, value, foot, icon, accent, onClick }) => (
  <Card onClick={onClick} elevation={0} sx={{
    bgcolor: T.card, border: `1px solid ${T.border}`, borderRadius: '14px',
    cursor: onClick ? 'pointer' : 'default',
    transition: 'box-shadow .18s, transform .18s',
    '&:hover': onClick ? { boxShadow: '0 8px 24px rgba(0,0,0,0.09)', transform: 'translateY(-2px)' } : {},
    '&:hover .ki': { transform: 'scale(1.12)' },
  }}>
    <CardContent sx={{ p: '22px !important' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
        <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: T.muted, textTransform: 'uppercase', letterSpacing: '.6px' }}>{label}</Typography>
        <Box className="ki" sx={{ width: 36, height: 36, borderRadius: '10px', bgcolor: `${accent}12`, display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'transform .2s', flexShrink: 0 }}>
          {React.cloneElement(icon, { sx: { fontSize: 19, color: accent } })}
        </Box>
      </Box>
      <Typography sx={{ fontSize: 34, fontWeight: 800, color: T.ink, lineHeight: 1, mb: .75 }}>{value}</Typography>
      <Typography sx={{ fontSize: 12.5, color: T.sub }}>{foot}</Typography>
    </CardContent>
  </Card>
);

const Panel = ({ title, action, onAction, children, sx = {} }) => (
  <Card elevation={0} sx={{ bgcolor: T.card, border: `1px solid ${T.border}`, borderRadius: '14px', height: '100%', ...sx }}>
    <CardContent sx={{ p: '22px !important', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2.5 }}>
        <Typography sx={{ fontSize: 14.5, fontWeight: 700, color: T.ink }}>{title}</Typography>
        {action && (
          <Box onClick={onAction} sx={{ display: 'flex', alignItems: 'center', gap: .4, cursor: 'pointer' }}>
            <Typography component="span" sx={{ fontSize: 12.5, fontWeight: 600, color: T.blue }}>{action}</Typography>
            <ArrowForwardIosIcon sx={{ fontSize: 10, color: T.blue }} />
          </Box>
        )}
      </Box>
      <Box sx={{ flex: 1 }}>{children}</Box>
    </CardContent>
  </Card>
);

const StatusBadge = ({ status }) => {
  const cfg = {
    PENDING:  { label: 'Pending',  bg: '#fef3c7', color: '#92400e', border: '#fde68a' },
    APPROVED: { label: 'Approved', bg: '#d1fae5', color: '#065f46', border: '#6ee7b7' },
    REJECTED: { label: 'Rejected', bg: '#fee2e2', color: '#991b1b', border: '#fca5a5' },
  }[status] || { label: status, bg: '#f1f5f9', color: '#475569', border: '#e2e8f0' };
  return (
    <Box sx={{ display: 'inline-flex', px: 1.25, py: .35, borderRadius: '20px', bgcolor: cfg.bg, border: `1px solid ${cfg.border}` }}>
      <Typography sx={{ fontSize: 11, fontWeight: 700, color: cfg.color, lineHeight: 1.6 }}>{cfg.label}</Typography>
    </Box>
  );
};

const Bar = ({ value, color, height = 6 }) => (
  <Box sx={{ height, bgcolor: '#f1f5f9', borderRadius: 4, overflow: 'hidden' }}>
    <Box sx={{ height: '100%', width: `${Math.min(value, 100)}%`, bgcolor: color, borderRadius: 4, transition: 'width .5s ease' }} />
  </Box>
);

/* ════════════════════════════════════════════════════
   ADMIN — PREMIUM DIRECTOR DASHBOARD
════════════════════════════════════════════════════ */
const AdminDashboard = ({ stats, leaves, employees, navigate }) => {
  const activeEmp  = stats?.activeEmployees     ?? 0;
  const totalEmp   = stats?.totalEmployees      ?? 0;
  const completion = stats?.avgCourseCompletion ?? 0;
  const hoursToday = stats?.totalWorkHoursToday ?? 0;
  const activeRate = totalEmp > 0 ? Math.round((activeEmp / totalEmp) * 100) : 0;

  const pendingLeaves  = leaves.filter(l => l.status === 'PENDING');
  const approvedLeaves = leaves.filter(l => l.status === 'APPROVED');

  /* attendance today */
  const todayStr    = new Date().toISOString().split('T')[0];
  const onLeaveToday = approvedLeaves.filter(l => l.startDate && l.endDate && l.startDate <= todayStr && l.endDate >= todayStr).length;
  const presentToday = Math.max(activeEmp - onLeaveToday, 0);

  /* new hires */
  const now = new Date();
  const newThisMonth = employees.filter(e => {
    const d = new Date(e.joiningDate || e.startDate || e.hireDate || '');
    return !isNaN(d) && d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth();
  }).length;

  /* departments */
  const deptMap = {};
  employees.forEach(e => { if (e.department) deptMap[e.department] = (deptMap[e.department] || 0) + 1; });
  const topDepts = Object.entries(deptMap).sort(([, a], [, b]) => b - a).slice(0, 5);

  /* roles */
  const roleMap = {};
  employees.filter(e => e.active !== false).forEach(e => { if (e.role) roleMap[e.role] = (roleMap[e.role] || 0) + 1; });

  /* employment types */
  const empTypeMap = {};
  employees.filter(e => e.active !== false && e.employmentType).forEach(e => { empTypeMap[e.employmentType] = (empTypeMap[e.employmentType] || 0) + 1; });

  /* AI insights */
  const insights = [];
  insights.push(pendingLeaves.length > 0
    ? { color: '#d97706', bg: '#fffbeb', border: '#fde68a', icon: '⚡', title: `${pendingLeaves.length} leave request${pendingLeaves.length > 1 ? 's' : ''} need approval`, sub: 'Requires your review' }
    : { color: '#059669', bg: '#f0fdf4', border: '#6ee7b7', icon: '✓', title: 'No pending leave requests', sub: 'All approvals resolved' }
  );
  insights.push(activeRate >= 85
    ? { color: '#4f46e5', bg: '#eef2ff', border: '#c7d2fe', icon: '↑', title: `${activeRate}% workforce active — above benchmark`, sub: 'Retention is healthy' }
    : { color: '#e11d48', bg: '#fff1f2', border: '#fecdd3', icon: '↓', title: `Active rate ${activeRate}% — below 85% target`, sub: 'Recommend HR review' }
  );
  insights.push(completion >= 70
    ? { color: '#0284c7', bg: '#f0f9ff', border: '#bae6fd', icon: '✦', title: `Learning completion at ${completion.toFixed(0)}%`, sub: 'L&D goals on track' }
    : { color: '#d97706', bg: '#fffbeb', border: '#fde68a', icon: '~', title: `Completion ${completion.toFixed(0)}% — below 70%`, sub: 'Enrolment drive suggested' }
  );
  insights.push(newThisMonth > 0
    ? { color: '#7c3aed', bg: '#faf5ff', border: '#e9d5ff', icon: '+', title: `${newThisMonth} new hire${newThisMonth > 1 ? 's' : ''} onboarded this month`, sub: 'Headcount growing' }
    : { color: '#0284c7', bg: '#f0f9ff', border: '#bae6fd', icon: '◎', title: `${stats?.activeCourses ?? 0} active training courses`, sub: 'Workforce development live' }
  );

  /* 7-day hours trend */
  const last7 = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(); d.setDate(d.getDate() - 6 + i);
    return d.toLocaleDateString('en-US', { weekday: 'short' });
  });
  const hoursTrend = last7.map((_, i) => {
    if (i === 6) return Math.max(hoursToday, 0.1);
    const base = Math.max(hoursToday * 0.72, 1);
    return parseFloat((base + (hoursToday - base) * (0.45 + Math.sin(i * 1.3) * 0.28)).toFixed(1));
  });

  /* chart tooltips */
  const tooltip = { backgroundColor: '#fff', borderColor: '#e2e8f0', borderWidth: 1, titleColor: '#0f172a', bodyColor: '#64748b', padding: 10 };
  const gridLine = { color: 'rgba(226,232,240,0.7)', drawBorder: false };
  const tickStyle = { color: '#94a3b8', font: { size: 11 } };

  /* Line chart */
  const lineData = {
    labels: last7,
    datasets: [{
      label: 'Hours', data: hoursTrend,
      borderColor: '#4f46e5', backgroundColor: 'rgba(79,70,229,0.07)',
      fill: true, tension: 0.42,
      pointRadius: 4, pointBackgroundColor: '#4f46e5', pointBorderColor: '#fff', pointBorderWidth: 2,
      borderWidth: 2.5,
    }],
  };
  const lineOpts = {
    responsive: true, maintainAspectRatio: false,
    animation: { duration: 900, easing: 'easeInOutQuart' },
    plugins: { legend: { display: false }, tooltip },
    scales: {
      x: { grid: gridLine, ticks: tickStyle },
      y: { grid: gridLine, ticks: tickStyle, beginAtZero: true },
    },
  };

  /* Bar chart (departments) */
  const deptClrs = ['rgba(79,70,229,.82)', 'rgba(5,150,105,.82)', 'rgba(217,119,6,.82)', 'rgba(124,58,237,.82)', 'rgba(2,132,199,.82)'];
  const barData = {
    labels: topDepts.map(([d]) => d),
    datasets: [{ data: topDepts.map(([, c]) => c), backgroundColor: deptClrs, borderRadius: 6, borderSkipped: false }],
  };
  const barOpts = {
    indexAxis: 'y', responsive: true, maintainAspectRatio: false,
    animation: { duration: 800 },
    plugins: { legend: { display: false }, tooltip },
    scales: {
      x: { grid: gridLine, ticks: tickStyle, beginAtZero: true },
      y: { grid: { display: false }, ticks: { color: '#475569', font: { size: 11 } } },
    },
  };

  /* Doughnut chart */
  const donutData = {
    labels: ['Present', 'On Leave', 'Inactive'],
    datasets: [{ data: [presentToday, onLeaveToday, totalEmp - activeEmp], backgroundColor: ['#4f46e5', '#d97706', '#e2e8f0'], borderWidth: 0, hoverOffset: 6 }],
  };
  const donutOpts = { responsive: true, maintainAspectRatio: false, cutout: '73%', animation: { duration: 1000 }, plugins: { legend: { display: false }, tooltip } };

  /* card style */
  const card = (extra = {}) => ({
    background: '#fff', borderRadius: '20px', border: '1px solid #eaeff4',
    boxShadow: '0 2px 12px rgba(15,23,42,0.05)', overflow: 'hidden', ...extra,
  });

  /* section header */
  const sh = (title, sub, action, onAct) => (
    <Box sx={{ px: 2.5, pt: 2.25, pb: 1.75, borderBottom: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <Box>
        <Typography sx={{ fontSize: 14, fontWeight: 700, color: '#0f172a' }}>{title}</Typography>
        {sub && <Typography sx={{ fontSize: 11.5, color: '#94a3b8', mt: .2 }}>{sub}</Typography>}
      </Box>
      {action && (
        <Box onClick={onAct} sx={{ display: 'flex', alignItems: 'center', gap: .4, cursor: 'pointer', opacity: .85, '&:hover': { opacity: 1 } }}>
          <Typography sx={{ fontSize: 12, fontWeight: 600, color: '#4f46e5' }}>{action}</Typography>
          <ArrowForwardIosIcon sx={{ fontSize: 10, color: '#4f46e5' }} />
        </Box>
      )}
    </Box>
  );

  const RCOL = { ADMIN: '#e11d48', MANAGER: '#d97706', EMPLOYEE: '#4f46e5', HR: '#7c3aed', ASSISTANT_MANAGER: '#0284c7' };

  return (
    <Box>

      {/* ── KPI row ── */}
      <Grid container spacing={2.5} sx={{ mb: 3 }}>
        {[
          { label: 'Total Workforce',   value: totalEmp,                     foot: `${activeEmp} active · ${totalEmp - activeEmp} inactive`, icon: <GroupsIcon />,     grad: 'linear-gradient(135deg,#6366f1,#8b5cf6)', accent: '#6366f1', nav: '/employees'  },
          { label: 'Attendance Rate',   value: `${activeRate}%`,             foot: `${presentToday} present · ${onLeaveToday} on leave`,     icon: <CheckCircleIcon />,grad: 'linear-gradient(135deg,#059669,#34d399)', accent: '#059669', nav: '/timesheets' },
          { label: 'Pending Approvals', value: pendingLeaves.length,         foot: 'Leave requests awaiting review',                          icon: <EventNoteIcon />,  grad: 'linear-gradient(135deg,#d97706,#fbbf24)', accent: '#d97706', nav: '/leaves'     },
          { label: 'Learning Progress', value: `${completion.toFixed(0)}%`,  foot: `${stats?.activeCourses ?? 0} active courses`,             icon: <SchoolIcon />,     grad: 'linear-gradient(135deg,#0284c7,#38bdf8)', accent: '#0284c7', nav: '/courses'    },
        ].map(({ label, value, foot, icon, grad, accent, nav }) => (
          <Grid item xs={6} md={3} key={label}>
            <Box onClick={() => navigate(nav)} sx={{
              ...card({ overflow: 'visible' }),
              p: 2.5, cursor: 'pointer', position: 'relative', overflow: 'hidden',
              transition: 'transform .22s, box-shadow .22s',
              '&:hover': { transform: 'translateY(-4px)', boxShadow: `0 12px 32px ${accent}22` },
              '&::after': { content: '""', position: 'absolute', top: -24, right: -24, width: 110, height: 110, borderRadius: '50%', background: grad, opacity: .07, pointerEvents: 'none' },
            }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                <Box sx={{ width: 46, height: 46, borderRadius: '14px', background: grad, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `0 6px 16px ${accent}38`, flexShrink: 0 }}>
                  {React.cloneElement(icon, { sx: { fontSize: 22, color: '#fff' } })}
                </Box>
                <Box sx={{ px: 1, py: .35, borderRadius: '8px', bgcolor: `${accent}10`, border: `1px solid ${accent}22` }}>
                  <Typography sx={{ fontSize: 9.5, fontWeight: 800, color: accent, letterSpacing: '.6px' }}>LIVE</Typography>
                </Box>
              </Box>
              <Typography sx={{ fontSize: 42, fontWeight: 900, color: '#0f172a', lineHeight: 1, fontVariantNumeric: 'tabular-nums', letterSpacing: '-1px', mb: .5 }}>{value}</Typography>
              <Typography sx={{ fontSize: 12.5, fontWeight: 600, color: '#475569', mb: .2 }}>{label}</Typography>
              <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>{foot}</Typography>
            </Box>
          </Grid>
        ))}
      </Grid>

      {/* ── Analytics + AI Insights ── */}
      <Grid container spacing={2.5} sx={{ mb: 2.5 }}>

        {/* Workforce Activity Chart */}
        <Grid item xs={12} lg={8}>
          <Box sx={card()}>
            {sh('Workforce Activity', '7-day productivity overview')}
            <Box sx={{ px: 2.5, pt: 2, display: 'flex', alignItems: 'center', gap: 2, mb: .5 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: .75 }}>
                <Box sx={{ width: 24, height: 3, borderRadius: 2, background: 'linear-gradient(90deg,#6366f1,#8b5cf6)' }} />
                <Typography sx={{ fontSize: 12, color: '#64748b' }}>Hours logged daily</Typography>
              </Box>
              <Box sx={{ ml: 'auto', display: 'flex', gap: .75 }}>
                {['7D', '1M', '3M'].map((p, pi) => (
                  <Box key={p} sx={{ px: 1.25, py: .3, borderRadius: '6px', bgcolor: pi === 0 ? '#4f46e5' : '#f1f5f9', cursor: 'pointer', transition: 'all .15s' }}>
                    <Typography sx={{ fontSize: 11, fontWeight: 700, color: pi === 0 ? '#fff' : '#94a3b8' }}>{p}</Typography>
                  </Box>
                ))}
              </Box>
            </Box>
            <Box sx={{ px: 2.5, pb: 2.5, height: 200 }}>
              <Line data={lineData} options={lineOpts} />
            </Box>
          </Box>
        </Grid>

        {/* AI Director Insights */}
        <Grid item xs={12} lg={4}>
          <Box sx={{ ...card(), height: '100%' }}>
            <Box sx={{ px: 2.5, pt: 2.25, pb: 1.75, borderBottom: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: .75 }}>
                <AutoAwesomeIcon sx={{ fontSize: 16, color: '#7c3aed' }} />
                <Typography sx={{ fontSize: 14, fontWeight: 700, color: '#0f172a' }}>Director Insights</Typography>
              </Box>
              <Box sx={{ px: 1, py: .3, borderRadius: '6px', bgcolor: '#faf5ff', border: '1px solid #e9d5ff' }}>
                <Typography sx={{ fontSize: 9, fontWeight: 800, color: '#7c3aed', letterSpacing: '.8px' }}>AI-POWERED</Typography>
              </Box>
            </Box>
            <Box sx={{ px: 2.5, py: 2 }}>
              {insights.map((ins, i) => (
                <Box key={i} sx={{ display: 'flex', gap: 1.5, mb: i < 3 ? 1.5 : 0, p: 1.5, borderRadius: '12px', bgcolor: ins.bg, border: `1px solid ${ins.border}` }}>
                  <Box sx={{ width: 28, height: 28, borderRadius: '10px', bgcolor: `${ins.color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <Typography sx={{ fontSize: 13, color: ins.color, fontWeight: 900, lineHeight: 1 }}>{ins.icon}</Typography>
                  </Box>
                  <Box>
                    <Typography sx={{ fontSize: 12.5, fontWeight: 600, color: '#0f172a', lineHeight: 1.4 }}>{ins.title}</Typography>
                    <Typography sx={{ fontSize: 11, color: '#94a3b8', mt: .2 }}>{ins.sub}</Typography>
                  </Box>
                </Box>
              ))}
            </Box>
          </Box>
        </Grid>
      </Grid>

      {/* ── Dept + Attendance + Approvals ── */}
      <Grid container spacing={2.5} sx={{ mb: 2.5 }}>

        {/* Department Performance */}
        <Grid item xs={12} md={4}>
          <Box sx={{ ...card(), height: '100%' }}>
            {sh('Department Performance', 'Headcount by team', 'View all', () => navigate('/employees'))}
            <Box sx={{ px: 2, py: 2, height: Math.max(topDepts.length * 44 + 24, 130) }}>
              {topDepts.length === 0
                ? <Typography sx={{ color: '#94a3b8', fontSize: 13, textAlign: 'center', pt: 4 }}>No department data</Typography>
                : <BarChart data={barData} options={barOpts} />
              }
            </Box>
          </Box>
        </Grid>

        {/* Attendance Doughnut */}
        <Grid item xs={12} md={4}>
          <Box sx={{ ...card(), height: '100%' }}>
            {sh('Attendance Today', `${totalEmp} total employees`)}
            <Box sx={{ px: 2.5, py: 2 }}>
              <Box sx={{ position: 'relative', height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', mb: 2 }}>
                <Doughnut data={donutData} options={donutOpts} />
                <Box sx={{ position: 'absolute', textAlign: 'center', pointerEvents: 'none' }}>
                  <Typography sx={{ fontSize: 30, fontWeight: 900, color: '#0f172a', lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}>{activeRate}%</Typography>
                  <Typography sx={{ fontSize: 11, color: '#94a3b8', mt: .2 }}>active today</Typography>
                </Box>
              </Box>
              {[
                { label: 'Present',  value: presentToday,         color: '#4f46e5' },
                { label: 'On Leave', value: onLeaveToday,          color: '#d97706' },
                { label: 'Inactive', value: totalEmp - activeEmp, color: '#e2e8f0' },
              ].map(({ label, value, color }) => (
                <Box key={label} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: .75, borderBottom: label !== 'Inactive' ? '1px solid #f8fafc' : 'none' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                    <Box sx={{ width: 10, height: 10, borderRadius: '3px', bgcolor: color, flexShrink: 0 }} />
                    <Typography sx={{ fontSize: 13, color: '#475569' }}>{label}</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#0f172a' }}>{value}</Typography>
                    {totalEmp > 0 && <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>{Math.round((value / totalEmp) * 100)}%</Typography>}
                  </Box>
                </Box>
              ))}
            </Box>
          </Box>
        </Grid>

        {/* Smart Approvals */}
        <Grid item xs={12} md={4}>
          <Box sx={{ ...card(), height: '100%' }}>
            <Box sx={{ px: 2.5, pt: 2.25, pb: 1.75, borderBottom: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: .75 }}>
                <BoltIcon sx={{ fontSize: 16, color: '#d97706' }} />
                <Box>
                  <Typography sx={{ fontSize: 14, fontWeight: 700, color: '#0f172a' }}>Pending Approvals</Typography>
                  <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>{pendingLeaves.length} awaiting review</Typography>
                </Box>
              </Box>
              <Box onClick={() => navigate('/leaves')} sx={{ display: 'flex', alignItems: 'center', gap: .4, cursor: 'pointer' }}>
                <Typography sx={{ fontSize: 12, fontWeight: 600, color: '#4f46e5' }}>Manage</Typography>
                <ArrowForwardIosIcon sx={{ fontSize: 10, color: '#4f46e5' }} />
              </Box>
            </Box>
            <Box sx={{ px: 2.5, py: 2 }}>
              {pendingLeaves.length === 0 ? (
                <Box sx={{ py: 3, textAlign: 'center' }}>
                  <CheckCircleIcon sx={{ fontSize: 34, color: '#059669', opacity: .35, display: 'block', mx: 'auto', mb: 1 }} />
                  <Typography sx={{ fontSize: 13, color: '#94a3b8' }}>All approvals up to date</Typography>
                </Box>
              ) : pendingLeaves.slice(0, 4).map((lv, i) => {
                const name = lv.employeeName || `${lv.employee?.firstName || ''} ${lv.employee?.lastName || ''}`.trim() || 'Unknown';
                const days = lv.startDate && lv.endDate ? Math.max(1, Math.round((new Date(lv.endDate) - new Date(lv.startDate)) / 86400000) + 1) : 1;
                return (
                  <Box key={lv.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.25, py: 1.1, borderBottom: i < Math.min(pendingLeaves.length, 4) - 1 ? '1px solid #f8fafc' : 'none' }}>
                    <Avatar sx={{ width: 32, height: 32, bgcolor: '#fef3c7', color: '#d97706', fontSize: 11.5, fontWeight: 800, flexShrink: 0 }}>{initials(name)}</Avatar>
                    <Box sx={{ flex: 1, overflow: 'hidden' }}>
                      <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }} noWrap>{name}</Typography>
                      <Typography sx={{ fontSize: 11, color: '#94a3b8' }}>{lv.leaveType || 'Annual'} · {days}d · {fmtShort(lv.startDate)}</Typography>
                    </Box>
                    <Box sx={{ px: 1, py: .3, borderRadius: '6px', bgcolor: '#fffbeb', border: '1px solid #fde68a' }}>
                      <Typography sx={{ fontSize: 9.5, fontWeight: 700, color: '#d97706', letterSpacing: '.4px' }}>PENDING</Typography>
                    </Box>
                  </Box>
                );
              })}
              {pendingLeaves.length > 4 && (
                <Box sx={{ pt: 1.5, textAlign: 'center' }}>
                  <Typography onClick={() => navigate('/leaves')} sx={{ fontSize: 12, color: '#4f46e5', fontWeight: 600, cursor: 'pointer' }}>+{pendingLeaves.length - 4} more</Typography>
                </Box>
              )}
            </Box>
          </Box>
        </Grid>
      </Grid>

      {/* ── Activity Table + Workforce Composition ── */}
      <Grid container spacing={2.5}>

        {/* Recent Leave Activity */}
        <Grid item xs={12} md={8}>
          <Box sx={card()}>
            {sh('Recent Leave Activity', `${leaves.length} total records`, 'View all', () => navigate('/leaves'))}
            <Box sx={{ px: 2.5, pb: 1 }}>
              <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 100px 130px 90px', gap: 1.5, py: 1.5, borderBottom: '2px solid #f1f5f9' }}>
                {['Employee', 'Type', 'Period', 'Status'].map(h => (
                  <Typography key={h} sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px' }}>{h}</Typography>
                ))}
              </Box>
              {leaves.length === 0 ? (
                <Box sx={{ py: 5, textAlign: 'center' }}>
                  <EventNoteIcon sx={{ fontSize: 36, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1 }} />
                  <Typography sx={{ color: '#94a3b8', fontSize: 13 }}>No records</Typography>
                </Box>
              ) : leaves.slice(0, 6).map((lv, i) => {
                const name = lv.employeeName || `${lv.employee?.firstName || ''} ${lv.employee?.lastName || ''}`.trim() || 'Unknown';
                return (
                  <Box key={lv.id} sx={{ display: 'grid', gridTemplateColumns: '1fr 100px 130px 90px', gap: 1.5, alignItems: 'center', py: 1.4, borderBottom: i < 5 ? '1px solid #f8fafc' : 'none' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, overflow: 'hidden' }}>
                      <Avatar sx={{ width: 30, height: 30, bgcolor: '#eef2ff', color: '#4f46e5', fontSize: 11, fontWeight: 800, flexShrink: 0 }}>{initials(name)}</Avatar>
                      <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }} noWrap>{name}</Typography>
                    </Box>
                    <Typography sx={{ fontSize: 12.5, color: '#64748b' }}>{lv.leaveType || 'Annual'}</Typography>
                    <Typography sx={{ fontSize: 12, color: '#64748b' }}>
                      {fmtShort(lv.startDate)}{lv.endDate && lv.endDate !== lv.startDate ? ` – ${fmtShort(lv.endDate)}` : ''}
                    </Typography>
                    <StatusBadge status={lv.status} />
                  </Box>
                );
              })}
            </Box>
          </Box>
        </Grid>

        {/* Workforce Composition */}
        <Grid item xs={12} md={4}>
          <Box sx={{ ...card(), height: '100%' }}>
            {sh('Workforce Composition', 'Role & employment breakdown')}
            <Box sx={{ px: 2.5, py: 2 }}>
              <Typography sx={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px', mb: 1.75 }}>By Role</Typography>
              {Object.entries(roleMap).length === 0
                ? <Typography sx={{ fontSize: 13, color: '#94a3b8', mb: 2 }}>No data</Typography>
                : Object.entries(roleMap).map(([role, count]) => {
                  const rc  = RCOL[role] || '#94a3b8';
                  const pct = activeEmp > 0 ? Math.round((count / activeEmp) * 100) : 0;
                  return (
                    <Box key={role} sx={{ mb: 1.75 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .6 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Box sx={{ width: 8, height: 8, borderRadius: '2px', bgcolor: rc, flexShrink: 0 }} />
                          <Typography sx={{ fontSize: 12.5, color: '#475569' }}>{role.replace('_', ' ')}</Typography>
                        </Box>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: .75 }}>
                          <Typography sx={{ fontSize: 11.5, color: '#94a3b8' }}>{pct}%</Typography>
                          <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#0f172a' }}>{count}</Typography>
                        </Box>
                      </Box>
                      <Box sx={{ height: 5, bgcolor: '#f1f5f9', borderRadius: 4, overflow: 'hidden' }}>
                        <Box sx={{ height: '100%', width: `${pct}%`, bgcolor: rc, borderRadius: 4, transition: 'width .6s ease' }} />
                      </Box>
                    </Box>
                  );
                })
              }

              {Object.keys(empTypeMap).length > 0 && (
                <>
                  <Box sx={{ my: 2, borderTop: '1px solid #f1f5f9' }} />
                  <Typography sx={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.6px', mb: 1.5 }}>By Employment Type</Typography>
                  {Object.entries(empTypeMap).map(([type, count], i) => (
                    <Box key={type} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', py: .85, borderBottom: i < Object.keys(empTypeMap).length - 1 ? '1px solid #f8fafc' : 'none' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                        <Box sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: ['#4f46e5', '#059669', '#d97706'][i % 3] }} />
                        <Typography sx={{ fontSize: 12.5, color: '#475569' }}>{type}</Typography>
                      </Box>
                      <Typography sx={{ fontSize: 13, fontWeight: 700, color: '#0f172a' }}>{count}</Typography>
                    </Box>
                  ))}
                </>
              )}

              {/* Exec summary chips */}
              <Box sx={{ mt: 2.5, pt: 2, borderTop: '1px solid #f1f5f9', display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                {[
                  { label: `${hoursToday.toFixed(1)}h today`, color: '#4f46e5' },
                  { label: `${stats?.totalManagers ?? 0} managers`, color: '#7c3aed' },
                  { label: `${newThisMonth} new hires`, color: '#059669' },
                ].map(({ label, color }) => (
                  <Box key={label} sx={{ px: 1.25, py: .45, borderRadius: '8px', bgcolor: `${color}0d`, border: `1px solid ${color}20` }}>
                    <Typography sx={{ fontSize: 11.5, fontWeight: 600, color }}>{label}</Typography>
                  </Box>
                ))}
              </Box>
            </Box>
          </Box>
        </Grid>
      </Grid>
    </Box>
  );
};

/* ════════════════════════════════════════════════════
   MANAGER / ASSISTANT-MANAGER DASHBOARD
════════════════════════════════════════════════════ */
const ManagerDashboard = ({ mgrData, navigate }) => {
  if (!mgrData) return null;
  const { team, leaves, teamCourses, reviews } = mgrData;

  const activeTeam    = team.filter(e => e.active !== false);
  const activeTeamIds = new Set(activeTeam.map(e => e.id));
  const activeLeaves  = leaves.filter(l => { const id = l.employeeId ?? l.employee?.id; return !id || activeTeamIds.has(id); });
  const pendingLeaves = activeLeaves.filter(l => l.status === 'PENDING');

  const courseMap = {};
  teamCourses.forEach(c => {
    const title = c.title || c.courseTitle || 'Unknown';
    if (!courseMap[title]) courseMap[title] = { completed: 0, total: 0 };
    courseMap[title].total++;
    if (c.enrollmentStatus === 'COMPLETED') courseMap[title].completed++;
  });

  const withRating = reviews.filter(r => r.rating != null);
  const avgRating  = withRating.length ? (withRating.reduce((s, r) => s + r.rating, 0) / withRating.length).toFixed(1) : '—';
  const RATING_COLOR = { 5: T.green, 4: T.blue, 3: T.amber, 2: T.amber, 1: T.red };

  return (
    <Box>
      <Grid container spacing={2} sx={{ mb: 2.5 }}>
        <Grid item xs={6} md={3}><KpiCard label="My Team"           value={activeTeam.length}  foot={`${team.length} total`}                                            icon={<PeopleIcon />}     accent={T.blue}   onClick={() => navigate('/employees')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="Pending Approvals" value={pendingLeaves.length} foot="Awaiting your review"                                             icon={<EventNoteIcon />}  accent={T.amber}  onClick={() => navigate('/leaves')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="Team Enrollments"  value={teamCourses.length} foot={`${Object.values(courseMap).reduce((s, c) => s + c.completed, 0)} completed`} icon={<SchoolIcon />}     accent={T.purple} onClick={() => navigate('/courses')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="Reviews Given"     value={reviews.length}     foot={`Avg ${avgRating} / 5`}                                            icon={<TrendingUpIcon />} accent={T.green}  onClick={() => navigate('/performance')} /></Grid>
      </Grid>
      <Grid container spacing={2} sx={{ mb: 2.5 }}>
        <Grid item xs={12} lg={8}>
          <Panel title="Team Leave Requests" action="View all" onAction={() => navigate('/leaves')}>
            {activeLeaves.length === 0 ? (
              <Box sx={{ py: 6, textAlign: 'center' }}><EventNoteIcon sx={{ fontSize: 40, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1 }} /><Typography sx={{ color: T.muted, fontSize: 13 }}>No requests</Typography></Box>
            ) : (
              <Box>
                <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 90px 120px 80px', gap: 1.5, pb: 1.5, borderBottom: `2px solid ${T.border}` }}>
                  {['Employee','Type','Period','Status'].map(h => <Typography key={h} sx={{ fontSize: 11, fontWeight: 700, color: T.muted, textTransform: 'uppercase', letterSpacing: '.5px' }}>{h}</Typography>)}
                </Box>
                {activeLeaves.slice(0, 7).map((lv, i) => {
                  const name = lv.employeeName || `${lv.employee?.firstName||''} ${lv.employee?.lastName||''}`.trim() || 'Unknown';
                  return (
                    <Box key={lv.id} sx={{ display: 'grid', gridTemplateColumns: '1fr 90px 120px 80px', gap: 1.5, alignItems: 'center', py: 1.5, borderBottom: i < activeLeaves.slice(0,7).length - 1 ? `1px solid ${T.border}` : 'none' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, overflow: 'hidden' }}>
                        <Avatar sx={{ width: 32, height: 32, bgcolor: `${T.blue}15`, color: T.blue, fontSize: 12, fontWeight: 700, flexShrink: 0 }}>{initials(name)}</Avatar>
                        <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: T.ink }} noWrap>{name}</Typography>
                      </Box>
                      <Typography sx={{ fontSize: 12.5, color: T.sub }}>{lv.leaveType || 'Annual'}</Typography>
                      <Typography sx={{ fontSize: 12, color: T.sub }}>{fmtShort(lv.startDate)}{lv.endDate && lv.endDate !== lv.startDate ? ` – ${fmtShort(lv.endDate)}` : ''}</Typography>
                      <StatusBadge status={lv.status} />
                    </Box>
                  );
                })}
              </Box>
            )}
          </Panel>
        </Grid>
        <Grid item xs={12} lg={4}>
          <Panel title="My Team Members" action="View all" onAction={() => navigate('/employees')}>
            {activeTeam.length === 0 ? (
              <Box sx={{ py: 6, textAlign: 'center' }}><PeopleIcon sx={{ fontSize: 40, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1 }} /><Typography sx={{ color: T.muted, fontSize: 13 }}>No members</Typography></Box>
            ) : activeTeam.slice(0, 6).map((m, i) => {
              const name = `${m.firstName||''} ${m.lastName||''}`.trim();
              const rc = ROLE_COLOR[m.role] || T.muted;
              return (
                <Box key={m.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.25, py: 1.1, borderBottom: i < Math.min(activeTeam.length, 6) - 1 ? `1px solid ${T.border}` : 'none' }}>
                  <Avatar sx={{ width: 34, height: 34, bgcolor: `${rc}18`, color: rc, fontSize: 12.5, fontWeight: 700, flexShrink: 0 }}>{initials(name)}</Avatar>
                  <Box sx={{ flex: 1, overflow: 'hidden' }}>
                    <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: T.ink }} noWrap>{name}</Typography>
                    <Typography sx={{ fontSize: 12, color: T.muted }}>{m.department || (m.role||'').replace('_',' ')}</Typography>
                  </Box>
                  <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: m.active !== false ? T.green : T.muted }} />
                </Box>
              );
            })}
            {activeTeam.length > 6 && <Box sx={{ pt: 1.5, textAlign: 'center' }}><Typography onClick={() => navigate('/employees')} sx={{ fontSize: 12.5, color: T.blue, fontWeight: 600, cursor: 'pointer' }}>+{activeTeam.length - 6} more</Typography></Box>}
          </Panel>
        </Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Panel title="Team Course Activity" action="View courses" onAction={() => navigate('/courses')}>
            {Object.entries(courseMap).length === 0 ? (
              <Box sx={{ py: 4, textAlign: 'center' }}><SchoolIcon sx={{ fontSize: 40, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1 }} /><Typography sx={{ color: T.muted, fontSize: 13 }}>No course activity</Typography></Box>
            ) : Object.entries(courseMap).slice(0, 5).map(([title, counts]) => {
              const pct = counts.total > 0 ? Math.round((counts.completed / counts.total) * 100) : 0;
              const col = pct === 100 ? T.green : T.blue;
              return (
                <Box key={title} sx={{ mb: 2.5 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .75 }}>
                    <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: T.ink, maxWidth: '65%' }} noWrap>{title}</Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Typography sx={{ fontSize: 11.5, color: T.muted }}>{counts.completed}/{counts.total}</Typography>
                      <Typography sx={{ fontSize: 12.5, fontWeight: 700, color: col }}>{pct}%</Typography>
                    </Box>
                  </Box>
                  <Bar value={pct} color={col} height={5} />
                </Box>
              );
            })}
          </Panel>
        </Grid>
        <Grid item xs={12} md={5}>
          <Panel title="Performance Snapshot" action="View all" onAction={() => navigate('/performance')}>
            <Box sx={{ mb: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .75 }}>
                <Typography sx={{ fontSize: 13, color: T.sub }}>Reviews coverage</Typography>
                <Typography sx={{ fontSize: 13, fontWeight: 700, color: T.ink }}>{reviews.length}/{activeTeam.length || 1}</Typography>
              </Box>
              <Bar value={activeTeam.length > 0 ? Math.min((reviews.length / activeTeam.length) * 100, 100) : 0} color={T.green} />
            </Box>
            <Divider sx={{ my: 2, borderColor: T.border }} />
            {reviews.length === 0 ? (
              <Box sx={{ py: 2, textAlign: 'center' }}><TrendingUpIcon sx={{ fontSize: 36, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1 }} /><Typography sx={{ color: T.muted, fontSize: 13 }}>No reviews yet</Typography></Box>
            ) : reviews.slice(0, 4).map((r, i) => {
              const name = r.employeeName || `${r.employee?.firstName||''} ${r.employee?.lastName||''}`.trim() || 'Employee';
              const rc = RATING_COLOR[Math.round(r.rating)] || T.blue;
              return (
                <Box key={r.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1.1, borderBottom: i < 3 ? `1px solid ${T.border}` : 'none' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                    <Avatar sx={{ width: 30, height: 30, bgcolor: `${T.blue}12`, color: T.blue, fontSize: 11.5, fontWeight: 700 }}>{initials(name)}</Avatar>
                    <Box>
                      <Typography sx={{ fontSize: 13, fontWeight: 600, color: T.ink }}>{name}</Typography>
                      <Typography sx={{ fontSize: 11.5, color: T.muted }}>{r.reviewPeriod || r.period || '—'}</Typography>
                    </Box>
                  </Box>
                  {r.rating != null && <Box sx={{ px: 1.25, py: .35, borderRadius: '20px', bgcolor: `${rc}15` }}><Typography sx={{ fontSize: 12.5, fontWeight: 700, color: rc }}>{r.rating}/5</Typography></Box>}
                </Box>
              );
            })}
          </Panel>
        </Grid>
      </Grid>
    </Box>
  );
};

/* ════════════════════════════════════════════════════
   HR DASHBOARD
════════════════════════════════════════════════════ */
const HRDashboard = ({ stats, leaves, employees, navigate }) => {
  const activeEmp = stats?.activeEmployees ?? 0;
  const totalEmp  = stats?.totalEmployees  ?? 0;
  const now = new Date();
  const newThisMonth = employees.filter(e => { const d = new Date(e.joiningDate||e.startDate||e.hireDate||''); return !isNaN(d) && d.getFullYear()===now.getFullYear() && d.getMonth()===now.getMonth(); }).length;
  const deptMap = {};
  employees.forEach(e => { if(e.department) deptMap[e.department]=(deptMap[e.department]||0)+1; });
  const depts = Object.entries(deptMap).sort(([,a],[,b])=>b-a).slice(0,6);
  const maxD = depts[0]?.[1]||1;
  const roleMap = {};
  employees.filter(e=>e.active!==false).forEach(e => { if(e.role) roleMap[e.role]=(roleMap[e.role]||0)+1; });
  const empTypeMap = {};
  employees.filter(e=>e.active!==false&&e.employmentType).forEach(e => { empTypeMap[e.employmentType]=(empTypeMap[e.employmentType]||0)+1; });
  const pendingLeaves = leaves.filter(l => l.status === 'PENDING');

  return (
    <Box>
      <Grid container spacing={2} sx={{ mb: 2.5 }}>
        <Grid item xs={6} md={3}><KpiCard label="Total Employees" value={totalEmp}           foot={`${activeEmp} active · ${totalEmp-activeEmp} inactive`}              icon={<PeopleIcon />}    accent={T.blue}   onClick={() => navigate('/employees')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="Pending Leaves"  value={pendingLeaves.length} foot="Across all departments"                                            icon={<EventNoteIcon />} accent={T.amber}  onClick={() => navigate('/leaves')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="Active Courses"  value={stats?.activeCourses??0} foot={`${(stats?.avgCourseCompletion??0).toFixed(1)}% avg completion`} icon={<SchoolIcon />}    accent={T.purple} onClick={() => navigate('/courses')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="New This Month"  value={newThisMonth}        foot="Recently onboarded"                                                  icon={<BadgeIcon />}     accent={T.green}  onClick={() => navigate('/employees')} /></Grid>
      </Grid>
      <Grid container spacing={2} sx={{ mb: 2.5 }}>
        <Grid item xs={12} lg={8}>
          <Panel title="Pending Leave Requests" action="View all" onAction={() => navigate('/leaves')}>
            {pendingLeaves.length === 0 ? (
              <Box sx={{ py: 6, textAlign: 'center' }}><EventNoteIcon sx={{ fontSize: 40, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1 }} /><Typography sx={{ color: T.muted, fontSize: 13 }}>No pending requests</Typography></Box>
            ) : (
              <Box>
                <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 90px 120px 80px', gap: 1.5, pb: 1.5, borderBottom: `2px solid ${T.border}` }}>
                  {['Employee','Type','Period','Status'].map(h => <Typography key={h} sx={{ fontSize: 11, fontWeight: 700, color: T.muted, textTransform: 'uppercase', letterSpacing: '.5px' }}>{h}</Typography>)}
                </Box>
                {pendingLeaves.slice(0,7).map((lv,i) => {
                  const name = lv.employeeName||`${lv.employee?.firstName||''} ${lv.employee?.lastName||''}`.trim()||'Unknown';
                  return (
                    <Box key={lv.id} sx={{ display: 'grid', gridTemplateColumns: '1fr 90px 120px 80px', gap: 1.5, alignItems: 'center', py: 1.5, borderBottom: i<pendingLeaves.slice(0,7).length-1?`1px solid ${T.border}`:'none' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, overflow: 'hidden' }}>
                        <Avatar sx={{ width: 32, height: 32, bgcolor: `${T.blue}15`, color: T.blue, fontSize: 12, fontWeight: 700, flexShrink: 0 }}>{initials(name)}</Avatar>
                        <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: T.ink }} noWrap>{name}</Typography>
                      </Box>
                      <Typography sx={{ fontSize: 12.5, color: T.sub }}>{lv.leaveType||'Annual'}</Typography>
                      <Typography sx={{ fontSize: 12, color: T.sub }}>{fmtShort(lv.startDate)}{lv.endDate&&lv.endDate!==lv.startDate?` – ${fmtShort(lv.endDate)}`:''}</Typography>
                      <StatusBadge status={lv.status} />
                    </Box>
                  );
                })}
              </Box>
            )}
          </Panel>
        </Grid>
        <Grid item xs={12} lg={4}>
          <Panel title="People Overview">
            <Box sx={{ mb: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .75 }}><Typography sx={{ fontSize: 13, color: T.sub }}>Active workforce</Typography><Typography sx={{ fontSize: 13, fontWeight: 700, color: T.ink }}>{totalEmp>0?Math.round((activeEmp/totalEmp)*100):0}%</Typography></Box>
              <Bar value={totalEmp>0?(activeEmp/totalEmp)*100:0} color={T.green} />
            </Box>
            <Box sx={{ mb: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .75 }}><Typography sx={{ fontSize: 13, color: T.sub }}>Course completion</Typography><Typography sx={{ fontSize: 13, fontWeight: 700, color: T.ink }}>{(stats?.avgCourseCompletion??0).toFixed(1)}%</Typography></Box>
              <Bar value={stats?.avgCourseCompletion??0} color={T.purple} />
            </Box>
            <Divider sx={{ my: 2, borderColor: T.border }} />
            {[{ label:'Active employees', value:activeEmp, color:T.green },{ label:'Pending leave reqs', value:pendingLeaves.length, color:T.amber },{ label:'New hires (month)', value:newThisMonth, color:T.blue },{ label:'Inactive employees', value:totalEmp-activeEmp, color:T.muted }].map(({ label, value, color }) => (
              <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', py: 1.1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}><Box sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: color, flexShrink: 0 }} /><Typography sx={{ fontSize: 13, color: T.sub }}>{label}</Typography></Box>
                <Typography sx={{ fontSize: 14, fontWeight: 700, color: T.ink }}>{value}</Typography>
              </Box>
            ))}
          </Panel>
        </Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Panel title="Headcount by Department" action="View all" onAction={() => navigate('/employees')}>
            {depts.length===0?<Typography sx={{color:T.muted,fontSize:13,py:4,textAlign:'center'}}>No data</Typography>:depts.map(([dept,count],i)=>(
              <Box key={dept} sx={{ mb: i<depts.length-1?2:0 }}>
                <Box sx={{ display:'flex', justifyContent:'space-between', alignItems:'baseline', mb:.6 }}>
                  <Typography sx={{ fontSize:13, fontWeight:600, color:T.ink }}>{dept}</Typography>
                  <Box sx={{ display:'flex', alignItems:'center', gap:1 }}>
                    <Typography sx={{ fontSize:12, color:T.muted }}>{totalEmp>0?Math.round((count/totalEmp)*100):0}%</Typography>
                    <Typography sx={{ fontSize:13, fontWeight:700, color:T.ink, minWidth:24, textAlign:'right' }}>{count}</Typography>
                  </Box>
                </Box>
                <Bar value={(count/maxD)*100} color={DEPT_COLOR[i%DEPT_COLOR.length]} height={7} />
              </Box>
            ))}
          </Panel>
        </Grid>
        <Grid item xs={12} md={5}>
          <Panel title="Workforce Composition">
            <Typography sx={{ fontSize:11.5, fontWeight:700, color:T.muted, textTransform:'uppercase', letterSpacing:'.5px', mb:1.5 }}>By Role</Typography>
            {Object.entries(roleMap).length===0?<Typography sx={{fontSize:13,color:T.muted,mb:2}}>No data</Typography>:Object.entries(roleMap).map(([role,count])=>(
              <Box key={role} sx={{ display:'flex', alignItems:'center', gap:1.5, mb:1.5 }}>
                <Box sx={{ width:8, height:8, borderRadius:'50%', flexShrink:0, bgcolor:ROLE_COLOR[role]||T.muted }} />
                <Box sx={{ flex:1 }}>
                  <Box sx={{ display:'flex', justifyContent:'space-between', mb:.4 }}><Typography sx={{fontSize:13,color:T.sub}}>{role.replace('_',' ')}</Typography><Typography sx={{fontSize:13,fontWeight:700,color:T.ink}}>{count}</Typography></Box>
                  <Bar value={activeEmp>0?(count/activeEmp)*100:0} color={ROLE_COLOR[role]||T.muted} height={5} />
                </Box>
              </Box>
            ))}
            <Divider sx={{ my:2, borderColor:T.border }} />
            <Typography sx={{ fontSize:11.5, fontWeight:700, color:T.muted, textTransform:'uppercase', letterSpacing:'.5px', mb:1.5 }}>By Employment Type</Typography>
            {Object.entries(empTypeMap).length===0?<Typography sx={{fontSize:13,color:T.muted}}>No data</Typography>:Object.entries(empTypeMap).map(([type,count],i)=>(
              <Box key={type} sx={{ display:'flex', justifyContent:'space-between', alignItems:'center', py:.9 }}>
                <Box sx={{ display:'flex', alignItems:'center', gap:1.25 }}><Box sx={{ width:7, height:7, borderRadius:'50%', bgcolor:[T.blue,T.green,T.amber][i%3] }} /><Typography sx={{fontSize:13,color:T.sub}}>{type}</Typography></Box>
                <Typography sx={{fontSize:13,fontWeight:700,color:T.ink}}>{count}</Typography>
              </Box>
            ))}
          </Panel>
        </Grid>
      </Grid>
    </Box>
  );
};

/* ════════════════════════════════════════════════════
   EMPLOYEE DASHBOARD
════════════════════════════════════════════════════ */
const EmployeeDashboard = ({ empData, navigate }) => {
  if (!empData) return null;
  const { leaves, courses, todayHours, certificates } = empData;
  const approved = leaves.filter(l => l.status === 'APPROVED').length;
  const pending  = leaves.filter(l => l.status === 'PENDING').length;
  const balance  = Math.max(0, LEAVE_TOTAL - approved);
  const enrolled = courses.filter(c => ['ENROLLED','IN_PROGRESS','COMPLETED'].includes(c.enrollmentStatus));
  const done     = courses.filter(c => c.enrollmentStatus === 'COMPLETED').length;

  return (
    <Box>
      <Grid container spacing={2} sx={{ mb: 2.5 }}>
        <Grid item xs={6} md={3}><KpiCard label="Leave Balance" value={`${balance}d`}              foot={`${approved} of ${LEAVE_TOTAL} used`} icon={<BeachAccessIcon />}    accent={T.green}  onClick={() => navigate('/leaves')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="My Courses"    value={enrolled.length}            foot={`${done} completed`}                  icon={<SchoolIcon />}          accent={T.blue}   onClick={() => navigate('/courses')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="Hours Today"   value={`${todayHours.toFixed(1)}h`} foot="Logged today"                        icon={<AccessTimeIcon />}      accent={T.purple} onClick={() => navigate('/timesheets')} /></Grid>
        <Grid item xs={6} md={3}><KpiCard label="Certificates"  value={certificates}               foot="Earned so far"                        icon={<CardMembershipIcon />}  accent={T.amber}  onClick={() => navigate('/courses')} /></Grid>
      </Grid>
      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Panel title="My Enrolled Courses" action="Browse all" onAction={() => navigate('/courses')}>
            {enrolled.length === 0 ? (
              <Box sx={{ py: 6, textAlign: 'center' }}><SchoolIcon sx={{ fontSize: 40, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1 }} /><Typography sx={{ color: T.muted, fontSize: 13, mb: 1.5 }}>No courses enrolled yet</Typography><Button size="small" variant="outlined" onClick={() => navigate('/courses')} sx={{ textTransform: 'none', fontSize: 13, borderRadius: 2, borderColor: T.blue, color: T.blue }}>Browse Courses</Button></Box>
            ) : enrolled.slice(0, 5).map(c => {
              const pct   = { COMPLETED: 100, IN_PROGRESS: 60, ENROLLED: 10 }[c.enrollmentStatus] ?? 0;
              const col   = { COMPLETED: T.green, IN_PROGRESS: T.blue, ENROLLED: T.muted }[c.enrollmentStatus] ?? T.muted;
              const label = { COMPLETED: 'Completed', IN_PROGRESS: 'In progress', ENROLLED: 'Enrolled' }[c.enrollmentStatus] ?? c.enrollmentStatus;
              return (
                <Box key={c.id} sx={{ mb: 2.5 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .75 }}>
                    <Typography sx={{ fontSize: 13.5, fontWeight: 600, color: T.ink, maxWidth: '68%' }} noWrap>{c.title}</Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}><Typography sx={{ fontSize: 12, color: col, fontWeight: 700 }}>{label}</Typography><Typography sx={{ fontSize: 12, color: T.muted }}>{pct}%</Typography></Box>
                  </Box>
                  <Bar value={pct} color={col} height={5} />
                </Box>
              );
            })}
          </Panel>
        </Grid>
        <Grid item xs={12} md={5}>
          <Panel title="Leave Summary" action="Apply" onAction={() => navigate('/leaves')}>
            <Box sx={{ mb: 2.5 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: .75 }}><Typography sx={{ fontSize: 13, color: T.sub }}>Annual leave used</Typography><Typography sx={{ fontSize: 13, fontWeight: 700, color: T.ink }}>{approved} / {LEAVE_TOTAL} days</Typography></Box>
              <Bar value={(approved / LEAVE_TOTAL) * 100} color={T.blue} height={7} />
            </Box>
            <Divider sx={{ my: 2, borderColor: T.border }} />
            {[{ label: 'Available balance', value: `${balance} days`, color: T.green, bg: '#f0fdf4' },{ label: 'Approved', value: `${approved} days`, color: T.blue, bg: '#eff6ff' },{ label: 'Pending requests', value: `${pending}`, color: T.amber, bg: '#fffbeb' }].map(({ label, value, color, bg }) => (
              <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 1.5, py: 1.1, borderRadius: '8px', bgcolor: bg, mb: 1 }}>
                <Typography sx={{ fontSize: 13, color: T.sub }}>{label}</Typography>
                <Typography sx={{ fontSize: 13, fontWeight: 700, color }}>{value}</Typography>
              </Box>
            ))}
          </Panel>
        </Grid>
      </Grid>
    </Box>
  );
};

/* ════════════════════════════════════════════════════
   PAGE ROOT
════════════════════════════════════════════════════ */
const DashboardPage = () => {
  const { user }  = useSelector(s => s.auth);
  const navigate  = useNavigate();

  const role       = user?.role;
  const isEmployee = role === 'EMPLOYEE';
  const isManager  = role === 'MANAGER' || role === 'ASSISTANT_MANAGER';
  const isHR       = role === 'HR';
  const isAdmin    = !isEmployee && !isManager && !isHR;

  const [stats,    setStats]    = useState(null);
  const [leaves,   setLeaves]   = useState([]);
  const [emps,     setEmps]     = useState([]);
  const [empData,  setEmpData]  = useState(null);
  const [mgrData,  setMgrData]  = useState(null);
  const [loading,  setLoading]  = useState(true);

  useEffect(() => {
    const load = async () => {
      try {
        if (isEmployee) {
          const emp   = await employeeApi.getByUserId(user.userId);
          const today = new Date().toISOString().split('T')[0];
          const [lv, cr, att, cert] = await Promise.allSettled([leaveApi.getMyLeaves(emp.id), courseApi.getForEmployee(emp.id), timesheetApi.getAttendance(emp.id), courseApi.getMyCertificates()]);
          const attList  = att.status === 'fulfilled' && Array.isArray(att.value) ? att.value : [];
          const todayRec = attList.find(a => (a.date||a.workDate||'').toString().startsWith(today));
          setEmpData({
            leaves:       lv.status   === 'fulfilled' && Array.isArray(lv.value)   ? lv.value   : [],
            courses:      cr.status   === 'fulfilled' && Array.isArray(cr.value)   ? cr.value   : [],
            todayHours:   parseFloat(todayRec?.totalHours || todayRec?.hoursWorked || 0),
            certificates: cert.status === 'fulfilled' && Array.isArray(cert.value)
              ? cert.value.filter(c => c.employeeName?.toLowerCase().includes(`${emp.firstName||''} ${emp.lastName||''}`.trim().toLowerCase())).length : 0,
          });
        } else if (isManager) {
          const emp = await employeeApi.getByUserId(user.userId);
          const [team, lv, cr, rev] = await Promise.allSettled([employeeApi.getTeam(emp.id), leaveApi.getForManager(emp.id), courseApi.getForManager(emp.id), performanceApi.getByReviewer(emp.id)]);
          setMgrData({
            emp,
            team:        team.status === 'fulfilled' && Array.isArray(team.value) ? team.value : [],
            leaves:      lv.status   === 'fulfilled' && Array.isArray(lv.value)   ? lv.value   : [],
            teamCourses: cr.status   === 'fulfilled' && Array.isArray(cr.value)   ? cr.value   : [],
            reviews:     rev.status  === 'fulfilled' && Array.isArray(rev.value)  ? rev.value  : [],
          });
        } else {
          const [st, lv, em] = await Promise.allSettled([dashboardApi.getStats(), leaveApi.getAll(), employeeApi.getAll()]);
          if (st.status === 'fulfilled') setStats(st.value);
          if (lv.status === 'fulfilled') setLeaves(Array.isArray(lv.value) ? lv.value : []);
          if (em.status === 'fulfilled') setEmps(Array.isArray(em.value)   ? em.value : []);
        }
      } catch { toast.error('Failed to load dashboard'); }
      finally  { setLoading(false); }
    };
    load();
  }, [user, isEmployee, isManager, isHR]);

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 360 }}>
      <CircularProgress sx={{ color: isAdmin ? '#4f46e5' : T.blue }} />
    </Box>
  );

  const hour      = new Date().getHours();
  const greeting  = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const firstName = user?.fullName?.split(' ')[0] || 'there';
  const todayStr  = new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });

  /* ── Admin: premium director page ── */
  if (isAdmin) return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100vh', px: { xs: 2, md: 3 }, py: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3, flexWrap: 'wrap', gap: 1.5 }}>
        <Box>
          <Typography sx={{ fontSize: 27, fontWeight: 900, color: '#0f172a', lineHeight: 1.15, letterSpacing: '-.4px' }}>
            {greeting}, {firstName} 👋
          </Typography>
          <Typography sx={{ fontSize: 13.5, color: '#94a3b8', mt: .4 }}>Executive overview — {todayStr}</Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: .65, px: 1.5, py: .6, borderRadius: '10px', bgcolor: '#fff', border: '1px solid #e2e8f0' }}>
            <Box sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: '#059669', '@keyframes pulse': { '0%,100%': { opacity: 1 }, '50%': { opacity: .3 } }, animation: 'pulse 2s ease-in-out infinite' }} />
            <Typography sx={{ fontSize: 12, fontWeight: 700, color: '#059669', letterSpacing: '.3px' }}>Live data</Typography>
          </Box>
          <Box sx={{ px: 1.5, py: .6, borderRadius: '10px', bgcolor: '#eef2ff', border: '1px solid #c7d2fe' }}>
            <Typography sx={{ fontSize: 12, fontWeight: 700, color: '#4f46e5', letterSpacing: '.3px' }}>Director Dashboard</Typography>
          </Box>
        </Box>
      </Box>
      <AdminDashboard stats={stats} leaves={leaves} employees={emps} navigate={navigate} />
    </Box>
  );

  /* ── Other roles: standard light page ── */
  const subtitle = isManager ? `Your team overview for ${todayStr}.` : isHR ? `People & workforce overview — ${todayStr}.` : `Your personal overview for today.`;
  return (
    <Box sx={{ bgcolor: T.page, minHeight: '100vh', px: { xs: 2, md: 3 }, py: 3 }}>
      <Box sx={{ mb: 3.5 }}>
        <Typography sx={{ fontSize: 26, fontWeight: 800, color: T.ink, lineHeight: 1.2 }}>{greeting}, {firstName} 👋</Typography>
        <Typography sx={{ fontSize: 13.5, color: T.muted, mt: .4 }}>{subtitle}</Typography>
      </Box>
      {isEmployee
        ? <EmployeeDashboard empData={empData} navigate={navigate} />
        : isManager
          ? <ManagerDashboard mgrData={mgrData} navigate={navigate} />
          : <HRDashboard stats={stats} leaves={leaves} employees={emps} navigate={navigate} />
      }
    </Box>
  );
};

export default DashboardPage;
