import React, { useEffect, useState } from 'react';
import {
  Box, Typography, Card, CardContent, Grid, Chip, Switch,
  FormControlLabel, CircularProgress, Alert, Avatar, Divider,
  TextField, InputAdornment, Button, Tooltip,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import LockResetIcon from '@mui/icons-material/LockReset';
import SaveIcon from '@mui/icons-material/Save';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import { permissionsApi } from '../api/permissionsApi';

const ROLE_COLORS = {
  ADMIN: '#ef4444',
  HR: '#8b5cf6',
  MANAGER: '#3b82f6',
  ASSISTANT_MANAGER: '#06b6d4',
  EMPLOYEE: '#10b981',
};

const ALL_MODULES = [
  { path: '/dashboard',    label: 'Dashboard',          always: true },
  { path: '/employees',    label: 'Employees' },
  { path: '/org-chart',    label: 'Organisation' },
  { path: '/attendance',   label: 'Attendance' },
  { path: '/timesheets',   label: 'Timesheets' },
  { path: '/work-reports', label: 'Work Reports' },
  { path: '/leaves',       label: 'Leaves' },
  { path: '/holidays',     label: 'Holidays' },
  { path: '/courses',      label: 'Courses' },
  { path: '/performance',  label: 'Performance' },
  { path: '/reports',      label: 'Reports' },
  { path: '/resources',    label: 'Resources' },
  { path: '/profile',      label: 'My Profile',         always: true },
];

// Granular action permissions inside the Employees module
const EMPLOYEE_ACTIONS = [
  { key: 'emp:add',           label: 'Add Employee' },
  { key: 'emp:upload_resume', label: 'Upload Resume' },
  { key: 'emp:view_detail',   label: 'View Employee Detail' },
  { key: 'emp:edit_profile',  label: 'Edit Employee Profile' },
  { key: 'emp:ex_employees',  label: 'View Ex-Employees' },
];

const ALL_EMP_ACTION_KEYS = EMPLOYEE_ACTIONS.map((a) => a.key);

const DEFAULT_MODULES_BY_ROLE = {
  ADMIN:             [...ALL_MODULES.map((m) => m.path), ...ALL_EMP_ACTION_KEYS],
  HR:                ['/dashboard', '/employees', 'emp:view_detail', '/org-chart', '/courses', '/timesheets', '/attendance', '/leaves', '/holidays', '/work-reports', '/performance', '/resources', '/profile'],
  MANAGER:           ['/dashboard', '/employees', 'emp:view_detail', 'emp:edit_profile', '/org-chart', '/courses', '/timesheets', '/attendance', '/leaves', '/holidays', '/work-reports', '/performance', '/resources', '/profile'],
  ASSISTANT_MANAGER: ['/dashboard', '/employees', 'emp:view_detail', 'emp:edit_profile', '/org-chart', '/courses', '/timesheets', '/attendance', '/leaves', '/holidays', '/work-reports', '/performance', '/resources', '/profile'],
  EMPLOYEE:          ['/dashboard', '/org-chart', '/courses', '/timesheets', '/attendance', '/leaves', '/holidays', '/work-reports', '/performance', '/resources', '/profile'],
};

const RolesPermissions = () => {
  const [users, setUsers]         = useState([]);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState('');
  const [selected, setSelected]   = useState(null); // selected user
  const [modules, setModules]     = useState([]);   // current toggle state
  const [saving, setSaving]       = useState(false);
  const [saved, setSaved]         = useState(false);
  const [search, setSearch]       = useState('');

  useEffect(() => {
    permissionsApi.getAll()
      .then((data) => setUsers(data))
      .catch(() => setError('Failed to load users'))
      .finally(() => setLoading(false));
  }, []);

  const selectUser = (u) => {
    setSelected(u);
    setSaved(false);
    if (u.allowedModules) {
      try { setModules(JSON.parse(u.allowedModules)); }
      catch { setModules(DEFAULT_MODULES_BY_ROLE[u.role] || []); }
    } else {
      setModules(DEFAULT_MODULES_BY_ROLE[u.role] || []);
    }
  };

  const toggle = (path) => {
    setModules((prev) => {
      if (prev.includes(path)) {
        // When disabling Employees module, also remove all action keys
        const filtered = prev.filter((p) => p !== path);
        if (path === '/employees') return filtered.filter((p) => !ALL_EMP_ACTION_KEYS.includes(p));
        return filtered;
      }
      return [...prev, path];
    });
    setSaved(false);
  };

  const toggleAction = (key) => {
    setModules((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]
    );
    setSaved(false);
  };

  const resetToDefault = () => {
    setModules(DEFAULT_MODULES_BY_ROLE[selected.role] || []);
    setSaved(false);
  };

  const save = async () => {
    setSaving(true);
    try {
      const json = JSON.stringify(modules);
      await permissionsApi.update(selected.userId, json);
      setUsers((prev) =>
        prev.map((u) => u.userId === selected.userId ? { ...u, allowedModules: json } : u)
      );
      setSelected((prev) => ({ ...prev, allowedModules: json }));
      setSaved(true);
    } catch {
      setError('Failed to save permissions');
    } finally {
      setSaving(false);
    }
  };

  const filtered = users.filter((u) =>
    u.name.toLowerCase().includes(search.toLowerCase()) ||
    u.email.toLowerCase().includes(search.toLowerCase()) ||
    u.role.toLowerCase().includes(search.toLowerCase())
  );

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}><CircularProgress /></Box>;

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      {/* Header */}
      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
          <AdminPanelSettingsIcon sx={{ color: 'primary.main', fontSize: 28 }} />
          <Typography variant="h5">Roles & Permissions</Typography>
        </Box>
        <Typography variant="body2" color="text.secondary">
          Select an employee to customise which modules they can access. By default employees follow their role's permissions.
        </Typography>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}

      <Grid container spacing={2.5}>
        {/* Left: employee list */}
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent sx={{ p: 2 }}>
              <TextField
                fullWidth size="small" placeholder="Search employees…"
                value={search} onChange={(e) => setSearch(e.target.value)}
                InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
                sx={{ mb: 1.5 }}
              />
              <Box sx={{ maxHeight: 520, overflowY: 'auto' }}>
                {filtered.map((u) => (
                  <Box
                    key={u.userId}
                    onClick={() => selectUser(u)}
                    sx={{
                      display: 'flex', alignItems: 'center', gap: 1.5,
                      p: 1.25, borderRadius: 2, cursor: 'pointer', mb: 0.5,
                      bgcolor: selected?.userId === u.userId ? 'primary.50' : 'transparent',
                      border: selected?.userId === u.userId ? '1.5px solid' : '1.5px solid transparent',
                      borderColor: selected?.userId === u.userId ? 'primary.main' : 'transparent',
                      '&:hover': { bgcolor: 'action.hover' },
                    }}
                  >
                    <Avatar sx={{ width: 36, height: 36, bgcolor: ROLE_COLORS[u.role] || '#64748b', fontSize: 14 }}>
                      {u.name.charAt(0).toUpperCase()}
                    </Avatar>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography variant="body2" fontWeight={600} noWrap>{u.name}</Typography>
                      <Typography variant="caption" color="text.secondary" noWrap>{u.email}</Typography>
                    </Box>
                    <Chip
                      label={u.role}
                      size="small"
                      sx={{ fontSize: '0.65rem', height: 20, bgcolor: ROLE_COLORS[u.role] + '22', color: ROLE_COLORS[u.role], fontWeight: 600 }}
                    />
                  </Box>
                ))}
                {filtered.length === 0 && (
                  <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 3 }}>
                    No employees found
                  </Typography>
                )}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Right: module toggles */}
        <Grid item xs={12} md={8}>
          {!selected ? (
            <Card sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <CardContent sx={{ textAlign: 'center', py: 6 }}>
                <AdminPanelSettingsIcon sx={{ fontSize: 56, color: 'text.disabled', mb: 2 }} />
                <Typography variant="h6" color="text.secondary">Select an employee</Typography>
                <Typography variant="body2" color="text.disabled">Choose from the list to configure their module access</Typography>
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardContent sx={{ p: 3 }}>
                {/* Selected user header */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2.5 }}>
                  <Avatar sx={{ width: 48, height: 48, bgcolor: ROLE_COLORS[selected.role] || '#64748b', fontSize: 18 }}>
                    {selected.name.charAt(0).toUpperCase()}
                  </Avatar>
                  <Box>
                    <Typography variant="h6" fontWeight={700}>{selected.name}</Typography>
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                      <Typography variant="caption" color="text.secondary">{selected.email}</Typography>
                      <Chip label={selected.role} size="small"
                        sx={{ fontSize: '0.65rem', height: 20, bgcolor: ROLE_COLORS[selected.role] + '22', color: ROLE_COLORS[selected.role], fontWeight: 600 }} />
                    </Box>
                  </Box>
                  <Box sx={{ ml: 'auto', display: 'flex', gap: 1 }}>
                    <Tooltip title="Reset to role defaults">
                      <Button variant="outlined" size="small" startIcon={<LockResetIcon />} onClick={resetToDefault}>
                        Reset
                      </Button>
                    </Tooltip>
                    <Button variant="contained" size="small" startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <SaveIcon />}
                      onClick={save} disabled={saving} color={saved ? 'success' : 'primary'}>
                      {saved ? 'Saved!' : 'Save'}
                    </Button>
                  </Box>
                </Box>

                <Divider sx={{ mb: 2.5 }} />

                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Toggle the modules this employee can see in their sidebar. Greyed items are always visible.
                </Typography>

                <Grid container spacing={1.5}>
                  {ALL_MODULES.map((mod) => {
                    const active = modules.includes(mod.path);
                    const isEmp  = mod.path === '/employees';

                    return (
                      <Grid item xs={12} sm={isEmp && active ? 12 : 6} key={mod.path}>
                        <Box sx={{
                          borderRadius: 2, border: '1px solid',
                          borderColor: active ? 'primary.main' : 'divider',
                          overflow: 'hidden',
                          opacity: mod.always ? 0.6 : 1,
                        }}>
                          {/* Module toggle row */}
                          <Box sx={{
                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                            px: 2, py: 1.25,
                            bgcolor: active ? 'primary.50' : 'background.default',
                          }}>
                            <Typography variant="body2" fontWeight={500}>{mod.label}</Typography>
                            <Switch
                              checked={active}
                              onChange={() => !mod.always && toggle(mod.path)}
                              disabled={mod.always}
                              size="small"
                              color="primary"
                            />
                          </Box>

                          {/* Employee sub-permissions — visible only when Employees is ON */}
                          {isEmp && active && (
                            <Box sx={{ bgcolor: '#f8faff', borderTop: '1px solid #e8eaf6', px: 2, py: 1 }}>
                              <Typography variant="caption" sx={{ color: '#6366f1', fontWeight: 700, fontSize: 10.5, letterSpacing: 0.5, textTransform: 'uppercase', display: 'block', mb: 1 }}>
                                Employee Actions
                              </Typography>
                              <Grid container spacing={0.5}>
                                {EMPLOYEE_ACTIONS.map((action) => {
                                  const actionOn = modules.includes(action.key);
                                  return (
                                    <Grid item xs={12} sm={6} md={4} key={action.key}>
                                      <Box sx={{
                                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                        px: 1.5, py: 0.75, borderRadius: 1.5,
                                        border: '1px solid',
                                        borderColor: actionOn ? '#c7d2fe' : '#e2e8f0',
                                        bgcolor: actionOn ? '#eef2ff' : 'white',
                                      }}>
                                        <Typography variant="caption" sx={{ fontSize: 12, fontWeight: actionOn ? 600 : 400, color: actionOn ? '#3730a3' : '#64748b' }}>
                                          {action.label}
                                        </Typography>
                                        <Switch
                                          size="small"
                                          checked={actionOn}
                                          onChange={() => toggleAction(action.key)}
                                          sx={{ '& .MuiSwitch-switchBase.Mui-checked': { color: '#6366f1' },
                                                '& .MuiSwitch-switchBase.Mui-checked + .MuiSwitch-track': { bgcolor: '#6366f1' } }}
                                        />
                                      </Box>
                                    </Grid>
                                  );
                                })}
                              </Grid>
                            </Box>
                          )}
                        </Box>
                      </Grid>
                    );
                  })}
                </Grid>

                <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 2 }}>
                  Changes take effect the next time the employee logs in.
                </Typography>
              </CardContent>
            </Card>
          )}
        </Grid>
      </Grid>
    </Box>
  );
};

export default RolesPermissions;
