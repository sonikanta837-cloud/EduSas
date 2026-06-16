import React, { useEffect, useLayoutEffect, useState, useMemo, useCallback, useRef } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Typography, IconButton, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Button, Dialog,
  DialogTitle, DialogContent, DialogActions, TextField,
  CircularProgress, Tooltip, InputBase,
} from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import TodayIcon from '@mui/icons-material/Today';
import AddIcon from '@mui/icons-material/Add';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import dayjs from 'dayjs';
import { timesheetEntryApi } from '../api/timesheetEntryApi';
import { employeeApi } from '../api/employeeApi';
import { leaveApi }      from '../api/leaveApi';
import { toast } from 'react-toastify';

const DAY_ABBR = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];

const COL_ACTIVITIES = 200;
const COL_TOTAL      = 48;
const COL_DAY_MIN    = 22;

// ─── Editable cell ──────────────────────────────────────────────────────────
const EditableCell = ({ value, onSave, weekend, holiday, holidayName, today, colW }) => {
  const [editing, setEditing] = useState(false);
  const [val, setVal]         = useState('');
  const inputRef              = useRef();

  const blocked = weekend || holiday;

  const startEdit = () => {
    if (blocked) return;
    setVal(value != null ? String(value) : '');
    setEditing(true);
  };

  useEffect(() => {
    if (editing && inputRef.current) inputRef.current.focus();
  }, [editing]);

  const commit = () => {
    setEditing(false);
    const num = parseFloat(val);
    onSave(isNaN(num) || num <= 0 ? null : num);
  };

  const cellBg    = today ? '#dbeafe' : holiday ? '#fef3c7' : weekend ? '#f3f4f6' : 'white';
  const textColor = holiday ? '#92400e' : weekend ? '#9ca3af' : today ? '#1d4ed8' : '#374151';
  const cellSx    = {
    p: 0, width: colW, minWidth: colW, maxWidth: colW,
    textAlign: 'center',
  };

  if (editing) {
    return (
      <TableCell sx={{ ...cellSx, bgcolor: 'white', border: '2px solid #3b82f6 !important', zIndex: 2 }}>
        <InputBase
          inputRef={inputRef}
          value={val}
          onChange={(e) => setVal(e.target.value)}
          onBlur={commit}
          onKeyDown={(e) => {
            if (e.key === 'Enter') commit();
            if (e.key === 'Escape') setEditing(false);
          }}
          inputProps={{ style: { textAlign: 'center', fontSize: 12, width: colW - 4, padding: '4px 0' } }}
        />
      </TableCell>
    );
  }

  const cell = (
    <TableCell
      onClick={startEdit}
      sx={{
        ...cellSx,
        cursor: blocked ? 'default' : 'pointer',
        bgcolor: cellBg, userSelect: 'none',
        '&:hover': { bgcolor: blocked ? cellBg : '#eff6ff' },
        fontSize: 12, color: textColor, fontWeight: value ? 600 : 400,
        position: 'relative',
      }}
    >
      {holiday
        ? <span style={{ fontSize: 10 }}>🏖</span>
        : value != null ? value : <span style={{ color: '#d1d5db' }}>-</span>
      }
    </TableCell>
  );

  return holiday
    ? <Tooltip title={holidayName || 'Public Holiday'} placement="top" arrow>{cell}</Tooltip>
    : cell;
};

