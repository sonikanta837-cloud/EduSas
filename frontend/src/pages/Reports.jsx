import React, { useEffect, useState, useMemo } from 'react';
import * as XLSX from 'xlsx';
import {
  Box, Card, CardContent, Typography, Grid, Button, Select,
  FormControl, InputLabel, MenuItem, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, CircularProgress,
  Chip, TablePagination,
} from '@mui/material';
import DownloadIcon   from '@mui/icons-material/Download';
import TableChartIcon from '@mui/icons-material/TableChart';
import FilterListIcon from '@mui/icons-material/FilterList';
import { employeeApi }    from '../api/employeeApi';
import { leaveApi }       from '../api/leaveApi';
import { performanceApi } from '../api/performanceApi';
import { toast }          from 'react-toastify';

// ── Month helpers ─────────────────────────────────────────────────────────────
const MONTH_NAMES = [
  'January','February','March','April','May','June',
  'July','August','September','October','November','December',
];

function buildMonthOptions() {
  const opts = [];
  const now  = new Date();
  for (let i = 0; i < 24; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    opts.push({
      year:  d.getFullYear(),
      month: d.getMonth(),
      label: `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}`,
    });
  }
  return opts;
}

function overlapsMonth(startDate, endDate, year, month) {
  const first = new Date(year, month, 1);
  const last  = new Date(year, month + 1, 0);
  const s = new Date(startDate);
  const e = new Date(endDate);
  return s <= last && e >= first;
}

const MONTH_OPTIONS = buildMonthOptions();

// ── Paid / Unpaid logic ───────────────────────────────────────────────────────
const paidLabel = (leaveType) => {
  if (!leaveType) return '—';
  const t = leaveType.toUpperCase();
  if (t.includes('LOP') || t.includes('LWP') || t.includes('WITHOUT PAY') || t.includes('LOSS')) {
    return 'Unpaid';
  }
  return 'Paid';
};

// ── Status colour ─────────────────────────────────────────────────────────────
const statusColor = (s) =>
  s === 'APPROVED' ? 'success' : s === 'REJECTED' ? 'error' : 'warning';

const cell = { fontSize: 13, py: 0.8, px: 1.5 };
const hdr  = { ...cell, fontWeight: 700, bgcolor: '#f1f5f9', whiteSpace: 'nowrap' };

const fmt = (d) => (d ? String(d).slice(0, 10) : '—');

// ── Excel export ──────────────────────────────────────────────────────────────
function exportExcel(rows, monthLabel) {
  const sheetData = rows.map((l, i) => ({
    '#':             i + 1,
    'Employee Name': l.employeeName  || '—',
    'Department':    l.department    || '—',
    'Manager':       l.managerName   || '—',
    'Location':      l.location      || '—',
    'Leave Type':    l.leaveType     || '—',
    'Leave Dates':   `${fmt(l.startDate)} to ${fmt(l.endDate)}`,
    'Total Days':    l.totalDays     ?? '—',
    'Paid / Unpaid': paidLabel(l.leaveType),
    'Status':        l.status        || '—',
    'Reason':        l.reason        || '—',
    'Approved By':   l.approvedByName || '—',
  }));

  const ws = XLSX.utils.json_to_sheet(sheetData);
  ws['!cols'] = [
    { wch: 4  }, { wch: 22 }, { wch: 18 }, { wch: 20 }, { wch: 14 },
    { wch: 16 }, { wch: 26 }, { wch: 10 }, { wch: 13 }, { wch: 12 },
    { wch: 30 }, { wch: 22 },
  ];

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Leave Report');
  XLSX.writeFile(wb, `Leave_Report_${monthLabel.replace(' ', '_')}.xlsx`);
  toast.success('Excel downloaded!');
}

