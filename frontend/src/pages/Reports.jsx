import React, { useEffect, useState, useMemo } from 'react';
import * as XLSX from 'xlsx';
import {
  Box, Card, CardContent, Typography, Grid, Button, Select,
  FormControl, InputLabel, MenuItem, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, CircularProgress,
  Chip, TablePagination, TextField, InputAdornment,
} from '@mui/material';
import DownloadIcon   from '@mui/icons-material/Download';
import TableChartIcon from '@mui/icons-material/TableChart';
import FilterListIcon from '@mui/icons-material/FilterList';
import SearchIcon     from '@mui/icons-material/Search';
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

// Count weekday overlap of a leave within a month
function countWeekdayOverlap(startStr, endStr, year, month) {
  if (!startStr || !endStr) return 0;
  const first = new Date(year, month, 1);
  const last  = new Date(year, month + 1, 0);
  const s = new Date(Math.max(new Date(startStr), first));
  const e = new Date(Math.min(new Date(endStr),   last));
  let count = 0;
  const cur = new Date(s);
  while (cur <= e) {
    const dow = cur.getDay();
    if (dow !== 0 && dow !== 6) count++;
    cur.setDate(cur.getDate() + 1);
  }
  return count;
}

// Count Mon–Fri in a month
function workingDaysInMonth(year, month) {
  const days = new Date(year, month + 1, 0).getDate();
  let count = 0;
  for (let d = 1; d <= days; d++) {
    const dow = new Date(year, month, d).getDay();
    if (dow !== 0 && dow !== 6) count++;
  }
  return count;
}

function isPaidLeave(leaveType) {
  if (!leaveType) return true;
  const t = leaveType.toUpperCase();
  return !(t.includes('LOP') || t.includes('LWP') || t.includes('WITHOUT PAY') || t.includes('LOSS OF PAY'));
}

const MONTH_OPTIONS = buildMonthOptions();

// ── Paid / Unpaid label ───────────────────────────────────────────────────────
const paidLabel = (leaveType) => {
  if (!leaveType) return '—';
  const t = leaveType.toUpperCase();
  if (t.includes('LOP') || t.includes('LWP') || t.includes('WITHOUT PAY') || t.includes('LOSS')) return 'Unpaid';
  return 'Paid';
};

const statusColor = (s) =>
  s === 'APPROVED' ? 'success' : s === 'REJECTED' ? 'error' : 'warning';

const cell = { fontSize: 13, py: 0.8, px: 1.5 };
const hdr  = { ...cell, fontWeight: 700, bgcolor: '#f1f5f9', whiteSpace: 'nowrap' };

const fmt     = (d) => (d ? String(d).slice(0, 10) : '—');
const fmtCurr = (n) => n != null ? `₹${Number(n).toLocaleString('en-IN')}` : '—';

// ── Excel export — monthly leaves ─────────────────────────────────────────────
function exportLeaveExcel(rows, monthLabel) {
  const sheetData = rows.map((l, i) => ({
    '#':             i + 1,
    'Employee Name': l.employeeName   || '—',
    'Department':    l.department     || '—',
    'Manager':       l.managerName    || '—',
    'Location':      l.location       || '—',
    'Leave Type':    l.leaveType      || '—',
    'Leave Dates':   `${fmt(l.startDate)} to ${fmt(l.endDate)}`,
    'Total Days':    l.totalDays      ?? '—',
    'Paid / Unpaid': paidLabel(l.leaveType),
    'Status':        l.status         || '—',
    'Reason':        l.reason         || '—',
    'Approved By':   l.approvedByName || '—',
  }));
  const ws = XLSX.utils.json_to_sheet(sheetData);
  ws['!cols'] = [4,22,18,20,14,16,26,10,13,12,30,22].map(wch => ({ wch }));
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Leave Report');
  XLSX.writeFile(wb, `Leave_Report_${monthLabel.replace(' ', '_')}.xlsx`);
  toast.success('Excel downloaded!');
}

// ── Excel export — employee summary ──────────────────────────────────────────
function exportSummaryExcel(rows, monthLabel) {
  const sheetData = rows.map((r, i) => ({
    '#':             i + 1,
    'Employee':      r.fullName,
    'Department':    r.department,
    'Attendance':    r.attendance,
    'Paid Leave':    r.paidLeaveDays,
    'Unpaid Leave':  r.unpaidLeaveDays,
    'Status':        r.active ? 'Active' : 'Inactive',
  }));
  const ws = XLSX.utils.json_to_sheet(sheetData);
  ws['!cols'] = [4,24,18,12,12,12,10].map(wch => ({ wch }));
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, `Summary ${monthLabel}`);
  XLSX.writeFile(wb, `Employee_Summary_${monthLabel.replace(' ', '_')}.xlsx`);
  toast.success('Excel downloaded!');
}

