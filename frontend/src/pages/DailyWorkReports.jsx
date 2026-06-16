import React, { useEffect, useState, useCallback } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, Paper, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, CircularProgress,
  Button, Chip, Tooltip, TextField, InputAdornment,
} from '@mui/material';
import DownloadIcon       from '@mui/icons-material/Download';
import RefreshIcon        from '@mui/icons-material/Refresh';
import AccessTimeIcon     from '@mui/icons-material/AccessTime';
import TimerOffIcon       from '@mui/icons-material/TimerOff';
import SearchIcon         from '@mui/icons-material/Search';
import WorkIcon           from '@mui/icons-material/Work';
import dayjs from 'dayjs';
import { workReportApi } from '../api/workReportApi';
import { toast } from 'react-toastify';

const fmtTime = (t) => (t ? String(t).substring(0, 5) : '—');

const StatCard = ({ icon, label, value, color }) => (
  <Paper sx={{ p: 2, flex: 1, border: '1px solid #e2e8f0', display: 'flex', alignItems: 'center', gap: 2 }}>
    <Box sx={{ width: 44, height: 44, borderRadius: '12px', bgcolor: color + '15',
      display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      {React.cloneElement(icon, { sx: { color, fontSize: 22 } })}
    </Box>
    <Box>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography fontWeight={700} fontSize={18}>{value}</Typography>
    </Box>
  </Paper>
);

const fmtAvg = (reports, field) => {
  const valid = reports.filter(r => r[field] != null && r[field] > 0);
  if (!valid.length) return '—';
  const avg = Math.round(valid.reduce((s, r) => s + r[field], 0) / valid.length);
  return `${Math.floor(avg / 60)}h ${String(avg % 60).padStart(2, '0')}m`;
};

const DailyWorkReports = () => {
  const { user } = useSelector((s) => s.auth);
  const isManagerOrAbove = ['ADMIN', 'HR', 'MANAGER', 'ASSISTANT_MANAGER'].includes(user?.role);
  const canGenerate      = ['ADMIN', 'HR'].includes(user?.role);

  const [fromDate,    setFromDate]    = useState(dayjs().startOf('month').format('YYYY-MM-DD'));
  const [toDate,      setToDate]      = useState(dayjs().format('YYYY-MM-DD'));
  const [reports,     setReports]     = useState([]);
  const [loading,     setLoading]     = useState(true);
  const [generating,  setGenerating]  = useState(false);
  const [search,      setSearch]      = useState('');

  const start = fromDate;
  const end   = toDate;

  const loadReports = useCallback(async () => {
    setLoading(true);
    try {
      const data = isManagerOrAbove
        ? await workReportApi.getTeamReports(start, end)
        : await workReportApi.getMyReports(start, end);
      setReports(Array.isArray(data) ? data : []);
    } catch {
      toast.error('Failed to load work reports');
    } finally {
      setLoading(false);
    }
  }, [start, end, isManagerOrAbove]);

  useEffect(() => { loadReports(); }, [loadReports]);

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const isToday = fromDate === toDate && toDate === dayjs().format('YYYY-MM-DD');
      if (isToday) {
        await workReportApi.generateReports(toDate);
      } else {
        await workReportApi.generateReportsForRange(fromDate, toDate);
      }
      const days = dayjs(toDate).diff(dayjs(fromDate), 'day') + 1;
      toast.success(`Reports generated for ${days === 1 ? dayjs(fromDate).format('DD MMM YYYY') : `${days} days`}`);
      loadReports();
    } catch {
      toast.error('Failed to generate reports');
    } finally {
      setGenerating(false);
    }
  };

  const handleExportCsv = async () => {
    try {
      const blob = await workReportApi.exportCsv(start, end);
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href     = url;
      a.download = `work-report-${fromDate}-to-${toDate}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('Export failed');
    }
  };

  const filtered = reports.filter(r =>
    !search || r.employeeName?.toLowerCase().includes(search.toLowerCase()) ||
    r.department?.toLowerCase().includes(search.toLowerCase())
  );

  const breakColor = (mins) => {
    if (mins == null) return 'inherit';
    if (mins > 90) return '#dc2626';
    if (mins > 60) return '#d97706';
    return '#16a34a';
  };

  return (
    <Box>
      {/* ── Header ── */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 1.5 }}>
        <Typography variant="h5" fontWeight={700}>Daily Work Reports</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          {canGenerate && (
            <Button size="small" variant="outlined" startIcon={<RefreshIcon />}
              onClick={handleGenerate} disabled={generating}
              sx={{ textTransform: 'none', borderRadius: '8px' }}>
              {generating ? 'Generating…'
                : fromDate === toDate
                  ? `Generate ${dayjs(fromDate).format('DD MMM')}`
                  : `Generate ${dayjs(fromDate).format('DD MMM')} – ${dayjs(toDate).format('DD MMM')}`}
            </Button>
          )}
          {isManagerOrAbove && (
            <Button size="small" variant="contained" startIcon={<DownloadIcon />}
              onClick={handleExportCsv}
              sx={{ textTransform: 'none', borderRadius: '8px' }}>
              Export CSV
            </Button>
          )}
        </Box>
      </Box>

      {/* ── Date range filter ── */}
      <Paper sx={{ display: 'flex', alignItems: 'center', gap: 2, px: 2.5, py: 1.5,
        mb: 2.5, border: '1px solid #e2e8f0', flexWrap: 'wrap' }}>
        <TextField label="From" type="date" size="small"
          value={fromDate} onChange={e => setFromDate(e.target.value)}
          InputLabelProps={{ shrink: true }}
          inputProps={{ max: toDate }}
          sx={{ width: 160 }} />
        <TextField label="To" type="date" size="small"
          value={toDate} onChange={e => setToDate(e.target.value)}
          InputLabelProps={{ shrink: true }}
          inputProps={{ min: fromDate, max: dayjs().format('YYYY-MM-DD') }}
          sx={{ width: 160 }} />
        <Box sx={{ display: 'flex', gap: 1, ml: 'auto', flexWrap: 'wrap' }}>
          {[
            { label: 'Today',      from: dayjs().format('YYYY-MM-DD'),                        to: dayjs().format('YYYY-MM-DD') },
            { label: 'This Week',  from: dayjs().startOf('week').format('YYYY-MM-DD'),        to: dayjs().format('YYYY-MM-DD') },
            { label: 'This Month', from: dayjs().startOf('month').format('YYYY-MM-DD'),       to: dayjs().format('YYYY-MM-DD') },
            { label: 'Last Month', from: dayjs().subtract(1,'month').startOf('month').format('YYYY-MM-DD'),
                                   to:   dayjs().subtract(1,'month').endOf('month').format('YYYY-MM-DD') },
          ].map(({ label, from, to }) => (
            <Button key={label} size="small" variant="outlined"
              onClick={() => { setFromDate(from); setToDate(to); }}
              sx={{ textTransform: 'none', borderRadius: '8px', fontSize: 12,
                borderColor: fromDate === from && toDate === to ? '#1e3a5f' : undefined,
                fontWeight: fromDate === from && toDate === to ? 700 : 400 }}>
              {label}
            </Button>
          ))}
        </Box>
      </Paper>

      {/* ── Summary cards ── */}
      {!loading && reports.length > 0 && (
        <Box sx={{ display: 'flex', gap: 2, mb: 2.5, flexWrap: 'wrap' }}>
          <StatCard icon={<WorkIcon />}       label="Avg Office Hours"  value={fmtAvg(reports, 'totalOfficeMinutes')} color="#1d4ed8" />
          <StatCard icon={<AccessTimeIcon />} label="Avg Active Hours"  value={fmtAvg(reports, 'activeMinutes')}      color="#16a34a" />
          <StatCard icon={<TimerOffIcon />}   label="Avg Break Duration" value={fmtAvg(reports, 'breakMinutes')}      color="#c2410c" />
        </Box>
      )}

      {/* ── Search (manager/HR/admin only) ── */}
      {isManagerOrAbove && (
        <Box sx={{ mb: 2 }}>
          <TextField size="small" placeholder="Search by employee or department…"
            value={search} onChange={e => setSearch(e.target.value)}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon sx={{ fontSize: 18, color: '#94a3b8' }} /></InputAdornment> }}
            sx={{ width: 300, '& .MuiOutlinedInput-root': { borderRadius: '8px' } }} />
        </Box>
      )}

      {/* ── Table ── */}
      <TableContainer component={Paper} sx={{ border: '1px solid #e2e8f0', borderRadius: 2 }}>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}><CircularProgress /></Box>
        ) : filtered.length === 0 ? (
          <Box sx={{ py: 6, textAlign: 'center', color: '#94a3b8', fontSize: 13 }}>
            No reports found for {dayjs(fromDate).format('DD MMM YYYY')} – {dayjs(toDate).format('DD MMM YYYY')}.<br />
            {canGenerate && 'Click "Generate Today" to create today\'s report.'}
          </Box>
        ) : (
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                {(isManagerOrAbove ? ['Employee', 'Dept'] : []).concat(
                  ['Date', 'First Login', 'Last Logout', 'Office Hours', 'Active Hours', 'Break Duration', 'Sessions', 'Generated']
                ).map(h => (
                  <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12, color: '#374151',
                    bgcolor: '#f8fafc', py: 1.5 }}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map(r => (
                <TableRow key={r.id} hover sx={{ '&:last-child td': { border: 0 } }}>
                  {isManagerOrAbove && (
                    <>
                      <TableCell>
                        <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{r.employeeName}</Typography>
                        <Typography sx={{ fontSize: 11, color: '#94a3b8' }}>{r.employeeCode}</Typography>
                      </TableCell>
                      <TableCell sx={{ fontSize: 12, color: '#64748b' }}>{r.department}</TableCell>
                    </>
                  )}
                  <TableCell sx={{ fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap' }}>
                    {dayjs(r.reportDate).format('ddd, DD MMM YYYY')}
                  </TableCell>
                  <TableCell sx={{ fontSize: 13, color: '#16a34a', fontWeight: 600 }}>
                    {fmtTime(r.firstLoginTime)}
                  </TableCell>
                  <TableCell sx={{ fontSize: 13, color: '#dc2626', fontWeight: 600 }}>
                    {fmtTime(r.lastLogoutTime)}
                  </TableCell>
                  <TableCell>
                    <Chip label={r.totalOfficeFormatted} size="small"
                      sx={{ bgcolor: '#eff6ff', color: '#1d4ed8', fontWeight: 700, fontSize: 11 }} />
                  </TableCell>
                  <TableCell>
                    <Chip label={r.activeFormatted} size="small"
                      sx={{ bgcolor: '#f0fdf4', color: '#16a34a', fontWeight: 700, fontSize: 11 }} />
                  </TableCell>
                  <TableCell>
                    <Tooltip title={r.breakMinutes > 75 ? 'Exceeds threshold (1h 15m)' : ''}>
                      <Chip label={r.breakFormatted} size="small" sx={{
                        bgcolor: r.breakMinutes > 90 ? '#fee2e2' : r.breakMinutes > 60 ? '#fff7ed' : '#f0fdf4',
                        color: breakColor(r.breakMinutes), fontWeight: 700, fontSize: 11,
                      }} />
                    </Tooltip>
                  </TableCell>
                  <TableCell sx={{ fontSize: 13, textAlign: 'center' }}>{r.sessionCount}</TableCell>
                  <TableCell sx={{ fontSize: 11, color: '#94a3b8', whiteSpace: 'nowrap' }}>
                    {r.generatedAt ? dayjs(r.generatedAt).format('DD MMM, HH:mm') : '—'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TableContainer>
    </Box>
  );
};

export default DailyWorkReports;
