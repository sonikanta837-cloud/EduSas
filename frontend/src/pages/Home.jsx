import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { Box, Typography, Button, Container, Grid, Card, CardContent, Stack, Divider } from '@mui/material';
import PeopleAltIcon          from '@mui/icons-material/PeopleAlt';
import AccessTimeIcon         from '@mui/icons-material/AccessTime';
import EventAvailableIcon     from '@mui/icons-material/EventAvailable';
import BarChartIcon           from '@mui/icons-material/BarChart';
import SecurityIcon           from '@mui/icons-material/Security';
import PersonAddIcon          from '@mui/icons-material/PersonAdd';
import PieChartIcon           from '@mui/icons-material/PieChart';
import TrendingUpIcon         from '@mui/icons-material/TrendingUp';
import BusinessIcon           from '@mui/icons-material/Business';
import VerifiedUserIcon       from '@mui/icons-material/VerifiedUser';
import SupportAgentIcon       from '@mui/icons-material/SupportAgent';
import StarIcon               from '@mui/icons-material/Star';
import ArrowForwardIcon       from '@mui/icons-material/ArrowForward';

// Brand tokens
const GOLD       = '#CAA763';
const GOLD_DARK  = '#725A2C';
const NAVY       = '#213555';
const NAVY_DARK  = '#182840';

// ── Wave SVG background ───────────────────────────────────────────────────────
const WaveBg = () => (
  <Box sx={{ position: 'absolute', inset: 0, overflow: 'hidden', pointerEvents: 'none', zIndex: 0 }}>
    <svg viewBox="0 0 1440 600" preserveAspectRatio="none"
      style={{ position: 'absolute', width: '100%', height: '100%', opacity: 0.18 }}>
      <path d="M0,300 C200,150 400,450 600,300 C800,150 1000,450 1200,300 C1350,200 1440,280 1440,300 L1440,600 L0,600Z"
        fill="none" stroke="#3D5296" strokeWidth="1" />
      <path d="M0,350 C250,200 450,480 700,330 C900,200 1100,460 1440,320 L1440,600 L0,600Z"
        fill="none" stroke="#3D5296" strokeWidth="0.8" />
      <path d="M0,250 C300,100 500,420 800,270 C1000,150 1200,420 1440,260"
        fill="none" stroke="#5A6FAD" strokeWidth="0.6" />
    </svg>
  </Box>
);

// ── Navbar ────────────────────────────────────────────────────────────────────
const Navbar = ({ onLogin, onStart }) => (
  <Box sx={{
    position: 'fixed', top: 0, left: 0, right: 0, zIndex: 100,
    bgcolor: 'rgba(33,53,85,0.97)', backdropFilter: 'blur(10px)',
    borderBottom: '1px solid rgba(202,167,99,0.12)',
    px: { xs: 2, md: 6 }, py: 1.25,
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
  }}>
    {/* Logo */}
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
      <img
        src="/logo_with_white_background.png"
        alt="EmpSAS"
        style={{ height: 42, width: 42, objectFit: 'cover', borderRadius: 8 }}
      />
      <Box>
        <Typography fontWeight={800} sx={{ color: 'white', fontSize: '1.1rem', lineHeight: 1.1, letterSpacing: -0.3 }}>
          Edu<span style={{ color: GOLD }}>SAS</span>
        </Typography>
        <Typography sx={{ color: 'rgba(230,212,168,0.55)', fontSize: '0.55rem', letterSpacing: 1.5, fontWeight: 600 }}>
          BY SAS KPO SERVICES
        </Typography>
      </Box>
    </Box>

    {/* Buttons */}
    <Stack direction="row" spacing={1.5}>
      <Button onClick={onLogin} sx={{
        color: GOLD, border: `1px solid ${GOLD}`,
        px: 2.5, fontWeight: 600, fontSize: '0.875rem',
        '&:hover': { bgcolor: 'rgba(202,167,99,0.1)' },
      }}>
        Log in
      </Button>
      <Button onClick={onStart} variant="contained" endIcon={<ArrowForwardIcon sx={{ fontSize: 16 }} />}
        sx={{
          bgcolor: GOLD, color: '#1a1a1a', fontWeight: 700, fontSize: '0.875rem',
          px: 2.5, '&:hover': { bgcolor: GOLD_DARK, color: 'white' },
          boxShadow: '0 2px 12px rgba(202,167,99,0.35)',
        }}>
        Get Started
      </Button>
    </Stack>
  </Box>
);