// ── Main page ─────────────────────────────────────────────────────────────────
const ReportsPage = () => {
  const [reportType, setReportType] = useState('employee-summary');
  const [employees,  setEmployees]  = useState([]);
  const [allLeaves,  setAllLeaves]  = useState([]);
  const [data,       setData]       = useState([]);
  const [loading,    setLoading]    = useState(false);
  const [page,       setPage]       = useState(0);
  const [rpp,        setRpp]        = useState(25);

  const [selMonth,       setSelMonth]       = useState(0);
  const [filterDept,     setFilterDept]     = useState('ALL');
  const [filterMgr,      setFilterMgr]      = useState('ALL');
  const [filterLoc,      setFilterLoc]      = useState('ALL');

  // Summary-specific filters
  const [sumDept,        setSumDept]        = useState('ALL');
  const [sumLoc,         setSumLoc]         = useState('ALL');
  const [sumStatus,      setSumStatus]      = useState('ALL');
  const [sumSearch,      setSumSearch]      = useState('');

  const { year: selYear, month: selMonthIdx, label: selLabel } = MONTH_OPTIONS[selMonth];

  // ── Load data ───────────────────────────────────────────────────────────────
  const loadReport = async (type) => {
    setLoading(true);
    try {
      if (type === 'employee-summary') {
        const [emps, lvs] = await Promise.all([employeeApi.getAll(), leaveApi.getAll()]);
        setEmployees(Array.isArray(emps) ? emps : []);
        setAllLeaves(Array.isArray(lvs)  ? lvs  : []);
        setData([]);
      } else {
        let result = [];
        switch (type) {
          case 'monthly-leaves': result = await leaveApi.getAll();       break;
          case 'employees':      result = await employeeApi.getAll();    break;
          case 'leaves':         result = await leaveApi.getAll();       break;
          case 'performance':    result = await performanceApi.getAll(); break;
          default: result = [];
        }
        setData(Array.isArray(result) ? result : []);
        setEmployees([]);
        setAllLeaves([]);
      }
    } catch {
      toast.error('Failed to load report');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadReport(reportType); setPage(0); }, [reportType]); // eslint-disable-line

  // ── Employee Summary computation ─────────────────────────────────────────────
  const workingDays = useMemo(
    () => workingDaysInMonth(selYear, selMonthIdx),
    [selYear, selMonthIdx],
  );

  const summaryRows = useMemo(() => {
    if (reportType !== 'employee-summary') return [];

    // Group approved leaves per employee for selected month
    const leaveMap = {};
    allLeaves.forEach((l) => {
      if (l.status !== 'APPROVED') return;
      const overlap = countWeekdayOverlap(l.startDate, l.endDate, selYear, selMonthIdx);
      if (overlap === 0) return;
      if (!leaveMap[l.employeeId]) leaveMap[l.employeeId] = { paid: 0, unpaid: 0 };
      const lt = l.leaveType || '';
      if (lt.toUpperCase().includes('PUBLIC HOLIDAY') || lt.toUpperCase().includes('PUBLIC_HOLIDAY')) {
        leaveMap[l.employeeId].paid += overlap; // public holidays count as paid
      } else if (isPaidLeave(lt)) {
        leaveMap[l.employeeId].paid += overlap;
      } else {
        leaveMap[l.employeeId].unpaid += overlap;
      }
    });

    return employees.map((emp) => {
      const lv = leaveMap[emp.id] || { paid: 0, unpaid: 0 };
      const attendance = Math.max(0, workingDays - lv.paid - lv.unpaid);
      return {
        id:              emp.id,
        fullName:        emp.fullName || `${emp.firstName} ${emp.lastName}`,
        employeeCode:    emp.employeeCode || '—',
        department:      emp.department   || '—',
        location:        emp.seatingLocation || '—',
        managerName:     emp.managerName  || '—',
        attendance,
        paidLeaveDays:   lv.paid,
        unpaidLeaveDays: lv.unpaid,
        active:          emp.active,
      };
    });
  }, [reportType, employees, allLeaves, selYear, selMonthIdx, workingDays]);

  // ── Filters for monthly-leaves ───────────────────────────────────────────────
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

  const monthlyLeaves = useMemo(() =>
    allMonthLeaves.filter((l) => {
      if (l.status !== 'APPROVED') return false;
      if (filterDept !== 'ALL' && l.department  !== filterDept) return false;
      if (filterMgr  !== 'ALL' && l.managerName !== filterMgr)  return false;
      if (filterLoc  !== 'ALL' && l.location    !== filterLoc)  return false;
      return true;
    }), [allMonthLeaves, filterDept, filterMgr, filterLoc]);

  // Summary filter options
  const sumDeptOptions = useMemo(() =>
    ['ALL', ...new Set(summaryRows.map(r => r.department).filter(d => d && d !== '—'))],
    [summaryRows]);
  const sumLocOptions  = useMemo(() =>
    ['ALL', ...new Set(summaryRows.map(r => r.location).filter(l => l && l !== '—'))],
    [summaryRows]);

  const filteredSummary = useMemo(() => summaryRows.filter(r => {
    if (sumDept   !== 'ALL' && r.department !== sumDept) return false;
    if (sumLoc    !== 'ALL' && r.location   !== sumLoc)  return false;
    if (sumStatus === 'Active'   && !r.active)  return false;
    if (sumStatus === 'Inactive' &&  r.active)  return false;
    if (sumSearch) {
      const q = sumSearch.toLowerCase();
      if (!r.fullName.toLowerCase().includes(q) && !r.employeeCode.toLowerCase().includes(q)) return false;
    }
    return true;
  }), [summaryRows, sumDept, sumLoc, sumStatus, sumSearch]);

  const displayRows = reportType === 'monthly-leaves'   ? monthlyLeaves
                    : reportType === 'employee-summary' ? filteredSummary
                    : data;

  const pageRows = displayRows.slice(page * rpp, (page + 1) * rpp);

  const handleMonthChange = (idx) => {
    setSelMonth(idx);
    setFilterDept('ALL'); setFilterMgr('ALL'); setFilterLoc('ALL');
    setPage(0);
  };

  // ── CSV export ───────────────────────────────────────────────────────────────
  const exportCSV = () => {
    if (!displayRows.length) return;
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

  // ── Table renderers ──────────────────────────────────────────────────────────

  // NEW: Employee Summary
  const cHdr  = { ...hdr, textAlign: 'center' };
  const cCell = { ...cell, textAlign: 'center' };

  const renderEmployeeSummary = () => (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell sx={hdr}>#</TableCell>
          <TableCell sx={hdr}>Employee</TableCell>
          <TableCell sx={hdr}>Department</TableCell>
          <TableCell sx={cHdr}>Attendance</TableCell>
          <TableCell sx={cHdr}>Paid Leave</TableCell>
          <TableCell sx={cHdr}>Unpaid Leave</TableCell>
          <TableCell sx={hdr}>Status</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {pageRows.length === 0 ? (
          <TableRow>
            <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
              No employees found
            </TableCell>
          </TableRow>
        ) : pageRows.map((r, i) => (
          <TableRow key={r.id} hover sx={{ bgcolor: (page * rpp + i) % 2 === 0 ? 'white' : '#f8fafc' }}>
            <TableCell sx={{ ...cell, color: '#94a3b8', fontSize: 12 }}>{page * rpp + i + 1}</TableCell>

            {/* Employee */}
            <TableCell sx={cell}>
              <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#0f172a', lineHeight: 1.3 }}>{r.fullName}</Typography>
              <Typography sx={{ fontSize: 11, color: '#94a3b8' }}>{r.employeeCode}</Typography>
            </TableCell>

            {/* Department */}
            <TableCell sx={cell}>{r.department}</TableCell>

            {/* Attendance */}
            <TableCell sx={cCell}>
              <Box sx={{ display: 'inline-flex', px: 1, py: 0.2, borderRadius: '5px', bgcolor: '#d1fae5', color: '#065f46', fontWeight: 700, fontSize: 12 }}>
                {r.attendance}d
              </Box>
            </TableCell>

            {/* Paid Leave */}
            <TableCell sx={cCell}>
              {r.paidLeaveDays > 0 ? (
                <Box sx={{ display: 'inline-flex', px: 1, py: 0.2, borderRadius: '5px', bgcolor: '#dbeafe', color: '#1d4ed8', fontWeight: 700, fontSize: 12 }}>
                  {r.paidLeaveDays}d
                </Box>
              ) : <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>0</Typography>}
            </TableCell>

            {/* Unpaid Leave */}
            <TableCell sx={cCell}>
              {r.unpaidLeaveDays > 0 ? (
                <Box sx={{ display: 'inline-flex', px: 1, py: 0.2, borderRadius: '5px', bgcolor: '#fee2e2', color: '#991b1b', fontWeight: 700, fontSize: 12 }}>
                  {r.unpaidLeaveDays}d
                </Box>
              ) : <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>0</Typography>}
            </TableCell>

            {/* Status */}
            <TableCell sx={cell}>
              <Chip label={r.active ? 'Active' : 'Inactive'} size="small"
                color={r.active ? 'success' : 'default'} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );

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
            <TableCell sx={{ ...cell, whiteSpace: 'nowrap' }}>{fmt(l.startDate)} — {fmt(l.endDate)}</TableCell>
            <TableCell sx={{ ...cell, textAlign: 'center' }}>{l.totalDays ?? '—'}</TableCell>
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
      case 'employee-summary': return renderEmployeeSummary();
      case 'monthly-leaves':   return renderMonthlyLeaves();
      case 'employees':        return renderEmployees();
      case 'leaves':           return renderLeaves();
      case 'performance':      return renderPerformance();
      default:                 return null;
    }
  };

  const reportLabel = {
    'employee-summary': 'Monthly Leave Report',
    'monthly-leaves':   'Monthly Leave Report',
    'employees':        'Employee Report',
    'leaves':           'All Leave Report',
    'performance':      'Performance Report',
  }[reportType];

  const isSummary = reportType === 'employee-summary';
  const isLeave   = reportType === 'monthly-leaves';

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Leave Reports</Typography>
          <Typography variant="body2" color="text.secondary" mt={0.5}>Attendance and leave summary by month</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5 }}>
          <Button variant="contained" startIcon={<TableChartIcon />}
            onClick={() => isSummary ? exportSummaryExcel(summaryRows, selLabel) : exportLeaveExcel(monthlyLeaves, selLabel)}
            disabled={!displayRows.length}
            sx={{ borderRadius: '10px', fontWeight: 600, fontSize: 13 }}>
            Export Excel
          </Button>
          <Button variant="outlined" startIcon={<DownloadIcon />}
            onClick={() => window.print()}
            sx={{ borderRadius: '10px', fontWeight: 600, fontSize: 13 }}>
            Download PDF
          </Button>
        </Box>
      </Box>

      {/* ── Controls card ── */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2} alignItems="center">



            {/* Month picker (summary + monthly-leaves) */}
            {(isSummary || isLeave) && (
              <Grid item xs={12} sm={2}>
                <FormControl fullWidth size="small">
                  <InputLabel>Select Month</InputLabel>
                  <Select value={selMonth} label="Select Month"
                    onChange={(e) => handleMonthChange(e.target.value)}>
                    {MONTH_OPTIONS.map((opt, idx) => (
                      <MenuItem key={idx} value={idx}>{opt.label}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Grid>
            )}

            {/* Summary filters */}
            {isSummary && (
              <>
                <Grid item xs={12} sm={2}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Select Department</InputLabel>
                    <Select value={sumDept} label="Select Department"
                      onChange={(e) => { setSumDept(e.target.value); setPage(0); }}>
                      {sumDeptOptions.map(d => (
                        <MenuItem key={d} value={d}>{d === 'ALL' ? 'All Departments' : d}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={2}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Select Location</InputLabel>
                    <Select value={sumLoc} label="Select Location"
                      onChange={(e) => { setSumLoc(e.target.value); setPage(0); }}>
                      {sumLocOptions.map(l => (
                        <MenuItem key={l} value={l}>{l === 'ALL' ? 'All Locations' : l}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={2}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Salary Status</InputLabel>
                    <Select value={sumStatus} label="Salary Status"
                      onChange={(e) => { setSumStatus(e.target.value); setPage(0); }}>
                      <MenuItem value="ALL">All Status</MenuItem>
                      <MenuItem value="Active">Active</MenuItem>
                      <MenuItem value="Inactive">Inactive</MenuItem>
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={2}>
                  <TextField fullWidth size="small" placeholder="Search Employee"
                    value={sumSearch}
                    onChange={(e) => { setSumSearch(e.target.value); setPage(0); }}
                    InputProps={{
                      startAdornment: (
                        <InputAdornment position="start">
                          <SearchIcon sx={{ fontSize: 16, color: '#94a3b8' }} />
                        </InputAdornment>
                      ),
                    }}
                  />
                </Grid>
              </>
            )}

            {/* Department / Manager / Location filters (monthly-leaves only) */}
            {isLeave && (
              <>
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
              </>
            )}

            {!isSummary && !isLeave && (
              <Grid item sx={{ ml: 'auto' }}>
                <Button variant="outlined" startIcon={<DownloadIcon />}
                  onClick={exportCSV} disabled={displayRows.length === 0}>
                  Export CSV
                </Button>
              </Grid>
            )}
          </Grid>
        </CardContent>
      </Card>

      {/* ── Table card ── */}
      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Box>
              <Typography variant="h6" fontWeight={600}>{reportLabel}</Typography>
              {isLeave && (
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
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              {!isSummary && (
                <Chip
                  label={`${displayRows.length} record${displayRows.length !== 1 ? 's' : ''}`}
                  size="small" color="primary"
                />
              )}
              {isSummary && (
                <Button variant="contained" size="small"
                  onClick={() => exportSummaryExcel(summaryRows, selLabel)}
                  disabled={!summaryRows.length}
                  sx={{ bgcolor: '#0f172a', '&:hover': { bgcolor: '#1e293b' }, borderRadius: '8px', fontSize: 13, fontWeight: 600, px: 2 }}>
                  Generate Report
                </Button>
              )}
            </Box>
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
