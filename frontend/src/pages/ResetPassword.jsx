import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box, Card, CardContent, TextField, Button, Typography,
  InputAdornment, IconButton, CircularProgress, Alert
} from '@mui/material';
import LockIcon from '@mui/icons-material/Lock';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import api from '../api/axios';

const ResetPasswordPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setLoading(true);
    try {
      await api.post('/auth/reset-password', null, { params: { token, newPassword: password } });
      setSuccess(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid or expired reset link. Please request a new one.');
    } finally {
      setLoading(false);
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
              <img src="/logo.png" alt="EduSAS" style={{ height: 64, width: 64, objectFit: 'contain' }} />
            </Box>
            <Typography variant="h5" fontWeight={800} sx={{ color: '#0f172a' }}>EduSAS</Typography>
            <Typography variant="body2" color="text.secondary" mt={1}>
              {success ? 'Password reset successful' : 'Set your new password'}
            </Typography>
          </Box>

          {!token && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Invalid reset link. Please request a new one.
            </Alert>
          )}

          {success ? (
            <Box sx={{ textAlign: 'center' }}>
              <CheckCircleIcon sx={{ fontSize: 56, color: 'success.main', mb: 2 }} />
              <Typography variant="body1" mb={3}>
                Your password has been reset successfully.
              </Typography>
              <Button fullWidth variant="contained" size="large" onClick={() => navigate('/login')}
                sx={{ py: 1.5 }}>
                Back to Sign In
              </Button>
            </Box>
          ) : (
            <>
              {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
              <form onSubmit={handleSubmit}>
                <TextField
                  fullWidth label="New Password" required
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  sx={{ mb: 2 }}
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
                <TextField
                  fullWidth label="Confirm New Password" required
                  type={showConfirm ? 'text' : 'password'}
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  sx={{ mb: 3 }}
                  InputProps={{
                    startAdornment: <InputAdornment position="start"><LockIcon color="action" /></InputAdornment>,
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton onClick={() => setShowConfirm(!showConfirm)} edge="end">
                          {showConfirm ? <VisibilityOffIcon /> : <VisibilityIcon />}
                        </IconButton>
                      </InputAdornment>
                    )
                  }}
                />
                <Button type="submit" fullWidth variant="contained" size="large"
                  disabled={loading || !token} sx={{ py: 1.5, fontSize: '1rem' }}>
                  {loading ? <CircularProgress size={24} color="inherit" /> : 'Reset Password'}
                </Button>
              </form>
              <Button fullWidth onClick={() => navigate('/login')}
                sx={{ mt: 1.5, color: 'text.secondary' }}>
                Back to Sign In
              </Button>
            </>
          )}
        </CardContent>
      </Card>
    </Box>
  );
};

export default ResetPasswordPage;
