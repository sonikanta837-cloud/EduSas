import React, { useEffect, useState, useMemo } from 'react';
import { useSelector } from 'react-redux';
import * as XLSX from 'xlsx';
import dayjs from 'dayjs';
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
import PlayArrowIcon  from '@mui/icons-material/PlayArrow';
import RefreshIcon    from '@mui/icons-material/Refresh';
import { employeeApi }        from '../api/employeeApi';
import { leaveApi }           from '../api/leaveApi';
import { jobWorkSessionApi }  from '../api/jobWorkSessionApi';
import { jobSummaryApi }      from '../api/jobSummaryApi';
import { toast }              from 'react-toastify';

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
// eslint-disable-next-line no-unused-vars
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

// ── Attendance Report helpers ─────────────────────────────────────────────────
const fmtTime = (t) => (t ? dayjs(t).format('HH:mm') : '—');
const fmtMinutes = (mins) => {
  if (mins == null) return '—';
  const h = Math.floor(mins / 60);
  const m = Math.round(mins % 60);
  return `${h}h ${String(m).padStart(2, '0')}m`;
};
const AR_STATUS_STYLES = {
  PRESENT:        { label: 'Present',        color: '#16a34a', bg: '#dcfce7' },
  UNDER_HOURS:    { label: 'Under Hours',    color: '#c2410c', bg: '#ffedd5' },
  OVERTIME:       { label: 'Overtime',       color: '#7c3aed', bg: '#ede9fe' },
  ABSENT:         { label: 'Absent',         color: '#dc2626', bg: '#fee2e2' },
  LEAVE:          { label: 'Leave',          color: '#2563eb', bg: '#dbeafe' },
  HOLIDAY:        { label: 'Holiday',        color: '#7e22ce', bg: '#f3e8ff' },
  WEEKEND:        { label: 'Weekly Off',     color: '#b45309', bg: '#fef3c7' },
  PENDING_LOGOUT: { label: 'Pending Logout', color: '#0891b2', bg: '#cffafe' },
};
const AR_STATUS_FILTERS = ['ALL', 'PRESENT', 'UNDER_HOURS', 'OVERTIME', 'ABSENT', 'LEAVE', 'HOLIDAY', 'WEEKEND', 'PENDING_LOGOUT'];

// "Pending Logout" isn't a persisted backend status — it's derived here for
// today's rows that have clocked in but not yet clocked out, so filtering/
// display can distinguish "still working" from a finished PRESENT/OVERTIME day.
const arEffectiveStatus = (r) => {
  const today = dayjs().format('YYYY-MM-DD');
  if (r.workDate === today && r.firstLoginTime && !r.lastLogoutTime) return 'PENDING_LOGOUT';
  return r.status;
};

// ── Excel export — attendance report ─────────────────────────────────────────
function exportAttendanceExcel(rows, startDate, endDate) {
  if (!rows.length) return;
  const sheetData = rows.map((r, i) => ({
    '#':                i + 1,
    'Employee':         r.employeeName,
    'Employee Code':    r.employeeCode,
    'Department':       r.department,
    'Date':             r.workDate,
    'First Login':      fmtTime(r.firstLoginTime),
    'Last Logout':      fmtTime(r.lastLogoutTime),
    'Working Hours':    fmtMinutes(r.totalWorkingMinutes),
    'Break Time':       fmtMinutes(r.totalBreakMinutes),
    'Office Time':      fmtMinutes(r.totalOfficeMinutes),
    'Overtime':         fmtMinutes(r.overtimeMinutes),
    'Attendance Status': AR_STATUS_STYLES[arEffectiveStatus(r)]?.label || r.status,
    'Correction Status': r.correctionStatus || 'NONE',
    'Session Count':    r.sessionCount,
  }));
  const ws = XLSX.utils.json_to_sheet(sheetData);
  ws['!cols'] = [4,22,14,18,12,12,12,14,12,12,12,16,14,10].map(wch => ({ wch }));
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Attendance Report');
  XLSX.writeFile(wb, `Attendance_Report_${startDate}_to_${endDate}.xlsx`);
  toast.success('Excel downloaded!');
}