// ── Main page ─────────────────────────────────────────────────────────────────
const ReportsPage = () => {
  const [reportType, setReportType] = useState('monthly-leaves');
  const [data,       setData]       = useState([]);
  const [loading,    setLoading]    = useState(false);
  const [page,       setPage]       = useState(0);
  const [rpp,        setRpp]        = useState(25);

  // Month selector
  const [selMonth, setSelMonth] = useState(0);

  // Leave filters
  const [filterDept,  setFilterDept]  = useState('ALL');
  const [filterMgr,   setFilterMgr]   = useState('ALL');
  const [filterLoc,   setFilterLoc]   = useState('ALL');

  const loadReport = async () => {
    setLoading(true);
    try {
      let result = [];
      switch (reportType) {
        case 'monthly-leaves':
          result = await leaveApi.getAll(); break;
        case 'employees':
          result = await employeeApi.getAll(); break;
        case 'leaves':
          result = await leaveApi.getAll(); break;
        case 'performance':
          result = await performanceApi.getAll(); break;
        default: result = [];
      }
      setData(Array.isArray(result) ? result : []);
    } catch {
      toast.error('Failed to load report');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadReport(); setPage(0); }, [reportType]); // eslint-disable-line

  // ── Month + filters ────────────────────────────────────────────────────────
  const { year: selYear, month: selMonthIdx, label: selLabel } = MONTH_OPTIONS[selMonth];

  // Derive unique filter values from all leaves that match the month
  const allMonthLeaves = useMemo(() => {
    if (reportType !== 'monthly-leaves') return [];
    return data.filter((l) =>
      l.startDate && l.endDate &&
      overlapsMonth(l.startDate, l.endDate, selYear, selMonthIdx)
    );
  }, [data, reportType, selYear, selMonthIdx]);

  const deptOptions = useMemo(() =>
    ['ALL', ...new Set(allMonthLeaves.map((l) => l.department).filter(Boolean))],
    [allMonthLeaves]);

  const mgrOptions = useMemo(() =>
    ['ALL', ...new Set(allMonthLeaves.map((l) => l.managerName).filter(Boolean))],
    [allMonthLeaves]);

  const locOptions = useMemo(() =>
    ['ALL', ...new Set(allMonthLeaves.map((l) => l.location).filter(Boolean))],
    [allMonthLeaves]);

  // Final filtered rows (approved only + 3 filters)
  const monthlyLeaves = useMemo(() => {
    return allMonthLeaves.filter((l) => {
      if (l.status !== 'APPROVED') return false;
      if (filterDept !== 'ALL' && l.department  !== filterDept) return false;
      if (filterMgr  !== 'ALL' && l.managerName !== filterMgr)  return false;
      if (filterLoc  !== 'ALL' && l.location    !== filterLoc)  return false;
      return true;
    });
  }, [allMonthLeaves, filterDept, filterMgr, filterLoc]);

  const displayRows = reportType === 'monthly-leaves' ? monthlyLeaves : data;
  const pageRows    = displayRows.slice(page * rpp, (page + 1) * rpp);

  // Reset filter dropdowns when month changes
  const handleMonthChange = (idx) => {
    setSelMonth(idx);
    setFilterDept('ALL');
    setFilterMgr('ALL');
    setFilterLoc('ALL');
    setPage(0);
  };

  // ── CSV export (other reports) ─────────────────────────────────────────────
  const exportCSV = () => {
    if (displayRows.length === 0) return;
    const headers = Object.keys(displayRows[0]).join(',');
    const rows = displayRows.map((row) =>
      Object.values(row).map((v) => `"${v ?? ''}"`).join(',')
    );
    const csv  = [headers, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href = url; a.download = `${reportType}-report.csv`; a.click();
    URL.revokeObjectURL(url);
    toast.success('CSV downloaded!');
  };

  // ── Table renderers ────────────────────────────────────────────────────────
  const renderMonthlyLeaves = () => (
    <Table size="small">
      <TableHead>
        <TableRow>
          {['#','Employee Name','Department','Manager','Leave Type',
            'Leave Dates','Total Days','Paid / Unpaid','Status'].map((h) => (
            <TableCell key={h} sx={hdr}>{h}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {pageRows.length === 0 ? (
          <TableRow>
            <TableCell colSpan={9} align="center" sx={{ py: 4, color: 'text.secondary' }}>
              No approved leaves found for {selLabel}
            </TableCell>
          </TableRow>
        ) : pageRows.map((l, i) => (
          <TableRow key={l.id} hover sx={{ bgcolor: (page * rpp + i) % 2 === 0 ? 'white' : '#f8fafc' }}>
            <TableCell sx={cell}>{page * rpp + i + 1}</TableCell>
            <TableCell sx={{ ...cell, fontWeight: 600 }}>{l.employeeName || '—'}</TableCell>
            <TableCell sx={cell}>{l.department  || '—'}</TableCell>
            <TableCell sx={cell}>{l.managerName || '—'}</TableCell>
            <TableCell sx={cell}>
              <Chip label={l.leaveType} size="small" variant="outlined" sx={{ fontSize: 11 }} />
            </TableCell>
            <TableCell sx={{ ...cell, whiteSpace: 'nowrap' }}>
              {fmt(l.startDate)} — {fmt(l.endDate)}
            </TableCell>
            <TableCell sx={{ ...cell, textAlign: 'center' }}>{l.totalDays ?? '—'}</TableCell>
            <TableCell sx={cell}>
              <Chip
                label={paidLabel(l.leaveType)}
                size="small"
                color={paidLabel(l.leaveType) === 'Paid' ? 'success' : 'warning'}
                variant="outlined"
                sx={{ fontSize: 11 }}
              />
            </TableCell>
            <TableCell sx={cell}>
              <Chip label={l.status} size="small" color={statusColor(l.status)} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );

  const renderEmployees = () => (
    <Table size="small">
      <TableHead>
        <TableRow>
          {['Name','Email','Department','Position','Role','Status'].map((h) => (
            <TableCell key={h} sx={hdr}>{h}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {pageRows.map((e, i) => (
          <TableRow key={e.id} hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
            <TableCell sx={{ ...cell, fontWeight: 600 }}>{e.fullName}</TableCell>
            <TableCell sx={cell}>{e.email}</TableCell>
            <TableCell sx={cell}>{e.department || '—'}</TableCell>
            <TableCell sx={cell}>{e.position   || '—'}</TableCell>
            <TableCell sx={cell}><Chip label={e.role} size="small" /></TableCell>
            <TableCell sx={cell}>
              <Chip label={e.active ? 'Active' : 'Inactive'} size="small"
                color={e.active ? 'success' : 'default'} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );

  const renderLeaves = () => (
    <Table size="small">
      <TableHead>
        <TableRow>
          {['Employee','Department','Manager','Type','Start','End','Days','Paid / Unpaid','Status'].map((h) => (
            <TableCell key={h} sx={hdr}>{h}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {pageRows.map((l, i) => (
          <TableRow key={l.id} hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
            <TableCell sx={{ ...cell, fontWeight: 600 }}>{l.employeeName}</TableCell>
            <TableCell sx={cell}>{l.department  || '—'}</TableCell>
            <TableCell sx={cell}>{l.managerName || '—'}</TableCell>
            <TableCell sx={cell}>{l.leaveType}</TableCell>
            <TableCell sx={cell}>{fmt(l.startDate)}</TableCell>
            <TableCell sx={cell}>{fmt(l.endDate)}</TableCell>
            <TableCell sx={cell}>{l.totalDays}</TableCell>
            <TableCell sx={cell}>
              <Chip label={paidLabel(l.leaveType)} size="small"
                color={paidLabel(l.leaveType) === 'Paid' ? 'success' : 'warning'}
                variant="outlined" sx={{ fontSize: 11 }} />
            </TableCell>
            <TableCell sx={cell}>
              <Chip label={l.status} size="small" color={statusColor(l.status)} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );

  const renderPerformance = () => (
    <Table size="small">
      <TableHead>
        <TableRow>
          {['Employee','Reviewer','Rating','Period','Date'].map((h) => (
            <TableCell key={h} sx={hdr}>{h}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {pageRows.map((r, i) => (
          <TableRow key={r.id} hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
            <TableCell sx={{ ...cell, fontWeight: 600 }}>{r.employeeName}</TableCell>
            <TableCell sx={cell}>{r.reviewerName}</TableCell>
            <TableCell sx={cell}>
              <Chip label={`${r.rating}/5`} size="small"
                color={r.rating >= 4 ? 'success' : r.rating >= 3 ? 'warning' : 'error'} />
            </TableCell>
            <TableCell sx={cell}>{r.reviewPeriod}</TableCell>
            <TableCell sx={cell}>{r.reviewDate}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );

  const renderTable = () => {
    switch (reportType) {
      case 'monthly-leaves': return renderMonthlyLeaves();
      case 'employees':      return renderEmployees();
      case 'leaves':         return renderLeaves();
      case 'performance':    return renderPerformance();
      default:               return null;
    }
  };

  const reportLabel = {
    'monthly-leaves': 'Monthly Leave Report',
    'employees':      'Employee Report',
    'leaves':         'All Leave Report',
    'performance':    'Performance Report',
  }[reportType];

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} mb={3}>Reports</Typography>

      {/* ── Controls card ── */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2} alignItems="center">

            {/* Report type */}
            <Grid item xs={12} sm={3}>
              <FormControl fullWidth size="small">
                <InputLabel>Report Type</InputLabel>
                <Select value={reportType} label="Report Type"
                  onChange={(e) => { setReportType(e.target.value); setPage(0); }}>
                  <MenuItem value="monthly-leaves">Monthly Leave Report</MenuItem>
                  <MenuItem value="employees">Employee Report</MenuItem>
                  <MenuItem value="leaves">All Leave Report</MenuItem>
                  <MenuItem value="performance">Performance Report</MenuItem>
                </Select>
              </FormControl>
            </Grid>

            {/* Month picker */}
            {reportType === 'monthly-leaves' && (
              <Grid item xs={12} sm={2}>
                <FormControl fullWidth size="small">
                  <InputLabel>Month</InputLabel>
                  <Select value={selMonth} label="Month"
                    onChange={(e) => handleMonthChange(e.target.value)}>
                    {MONTH_OPTIONS.map((opt, idx) => (
                      <MenuItem key={idx} value={idx}>{opt.label}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            )}

            {/* Department filter */}
            {reportType === 'monthly-leaves' && (
              <Grid item xs={12} sm={2}>
                <FormControl fullWidth size="small">
                  <InputLabel>Department</InputLabel>
                  <Select value={filterDept} label="Department"
                    onChange={(e) => { setFilterDept(e.target.value); setPage(0); }}>
                    {deptOptions.map((d) => (
                      <MenuItem key={d} value={d}>{d === 'ALL' ? 'All Departments' : d}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            )}

            {/* Manager filter */}
            {reportType === 'monthly-leaves' && (
              <Grid item xs={12} sm={2}>
                <FormControl fullWidth size="small">
                  <InputLabel>Manager</InputLabel>
                  <Select value={filterMgr} label="Manager"
                    onChange={(e) => { setFilterMgr(e.target.value); setPage(0); }}>
                    {mgrOptions.map((m) => (
                      <MenuItem key={m} value={m}>{m === 'ALL' ? 'All Managers' : m}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            )}

            {/* Location filter */}
            {reportType === 'monthly-leaves' && (
              <Grid item xs={12} sm={2}>
                <FormControl fullWidth size="small">
                  <InputLabel>Location</InputLabel>
                  <Select value={filterLoc} label="Location"
                    onChange={(e) => { setFilterLoc(e.target.value); setPage(0); }}>
                    {locOptions.map((l) => (
                      <MenuItem key={l} value={l}>{l === 'ALL' ? 'All Locations' : l}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            )}

            {/* Export button */}
            <Grid item sx={{ ml: reportType === 'monthly-leaves' ? 0 : 'auto', display: 'flex', gap: 1 }}>
              {reportType === 'monthly-leaves' ? (
                <Button variant="contained" startIcon={<TableChartIcon />}
                  onClick={() => exportExcel(monthlyLeaves, selLabel)}
                  disabled={monthlyLeaves.length === 0}
                  sx={{ bgcolor: '#217346', '&:hover': { bgcolor: '#1a5c38' }, textTransform: 'none' }}>
                  Export Excel
                </Button>
              ) : (
                <Button variant="outlined" startIcon={<DownloadIcon />}
                  onClick={exportCSV} disabled={displayRows.length === 0}>
                  Export CSV
                </Button>
              )}
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* ── Table card ── */}
      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Box>
              <Typography variant="h6" fontWeight={600}>{reportLabel}</Typography>
              {reportType === 'monthly-leaves' && (
                <Typography variant="body2" color="text.secondary">
                  Approved leaves overlapping {selLabel}
                  {(filterDept !== 'ALL' || filterMgr !== 'ALL' || filterLoc !== 'ALL') && (
                    <Box component="span" sx={{ ml: 1 }}>
                      <FilterListIcon sx={{ fontSize: 14, verticalAlign: 'middle', mr: 0.3 }} />
                      Filtered
                    </Box>
                  )}
                </Typography>
              )}
            </Box>
            <Chip
              label={`${displayRows.length} record${displayRows.length !== 1 ? 's' : ''}`}
              size="small" color="primary"
            />
          </Box>

          <TableContainer sx={{ overflowX: 'auto' }}>
            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress />
              </Box>
            ) : renderTable()}
          </TableContainer>

          {displayRows.length > 0 && (
            <TablePagination
              component="div"
              count={displayRows.length}
              page={page}
              onPageChange={(_, p) => setPage(p)}
              rowsPerPage={rpp}
              onRowsPerPageChange={(e) => { setRpp(parseInt(e.target.value, 10)); setPage(0); }}
              rowsPerPageOptions={[10, 25, 50, 100]}
            />
          )}
        </CardContent>
      </Card>
    </Box>
  );
};

export default ReportsPage;
