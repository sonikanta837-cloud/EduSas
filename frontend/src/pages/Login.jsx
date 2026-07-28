import React, { useState, useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import {
  Box, TextField, Button, Typography,
  InputAdornment, IconButton, CircularProgress, Alert,
  Dialog, DialogTitle, DialogContent, DialogActions
} from '@mui/material';
import EmailIcon        from '@mui/icons-material/Email';
import LockIcon         from '@mui/icons-material/Lock';
import VisibilityIcon   from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import CheckCircleIcon  from '@mui/icons-material/CheckCircle';
import ArrowBackIcon    from '@mui/icons-material/ArrowBack';
import { login, clearError, setEmployeeId } from '../store/authSlice';
import { employeeApi } from '../api/employeeApi';
import api from '../api/axios';

const GOLD      = '#CAA763';
const GOLD_DARK = '#725A2C';
const NAVY_DARK = '#182840';
const CARD_BG   = '#1c3354';

/* Shared field overrides — white bg, dark text */
const darkField = {
  '& .MuiOutlinedInput-root': {
    borderRadius: 1.5,
    overflow: 'hidden',
    backgroundColor: '#ffffff',
    color: '#111827',
    '& fieldset': { borderColor: 'rgba(255,255,255,0.2)' },
    '&:hover fieldset': { borderColor: 'rgba(202,167,99,0.6)' },
    '&.Mui-focused fieldset': { borderColor: GOLD },
    '& input': {
      color: '#111827',
      backgroundColor: '#ffffff',
      '&:-webkit-autofill': {
        WebkitBoxShadow: '0 0 0 100px #ffffff inset',
        WebkitTextFillColor: '#111827',
      },
    },
  },
  '& .MuiInputLabel-root': { color: 'rgba(255,255,255,0.6)' },
  '& .MuiInputLabel-root.Mui-focused': { color: GOLD },
};

const LoginPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { loading, error, token } = useSelector((s) => s.auth);
  const [form, setForm]               = useState({ email: '', password: '' });
  const [showPassword, setShowPassword] = useState(false);

  const [forgotOpen,    setForgotOpen]    = useState(false);
  const [forgotEmail,   setForgotEmail]   = useState('');
  const [forgotLoading, setForgotLoading] = useState(false);
  const [forgotError,   setForgotError]   = useState('');
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
      try {
        const emp = await employeeApi.getByUserId(result.payload.userId);
        dispatch(setEmployeeId(emp.id));
      } catch { /* never block login */ }
    }
  };

  return (
    <Box sx={{
      minHeight: '100vh',
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      background: `linear-gradient(160deg, ${NAVY_DARK} 0%, #0f1e33 50%, #0a1628 100%)`,
      position: 'relative',
      px: 2,
    }}>
      {/* Back to home */}
      <Button
        onClick={() => navigate('/')}
        startIcon={<ArrowBackIcon />}
        sx={{
          position: 'absolute', top: 20, left: 20,
          color: 'rgba(255,255,255,0.45)',
          '&:hover': { color: GOLD, bgcolor: 'transparent' },
          fontWeight: 500, fontSize: '0.82rem',
        }}
      >
        Back to home
      </Button>

      {/* Logo — above card */}
      <Box sx={{ textAlign: 'center', mb: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'center', mb: 0.5 }}>
          <img
            src="/logo_with_white_background.png"
            alt="EmpSAS"
            style={{ height: 60, width: 60, objectFit: 'cover', borderRadius: 14 }}
          />
        </Box>
        <Typography fontWeight={800} sx={{ color: 'white', fontSize: '1.3rem', letterSpacing: -0.3, lineHeight: 1.2 }}>
          Edu<span style={{ color: GOLD }}>SAS</span>
        </Typography>
        <Typography sx={{ color: 'rgba(255,255,255,0.45)', fontSize: '0.7rem', letterSpacing: 0.5, mt: 0.25 }}>
          By SAS KPO Services
        </Typography>
      </Box>

      <Typography variant="body2" sx={{ color: 'rgba(160,200,255,0.75)', mb: 2, letterSpacing: 0.3 }}>
        Sign in to your account
      </Typography>

      {/* Dark card */}
      <Box sx={{
        width: '100%', maxWidth: 420,
        bgcolor: CARD_BG,
        borderRadius: 3,
        p: 4,
        boxShadow: '0 24px 64px rgba(0,0,0,0.55)',
        border: '1px solid rgba(255,255,255,0.07)',
      }}>
        {error && (
          <Alert severity="error" sx={{ mb: 2.5, borderRadius: 2, bgcolor: 'rgba(239,68,68,0.15)', color: '#fca5a5', '& .MuiAlert-icon': { color: '#f87171' } }}>
            {error}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          {/* Email */}
          <Typography variant="caption" sx={{ color: 'rgba(160,200,255,0.75)', fontWeight: 600, mb: 0.75, display: 'block', fontSize: '0.82rem' }}>
            Email address
          </Typography>
          <TextField
            fullWidth type="email" required
            placeholder="you@example.com"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            sx={{ ...darkField, mb: 2.5 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <EmailIcon sx={{ fontSize: 18, color: '#9CA3AF' }} />
                </InputAdornment>
              ),
            }}
          />

          {/* Password row */}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.75 }}>
            <Typography variant="caption" sx={{ color: 'rgba(160,200,255,0.75)', fontWeight: 600, fontSize: '0.82rem' }}>
              Password
            </Typography>
            <Button size="small" onClick={() => setForgotOpen(true)}
              sx={{ color: GOLD, fontWeight: 600, p: 0, minWidth: 0, textTransform: 'none', fontSize: '0.78rem', '&:hover': { color: GOLD_DARK, bgcolor: 'transparent' } }}>
              Forgot password?
            </Button>
          </Box>
          <TextField
            fullWidth required
            placeholder="••••••••"
            type={showPassword ? 'text' : 'password'}
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            sx={{ ...darkField, mb: 3 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <LockIcon sx={{ fontSize: 18, color: '#9CA3AF' }} />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={() => setShowPassword(!showPassword)} edge="end" sx={{ color: '#9CA3AF' }}>
                    {showPassword ? <VisibilityOffIcon sx={{ fontSize: 18 }} /> : <VisibilityIcon sx={{ fontSize: 18 }} />}
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />

          <Button type="submit" fullWidth variant="contained" size="large"
            disabled={loading || !form.email.trim() || !form.password}
            sx={{
              py: 1.5, fontSize: '1rem', fontWeight: 700,
              bgcolor: GOLD, color: '#1a1a1a',
              borderRadius: 2,
              boxShadow: `0 4px 20px rgba(202,167,99,0.35)`,
              '&:hover': { bgcolor: GOLD_DARK, color: 'white', boxShadow: `0 6px 24px rgba(202,167,99,0.5)` },
              '&.Mui-disabled': { bgcolor: 'rgba(202,167,99,0.4)', color: 'rgba(0,0,0,0.4)' },
            }}>
            {loading ? <CircularProgress size={24} color="inherit" /> : 'Sign in'}
          </Button>
        </form>

        <Typography variant="caption" sx={{ display: 'block', textAlign: 'center', mt: 3, color: 'rgba(255,255,255,0.35)', fontSize: '0.75rem' }}>
          Don't have an account?{' '}
          <span style={{ color: GOLD, fontWeight: 600 }}>Contact your administrator</span>
        </Typography>
      </Box>

      {/* Forgot Password Dialog */}
      <Dialog open={forgotOpen} onClose={closeForgot} maxWidth="xs" fullWidth
        PaperProps={{ sx: { borderRadius: 3, bgcolor: CARD_BG, border: '1px solid rgba(255,255,255,0.08)' } }}>
        <DialogTitle fontWeight={700} sx={{ color: 'white' }}>Reset Password</DialogTitle>
        <DialogContent sx={{ px: 3, pb: 1 }}>
          {forgotSuccess ? (
            <Box sx={{ textAlign: 'center', py: 1 }}>
              <CheckCircleIcon sx={{ fontSize: 48, color: 'success.main', mb: 1.5 }} />
              <Typography variant="body1" fontWeight={600} mb={0.5} sx={{ color: 'white' }}>Check your inbox</Typography>
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.6)' }}>
                A password reset link has been sent to <strong style={{ color: GOLD }}>{forgotEmail}</strong>.
                The link expires in 1 hour.
              </Typography>
            </Box>
          ) : (
            <>
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.6)', mb: 2 }}>
                Enter your email and we'll send you a link to reset your password.
              </Typography>
              {forgotError && <Alert severity="error" sx={{ mb: 2 }}>{forgotError}</Alert>}
              <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.6)', display: 'block', mb: 0.75 }}>
                Email Address
              </Typography>
              <TextField
                fullWidth type="email" autoFocus
                placeholder="Enter your email"
                value={forgotEmail}
                onChange={(e) => setForgotEmail(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleForgotSubmit()}
                sx={darkField}
                InputProps={{
                  notched: false,
                  startAdornment: <InputAdornment position="start"><EmailIcon sx={{ color: 'rgba(255,255,255,0.4)', fontSize: 18 }} /></InputAdornment>
                }}
              />
            </>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={closeForgot} sx={{ color: 'rgba(255,255,255,0.5)' }}>
            {forgotSuccess ? 'Close' : 'Cancel'}
          </Button>
          {!forgotSuccess && (
            <Button variant="contained" onClick={handleForgotSubmit}
              disabled={forgotLoading || !forgotEmail.trim()}
              sx={{ bgcolor: GOLD, color: '#1a1a1a', '&:hover': { bgcolor: GOLD_DARK, color: 'white' } }}>
              {forgotLoading ? <CircularProgress size={20} color="inherit" /> : 'Send Reset Link'}
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default LoginPage;