// ─── Main component ──────────────────────────────────────────────────────────
const TimesheetsPage = () => {
  const { user } = useSelector((s) => s.auth);

  const [myEmployee,   setMyEmployee]   = useState(null);
  const [currentMonth, setCurrentMonth] = useState(dayjs().startOf('month'));
  const [rows,         setRows]         = useState([]);
  const [expanded,     setExpanded]     = useState({});
  const [loading,      setLoading]      = useState(true);
  const [addOpen,      setAddOpen]      = useState(false);
  const [newRow,       setNewRow]       = useState({ projectName: '', taskName: '' });
  const [saving,       setSaving]       = useState(false);
  const [colDayWidth,  setColDayWidth]  = useState(0);   // 0 = not measured yet
  const [holidayDates, setHolidayDates] = useState({});  // { 'YYYY-MM-DD': 'Holiday Name' }

  const containerRef = useRef(null);
  const measureRef   = useRef(null);   // measures the exact TableContainer width

  // Days array for the current month
  const days = useMemo(() => {
    const count = currentMonth.daysInMonth();
    return Array.from({ length: count }, (_, i) => currentMonth.date(i + 1));
  }, [currentMonth]);

  const todayStr = dayjs().format('YYYY-MM-DD');

  // ── Dynamic column width ──────────────────────────────────────────────────
  useLayoutEffect(() => {
    if (!measureRef.current) return;
    const calc = () => {
      const w = measureRef.current.offsetWidth;
      if (!w) return;
      const available = w - COL_ACTIVITIES - COL_TOTAL - 4;
      setColDayWidth(Math.max(COL_DAY_MIN, Math.floor(available / days.length)));
    };
    calc();
    const ro = new ResizeObserver(calc);
    ro.observe(measureRef.current);
    return () => ro.disconnect();
  }, [days.length]);

  // ── Load entries ──────────────────────────────────────────────────────────
  const loadEntries = useCallback(async (emp) => {
    if (!emp) return;
    setLoading(true);
    try {
      const entries = await timesheetEntryApi.getMonthly(emp.id, currentMonth.year(), currentMonth.month() + 1);
      const rowMap = {};
      entries.forEach((e) => {
        const key = `${e.projectName}||${e.taskName || ''}`;
        if (!rowMap[key]) rowMap[key] = { rowId: key, projectName: e.projectName, taskName: e.taskName || '', entries: {} };
        rowMap[key].entries[e.date] = { id: e.id, hours: e.hours };
      });
      const built = Object.values(rowMap);
      setRows(built);
      const exp = {};
      built.forEach((r) => { exp[r.projectName] = true; });
      setExpanded(exp);
    } catch {
      toast.error('Failed to load timesheet');
    } finally {
      setLoading(false);
    }
  }, [currentMonth]);

  useEffect(() => {
    employeeApi.getByUserId(user.userId)
      .then((emp) => { setMyEmployee(emp); loadEntries(emp); })
      .catch(() => { toast.error('Could not load your employee profile'); setLoading(false); });
  }, [user, loadEntries]);

  useEffect(() => {
    if (myEmployee) loadEntries(myEmployee);
  }, [currentMonth]); // eslint-disable-line

  // ── Load leaves + public holidays, build holidayDates map ───────────────────
  useEffect(() => {
    if (!myEmployee) return;
    let cancelled = false;
    const monthStart = currentMonth.startOf('month');
    const monthEnd   = currentMonth.endOf('month');

    Promise.allSettled([
      leaveApi.getMyLeaves(myEmployee.id),
      leaveApi.getPublicHolidays(),
    ]).then(([lvRes, phRes]) => {
      if (cancelled) return;
      const leaves   = lvRes.status === 'fulfilled' && Array.isArray(lvRes.value) ? lvRes.value : [];
      const holidays = phRes.status === 'fulfilled' && Array.isArray(phRes.value) ? phRes.value : [];

      const map = {};
      leaves
        .filter((l) => l.status === 'APPROVED' &&
          !dayjs(l.endDate).isBefore(monthStart) &&
          !dayjs(l.startDate).isAfter(monthEnd))
        .forEach((l) => {
          let cur = dayjs(l.startDate);
          const end = dayjs(l.endDate);
          while (!cur.isAfter(end)) {
            const ds = cur.format('YYYY-MM-DD');
            if (!map[ds]) map[ds] = l.reason || l.leaveType || 'Leave';
            cur = cur.add(1, 'day');
          }
        });
      holidays.forEach((h) => {
        if (!h.date) return;
        const ds = dayjs(h.date).format('YYYY-MM-DD');
        map[ds] = h.name || 'Public Holiday';
      });

      setHolidayDates(map);
    });

    return () => { cancelled = true; };
  }, [myEmployee, currentMonth]);

  // ── Cell save ─────────────────────────────────────────────────────────────
  const handleCellSave = useCallback(async (rowId, dateStr, hours) => {
    const row = rows.find((r) => r.rowId === rowId);
    if (!row) return;
    const existing = row.entries[dateStr];

    if (hours == null) {
      if (existing?.id) {
        setSaving(true);
        try { await timesheetEntryApi.delete(existing.id); } catch { toast.error('Failed to delete'); }
        setSaving(false);
      }
      setRows((prev) => prev.map((r) => {
        if (r.rowId !== rowId) return r;
        const newEntries = { ...r.entries };
        delete newEntries[dateStr];
        return { ...r, entries: newEntries };
      }));
      return;
    }

    setSaving(true);
    try {
      const saved = await timesheetEntryApi.save({
        id: existing?.id || null,
        employeeId: myEmployee.id,
        date: dateStr,
        projectName: row.projectName,
        taskName: row.taskName,
        hours,
      });
      setRows((prev) => prev.map((r) => {
        if (r.rowId !== rowId) return r;
        return { ...r, entries: { ...r.entries, [dateStr]: { id: saved.id, hours: saved.hours } } };
      }));
    } catch { toast.error('Failed to save hours'); }
    setSaving(false);
  }, [rows, myEmployee]);

  // ── Open add dialog (optionally pre-fill project name) ─────────────────────
  const openAddDialog = (prefillProject = '') => {
    setNewRow({ projectName: prefillProject, taskName: '' });
    setAddOpen(true);
  };

  // ── Add row ───────────────────────────────────────────────────────────────
  const handleAddRow = () => {
    if (!newRow.projectName.trim()) { toast.error('Project name is required'); return; }
    const key = `${newRow.projectName.trim()}||${newRow.taskName.trim()}`;
    if (rows.find((r) => r.rowId === key)) { toast.error('This row already exists'); return; }
    const r = { rowId: key, projectName: newRow.projectName.trim(), taskName: newRow.taskName.trim(), entries: {} };
    setRows((prev) => [...prev, r]);
    setExpanded((prev) => ({ ...prev, [r.projectName]: true }));
    setAddOpen(false);
    setNewRow({ projectName: '', taskName: '' });
  };

  // ── Delete row ────────────────────────────────────────────────────────────
  const handleDeleteRow = async (rowId) => {
    const row = rows.find((r) => r.rowId === rowId);
    if (!row) return;
    setSaving(true);
    try {
      await timesheetEntryApi.deleteProject(myEmployee.id, row.projectName, row.taskName);
      setRows((prev) => prev.filter((r) => r.rowId !== rowId));
      toast.success('Row removed');
    } catch { toast.error('Failed to remove row'); }
    setSaving(false);
  };

  // ── Derived totals ────────────────────────────────────────────────────────
  const rowTotal        = (row)      => Object.values(row.entries).reduce((s, e) => s + (e.hours || 0), 0);
  const dayTotal        = (dateStr)  => rows.reduce((s, r) => s + (r.entries[dateStr]?.hours || 0), 0);
  const grandTotal      = rows.reduce((s, r) => s + rowTotal(r), 0);
  const projectTotal    = (pName)    => (projectGroups[pName] || []).reduce((s, r) => s + rowTotal(r), 0);
  const projectDayTotal = (pName, dateStr) =>
    (projectGroups[pName] || []).reduce((s, r) => s + (r.entries[dateStr]?.hours || 0), 0);

  const projectGroups = useMemo(() => {
    const g = {};
    rows.forEach((r) => {
      if (!g[r.projectName]) g[r.projectName] = [];
      g[r.projectName].push(r);
    });
    return g;
  }, [rows]);

  // ── Style helpers ─────────────────────────────────────────────────────────
  const headerBg   = '#1e293b';
  const stickyCell = (left, bg = 'white') => ({ position: 'sticky', left, zIndex: 3, bgcolor: bg, whiteSpace: 'nowrap' });
  const stickyHdr  = (left) => ({ position: 'sticky', left, zIndex: 4, bgcolor: headerBg });

  return (
    <Box ref={containerRef} sx={{ height: '100%', display: 'flex', flexDirection: 'column', minWidth: 0 }}>
      {/* ── Top bar ───────────────────────────────────────────────────────── */}
      <Box sx={{
        display: 'flex', alignItems: 'center', gap: 2, mb: 2,
        bgcolor: 'white', p: 1.5, borderRadius: 2, boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
        flexWrap: 'wrap',
      }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: '50%', bgcolor: '#e2e8f0',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Typography variant="caption" fontWeight={700} color="text.secondary">
              {myEmployee?.firstName?.charAt(0)}
            </Typography>
          </Box>
          <Typography variant="body2" fontWeight={600}>{myEmployee?.fullName}</Typography>
        </Box>

        <Button size="small" variant="outlined" startIcon={<TodayIcon />}
          onClick={() => setCurrentMonth(dayjs().startOf('month'))} sx={{ ml: 'auto' }}>
          Today
        </Button>
        <IconButton size="small" onClick={() => setCurrentMonth((m) => m.subtract(1, 'month'))}>
          <ChevronLeftIcon />
        </IconButton>
        <Typography variant="body2" fontWeight={600} sx={{ minWidth: 200, textAlign: 'center' }}>
          {currentMonth.format('MMMM D, YYYY')} – {currentMonth.endOf('month').format('MMMM D, YYYY')}
        </Typography>
        <IconButton size="small" onClick={() => setCurrentMonth((m) => m.add(1, 'month'))}>
          <ChevronRightIcon />
        </IconButton>

        {saving && <CircularProgress size={16} />}

        <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={() => openAddDialog()}>
          Add Row
        </Button>
      </Box>


      {/* measureRef: always in DOM so useLayoutEffect can measure on first render */}
      <div ref={measureRef} style={{ width: '100%' }} />

      {/* ── Grid ──────────────────────────────────────────────────────────── */}
      <TableContainer sx={{
        flex: 1,
        overflowX: 'auto',
        overflowY: 'auto',
        borderRadius: 2,
        boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
        bgcolor: 'white',
        maxHeight: 'calc(100vh - 190px)',
      }}>
        {/* Show data-loading spinner inside the container (not an early return) */}
        {loading || colDayWidth === 0 ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}><CircularProgress /></Box>
        ) : (
        <Table size="small" stickyHeader sx={{ borderCollapse: 'separate', tableLayout: 'fixed',
          width: COL_ACTIVITIES + COL_TOTAL + colDayWidth * days.length }}>

          {/* ── Column header ── */}
          <TableHead>
            <TableRow sx={{ height: 40 }}>
              <TableCell sx={{
                ...stickyHdr(0),
                width: COL_ACTIVITIES, minWidth: COL_ACTIVITIES,
                color: 'white', fontWeight: 700, fontSize: 13, py: 1,
              }}>
                Activities
              </TableCell>
              <TableCell sx={{
                ...stickyHdr(COL_ACTIVITIES),
                width: COL_TOTAL, minWidth: COL_TOTAL, textAlign: 'center',
                color: 'white', fontWeight: 700, fontSize: 13, py: 1,
              }}>
                Σ
              </TableCell>
              {days.map((d) => {
                const dateStr   = d.format('YYYY-MM-DD');
                const isWeekend = d.day() === 0 || d.day() === 6;
                const isToday   = dateStr === todayStr;
                const isHoliday = !isWeekend && !!holidayDates[dateStr];
                const hdName    = holidayDates[dateStr];
                const bgColor   = isToday ? '#1d4ed8' : isHoliday ? '#b45309' : isWeekend ? '#334155' : headerBg;
                const headerCell = (
                  <TableCell key={d.date()}
                    style={{ backgroundColor: bgColor }}
                    sx={{
                      width: colDayWidth, minWidth: colDayWidth, maxWidth: colDayWidth,
                      textAlign: 'center', p: 0, py: 0.5,
                      color: (isWeekend && !isToday) ? '#94a3b8' : 'white',
                      fontWeight: isToday ? 700 : 400,
                    }}>
                    {colDayWidth >= 32 && (
                      <Typography sx={{ fontSize: 8, lineHeight: 1, color: 'inherit' }}>
                        {isHoliday ? '🏖' : DAY_ABBR[d.day()]}
                      </Typography>
                    )}
                    <Typography sx={{ fontSize: colDayWidth >= 32 ? 12 : 10, fontWeight: 600, color: 'inherit', lineHeight: 1.4 }}>
                      {String(d.date()).padStart(2, '0')}
                    </Typography>
                  </TableCell>
                );
                return isHoliday
                  ? <Tooltip key={d.date()} title={hdName} placement="top" arrow>{headerCell}</Tooltip>
                  : headerCell;
              })}
            </TableRow>


          </TableHead>

          <TableBody>
            {/* ── Grand total row ── */}
            <TableRow sx={{ bgcolor: '#f1f5f9', height: 36 }}>
              <TableCell sx={{ ...stickyCell(0, '#f1f5f9'), pl: 2, fontSize: 13, color: '#475569' }} />
              <TableCell sx={{
                ...stickyCell(COL_ACTIVITIES, '#f1f5f9'),
                textAlign: 'center', fontWeight: 700, fontSize: 13, color: '#1e293b',
              }}>
                {grandTotal || ''}
              </TableCell>
              {days.map((d) => {
                const dateStr   = d.format('YYYY-MM-DD');
                const total     = dayTotal(dateStr);
                const isWeekend = d.day() === 0 || d.day() === 6;
                const isToday   = dateStr === todayStr;
                const isHoliday = !isWeekend && !!holidayDates[dateStr];
                return (
                  <TableCell key={dateStr} sx={{
                    textAlign: 'center', fontSize: 11, fontWeight: total ? 700 : 400,
                    color: total ? '#1e293b' : '#cbd5e1',
                    bgcolor: isToday ? '#dbeafe' : isHoliday ? '#fef3c7' : isWeekend ? '#f3f4f6' : '#f1f5f9',
                    width: colDayWidth, minWidth: colDayWidth, maxWidth: colDayWidth,
                  }}>
                    {total || '-'}
                  </TableCell>
                );
              })}
            </TableRow>

            {/* ── PROJECTS section label ── */}
            <TableRow sx={{ bgcolor: '#f8fafc', height: 32 }}>
              <TableCell colSpan={2 + days.length} sx={{ ...stickyCell(0, '#f8fafc'), pl: 2 }}>
                <Typography variant="caption" fontWeight={700} color="text.secondary" letterSpacing={1}>
                  PROJECTS
                </Typography>
              </TableCell>
            </TableRow>

            {Object.entries(projectGroups).map(([pName, pRows]) => (
              <React.Fragment key={pName}>
                {/* ── Project header row ── */}
                <TableRow
                  hover
                  sx={{ bgcolor: '#fafafa', cursor: 'pointer', height: 36 }}
                  onClick={() => setExpanded((p) => ({ ...p, [pName]: !p[pName] }))}
                >
                  <TableCell sx={{ ...stickyCell(0, '#fafafa'), pl: 1, fontSize: 13, fontWeight: 600 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      {expanded[pName]
                        ? <KeyboardArrowDownIcon fontSize="small" />
                        : <KeyboardArrowRightIcon fontSize="small" />}
                      <span style={{ flex: 1 }}>{pName}</span>
                      {/* Add task button within this project */}
                      <Tooltip title={`Add task to ${pName}`}>
                        <IconButton
                          size="small"
                          onClick={(e) => { e.stopPropagation(); openAddDialog(pName); }}
                          sx={{
                            opacity: 0, '.MuiTableRow-root:hover &': { opacity: 1 },
                            color: '#1976d2', p: 0.3,
                          }}
                        >
                          <AddIcon sx={{ fontSize: 15 }} />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </TableCell>
                  <TableCell sx={{
                    ...stickyCell(COL_ACTIVITIES, '#fafafa'),
                    textAlign: 'center', fontWeight: 700, fontSize: 13,
                  }}>
                    {projectTotal(pName) || ''}
                  </TableCell>
                  {days.map((d) => {
                    const dateStr   = d.format('YYYY-MM-DD');
                    const total     = projectDayTotal(pName, dateStr);
                    const isWeekend = d.day() === 0 || d.day() === 6;
                    const isToday   = dateStr === todayStr;
                    const isHoliday = !isWeekend && !!holidayDates[dateStr];
                    return (
                      <TableCell key={dateStr} sx={{
                        textAlign: 'center', fontSize: 11,
                        bgcolor: isToday ? '#dbeafe' : isHoliday ? '#fef3c7' : isWeekend ? '#f3f4f6' : '#fafafa',
                        color: total ? '#1e293b' : '#d1d5db',
                        width: colDayWidth, minWidth: colDayWidth, maxWidth: colDayWidth,
                      }}>
                        {total || '-'}
                      </TableCell>
                    );
                  })}
                </TableRow>

                {/* ── Task rows ── */}
                {expanded[pName] && pRows.map((row) => (
                  <TableRow key={row.rowId} hover sx={{ height: 34 }}>
                    <TableCell sx={{ ...stickyCell(0), pl: 4, fontSize: 13 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pr: 1 }}>
                        <Typography variant="body2" noWrap sx={{ maxWidth: COL_ACTIVITIES - 60 }}>
                          {row.taskName || <em style={{ color: '#9ca3af' }}>No task name</em>}
                        </Typography>
                        <Tooltip title="Remove row">
                          <IconButton
                            size="small"
                            onClick={(e) => { e.stopPropagation(); handleDeleteRow(row.rowId); }}
                            sx={{ opacity: 0, '.MuiTableRow-root:hover &': { opacity: 1 }, color: '#ef4444' }}
                          >
                            <DeleteOutlineIcon sx={{ fontSize: 15 }} />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                    <TableCell sx={{
                      ...stickyCell(COL_ACTIVITIES),
                      textAlign: 'center', fontWeight: 600, fontSize: 13,
                    }}>
                      {rowTotal(row) || ''}
                    </TableCell>
                    {days.map((d) => {
                      const dateStr   = d.format('YYYY-MM-DD');
                      const entry     = row.entries[dateStr];
                      const isWeekend = d.day() === 0 || d.day() === 6;
                      const isToday   = dateStr === todayStr;
                      const isHoliday = !isWeekend && !!holidayDates[dateStr];
                      return (
                        <EditableCell
                          key={dateStr}
                          value={entry?.hours ?? null}
                          onSave={(h) => handleCellSave(row.rowId, dateStr, h)}
                          weekend={isWeekend}
                          holiday={isHoliday}
                          holidayName={holidayDates[dateStr]}
                          today={isToday}
                          colW={colDayWidth}
                        />
                      );
                    })}
                  </TableRow>
                ))}
              </React.Fragment>
            ))}

            {/* ── Empty state ── */}
            {Object.keys(projectGroups).length === 0 && (
              <TableRow>
                <TableCell colSpan={2 + days.length} sx={{ textAlign: 'center', py: 4, color: '#9ca3af' }}>
                  No projects yet. Click <strong>Add Row</strong> to start tracking hours.
                </TableCell>
              </TableRow>
            )}

            <TableRow sx={{ height: 8 }} />
          </TableBody>
        </Table>
        )}
      </TableContainer>

      {/* ── Add row dialog ────────────────────────────────────────────────── */}
      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle fontWeight={700}>Add Timesheet Row</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField
              label="Project Name" placeholder="e.g. EDJO-TRDP" fullWidth
              value={newRow.projectName}
              onChange={(e) => setNewRow({ ...newRow, projectName: e.target.value })}
              onKeyDown={(e) => e.key === 'Enter' && handleAddRow()}
              autoFocus={!newRow.projectName}
            />
            <TextField
              label="Task / Activity Name" placeholder="e.g. Delivery Streams" fullWidth
              value={newRow.taskName}
              onChange={(e) => setNewRow({ ...newRow, taskName: e.target.value })}
              onKeyDown={(e) => e.key === 'Enter' && handleAddRow()}
              autoFocus={!!newRow.projectName}
            />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleAddRow}>Submit</Button>
        </DialogActions>
      </Dialog>

    </Box>
  );
};

export default TimesheetsPage;
