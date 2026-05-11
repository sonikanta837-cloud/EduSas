import React, { useState, useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import {
  Box, Card, CardContent, TextField, Button, Typography,
  InputAdornment, IconButton, CircularProgress, Alert,
  Dialog, DialogTitle, DialogContent, DialogActions
} from '@mui/material';
import EmailIcon from '@mui/icons-material/Email';
import LockIcon from '@mui/icons-material/Lock';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { login, clearError, setEmployeeId } from '../store/authSlice';
import { employeeApi } from '../api/employeeApi';
import { timesheetApi } from '../api/timesheetApi';
import api from '../api/axios';

const LoginPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { loading, error, token } = useSelector((s) => s.auth);
  const [form, setForm] = useState({ email: '', password: '' });
  const [showPassword, setShowPassword] = useState(false);

  const [forgotOpen, setForgotOpen] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotLoading, setForgotLoading] = useState(false);
  const [forgotError, setForgotError] = useState('');
  const [forgotSuccess, setForgotSuccess] = useState(false);

  const handleForgotSubmit = async () => {
    setForgotError('');
    if (!forgotEmail.trim()) { setForgotError('Please enter your email'); return; }
    setForgotLoading(true);
    try {
      await api.post('/auth/forgot-password', null, { params: { email: forgotEmail } });
      setForgotSuccess(true);
    } catch (err) {
      setForgotError(err.response?.data?.message || 'No account found with that email');
    } finally {
      setForgotLoading(false);
    }
  };

  const closeForgot = () => {
    setForgotOpen(false);
    setForgotEmail('');
    setForgotError('');
    setForgotSuccess(false);
  };

  useEffect(() => {
    if (token) navigate('/dashboard', { replace: true });
    return () => dispatch(clearError());
  }, [token, navigate, dispatch]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const result = await dispatch(login(form));
    if (login.fulfilled.match(result)) {
      // Auto check-in: silently record login time for attendance
      try {
        const emp = await employeeApi.getByUserId(result.payload.userId);
        dispatch(setEmployeeId(emp.id));
        await timesheetApi.checkIn(emp.id);
      } catch {
        // Never block login if attendance call fails
      }
    }
  };

  return (
    <Box sx={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 60%, #0f2744 100%)',
    }}>
      <Card sx={{ width: '100%', maxWidth: 420, mx: 2, borderRadius: 3, boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
        <CardContent sx={{ p: 4 }}>
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'center', mb: 1.5 }}>
              <img
                src="/logo.png"
                alt="EduSAS"
                style={{ height: 64, width: 64, objectFit: 'contain' }}
              />
            </Box>
            <Typography variant="h5" fontWeight={800} sx={{ color: '#0f172a', letterSpacing: '-0.01em' }}>
              EduSAS
            </Typography>
            <Typography variant="body2" color="text.secondary" mt={1}>
              Sign in to your account
            </Typography>
          </Box>

          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

          <form onSubmit={handleSubmit}>
            <TextField
              fullWidth label="Email Address" type="email" required
              value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}
              sx={{ mb: 2 }}
              InputProps={{
                startAdornment: <InputAdornment position="start"><EmailIcon color="action" /></InputAdornment>
              }}
            />
            <TextField
              fullWidth label="Password" required
              type={showPassword ? 'text' : 'password'}
              value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })}
              sx={{ mb: 3 }}
              InputProps={{
                startAdornment: <InputAdornment position="start"><LockIcon color="action" /></InputAdornment>,
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton onClick={() => setShowPassword(!showPassword)} edge="end">
                      {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                    </IconButton>
                  </InputAdornment>
                )
              }}
            />
            <Box sx={{ textAlign: 'right', mt: -1.5, mb: 2.5 }}>
              <Button size="small" onClick={() => setForgotOpen(true)}
                sx={{ color: 'primary.main', fontWeight: 500, p: 0, minWidth: 0, textTransform: 'none' }}>
                Forgot password?
              </Button>
            </Box>
            <Button type="submit" fullWidth variant="contained" size="large" disabled={loading}
              sx={{ py: 1.5, fontSize: '1rem' }}>
              {loading ? <CircularProgress size={24} color="inherit" /> : 'Sign In'}
            </Button>
          </form>

          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center', mt: 2 }}>
            Contact your administrator to get access
          </Typography>
        </CardContent>
      </Card>

      {/* Forgot Password Dialog */}
      <Dialog open={forgotOpen} onClose={closeForgot} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>Reset Password</DialogTitle>
        <DialogContent>
          {forgotSuccess ? (
            <Box sx={{ textAlign: 'center', py: 1 }}>
              <CheckCircleIcon sx={{ fontSize: 48, color: 'success.main', mb: 1.5 }} />
              <Typography variant="body1" fontWeight={600} mb={0.5}>Check your inbox</Typography>
              <Typography variant="body2" color="text.secondary">
                A password reset link has been sent to <strong>{forgotEmail}</strong>.
                The link expires in 1 hour.
              </Typography>
            </Box>
          ) : (
            <>
              <Typography variant="body2" color="text.secondary" mb={2}>
                Enter your email address and we'll send you a link to reset your password.
              </Typography>
              {forgotError && <Alert severity="error" sx={{ mb: 2 }}>{forgotError}</Alert>}
              <TextField
                fullWidth label="Email Address" type="email" autoFocus
                value={forgotEmail}
                onChange={(e) => setForgotEmail(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleForgotSubmit()}
                InputProps={{
                  startAdornment: <InputAdornment position="start"><EmailIcon color="action" /></InputAdornment>
                }}
              />
            </>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={closeForgot}>{forgotSuccess ? 'Close' : 'Cancel'}</Button>
          {!forgotSuccess && (
            <Button variant="contained" onClick={handleForgotSubmit} disabled={forgotLoading}>
              {forgotLoading ? <CircularProgress size={20} color="inherit" /> : 'Send Reset Link'}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default LoginPage;