// ── Hero feature icon item ────────────────────────────────────────────────────
const HeroFeatureItem = ({ icon, label, sub, noBorder }) => (
  <Box sx={{
    flex: 1, textAlign: 'center', px: { xs: 1, md: 2 },
    borderLeft: noBorder ? 'none' : '1px solid rgba(255,255,255,0.1)',
  }}>
    <Box sx={{ color: GOLD, mb: 1 }}>{React.cloneElement(icon, { sx: { fontSize: 28, color: GOLD } })}</Box>
    <Typography fontWeight={700} sx={{ color: 'white', fontSize: { xs: '0.7rem', md: '0.825rem' }, mb: 0.25 }}>
      {label}
    </Typography>
    <Typography sx={{ color: 'rgba(255,255,255,0.45)', fontSize: { xs: '0.65rem', md: '0.75rem' }, lineHeight: 1.4 }}>
      {sub}
    </Typography>
  </Box>
);

// ── Step circle ───────────────────────────────────────────────────────────────
const StepItem = ({ num, icon, title, desc }) => (
  <Box sx={{ textAlign: 'center', position: 'relative', px: 2 }}>
    <Box sx={{ position: 'relative', display: 'inline-block', mb: 2 }}>
      <Box sx={{
        width: 80, height: 80, borderRadius: '50%',
        bgcolor: '#F3F4F6', display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 2px 12px rgba(0,0,0,0.08)',
      }}>
        {React.cloneElement(icon, { sx: { fontSize: 32, color: '#374151' } })}
      </Box>
      {/* Badge */}
      <Box sx={{
        position: 'absolute', top: -4, right: -4,
        width: 24, height: 24, borderRadius: '50%',
        bgcolor: GOLD, display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Typography sx={{ color: 'white', fontSize: '0.6rem', fontWeight: 800 }}>0{num}</Typography>
      </Box>
    </Box>
    <Typography fontWeight={700} color="#111827" gutterBottom>{title}</Typography>
    <Typography variant="body2" color="#6B7280" lineHeight={1.7} sx={{ maxWidth: 220, mx: 'auto' }}>{desc}</Typography>
  </Box>
);

// ── Stat card ─────────────────────────────────────────────────────────────────
const StatCard = ({ icon, value, label }) => (
  <Card sx={{ border: '1px solid #E5E7EB', bgcolor: 'white', flex: 1 }}>
    <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2, py: '16px !important', px: 2.5 }}>
      <Box sx={{
        width: 44, height: 44, borderRadius: 2, bgcolor: NAVY,
        display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
      }}>
        {React.cloneElement(icon, { sx: { color: GOLD, fontSize: 22 } })}
      </Box>
      <Box>
        <Typography fontWeight={800} color="#111827" fontSize="1.15rem">{value}</Typography>
        <Typography variant="caption" color="#6B7280" fontWeight={500}>{label}</Typography>
      </Box>
    </CardContent>
  </Card>
);

