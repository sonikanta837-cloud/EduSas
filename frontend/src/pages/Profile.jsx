import React, { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Card, CardContent, Grid, Typography, TextField, Button,
  Avatar, Chip, CircularProgress, Divider
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import SchoolIcon from '@mui/icons-material/School';
import { employeeApi } from '../api/employeeApi';
import { courseApi } from '../api/courseApi';
import { toast } from 'react-toastify';

const ProfilePage = () => {
  const { user } = useSelector((s) => s.auth);
  const [employee, setEmployee] = useState(null);
  const [certifications, setCertifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({});

  useEffect(() => {
    Promise.all([
      employeeApi.getByUserId(user.userId),
      courseApi.getForEmployee(user.userId).catch(() => []),
    ]).then(([emp, courses]) => {
      setEmployee(emp);
      setForm(emp);
      setCertifications(courses.filter(c => c.enrollmentStatus === 'COMPLETED'));
    }).catch(() => toast.error('Failed to load profile'))
      .finally(() => setLoading(false));
  }, [user]);

  const handleSave = async () => {
    try {
      const updated = await employeeApi.update(employee.id, form);
      setEmployee(updated);
      setEditing(false);
      toast.success('Profile updated successfully!');
    } catch {
      toast.error('Failed to update profile');
    }
  };

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}><CircularProgress /></Box>;

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} mb={3}>My Profile</Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent sx={{ textAlign: 'center', py: 4 }}>
              <Avatar sx={{ width: 100, height: 100, bgcolor: '#1976d2', fontSize: '2.5rem', mx: 'auto', mb: 2 }}>
                {employee?.firstName?.charAt(0)}
              </Avatar>
              <Typography variant="h5" fontWeight={700}>{employee?.fullName}</Typography>
              <Typography variant="body2" color="text.secondary" mb={1}>{user?.email}</Typography>
              <Chip label={user?.role} color={user?.role === 'ADMIN' ? 'error' : user?.role === 'MANAGER' ? 'warning' : 'primary'} />
              {employee?.department && (
                <Typography variant="body2" color="text.secondary" mt={1}>
                  {employee.position} • {employee.department}
                </Typography>
              )}
              {employee?.managerName && (
                <Typography variant="caption" color="text.secondary">
                  Reports to: {employee.managerName}
                </Typography>
              )}
            </CardContent>
          </Card>

          {certifications.length > 0 && (
            <Card sx={{ mt: 2 }}>
              <CardContent>
                <Typography variant="h6" fontWeight={600} mb={2}>Certifications</Typography>
                {certifications.map((c) => (
                  <Box key={c.id} sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                    <SchoolIcon color="success" fontSize="small" />
                    <Typography variant="body2">{c.title}</Typography>
                  </Box>
                ))}
              </CardContent>
            </Card>
          )}
        </Grid>

        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                <Typography variant="h6" fontWeight={600}>Personal Information</Typography>
                {!editing ? (
                  <Button startIcon={<EditIcon />} onClick={() => setEditing(true)}>Edit</Button>
                ) : (
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button startIcon={<CancelIcon />} onClick={() => { setEditing(false); setForm(employee); }}>Cancel</Button>
                    <Button variant="contained" startIcon={<SaveIcon />} onClick={handleSave}>Save</Button>
                  </Box>
                )}
              </Box>

              {editing ? (
                <Grid container spacing={2}>
                  {[['firstName','First Name'],['lastName','Last Name'],['phone','Phone'],
                    ['department','Department'],['position','Position'],['address','Address']].map(([f,l]) => (
                    <Grid item xs={12} sm={6} key={f}>
                      <TextField fullWidth size="small" label={l} value={form[f] || ''}
                        onChange={(e) => setForm({ ...form, [f]: e.target.value })} />
                    </Grid>
                  ))}
                  <Grid item xs={12}>
                    <TextField fullWidth multiline rows={3} size="small" label="Skills"
                      placeholder="e.g., JavaScript, React, Java, Spring Boot"
                      value={form.skills || ''} onChange={(e) => setForm({ ...form, skills: e.target.value })} />
                  </Grid>
                  <Grid item xs={12}>
                    <TextField fullWidth multiline rows={3} size="small" label="Experience"
                      placeholder="Describe your work experience..."
                      value={form.experience || ''} onChange={(e) => setForm({ ...form, experience: e.target.value })} />
                  </Grid>
                </Grid>
              ) : (
                <Box>
                  <Grid container spacing={2}>
                    {[
                      ['Phone', employee?.phone], ['Department', employee?.department],
                      ['Position', employee?.position], ['Hire Date', employee?.hireDate],
                      ['Date of Birth', employee?.dateOfBirth], ['Address', employee?.address],
                    ].map(([label, value]) => (
                      <Grid item xs={12} sm={6} key={label}>
                        <Typography variant="caption" color="text.secondary">{label}</Typography>
                        <Typography variant="body2" fontWeight={500}>{value || '-'}</Typography>
                      </Grid>
                    ))}
                  </Grid>

                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" fontWeight={600} mb={1}>Skills</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {employee?.skills || 'No skills listed yet. Click Edit to add your skills.'}
                  </Typography>

                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" fontWeight={600} mb={1}>Experience</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {employee?.experience || 'No experience listed yet. Click Edit to add your experience.'}
                  </Typography>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ProfilePage;