// ── Excel export — daily work report ─────────────────────────────────────────
function exportWorkReportExcel(rows, startDate, endDate) {
  if (!rows.length) return;

  // Sheet 1: Detail rows
  const detail = rows.map((r, i) => ({
    '#':               i + 1,
    'Employee Code':   r.employeeCode,
    'Employee Name':   r.employeeName,
    'Department':      r.department,
    'Location':        r.location,
    'Manager':         r.managerName,
    'Date':            r.date,
    'Project':         r.projectName,
    'Task':            r.taskName,
    'Hours':           r.hours,
  }));

  // Sheet 2: Summary — total hours per employee per project
  const summaryMap = {};
  rows.forEach(r => {
    const key = `${r.employeeName}||${r.projectName}`;
    if (!summaryMap[key]) summaryMap[key] = { ...r, hours: 0 };
    summaryMap[key].hours += r.hours || 0;
  });
  const summary = Object.values(summaryMap).map((r, i) => ({
    '#':             i + 1,
    'Employee Code': r.employeeCode,
    'Employee Name': r.employeeName,
    'Department':    r.department,
    'Manager':       r.managerName,
    'Project':       r.projectName,
    'Total Hours':   +r.hours.toFixed(2),
  }));

  const wsDetail  = XLSX.utils.json_to_sheet(detail);
  const wsSummary = XLSX.utils.json_to_sheet(summary);
  wsDetail['!cols']  = [4,14,22,18,14,20,12,24,24,8].map(wch => ({ wch }));
  wsSummary['!cols'] = [4,14,22,18,20,24,12].map(wch => ({ wch }));

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, wsDetail,  'Daily Work Detail');
  XLSX.utils.book_append_sheet(wb, wsSummary, 'Project Summary');
  XLSX.writeFile(wb, `Work_Report_${startDate}_to_${endDate}.xlsx`);
  toast.success('Excel downloaded!');
}