// ── Page ──────────────────────────────────────────────────────────────────────
const HomePage = () => {
  const navigate  = useNavigate();
  const { token } = useSelector((s) => s.auth);

  useEffect(() => {
    if (token) navigate('/dashboard', { replace: true });
  }, [token, navigate]);

  const goLogin    = () => navigate('/login');
  const scrollDown = () => document.getElementById('how-it-works')?.scrollIntoView({ behavior: 'smooth' });

  return (
    <Box sx={{ fontFamily: '"Inter","Roboto",sans-serif' }}>
      <Navbar onLogin={goLogin} onStart={scrollDown} />

      {/* ── HERO ── */}
      <Box sx={{
        minHeight: '100vh',
        background: `linear-gradient(160deg, ${NAVY} 0%, ${NAVY_DARK} 55%, ${NAVY_DARK} 100%)`,
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
        textAlign: 'center', px: 2, pt: 12, pb: 0,
        position: 'relative', overflow: 'hidden',
      }}>
        <WaveBg />

        {/* Gold glow */}
        <Box sx={{
          position: 'absolute', top: '40%', left: '50%', transform: 'translate(-50%,-50%)',
          width: 600, height: 400, borderRadius: '50%',
          background: 'radial-gradient(ellipse, rgba(202,167,99,0.07) 0%, transparent 70%)',
          pointerEvents: 'none',
        }} />

        {/* Badge */}
        <Box sx={{
          display: 'inline-flex', alignItems: 'center',
          border: `1px solid ${GOLD}`, borderRadius: 50,
          px: 2, py: 0.5, mb: 3, position: 'relative', zIndex: 1,
        }}>
          <Typography sx={{ color: GOLD, fontSize: '0.7rem', fontWeight: 700, letterSpacing: 2 }}>
            EMPLOYEE MANAGEMENT SYSTEM
          </Typography>
        </Box>

        {/* Headline */}
        <Typography fontWeight={800} sx={{
          color: 'white', maxWidth: 680, lineHeight: 1.12,
          fontSize: { xs: '2.2rem', sm: '3rem', md: '3.75rem' },
          mb: 2.5, position: 'relative', zIndex: 1,
        }}>
          Manage your workforce<br />
          with <span style={{ color: GOLD }}>confidence</span>
        </Typography>

        {/* Subtitle */}
        <Typography sx={{
          color: 'rgba(255,255,255,0.6)', maxWidth: 520,
          fontSize: { xs: '0.95rem', md: '1.05rem' },
          lineHeight: 1.8, mb: 4.5, position: 'relative', zIndex: 1,
        }}>
          EduSAS brings together employee data, attendance, leave,
          performance reviews, and more — all in one simple platform.
        </Typography>

        {/* CTAs */}
        <Stack direction="row" spacing={2} sx={{ position: 'relative', zIndex: 1 }}>
          <Button variant="contained" size="large" onClick={scrollDown}
            endIcon={<ArrowForwardIcon />}
            sx={{
              bgcolor: GOLD, color: '#1a1a1a', fontWeight: 700, px: 4, py: 1.5,
              fontSize: '1rem', '&:hover': { bgcolor: GOLD_DARK, color: 'white' },
              boxShadow: '0 4px 20px rgba(202,167,99,0.45)',
            }}>
            Get Started
          </Button>
          <Button size="large" onClick={goLogin}
            endIcon={<ArrowForwardIcon />}
            sx={{
              color: 'white', border: '1px solid rgba(255,255,255,0.3)',
              px: 4, py: 1.5, fontSize: '1rem', fontWeight: 600,
              '&:hover': { border: `1px solid ${GOLD}`, color: GOLD, bgcolor: 'rgba(202,167,99,0.06)' },
            }}>
            Log in
          </Button>
        </Stack>

        {/* Feature strip */}
        <Box sx={{
          mt: 8, width: '100%', maxWidth: 900,
          borderTop: '1px solid rgba(255,255,255,0.08)',
          pt: 4, pb: 5,
          display: 'flex', alignItems: 'flex-start', position: 'relative', zIndex: 1,
          flexWrap: { xs: 'wrap', md: 'nowrap' }, gap: { xs: 3, md: 0 },
        }}>
          <HeroFeatureItem noBorder icon={<PeopleAltIcon />}
            label="Employee Management" sub="Centralized employee information" />
          <HeroFeatureItem icon={<AccessTimeIcon />}
            label="Attendance Tracking" sub="Real-time attendance monitoring" />
          <HeroFeatureItem icon={<EventAvailableIcon />}
            label="Leave Management" sub="Easy leave requests and approvals" />
          <HeroFeatureItem icon={<BarChartIcon />}
            label="Performance Management" sub="Track performance and achieve goals" />
          <HeroFeatureItem icon={<SecurityIcon />}
            label="Secure & Reliable" sub="Enterprise-grade security you can trust" />
        </Box>
      </Box>

      {/* ── HOW IT WORKS ── */}
      <Box id="how-it-works" sx={{ bgcolor: 'white', py: 10, px: 2 }}>
        <Container maxWidth="lg">
          {/* Section title */}
          <Box sx={{ textAlign: 'center', mb: 7 }}>
            <Typography variant="h4" fontWeight={800} color="#111827" gutterBottom>
              How it works
            </Typography>
            {/* Underline decoration */}
            <Box sx={{ display: 'flex', justifyContent: 'center', mb: 1.5 }}>
              <Box sx={{ width: 40, height: 3, bgcolor: GOLD, borderRadius: 2 }} />
            </Box>
            <Typography variant="body1" color="#6B7280">
              Get started in just three simple steps.
            </Typography>
          </Box>

          {/* Steps with dashed connectors */}
          <Box sx={{ position: 'relative' }}>
            <Grid container spacing={4} justifyContent="center" alignItems="flex-start">
              <Grid item xs={12} md={4}>
                <StepItem num={1} icon={<PersonAddIcon />}
                  title="Add Your Team"
                  desc="Add and organize your employees and departments." />
              </Grid>
              <Grid item xs={12} md={4} sx={{ position: 'relative' }}>
                {/* Dashed line left */}
                <Box sx={{
                  display: { xs: 'none', md: 'block' },
                  position: 'absolute', top: 40, left: -40, right: '50%',
                  borderTop: '2px dashed #D1D5DB', zIndex: 0,
                }} />
                {/* Dashed line right */}
                <Box sx={{
                  display: { xs: 'none', md: 'block' },
                  position: 'absolute', top: 40, left: '50%', right: -40,
                  borderTop: '2px dashed #D1D5DB', zIndex: 0,
                }} />
                <StepItem num={2} icon={<PieChartIcon />}
                  title="Manage & Track"
                  desc="Track attendance, leaves and performance in real-time." />
              </Grid>
              <Grid item xs={12} md={4}>
                <StepItem num={3} icon={<TrendingUpIcon />}
                  title="Grow Together"
                  desc="Make data-driven decisions and empower your team." />
              </Grid>
            </Grid>
          </Box>

          {/* Stat cards */}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 6 }} flexWrap="wrap">
            <StatCard icon={<BusinessIcon />}    value="500+"    label="Happy Companies" />
            <StatCard icon={<VerifiedUserIcon />} value="99.9%"  label="Uptime & Secure" />
            <StatCard icon={<SupportAgentIcon />} value="24/7"   label="Customer Support" />
            <StatCard icon={<StarIcon />}         value="Trusted" label="By HR Professionals" />
          </Stack>
        </Container>
      </Box>

      <Divider sx={{ borderColor: '#F3F4F6' }} />

      {/* ── CTA BANNER ── */}
      <Box sx={{
        background: `linear-gradient(135deg, ${NAVY} 0%, ${NAVY_DARK} 100%)`,
        py: 9, px: 2, textAlign: 'center',
        borderTop: '1px solid rgba(202,167,99,0.12)',
      }}>
        <Container maxWidth="sm">
          <Typography variant="h4" fontWeight={800} sx={{ color: 'white', mb: 1.5 }}>
            Ready to get started?
          </Typography>
          <Typography sx={{ color: 'rgba(255,255,255,0.55)', mb: 4, lineHeight: 1.8 }}>
            Log in and take full control of your workforce today.
          </Typography>
          <Button variant="contained" size="large" onClick={goLogin}
            endIcon={<ArrowForwardIcon />}
            sx={{
              bgcolor: GOLD, color: '#1a1a1a', fontWeight: 700,
              px: 5, py: 1.5, fontSize: '1rem',
              '&:hover': { bgcolor: GOLD_DARK, color: 'white' },
              boxShadow: '0 6px 28px rgba(202,167,99,0.45)',
            }}>
            Log in to your account
          </Button>
        </Container>
      </Box>

      {/* ── FOOTER ── */}
      <Box sx={{
        bgcolor: NAVY, py: 2.5, textAlign: 'center',
        borderTop: '1px solid rgba(202,167,99,0.08)',
      }}>
        <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.22)' }}>
          © {new Date().getFullYear()} EduSAS · By SAS KPO Services · All rights reserved.
        </Typography>
      </Box>
    </Box>
  );
};

export default HomePage;