// ── Main page ─────────────────────────────────────────────────────────────────
const ReportsPage = () => {
  const { user } = useSelector((s) => s.auth);
  const role = user?.role;
  // Org-wide report types (Monthly Summary, Monthly Leave, All Employees, All
  // Leave) stay ADMIN/DIRECTOR-only — same exposure as the old ['ADMIN','DIRECTOR']
  // nav restriction on /reports, now enforced inside the page since the nav item
  // itself is open to everyone (parity with the old always-visible Attendance Reports).
  const canSeeAllReportTypes = ['ADMIN', 'DIRECTOR'].includes(role);
  const isManagerOrAbove     = ['ADMIN', 'DIRECTOR', 'HR', 'MANAGER', 'ASSISTANT_MANAGER'].includes(role);
  const canGenerateAttendance = ['ADMIN', 'DIRECTOR', 'HR'].includes(role);

  const [reportType, setReportType] = useState(() => (['ADMIN', 'DIRECTOR'].includes(role) ? 'employee-summary' : 'attendance-report'));
  const [employees,  setEmployees]  = useState([]);
  const [allLeaves,  setAllLeaves]  = useState([]);
  const [data,       setData]       = useState([]);
  const [loading,    setLoading]    = useState(false);
  const [page,       setPage]       = useState(0);
  const [rpp,        setRpp]        = useState(25);
  const [generated,  setGenerated]  = useState(false);

  const [selMonth,       setSelMonth]       = useState(0);
  const [filterDept,     setFilterDept]     = useState('ALL');
  const [filterMgr,      setFilterMgr]      = useState('ALL');
  const [filterLoc,      setFilterLoc]      = useState('ALL');

  // Work Report state — always shows TODAY, no date picker
  const [wrData,       setWrData]       = useState([]);
  const [wrLoading,    setWrLoading]    = useState(false);
  const [wrDeptFilter, setWrDeptFilter] = useState('ALL');
  const [wrLocFilter,  setWrLocFilter]  = useState('ALL');

  // Summary-specific filters
  const [sumDept,        setSumDept]        = useState('ALL');
  const [sumLoc,         setSumLoc]         = useState('ALL');
  const [sumStatus] = useState('ALL');
  const [sumSearch,      setSumSearch]      = useState('');

  // Attendance Report state
  const [arFromDate,    setArFromDate]    = useState(dayjs().startOf('month').format('YYYY-MM-DD'));
  const [arToDate,      setArToDate]      = useState(dayjs().format('YYYY-MM-DD'));
  const [arReports,     setArReports]     = useState([]);
  const [arLoading,     setArLoading]     = useState(false);
  const [arGenerating,  setArGenerating]  = useState(false);
  const [arSearch,      setArSearch]      = useState('');
  const [arStatusFilter, setArStatusFilter] = useState('ALL');

  const { year: selYear, month: selMonthIdx, label: selLabel } = MONTH_OPTIONS[selMonth];

  // ── Load data ───────────────────────────────────────────────────────────────
  const loadReport = async (type) => {
    if (type === 'attendance-report') {
      setArLoading(true);
      try {
        const result = isManagerOrAbove
          ? await jobSummaryApi.getTeam(arFromDate, arToDate)
          : await jobSummaryApi.getMy(arFromDate, arToDate);
        setArReports(Array.isArray(result) ? result : []);
        setPage(0);
      } catch { toast.error('Failed to load attendance report'); }
      finally { setArLoading(false); }
      return;
    }
    if (type === 'daily-work-report') {
      // Work report only ever covers today — no date range to pick
      const today = new Date().toISOString().slice(0, 10);
      setWrLoading(true);
      try {
        const result = await jobWorkSessionApi.getWorkReport(today, today);
        setWrData(Array.isArray(result) ? result : []);
        setWrDeptFilter('ALL'); setWrLocFilter('ALL'); setPage(0);
      } catch { toast.error('Failed to load work report'); }
      finally { setWrLoading(false); }
      return;
    }
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


  // Switching report type clears any previously generated data — the user must
  // press "Generate Report" to fetch, so the page never dumps a table on load.
  useEffect(() => {
    setGenerated(false);
    setData([]);
    setEmployees([]);
    setAllLeaves([]);
    setWrData([]);
    setArReports([]);
    setPage(0);
  }, [reportType]);

  const handleGenerate = () => {
    loadReport(reportType);
    setGenerated(true);
  };

  // Recomputes/backfills the underlying job_daily_summaries rows for the
  // selected range — distinct from "Generate Report" above, which only
  // fetches/displays whatever is already computed.
  const handleGenerateAttendanceRange = async () => {
    setArGenerating(true);
    try {
      await jobSummaryApi.generateRange(arFromDate, arToDate);
      toast.success('Attendance data recomputed');
      loadReport('attendance-report');
    } catch { toast.error('Failed to recompute attendance data'); }
    finally { setArGenerating(false); }
  };

  const handleExportAttendanceCsv = async () => {
    try {
      const blob = await jobSummaryApi.exportCsv(arFromDate, arToDate);
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = `attendance-report-${arFromDate}-to-${arToDate}.csv`; a.click();
      URL.revokeObjectURL(url);
    } catch { toast.error('Export failed'); }
  };

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

  // ── Work Report filtered data ────────────────────────────────────────────────
  const wrDeptOptions = useMemo(() =>
    ['ALL', ...new Set(wrData.map(r => r.department).filter(Boolean))], [wrData]);
  const wrLocOptions  = useMemo(() =>
    ['ALL', ...new Set(wrData.map(r => r.location).filter(Boolean))], [wrData]);

  const filteredWrData = useMemo(() => wrData.filter(r => {
    if (wrDeptFilter !== 'ALL' && r.department !== wrDeptFilter) return false;
    if (wrLocFilter  !== 'ALL' && r.location   !== wrLocFilter)  return false;
    return true;
  }), [wrData, wrDeptFilter, wrLocFilter]);

  // Total hours summary for work report
  const wrTotalHours = useMemo(() =>
    filteredWrData.reduce((s, r) => s + (r.hours || 0), 0).toFixed(1),
    [filteredWrData]);

  // ── Attendance Report filtered data / summary ────────────────────────────────
  const filteredAttendance = useMemo(() => arReports.filter((r) => {
    const st = arEffectiveStatus(r);
    if (arStatusFilter !== 'ALL' && st !== arStatusFilter) return false;
    if (arSearch) {
      const q = arSearch.toLowerCase();
      if (!r.employeeName?.toLowerCase().includes(q) && !r.department?.toLowerCase().includes(q)) return false;
    }
    return true;
  }), [arReports, arStatusFilter, arSearch]);

  const displayRows = reportType === 'monthly-leaves'    ? monthlyLeaves
                    : reportType === 'employee-summary'  ? filteredSummary
                    : reportType === 'daily-work-report' ? filteredWrData
                    : reportType === 'attendance-report' ? filteredAttendance
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
            <TableCell colSpan={6} align="center" sx={{ py: 4, color: 'text.secondary' }}>
              No employees found
            </TableCell>
          </TableRow>
        ) : pageRows.map((r, i) => (
          <TableRow key={r.id} hover sx={{ bgcolor: (page * rpp + i) % 2 === 0 ? 'white' : '#f8fafc' }}>
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
          {['Employee Name','Department','Manager','Leave Type',
            'Leave Dates','Total Days','Paid / Unpaid','Status'].map((h) => (
            <TableCell key={h} sx={hdr}>{h}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {pageRows.length === 0 ? (
          <TableRow>
            <TableCell colSpan={8} align="center" sx={{ py: 4, color: 'text.secondary' }}>
              No approved leaves found for {selLabel}
            </TableCell>
          </TableRow>
        ) : pageRows.map((l, i) => (
          <TableRow key={l.id} hover sx={{ bgcolor: (page * rpp + i) % 2 === 0 ? 'white' : '#f8fafc' }}>
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

  const renderWorkReport = () => (
    <Table size="small">
      <TableHead>
        <TableRow>
          {['Employee','Department','Manager','Date','Project','Task','Hours'].map(h => (
            <TableCell key={h} sx={hdr}>{h}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {wrLoading ? (
          <TableRow>
            <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
              <CircularProgress size={24} />
            </TableCell>
          </TableRow>
        ) : pageRows.length === 0 ? (
          <TableRow>
            <TableCell colSpan={7} align="center" sx={{ py: 4, color: 'text.secondary' }}>
              {wrData.length === 0
                ? 'No timesheet entries found for today'
                : 'No entries match the selected filters'}
            </TableCell>
          </TableRow>
        ) : pageRows.map((r, i) => (
          <TableRow key={r.entryId} hover sx={{ bgcolor: (page * rpp + i) % 2 === 0 ? 'white' : '#f8fafc' }}>
            <TableCell sx={cell}>
              <Typography sx={{ fontSize: 13, fontWeight: 600, lineHeight: 1.2 }}>{r.employeeName}</Typography>
              <Typography sx={{ fontSize: 10.5, color: '#94a3b8' }}>{r.employeeCode}</Typography>
            </TableCell>
            <TableCell sx={cell}>{r.department}</TableCell>
            <TableCell sx={cell}>{r.managerName}</TableCell>
            <TableCell sx={{ ...cell, whiteSpace: 'nowrap' }}>{r.date}</TableCell>
            <TableCell sx={cell}>
              <Chip label={r.projectName} size="small"
                sx={{ fontSize: 11, bgcolor: '#eff6ff', color: '#1d4ed8', fontWeight: 600 }} />
            </TableCell>
            <TableCell sx={{ ...cell, color: '#64748b' }}>{r.taskName}</TableCell>
            <TableCell sx={cell}>
              <Box sx={{ display: 'inline-flex', px: 1.2, py: 0.3, borderRadius: 1,
                bgcolor: r.hours >= 8 ? '#d1fae5' : r.hours >= 4 ? '#fef9c3' : '#fee2e2',
                color:  r.hours >= 8 ? '#065f46' : r.hours >= 4 ? '#92400e' : '#991b1b',
                fontWeight: 700, fontSize: 12 }}>
                {r.hours}h
              </Box>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );

  const renderAttendanceReport = () => (
    <Table size="small" stickyHeader>
      <TableHead>
        <TableRow>
          {(isManagerOrAbove ? ['Employee', 'Department'] : []).concat(
            ['Date', 'First Login', 'Last Logout', 'Working Hours', 'Break Time', 'Office Time', 'Overtime', 'Attendance Status', 'Session Count']
          ).map((h) => (
            <TableCell key={h} sx={hdr}>{h}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {arLoading ? (
          <TableRow>
            <TableCell colSpan={11} align="center" sx={{ py: 4 }}>
              <CircularProgress size={24} />
            </TableCell>
          </TableRow>
        ) : pageRows.length === 0 ? (
          <TableRow>
            <TableCell colSpan={11} align="center" sx={{ py: 4, color: 'text.secondary' }}>
              No attendance records found for {arFromDate} to {arToDate}
            </TableCell>
          </TableRow>
        ) : pageRows.map((r, i) => {
          const st = arEffectiveStatus(r);
          const style = AR_STATUS_STYLES[st];
          return (
            <TableRow key={`${r.employeeId}-${r.workDate}-${i}`} hover sx={{ bgcolor: (page * rpp + i) % 2 === 0 ? 'white' : '#f8fafc' }}>
              {isManagerOrAbove && (
                <>
                  <TableCell sx={cell}>
                    <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{r.employeeName}</Typography>
                  </TableCell>
                  <TableCell sx={{ ...cell, color: '#64748b' }}>{r.department}</TableCell>
                </>
              )}
              <TableCell sx={{ ...cell, fontWeight: 600, whiteSpace: 'nowrap' }}>
                {dayjs(r.workDate).format('ddd, DD MMM YYYY')}
              </TableCell>
              <TableCell sx={{ ...cell, color: '#16a34a', fontWeight: 600 }}>{fmtTime(r.firstLoginTime)}</TableCell>
              <TableCell sx={{ ...cell, color: '#dc2626', fontWeight: 600 }}>{fmtTime(r.lastLogoutTime)}</TableCell>
              <TableCell sx={cell}>
                <Chip label={fmtMinutes(r.totalWorkingMinutes)} size="small"
                  sx={{ bgcolor: '#f0fdf4', color: '#16a34a', fontWeight: 700, fontSize: 11 }} />
              </TableCell>
              <TableCell sx={cell}>
                <Chip label={fmtMinutes(r.totalBreakMinutes)} size="small"
                  sx={{ bgcolor: '#fff7ed', color: '#c2410c', fontWeight: 700, fontSize: 11 }} />
              </TableCell>
              <TableCell sx={cell}>
                <Chip label={fmtMinutes(r.totalOfficeMinutes)} size="small"
                  sx={{ bgcolor: '#eff6ff', color: '#1d4ed8', fontWeight: 700, fontSize: 11 }} />
              </TableCell>
              <TableCell sx={cell}>
                {r.overtimeMinutes > 0 ? (
                  <Chip label={`+${fmtMinutes(r.overtimeMinutes)}`} size="small"
                    sx={{ bgcolor: '#ede9fe', color: '#7c3aed', fontWeight: 700, fontSize: 11 }} />
                ) : '—'}
              </TableCell>
              <TableCell sx={cell}>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, alignItems: 'flex-start' }}>
                  {style && (
                    <Chip label={style.label} size="small"
                      sx={{ bgcolor: style.bg, color: style.color, fontWeight: 700, fontSize: 11 }} />
                  )}
                  {st === 'OVERTIME' && r.correctionStatus !== 'APPROVED' && (
                    <Chip label="Correction Pending" size="small"
                      sx={{ bgcolor: '#fff7ed', color: '#c2410c', fontWeight: 600, fontSize: 10 }} />
                  )}
                </Box>
              </TableCell>
              <TableCell sx={{ ...cell, textAlign: 'center' }}>{r.sessionCount}</TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );

  const renderTable = () => {
    switch (reportType) {
      case 'employee-summary':  return renderEmployeeSummary();
      case 'monthly-leaves':    return renderMonthlyLeaves();
      case 'employees':         return renderEmployees();
      case 'leaves':            return renderLeaves();
      case 'daily-work-report': return renderWorkReport();
      case 'attendance-report': return renderAttendanceReport();
      default:                  return null;
    }
  };

  const reportLabel = {
    'employee-summary':  'Monthly Employee Summary',
    'monthly-leaves':    'Monthly Leave Report',
    'employees':         'Employee Report',
    'leaves':            'All Leave Report',
    'daily-work-report': 'Daily Work Report',
    'attendance-report': 'Attendance Report',
  }[reportType];

  const isSummary    = reportType === 'employee-summary';
  const isLeave      = reportType === 'monthly-leaves';
  const isWorkReport = reportType === 'daily-work-report';
  const isAttendance = reportType === 'attendance-report';

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3, flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>Reports</Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1.5 }}>
          <Button variant="contained" startIcon={<TableChartIcon />}
            onClick={() => {
              if (isWorkReport) exportWorkReportExcel(filteredWrData, new Date().toISOString().slice(0,10), new Date().toISOString().slice(0,10));
              else if (isAttendance) exportAttendanceExcel(filteredAttendance, arFromDate, arToDate);
              else if (isSummary) exportSummaryExcel(summaryRows, selLabel);
              else exportLeaveExcel(monthlyLeaves, selLabel);
            }}
            disabled={!displayRows.length}
            sx={{ borderRadius: '10px', fontWeight: 600, fontSize: 13 }}>
            Export Excel
          </Button>
          {!isWorkReport && (
            <Button variant="outlined" startIcon={<DownloadIcon />}
              onClick={() => window.print()}
              sx={{ borderRadius: '10px', fontWeight: 600, fontSize: 13 }}>
              Download PDF
            </Button>
          )}
        </Box>
      </Box>

      {/* ── Controls card ── */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2} alignItems="center">

            {/* Report type selector */}
            <Grid item xs={12} sm={3}>
              <FormControl fullWidth size="small">
                <InputLabel>Report Type</InputLabel>
                <Select value={reportType} label="Report Type"
                  onChange={(e) => { setReportType(e.target.value); setPage(0); }}>
                  {canSeeAllReportTypes && <MenuItem value="employee-summary">Monthly Employee Summary</MenuItem>}
                  {canSeeAllReportTypes && <MenuItem value="monthly-leaves">Monthly Leave Report</MenuItem>}
                  {canSeeAllReportTypes && <MenuItem value="employees">All Employees</MenuItem>}
                  {canSeeAllReportTypes && <MenuItem value="leaves">All Leave Report</MenuItem>}
                  {canSeeAllReportTypes && <MenuItem value="daily-work-report">Daily Work Report</MenuItem>}
                  <MenuItem value="attendance-report">Attendance Report</MenuItem>
                </Select>
              </FormControl>
            </Grid>

            {/* Attendance Report filters — date range + quick filters + recompute */}
            {isAttendance && (
              <>
                <Grid item xs={12} sm={2}>
                  <TextField fullWidth size="small" label="From" type="date"
                    value={arFromDate}
                    onChange={(e) => { setArFromDate(e.target.value); setPage(0); }}
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ max: arToDate }} />
                </Grid>
                <Grid item xs={12} sm={2}>
                  <TextField fullWidth size="small" label="To" type="date"
                    value={arToDate}
                    onChange={(e) => { setArToDate(e.target.value); setPage(0); }}
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ min: arFromDate, max: dayjs().format('YYYY-MM-DD') }} />
                </Grid>
                <Grid item xs={12} sm="auto">
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    {[
                      { label: 'Today',      from: dayjs().format('YYYY-MM-DD'),                  to: dayjs().format('YYYY-MM-DD') },
                      { label: 'This Week',  from: dayjs().startOf('week').format('YYYY-MM-DD'),  to: dayjs().format('YYYY-MM-DD') },
                      { label: 'This Month', from: dayjs().startOf('month').format('YYYY-MM-DD'), to: dayjs().format('YYYY-MM-DD') },
                      { label: 'Last Month', from: dayjs().subtract(1, 'month').startOf('month').format('YYYY-MM-DD'),
                                             to:   dayjs().subtract(1, 'month').endOf('month').format('YYYY-MM-DD') },
                    ].map(({ label, from, to }) => (
                      <Button key={label} size="small" variant="outlined"
                        onClick={() => { setArFromDate(from); setArToDate(to); setPage(0); }}
                        sx={{ textTransform: 'none', borderRadius: '8px', fontSize: 12,
                          borderColor: arFromDate === from && arToDate === to ? '#1e3a5f' : undefined,
                          fontWeight: arFromDate === from && arToDate === to ? 700 : 400 }}>
                        {label}
                      </Button>
                    ))}
                  </Box>
                </Grid>
                {isManagerOrAbove && (
                  <Grid item xs={12} sm={2}>
                    <TextField fullWidth size="small" placeholder="Search Employee/Dept"
                      value={arSearch}
                      onChange={(e) => { setArSearch(e.target.value); setPage(0); }}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <SearchIcon sx={{ fontSize: 16, color: '#94a3b8' }} />
                          </InputAdornment>
                        ),
                      }} />
                  </Grid>
                )}
                {canGenerateAttendance && (
                  <Grid item xs={12} sm="auto">
                    <Button size="small" variant="outlined" startIcon={<RefreshIcon />}
                      onClick={handleGenerateAttendanceRange}
                      disabled={arGenerating || !arFromDate || !arToDate || dayjs(arFromDate).isAfter(dayjs(arToDate))}
                      sx={{ textTransform: 'none', borderRadius: '8px' }}>
                      {arGenerating ? 'Recomputing…' : 'Recompute Data'}
                    </Button>
                  </Grid>
                )}
              </>
            )}

            {/* Work Report filters — Department + Location only, auto-loads today */}
            {isWorkReport && (
              <>
                <Grid item xs={12} sm={2}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Department</InputLabel>
                    <Select value={wrDeptFilter} label="Department"
                      onChange={(e) => { setWrDeptFilter(e.target.value); setPage(0); }}>
                      {wrDeptOptions.map(d => (
                        <MenuItem key={d} value={d}>{d === 'ALL' ? 'All Departments' : d}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} sm={2}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Location</InputLabel>
                    <Select value={wrLocFilter} label="Location"
                      onChange={(e) => { setWrLocFilter(e.target.value); setPage(0); }}>
                      {wrLocOptions.map(l => (
                        <MenuItem key={l} value={l}>{l === 'ALL' ? 'All Locations' : l}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
              </>
            )}

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
                {/* <Grid item xs={12} sm={2}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Salary Status</InputLabel>
                    <Select value={sumStatus} label="Salary Status"
                      onChange={(e) => { setSumStatus(e.target.value); setPage(0); }}>
                      <MenuItem value="ALL">All Status</MenuItem>
                      <MenuItem value="Active">Active</MenuItem>
                      <MenuItem value="Inactive">Inactive</MenuItem>
                    </Select>
                  </FormControl>
                </Grid> */}
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

            <Grid item sx={{ ml: 'auto', display: 'flex', gap: 1.5 }}>
              {!isSummary && !isLeave && !isWorkReport && !isAttendance && (
                <Button variant="outlined" startIcon={<DownloadIcon />}
                  onClick={exportCSV} disabled={displayRows.length === 0}>
                  Export CSV
                </Button>
              )}
              {isAttendance && isManagerOrAbove && (
                <Button variant="outlined" startIcon={<DownloadIcon />}
                  onClick={handleExportAttendanceCsv} disabled={filteredAttendance.length === 0}>
                  Export CSV
                </Button>
              )}
              <Button variant="contained" startIcon={<PlayArrowIcon />}
                onClick={handleGenerate}
                sx={{ borderRadius: '10px', fontWeight: 600, fontSize: 13 }}>
                Generate Report
              </Button>
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
              {isWorkReport && generated && (
                <Box sx={{ display: 'flex', gap: 2, mt: 0.5, alignItems: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    {new Date().toLocaleDateString('en-GB', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })}
                  </Typography>
                  {wrLoading
                    ? <CircularProgress size={14} />
                    : <>
                        <Chip label={`${filteredWrData.length} entries`} size="small" color="primary" variant="outlined" />
                        <Chip label={`${wrTotalHours} total hrs`} size="small"
                          sx={{ bgcolor: '#d1fae5', color: '#065f46', fontWeight: 700 }} />
                        <Chip label={`${new Set(filteredWrData.map(r => r.projectName)).size} projects`} size="small"
                          sx={{ bgcolor: '#eff6ff', color: '#1d4ed8', fontWeight: 700 }} />
                      </>
                  }
                </Box>
              )}
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              {generated && (
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
                  Export Summary
                </Button>
              )}
            </Box>
          </Box>

          {isAttendance && generated && !arLoading && arReports.length > 0 && (
            <>
              {/* ── Status filter chips ── */}
              <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
                {AR_STATUS_FILTERS.map((s) => {
                  const style = s === 'ALL' ? { label: 'All', color: '#1e3a5f', bg: '#eef2f7' } : AR_STATUS_STYLES[s];
                  const active = arStatusFilter === s;
                  return (
                    <Chip key={s} label={style.label} size="small" onClick={() => { setArStatusFilter(s); setPage(0); }}
                      sx={{
                        fontWeight: 600, fontSize: 12, cursor: 'pointer',
                        bgcolor: active ? style.color : style.bg,
                        color: active ? '#fff' : style.color,
                        border: active ? 'none' : `1px solid ${style.color}33`,
                      }} />
                  );
                })}
              </Box>
            </>
          )}

          <TableContainer sx={{ overflowX: 'auto' }}>
            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress />
              </Box>
            ) : !generated ? (
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 6, gap: 1 }}>
                <TableChartIcon sx={{ fontSize: 36, color: '#cbd5e1' }} />
                <Typography variant="body2" color="text.secondary">
                  Choose your filters, then click <strong>Generate Report</strong> to view data.
                </Typography>
              </Box>
            ) : renderTable()}
          </TableContainer>

          {generated && displayRows.length > 0 && (
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
