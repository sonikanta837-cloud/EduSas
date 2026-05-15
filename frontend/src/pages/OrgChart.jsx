import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import {
  Box, Typography, CircularProgress, Tooltip, IconButton, Button,
  Divider, Tabs, Tab, Avatar, Chip, TextField,
  Popover, List, ListItemButton, ListItemIcon, ListItemText,
} from '@mui/material';
import PersonIcon          from '@mui/icons-material/Person';
import OpenInNewIcon       from '@mui/icons-material/OpenInNew';
import PeopleIcon          from '@mui/icons-material/People';
import AccountTreeIcon     from '@mui/icons-material/AccountTree';
import BusinessIcon        from '@mui/icons-material/Business';
import CakeIcon            from '@mui/icons-material/Cake';
import CalendarMonthIcon   from '@mui/icons-material/CalendarMonth';
import AnnouncementIcon    from '@mui/icons-material/Announcement';
import GridViewIcon        from '@mui/icons-material/GridView';
import ListAltIcon         from '@mui/icons-material/ListAlt';
import ChevronRightIcon    from '@mui/icons-material/ChevronRight';
import ChevronLeftIcon     from '@mui/icons-material/ChevronLeft';
import AddIcon             from '@mui/icons-material/Add';
import DeleteIcon          from '@mui/icons-material/Delete';
import SearchIcon          from '@mui/icons-material/Search';
import CloseIcon           from '@mui/icons-material/Close';
import StarBorderIcon      from '@mui/icons-material/StarBorder';
import StarIcon            from '@mui/icons-material/Star';
import PhoneIcon           from '@mui/icons-material/Phone';
import CallIcon            from '@mui/icons-material/Call';
import ChatIcon            from '@mui/icons-material/Chat';
import { employeeApi }      from '../api/employeeApi';
import { announcementApi }  from '../api/announcementApi';
import { toast }            from 'react-toastify';

// ── Layout constants ──────────────────────────────────────────────────────────
const CARD_W = 200;
const CARD_H = 68;
const LINE   = '#cbd5e1';
const BLUE   = '#14b8a6';

const DEPT_COLORS = [
  { bg: '#ede9fe', text: '#7c3aed', border: '#c4b5fd' },
  { bg: '#dbeafe', text: '#1d4ed8', border: '#93c5fd' },
  { bg: '#dcfce7', text: '#15803d', border: '#86efac' },
  { bg: '#fef9c3', text: '#a16207', border: '#fde047' },
  { bg: '#ffe4e6', text: '#be123c', border: '#fda4af' },
  { bg: '#e0f2fe', text: '#0369a1', border: '#7dd3fc' },
];

const CAL_MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
const CAL_DAYS   = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
const SHORT_MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

// ── Employee Tree View ────────────────────────────────────────────────────────
const MC_ROW_H   = 80;   // fixed row height — ensures every center lands at ROW_H/2 = 40px
const MC_FULL_W  = 260;  // card width
const H_CONN_W   = 40;   // horizontal connector: root → children column
const BRANCH_W   = 24;   // horizontal branch inside children column
const COMPACT_SZ = 44;   // compact circle diameter

// Shared full-size card (used for root, children, grandchildren)
const TreeCard = ({ node, selected, loading, onClick, onOpen }) => {
  const hasKids = node.subordinateCount > 0;
  return (
    <Box
      onClick={onClick}
      sx={{
        width: MC_FULL_W, height: CARD_H, flexShrink: 0,
        display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5,
        border: selected ? `2px solid ${BLUE}` : '1.5px solid #d1d5db',
        borderRadius: '10px',
        bgcolor: selected ? '#f0fdfb' : 'white',
        boxShadow: selected ? '0 2px 12px rgba(20,184,166,0.2)' : '0 1px 4px rgba(0,0,0,0.08)',
        cursor: onClick ? 'pointer' : 'default',
        transition: 'all 0.15s',
        '&:hover .ol': { opacity: 1 },
        ...(!selected && onClick ? { '&:hover': { borderColor: BLUE, bgcolor: '#f0fdfb', boxShadow: '0 2px 12px rgba(20,184,166,0.15)' } } : {}),
      }}
    >
      <Box sx={{ width: 32, height: 32, borderRadius: '50%', bgcolor: '#f0f2f5', border: '1px solid #e5e7eb', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <PersonIcon sx={{ fontSize: 18, color: '#9aa0a6' }} />
      </Box>
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Typography sx={{ fontSize: 12.5, fontWeight: 700, color: '#1e293b', lineHeight: 1.3 }} noWrap>{node.fullName}</Typography>
        <Typography sx={{ fontSize: 11, color: '#64748b', lineHeight: 1.3 }} noWrap>{node.position || node.role || '—'}</Typography>
      </Box>
      <Tooltip title="View profile">
        <IconButton className="ol" size="small"
          onClick={(e) => { e.stopPropagation(); onOpen(node.id); }}
          sx={{ opacity: 0, p: 0.3, transition: 'opacity 0.15s', flexShrink: 0 }}>
          <OpenInNewIcon sx={{ fontSize: 12 }} />
        </IconButton>
      </Tooltip>
      {hasKids && (
        <Box sx={{
          minWidth: 26, px: '5px', height: 20, flexShrink: 0,
          bgcolor: selected ? BLUE : '#e2e8f0',
          color: selected ? 'white' : '#64748b',
          borderRadius: '5px', fontSize: 11, fontWeight: 700,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          {loading ? <CircularProgress size={9} sx={{ color: selected ? 'white' : '#64748b' }} /> : node.subordinateCount}
        </Box>
      )}
    </Box>
  );
};

// Module-level column renderer — avoids defining a component inside another component's render
function renderChildrenCol(childIds, selectedId, onChildClick, onGrandchildClick, nodes, loadingId, onOpen) {
  return (
    <Box sx={{ position: 'relative', flexShrink: 0 }}>
      {childIds.length > 1 && (
        <Box sx={{
          position: 'absolute', zIndex: 0,
          left: 0,
          top: `${MC_ROW_H / 2}px`,
          bottom: `${MC_ROW_H / 2}px`,
          width: 2, bgcolor: '#cbd5e1',
        }} />
      )}
      {childIds.map((cid) => {
        const child   = nodes.get(cid);
        if (!child) return null;
        const hasKids = child.subordinateCount > 0;
        const isSel   = selectedId === cid;
        const handler = onGrandchildClick ?? onChildClick;
        return (
          <Box key={cid} sx={{ height: MC_ROW_H, display: 'flex', alignItems: 'center', position: 'relative', zIndex: 1 }}>
            <Box sx={{ width: BRANCH_W, height: 2, bgcolor: isSel ? BLUE : '#cbd5e1', flexShrink: 0 }} />
            <TreeCard
              node={child} selected={isSel}
              loading={loadingId === cid}
              onClick={hasKids ? () => handler(cid) : undefined}
              onOpen={onOpen}
            />
            {hasKids && !isSel && (
              <ChevronRightIcon sx={{ fontSize: 16, color: '#94a3b8', ml: 0.75, flexShrink: 0 }} />
            )}
          </Box>
        );
      })}
    </Box>
  );
}

const EmployeeTreeView = ({ nodes, childrenMap, rootIds, loadChildren, onOpen }) => {
  const [history,         setHistory]         = useState([]);
  const [currentId,       setCurrentId]       = useState(null); // null = multi-root list
  const [selectedChildId, setSelectedChildId] = useState(null); // child selected in col 2
  const [loadingId,       setLoadingId]       = useState(null);

  // Auto-drill into single root on first load
  useEffect(() => {
    if (rootIds.length === 1 && currentId === null) setCurrentId(rootIds[0]);
  }, [rootIds]);

  const isTopLevel  = history.length === 0;
  const currentNode = currentId != null ? nodes.get(currentId) : null;
  const mainIds     = currentId != null ? (childrenMap.get(currentId) || []) : rootIds;
  const selIdx      = selectedChildId != null ? mainIds.indexOf(selectedChildId) : -1;
  const subIds      = selectedChildId != null ? (childrenMap.get(selectedChildId) || []) : [];

  const ensureLoaded = useCallback(async (nodeId) => {
    if (!childrenMap.has(nodeId)) {
      setLoadingId(nodeId);
      try { await loadChildren(nodeId); }
      catch { toast.error('Failed to load team'); }
      finally { setLoadingId(null); }
    }
  }, [childrenMap, loadChildren]);

  // Clicking a child in col 2: toggle selection (shows/hides col 3)
  const handleChildClick = useCallback(async (nodeId) => {
    const node = nodes.get(nodeId);
    if (!node) return;
    if (node.subordinateCount > 0) await ensureLoaded(nodeId);
    setSelectedChildId((prev) => (prev === nodeId ? null : nodeId));
  }, [nodes, ensureLoaded]);

  // Clicking a grandchild in col 3: drill deeper
  const handleGrandchildClick = useCallback(async (nodeId) => {
    const node = nodes.get(nodeId);
    if (!node || node.subordinateCount === 0) return;
    await ensureLoaded(nodeId);
    setHistory((h) => [...h, currentId]);
    setCurrentId(nodeId);
    setSelectedChildId(null);
  }, [nodes, ensureLoaded, currentId]);

  if (!rootIds.length) return (
    <Typography color="text.secondary" textAlign="center" mt={4}>No employees found</Typography>
  );

  // Breadcrumb items
  const breadcrumbs = history.map((id, idx) => ({
    label: id === null ? 'Top' : (nodes.get(id)?.fullName || '...'),
    onClick: () => { setCurrentId(id); setHistory(history.slice(0, idx)); setSelectedChildId(null); },
  }));

  return (
    <Box>
      {/* Breadcrumb — click any ancestor to jump back */}
      {breadcrumbs.length > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 2.5, flexWrap: 'wrap' }}>
          {breadcrumbs.map((crumb, i) => (
            <React.Fragment key={i}>
              <Typography fontSize={12} fontWeight={600} color="text.secondary"
                onClick={crumb.onClick}
                sx={{ cursor: 'pointer', '&:hover': { color: BLUE, textDecoration: 'underline' } }}>
                {crumb.label}
              </Typography>
              <ChevronRightIcon sx={{ fontSize: 14, color: '#cbd5e1' }} />
            </React.Fragment>
          ))}
          {currentNode && (
            <Typography fontSize={12} color="text.primary" fontWeight={700}>{currentNode.fullName}</Typography>
          )}
        </Box>
      )}

      <Box sx={{ overflowX: 'auto', overflowY: 'auto', maxHeight: 520, pb: 2 }}>

        {/* ── Multiple roots: plain list ── */}
        {currentId === null ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {rootIds.map((id) => {
              const node = nodes.get(id);
              if (!node) return null;
              return (
                <TreeCard key={id} node={node} selected={false}
                  loading={loadingId === id}
                  onClick={() => { setHistory([null]); setCurrentId(id); setSelectedChildId(null); }}
                  onOpen={onOpen} />
              );
            })}
          </Box>
        ) : (
          <Box sx={{ display: 'inline-flex', alignItems: 'flex-start', minWidth: 'max-content' }}>

            {/* ── Col 0: root node ─────────────────────────────────────────── */}
            {isTopLevel ? (
              /* Top level → full card */
              <Box sx={{ height: MC_ROW_H, display: 'flex', alignItems: 'center', flexShrink: 0 }}>
                {currentNode && <TreeCard node={currentNode} selected loading={false} onClick={undefined} onOpen={onOpen} />}
              </Box>
            ) : (
              /* Drilled in → compact circle */
              <Tooltip title={currentNode?.fullName || ''} placement="top">
                <Box sx={{
                  alignSelf: 'flex-start',
                  mt: `${MC_ROW_H / 2 - COMPACT_SZ / 2}px`,
                  display: 'flex', alignItems: 'center', gap: 0.5, flexShrink: 0, cursor: 'default',
                }}>
                  <Box sx={{
                    width: COMPACT_SZ, height: COMPACT_SZ, borderRadius: '50%',
                    bgcolor: '#f0fdfb', border: `2px solid ${BLUE}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                  }}>
                    <PersonIcon sx={{ fontSize: 22, color: BLUE }} />
                  </Box>
                  {currentNode?.subordinateCount > 0 && (
                    <Box sx={{
                      bgcolor: BLUE, color: 'white', borderRadius: '5px',
                      px: '5px', height: 20, fontSize: 10, fontWeight: 700,
                      minWidth: 26, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                    }}>
                      {currentNode.subordinateCount}
                    </Box>
                  )}
                </Box>
              </Tooltip>
            )}

            {/* Horizontal connector: root → col 1 */}
            {mainIds.length > 0 && (
              <Box sx={{ width: H_CONN_W, height: 2, bgcolor: BLUE, flexShrink: 0, alignSelf: 'flex-start', mt: `${MC_ROW_H / 2 - 1}px` }} />
            )}

            {/* ── Col 1: direct children ───────────────────────────────────── */}
            {mainIds.length > 0 && renderChildrenCol(
              mainIds, selectedChildId, handleChildClick, null, nodes, loadingId, onOpen,
            )}

            {/* ── Col 2: grandchildren (selected child's reports) ──────────── */}
            {selectedChildId && subIds.length > 0 && selIdx >= 0 && (
              <>
                {/* Horizontal connector aligned to selected child's row center */}
                <Box sx={{
                  width: H_CONN_W, height: 2, bgcolor: BLUE, flexShrink: 0,
                  alignSelf: 'flex-start',
                  mt: `${selIdx * MC_ROW_H + MC_ROW_H / 2 - 1}px`,
                }} />
                {/* Column offset so first grandchild aligns with selected child */}
                <Box sx={{ alignSelf: 'flex-start', mt: `${selIdx * MC_ROW_H}px` }}>
                  {renderChildrenCol(subIds, null, handleGrandchildClick, handleGrandchildClick, nodes, loadingId, onOpen)}
                </Box>
              </>
            )}

            {mainIds.length === 0 && (
              <Box sx={{ height: MC_ROW_H, display: 'flex', alignItems: 'center', ml: 4 }}>
                <Typography fontSize={13} color="text.secondary">No direct reports</Typography>
              </Box>
            )}
          </Box>
        )}
      </Box>
    </Box>
  );
};

// ── Overview Tab ──────────────────────────────────────────────────────────────
const OverviewTab = ({ employees }) => {
  const deptMap = useMemo(() => {
    const m = new Map();
    employees.forEach((e) => {
      const d = e.department || 'Unassigned';
      m.set(d, (m.get(d) || 0) + 1);
    });
    return m;
  }, [employees]);

  const stats = [
    { label: 'Total Employees', value: employees.length,                                       color: '#6366f1', bg: '#ede9fe' },
    { label: 'Departments',     value: deptMap.size,                                           color: '#0369a1', bg: '#e0f2fe' },
    { label: 'Managers',        value: employees.filter((e) => e.role === 'MANAGER' || e.role === 'ASSISTANT_MANAGER').length,   color: '#15803d', bg: '#dcfce7' },
    { label: 'HR Staff',        value: employees.filter((e) => e.role === 'HR').length,        color: '#be123c', bg: '#ffe4e6' },
  ];

  const sorted = [...deptMap.entries()].sort((a, b) => b[1] - a[1]);

  return (
    <Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))', gap: 2, mb: 3.5 }}>
        {stats.map((s) => (
          <Box key={s.label} sx={{ p: 2.5, borderRadius: 2, bgcolor: s.bg, border: `1px solid ${s.color}33` }}>
            <Typography fontSize={32} fontWeight={800} sx={{ color: s.color, lineHeight: 1 }}>{s.value}</Typography>
            <Typography fontSize={12.5} color="text.secondary" mt={0.5}>{s.label}</Typography>
          </Box>
        ))}
      </Box>

      <Typography fontWeight={700} fontSize={15} mb={1.5}>Department Breakdown</Typography>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.2 }}>
        {sorted.map(([dept, count], idx) => {
          const color = DEPT_COLORS[idx % DEPT_COLORS.length];
          const pct   = employees.length ? Math.round((count / employees.length) * 100) : 0;
          return (
            <Box key={dept} sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box sx={{ width: 36, height: 36, borderRadius: 1, bgcolor: color.bg, border: `1px solid ${color.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 14, color: color.text, flexShrink: 0 }}>
                {dept.charAt(0)}
              </Box>
              <Box sx={{ width: 140, flexShrink: 0 }}>
                <Typography fontSize={13} fontWeight={600} noWrap>{dept}</Typography>
              </Box>
              <Box sx={{ flex: 1, height: 8, borderRadius: 4, bgcolor: '#f1f5f9', overflow: 'hidden' }}>
                <Box sx={{ width: `${pct}%`, height: '100%', bgcolor: color.text, borderRadius: 4, transition: 'width 0.4s' }} />
              </Box>
              <Typography fontSize={12} color="text.secondary" sx={{ width: 65, textAlign: 'right', flexShrink: 0 }}>
                {count} ({pct}%)
              </Typography>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
};

// ── Announcements Tab ─────────────────────────────────────────────────────────
const AnnouncementsTab = ({ userRole }) => {
  const [items,   setItems]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [open,    setOpen]    = useState(false);
  const [saving,  setSaving]  = useState(false);
  const [title,   setTitle]   = useState('');
  const [body,    setBody]    = useState('');

  const canPost = userRole === 'ADMIN' || userRole === 'HR';

  useEffect(() => {
    announcementApi.getAll()
      .then(setItems)
      .catch(() => toast.error('Failed to load announcements'))
      .finally(() => setLoading(false));
  }, []);

  const post = async () => {
    if (!title.trim()) return;
    setSaving(true);
    try {
      const created = await announcementApi.create({ title, body, pinned: false });
      setItems((prev) => [created, ...prev]);
      setTitle(''); setBody(''); setOpen(false);
      toast.success('Announcement posted');
    } catch {
      toast.error('Failed to post announcement');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id) => {
    try {
      await announcementApi.delete(id);
      setItems((prev) => prev.filter((i) => i.id !== id));
    } catch {
      toast.error('Failed to delete announcement');
    }
  };

  const fmt = (dt) => {
    if (!dt) return '';
    const d = new Date(dt);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  };

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}><CircularProgress /></Box>
  );

  return (
    <Box>
      {canPost && (
        <Box sx={{ mb: 2.5 }}>
          {open ? (
            <Box sx={{ p: 2.5, border: '1px solid #e2e8f0', borderRadius: 2, bgcolor: 'white' }}>
              <Typography fontWeight={700} fontSize={14} mb={1.5}>New Announcement</Typography>
              <TextField fullWidth size="small" label="Title" value={title} onChange={(e) => setTitle(e.target.value)} sx={{ mb: 1.5 }} />
              <TextField fullWidth size="small" label="Message" multiline rows={3} value={body} onChange={(e) => setBody(e.target.value)} sx={{ mb: 1.5 }} />
              <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
                <Button size="small" onClick={() => setOpen(false)} disabled={saving}>Cancel</Button>
                <Button size="small" variant="contained" onClick={post} disabled={saving || !title.trim()}>
                  {saving ? 'Posting…' : 'Post'}
                </Button>
              </Box>
            </Box>
          ) : (
            <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button size="small" variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
                Post Announcement
              </Button>
            </Box>
          )}
        </Box>
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        {items.map((item) => (
          <Box key={item.id} sx={{
            p: 2.5, borderRadius: 2, bgcolor: 'white',
            border: '1px solid #e2e8f0',
            borderLeft: item.pinned ? '4px solid #1976d2' : '1px solid #e2e8f0',
          }}>
            <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5, flexWrap: 'wrap' }}>
                  {item.pinned && <Chip label="Pinned" size="small" color="primary" sx={{ height: 18, fontSize: 10 }} />}
                  <Typography fontWeight={700} fontSize={14}>{item.title}</Typography>
                </Box>
                {item.body && <Typography fontSize={13} color="text.secondary" sx={{ mb: 1 }}>{item.body}</Typography>}
                <Typography fontSize={11} color="text.disabled">{item.authorName} · {fmt(item.createdAt)}</Typography>
              </Box>
              {canPost && !item.pinned && (
                <IconButton size="small" onClick={() => remove(item.id)}>
                  <DeleteIcon sx={{ fontSize: 16 }} />
                </IconButton>
              )}
            </Box>
          </Box>
        ))}
        {!items.length && (
          <Box sx={{ textAlign: 'center', py: 7 }}>
            <AnnouncementIcon sx={{ fontSize: 52, color: '#cbd5e1', mb: 1.5 }} />
            <Typography color="text.secondary">No announcements yet</Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
};

// ── Department Tree Tab ───────────────────────────────────────────────────────
const DEPT_CARD_W = 290; // dept card width in the left column

const DepartmentTreeTab = ({ employees, onNavigate }) => {
  const deptMap = useMemo(() => {
    const m = new Map();
    employees.forEach((e) => {
      const d = e.department || 'Unassigned';
      if (!m.has(d)) m.set(d, []);
      m.get(d).push(e);
    });
    return m;
  }, [employees]);

  const departments = useMemo(() => [...deptMap.keys()].sort(), [deptMap]);
  const [selectedDept, setSelectedDept] = useState(null);

  useEffect(() => {
    if (departments.length > 0 && selectedDept === null) setSelectedDept(departments[0]);
  }, [departments, selectedDept]);

  const selIdx  = selectedDept ? departments.indexOf(selectedDept) : -1;
  const deptEmps = useMemo(
    () => (selectedDept ? (deptMap.get(selectedDept) || []) : []),
    [deptMap, selectedDept],
  );

  // Generate 2-char abbreviation from dept name words (e.g. "Human Resource" → "HR")
  const abbr = (name) => name.split(' ').map((w) => w[0]).join('').substring(0, 2).toUpperCase();

  return (
    <Box sx={{ overflowX: 'auto', overflowY: 'auto', maxHeight: 580, pb: 1 }}>
      <Box sx={{ display: 'inline-flex', alignItems: 'flex-start', minWidth: 'max-content' }}>

        {/* ── Left: department list ── */}
        <Box sx={{ flexShrink: 0 }}>
          {departments.map((dept, idx) => {
            const count = deptMap.get(dept)?.length || 0;
            const isSel = selectedDept === dept;
            const color = DEPT_COLORS[idx % DEPT_COLORS.length];
            return (
              <Box key={dept}
                sx={{ height: MC_ROW_H, display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}
              >
                {/* Dept card */}
                <Box
                  onClick={() => setSelectedDept(dept)}
                  sx={{
                    width: DEPT_CARD_W, height: CARD_H, flexShrink: 0,
                    display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5,
                    border: isSel ? `1.5px solid ${BLUE}` : '1px solid #e2e8f0',
                    borderRadius: '10px',
                    bgcolor: isSel ? '#f0fdfb' : 'white',
                    cursor: 'pointer', transition: 'all 0.15s',
                    boxShadow: isSel ? '0 2px 8px rgba(20,184,166,0.12)' : '0 1px 3px rgba(0,0,0,0.05)',
                    '&:hover': { borderColor: BLUE, bgcolor: '#f0fdfb' },
                  }}
                >
                  <Box sx={{
                    width: 34, height: 34, borderRadius: 1.5, flexShrink: 0,
                    bgcolor: isSel ? 'rgba(20,184,166,0.15)' : color.bg,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontWeight: 800, fontSize: 13, color: isSel ? BLUE : color.text,
                    letterSpacing: '0.02em',
                  }}>
                    {abbr(dept)}
                  </Box>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography fontWeight={600} fontSize={13.5} noWrap sx={{ color: isSel ? BLUE : '#1e293b' }}>
                      {dept}
                    </Typography>
                    <Typography fontSize={11} color="text.disabled">-</Typography>
                  </Box>
                </Box>

                {/* Count badge — outside the card, to the right */}
                <Box sx={{
                  minWidth: 28, height: 22, px: '6px', flexShrink: 0,
                  bgcolor: isSel ? BLUE : '#e2e8f0',
                  color: isSel ? 'white' : '#475569',
                  borderRadius: '5px', fontSize: 11, fontWeight: 700,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  {count}
                </Box>
              </Box>
            );
          })}
        </Box>

        {/* ── Horizontal connector from selected dept badge → employee column ── */}
        {selectedDept && deptEmps.length > 0 && (
          <Box sx={{
            width: H_CONN_W, height: 2, bgcolor: BLUE, flexShrink: 0,
            alignSelf: 'flex-start',
            mt: `${selIdx * MC_ROW_H + MC_ROW_H / 2 - 1}px`,
          }} />
        )}

        {/* ── Employee column (offset to align first row with selected dept) ── */}
        {selectedDept && deptEmps.length > 0 && (
          <Box sx={{ position: 'relative', flexShrink: 0, alignSelf: 'flex-start', mt: `${selIdx * MC_ROW_H}px` }}>
            {/* Vertical line from center-of-row-0 to center-of-last-row */}
            {deptEmps.length > 1 && (
              <Box sx={{
                position: 'absolute', zIndex: 0,
                left: 0,
                top: `${MC_ROW_H / 2}px`,
                bottom: `${MC_ROW_H / 2}px`,
                width: 2, bgcolor: '#cbd5e1',
              }} />
            )}

            {deptEmps.map((emp) => (
              <Box key={emp.id}
                sx={{ height: MC_ROW_H, display: 'flex', alignItems: 'center', position: 'relative', zIndex: 1 }}
              >
                {/* Horizontal branch */}
                <Box sx={{ width: BRANCH_W, height: 2, bgcolor: '#cbd5e1', flexShrink: 0 }} />

                {/* Employee card */}
                <Box
                  onClick={onNavigate ? () => onNavigate(emp.id) : undefined}
                  sx={{
                    width: MC_FULL_W, height: CARD_H, flexShrink: 0,
                    display: 'flex', alignItems: 'center', gap: 1.5, px: 1.5,
                    border: '1px solid #e2e8f0', borderRadius: '10px', bgcolor: 'white',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                    cursor: onNavigate ? 'pointer' : 'default', transition: 'all 0.15s',
                    '&:hover': onNavigate ? { borderColor: BLUE, bgcolor: '#f0fdfb', boxShadow: '0 2px 8px rgba(20,184,166,0.12)' } : {},
                  }}
                >
                  <Avatar
                    src={emp.photoUrl || emp.profileImageUrl}
                    sx={{ width: 40, height: 40, bgcolor: '#94a3b8', fontSize: '0.9rem', flexShrink: 0 }}
                  >
                    {emp.firstName?.charAt(0)}
                  </Avatar>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Typography fontWeight={700} fontSize={13} noWrap>{emp.fullName}</Typography>
                    <Typography fontSize={11} color="text.secondary" noWrap>{emp.position || emp.role || '—'}</Typography>
                  </Box>
                </Box>
              </Box>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
};

// ── Department Directory Tab ──────────────────────────────────────────────────
const DepartmentDirectoryTab = ({ employees, onNavigate }) => {
  const [search, setSearch]             = useState('');
  const [selectedDept, setSelectedDept] = useState(null);
  const [favorites, setFavorites]       = useState(() => {
    try { return new Set(JSON.parse(localStorage.getItem('dd_favorites') || '[]')); }
    catch { return new Set(); }
  });
  const [callAnchor, setCallAnchor]     = useState(null); // { el, emp }

  const toggleFavorite = (e, empId) => {
    e.stopPropagation();
    setFavorites(prev => {
      const next = new Set(prev);
      if (next.has(empId)) next.delete(empId); else next.add(empId);
      localStorage.setItem('dd_favorites', JSON.stringify([...next]));
      return next;
    });
  };

  const openCallMenu = (e, emp) => {
    e.stopPropagation();
    setCallAnchor({ el: e.currentTarget, emp });
  };

  const deptMap = useMemo(() => {
    const m = new Map();
    employees.forEach((e) => {
      const d = e.department || 'Unassigned';
      if (!m.has(d)) m.set(d, []);
      m.get(d).push(e);
    });
    return m;
  }, [employees]);

  const departments = useMemo(() => [...deptMap.keys()].sort(), [deptMap]);

  useEffect(() => {
    if (!selectedDept && departments.length > 0) setSelectedDept(departments[0]);
  }, [departments, selectedDept]);

  const filteredDepts = useMemo(
    () => departments.filter(d => d.toLowerCase().includes(search.toLowerCase())),
    [departments, search]
  );

  const deptEmps = selectedDept ? (deptMap.get(selectedDept) || []) : [];

  return (
    <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start', minHeight: 400 }}>
      {/* ── Left sidebar ── */}
      <Box sx={{
        width: 260, flexShrink: 0, bgcolor: 'white', borderRadius: 2,
        border: '1px solid #e2e8f0', overflow: 'hidden', display: 'flex', flexDirection: 'column',
      }}>
        {/* Search box */}
        <Box sx={{ px: 1.5, py: 1.25, borderBottom: '1px solid #f1f5f9' }}>
          <Box sx={{
            display: 'flex', alignItems: 'center', gap: 0.75,
            px: 1.25, py: 0.75, border: '1px solid #e2e8f0', borderRadius: 1.5, bgcolor: '#f8fafc',
          }}>
            <SearchIcon sx={{ fontSize: 16, color: '#94a3b8', flexShrink: 0 }} />
            <Box component="input"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search Department"
              sx={{
                border: 'none', outline: 'none', background: 'transparent',
                fontSize: 13, flex: 1, color: '#334155',
                '&::placeholder': { color: '#94a3b8' },
              }}
            />
            {search && (
              <CloseIcon
                sx={{ fontSize: 14, color: '#94a3b8', cursor: 'pointer', flexShrink: 0 }}
                onClick={() => setSearch('')}
              />
            )}
          </Box>
        </Box>
        {/* Department list */}
        <Box sx={{ overflowY: 'auto', py: 0.5 }}>
          {filteredDepts.map(dept => (
            <Box key={dept}
              onClick={() => setSelectedDept(dept)}
              sx={{
                px: 2.5, py: 0.875,
                fontSize: 13.5,
                fontWeight: selectedDept === dept ? 700 : 400,
                color: '#1e293b',
                bgcolor: selectedDept === dept ? '#f1f5f9' : 'transparent',
                cursor: 'pointer',
                '&:hover': { bgcolor: '#f8fafc' },
                transition: 'background 0.12s',
              }}
            >
              {dept}
            </Box>
          ))}
        </Box>
      </Box>

      {/* ── Right content ── */}
      <Box sx={{ flex: 1, minWidth: 0 }}>
        {selectedDept && (
          <>
            {/* Department header */}
            <Box sx={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              bgcolor: 'white', border: '1px solid #e2e8f0', borderRadius: 2,
              px: 3, py: 1.75, mb: 2,
            }}>
              <Typography fontWeight={600} fontSize={15.5}>{selectedDept}</Typography>
              <Box sx={{ textAlign: 'right' }}>
                <Typography fontWeight={700} fontSize={20} lineHeight={1.1}>{deptEmps.length}</Typography>
                <Typography fontSize={11.5} color="text.secondary">Members</Typography>
              </Box>
            </Box>

            {/* Call / Chat popover */}
            <Popover
              open={Boolean(callAnchor)}
              anchorEl={callAnchor?.el}
              onClose={() => setCallAnchor(null)}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              transformOrigin={{ vertical: 'top', horizontal: 'right' }}
              PaperProps={{ sx: { borderRadius: 2, boxShadow: '0 4px 16px rgba(0,0,0,0.12)', minWidth: 160 } }}
            >
              <Box sx={{ px: 2, pt: 1.5, pb: 0.5 }}>
                <Typography fontWeight={700} fontSize={13}>{callAnchor?.emp?.fullName}</Typography>
                <Typography fontSize={11.5} color="text.secondary">{callAnchor?.emp?.phone || 'No phone on record'}</Typography>
              </Box>
              <List dense disablePadding sx={{ pb: 0.5 }}>
                <ListItemButton
                  component="a"
                  href={callAnchor?.emp?.phone ? `tel:${callAnchor.emp.phone}` : undefined}
                  disabled={!callAnchor?.emp?.phone}
                  onClick={() => setCallAnchor(null)}
                  sx={{ px: 2, py: 0.875, gap: 1 }}
                >
                  <ListItemIcon sx={{ minWidth: 0 }}><CallIcon sx={{ fontSize: 18, color: '#14b8a6' }} /></ListItemIcon>
                  <ListItemText primary="Call" primaryTypographyProps={{ fontSize: 13 }} />
                </ListItemButton>
                <ListItemButton
                  component="a"
                  href={callAnchor?.emp?.email ? `mailto:${callAnchor.emp.email}` : undefined}
                  disabled={!callAnchor?.emp?.email}
                  onClick={() => setCallAnchor(null)}
                  sx={{ px: 2, py: 0.875, gap: 1 }}
                >
                  <ListItemIcon sx={{ minWidth: 0 }}><ChatIcon sx={{ fontSize: 18, color: '#6366f1' }} /></ListItemIcon>
                  <ListItemText primary="Chat" primaryTypographyProps={{ fontSize: 13 }} />
                </ListItemButton>
              </List>
            </Popover>

            {/* Employee cards grid */}
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 220px))', gap: 2 }}>
              {deptEmps.map((emp) => (
                <Box key={emp.id}
                  onClick={onNavigate ? () => onNavigate(emp.id) : undefined}
                  sx={{
                    position: 'relative', bgcolor: 'white', borderRadius: 2.5,
                    border: '1px solid #e2e8f0', pt: 2.5, pb: 2, px: 2,
                    display: 'flex', flexDirection: 'column', alignItems: 'center',
                    cursor: onNavigate ? 'pointer' : 'default',
                    '&:hover': onNavigate ? { boxShadow: '0 4px 14px rgba(0,0,0,0.09)', borderColor: '#cbd5e1' } : {},
                    transition: 'box-shadow 0.15s, border-color 0.15s',
                  }}
                >
                  {/* Star + phone icons top-right */}
                  <Box sx={{ position: 'absolute', top: 10, right: 10, display: 'flex', flexDirection: 'column', gap: 0.5, alignItems: 'center' }}>
                    <Tooltip title={favorites.has(emp.id) ? 'Remove favorite' : 'Mark favorite'} placement="left">
                      <Box onClick={(e) => toggleFavorite(e, emp.id)} sx={{ cursor: 'pointer', lineHeight: 0 }}>
                        {favorites.has(emp.id)
                          ? <StarIcon       sx={{ fontSize: 18, color: '#f59e0b' }} />
                          : <StarBorderIcon sx={{ fontSize: 18, color: '#94a3b8', '&:hover': { color: '#f59e0b' } }} />}
                      </Box>
                    </Tooltip>
                    <Tooltip title="Chat & Call" placement="left">
                      <Box onClick={(e) => openCallMenu(e, emp)} sx={{ cursor: 'pointer', lineHeight: 0 }}>
                        <PhoneIcon sx={{ fontSize: 17, color: '#94a3b8', '&:hover': { color: '#14b8a6' } }} />
                      </Box>
                    </Tooltip>
                  </Box>

                  {/* Avatar */}
                  <Avatar
                    src={emp.photoUrl || emp.profileImageUrl}
                    sx={{ width: 84, height: 84, mb: 1.5, bgcolor: '#e2e8f0', fontSize: '2rem', color: '#94a3b8' }}
                  >
                    {emp.firstName?.charAt(0)}
                  </Avatar>

                  {/* Employee code + name */}
                  <Typography fontSize={12.5} textAlign="center" sx={{ mb: 0.3 }}>
                    {emp.employeeCode} -{' '}
                    <Box component="span" fontWeight={700}>{emp.fullName}</Box>
                  </Typography>

                  {/* Email */}
                  <Typography fontSize={11.5} color="text.secondary" textAlign="center"
                    noWrap sx={{ maxWidth: '100%', mb: 0.3 }}>
                    {emp.email}
                  </Typography>

                  {/* Role */}
                  <Typography fontSize={12} color="text.secondary" textAlign="center" sx={{ mb: 0.2 }}>
                    {emp.role}
                  </Typography>

                  {/* Department */}
                  <Typography fontSize={12} color="text.secondary" textAlign="center" sx={{ mb: 0.75 }}>
                    {emp.department}
                  </Typography>

                  {/* Status */}
                  <Typography fontSize={13} fontWeight={600}
                    color={emp.active ? '#16a34a' : '#dc2626'}>
                    {emp.active ? 'In' : 'Out'}
                  </Typography>
                </Box>
              ))}
            </Box>
          </>
        )}
      </Box>
    </Box>
  );
};

// ── Birthday Folks Tab ────────────────────────────────────────────────────────
const BirthdayFolksTab = ({ employees, onNavigate }) => {
  const today  = new Date();
  const todayM = today.getMonth() + 1;
  const todayD = today.getDate();

  const upcoming = useMemo(() => {
    return employees
      .filter((e) => e.dateOfBirth)
      .map((e) => {
        const parts = e.dateOfBirth.split('-').map(Number);
        const m = parts[1], d = parts[2];
        const thisYear  = new Date(today.getFullYear(), m - 1, d);
        const diff = Math.round((thisYear - new Date(today.getFullYear(), todayM - 1, todayD)) / 86400000);
        const daysUntil = diff < 0 ? diff + 365 : diff;
        return { ...e, bdayM: m, bdayD: d, daysUntil };
      })
      .sort((a, b) => a.daysUntil - b.daysUntil);
  }, [employees]);

  const thisMonth = upcoming.filter((e) => e.bdayM === todayM);
  const nextUp    = upcoming.filter((e) => e.bdayM !== todayM).slice(0, 10);

  const BCard = ({ emp }) => {
    const isToday = emp.bdayM === todayM && emp.bdayD === todayD;
    return (
      <Box onClick={onNavigate ? () => onNavigate(emp.id) : undefined} sx={{
        display: 'flex', alignItems: 'center', gap: 1.5, p: 1.5, borderRadius: 2, bgcolor: 'white',
        border: `1px solid ${isToday ? '#fbbf24' : '#e2e8f0'}`,
        boxShadow: isToday ? '0 2px 8px rgba(251,191,36,0.2)' : 'none',
        cursor: onNavigate ? 'pointer' : 'default', transition: 'all 0.15s',
        '&:hover': onNavigate ? { borderColor: '#1976d2', bgcolor: '#f0f9ff' } : {},
      }}>
        <Avatar src={emp.photoUrl || emp.profileImageUrl} sx={{ width: 44, height: 44, bgcolor: isToday ? '#f59e0b' : '#6366f1', fontSize: '0.9rem', flexShrink: 0 }}>
          {emp.firstName?.charAt(0)}
        </Avatar>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography fontWeight={600} fontSize={13.5} noWrap>{emp.fullName}</Typography>
          <Typography fontSize={11} color="text.secondary" noWrap>{emp.position || emp.department || '—'}</Typography>
        </Box>
        <Box sx={{ textAlign: 'right', flexShrink: 0 }}>
          {isToday ? (
            <Chip label="🎂 Today!" size="small" sx={{ bgcolor: '#fef9c3', color: '#92400e', fontWeight: 700, fontSize: 11 }} />
          ) : (
            <>
              <Typography fontSize={13} fontWeight={700} sx={{ color: '#f59e0b' }}>
                {SHORT_MONTHS[emp.bdayM - 1]} {emp.bdayD}
              </Typography>
              <Typography fontSize={11} color="text.secondary">
                {emp.daysUntil === 0 ? 'Today' : `in ${emp.daysUntil}d`}
              </Typography>
            </>
          )}
        </Box>
      </Box>
    );
  };

  if (!upcoming.length) return (
    <Box sx={{ textAlign: 'center', py: 8 }}>
      <CakeIcon sx={{ fontSize: 56, color: '#cbd5e1', mb: 1.5 }} />
      <Typography color="text.secondary">No birthday data available</Typography>
      <Typography fontSize={12} color="text.disabled" mt={0.5}>Add date of birth to employee profiles</Typography>
    </Box>
  );

  return (
    <Box>
      {thisMonth.length > 0 && (
        <>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
            <CakeIcon sx={{ color: '#f59e0b', fontSize: 20 }} />
            <Typography fontWeight={700} fontSize={15}>This Month — {CAL_MONTHS[todayM - 1]}</Typography>
            <Chip label={thisMonth.length} size="small" sx={{ bgcolor: '#fef9c3', color: '#92400e', fontWeight: 700 }} />
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(270px, 1fr))', gap: 1.5, mb: 3.5 }}>
            {thisMonth.map((e) => <BCard key={e.id} emp={e} />)}
          </Box>
        </>
      )}
      {nextUp.length > 0 && (
        <>
          <Typography fontWeight={700} fontSize={15} mb={1.5}>Upcoming</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(270px, 1fr))', gap: 1.5 }}>
            {nextUp.map((e) => <BCard key={e.id} emp={e} />)}
          </Box>
        </>
      )}
    </Box>
  );
};

// ── Calendar Tab ──────────────────────────────────────────────────────────────
const CalendarTab = ({ employees }) => {
  const now = new Date();
  const [year,  setYear]  = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth());

  const bdaysByDay = useMemo(() => {
    const m = new Map();
    employees.filter((e) => e.dateOfBirth).forEach((e) => {
      const parts = e.dateOfBirth.split('-').map(Number);
      if (parts[1] === month + 1) {
        const d = parts[2];
        if (!m.has(d)) m.set(d, []);
        m.get(d).push(e);
      }
    });
    return m;
  }, [employees, month, year]);

  const firstDay    = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const prevMonth = () => { if (month === 0) { setMonth(11); setYear((y) => y - 1); } else setMonth((m) => m - 1); };
  const nextMonth = () => { if (month === 11) { setMonth(0); setYear((y) => y + 1); } else setMonth((m) => m + 1); };

  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  return (
    <Box>
      {/* Month navigator */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2.5 }}>
        <IconButton size="small" onClick={prevMonth} sx={{ border: '1px solid #e2e8f0' }}>
          <ChevronLeftIcon sx={{ fontSize: 18 }} />
        </IconButton>
        <Typography fontWeight={700} fontSize={16} sx={{ minWidth: 180, textAlign: 'center' }}>
          {CAL_MONTHS[month]} {year}
        </Typography>
        <IconButton size="small" onClick={nextMonth} sx={{ border: '1px solid #e2e8f0' }}>
          <ChevronRightIcon sx={{ fontSize: 18 }} />
        </IconButton>
        <Button size="small" variant="outlined" onClick={() => { setYear(now.getFullYear()); setMonth(now.getMonth()); }}
          sx={{ ml: 1, textTransform: 'none', fontSize: 12 }}>
          Today
        </Button>
      </Box>

      {/* Day-of-week headers */}
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', mb: 0.75 }}>
        {CAL_DAYS.map((d) => (
          <Typography key={d} fontSize={11} fontWeight={700} color="text.secondary" sx={{ textAlign: 'center', py: 0.5 }}>{d}</Typography>
        ))}
      </Box>

      {/* Grid */}
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 0.5 }}>
        {cells.map((day, i) => {
          if (!day) return <Box key={`e${i}`} sx={{ minHeight: 76 }} />;
          const isToday = day === now.getDate() && month === now.getMonth() && year === now.getFullYear();
          const bdays   = bdaysByDay.get(day) || [];
          return (
            <Box key={day} sx={{
              minHeight: 76, p: '6px 8px', borderRadius: 1.5,
              bgcolor: isToday ? '#eff6ff' : 'white',
              border: isToday ? '1.5px solid #1976d2' : '1px solid #f1f5f9',
            }}>
              <Typography fontSize={12} fontWeight={isToday ? 800 : 500}
                sx={{ color: isToday ? '#1976d2' : 'inherit', mb: 0.5 }}>
                {day}
              </Typography>
              {bdays.slice(0, 2).map((e) => (
                <Tooltip key={e.id} title={`🎂 ${e.fullName}`} arrow>
                  <Box sx={{ fontSize: 9.5, fontWeight: 600, bgcolor: '#fef9c3', color: '#92400e', borderRadius: 0.5, px: 0.5, py: 0.1, mb: 0.25, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', cursor: 'default' }}>
                    🎂 {e.firstName}
                  </Box>
                </Tooltip>
              ))}
              {bdays.length > 2 && (
                <Typography fontSize={9} color="text.secondary">+{bdays.length - 2} more</Typography>
              )}
            </Box>
          );
        })}
      </Box>
    </Box>
  );
};

// ── Main page ─────────────────────────────────────────────────────────────────
const OrganisationPage = () => {
  const { user }   = useSelector((s) => s.auth);
  const navigate   = useNavigate();
  const [tab, setTab] = useState(0);

  // All employees — used by every tab except Employee Tree
  const [allEmployees, setAllEmployees] = useState([]);
  const [allLoading,   setAllLoading]   = useState(true);

  // Employee Tree state
  const [nodes,       setNodes]       = useState(new Map());
  const [childrenMap, setChildrenMap] = useState(new Map());
  const [rootIds,     setRootIds]     = useState([]);
  const [treeLoading, setTreeLoading] = useState(true);

  // Single fetch for all employees
  useEffect(() => {
    employeeApi.getAll()
      .then((emps) => setAllEmployees(emps.filter((e) => e.active)))
      .catch(() => {})
      .finally(() => setAllLoading(false));
  }, []);

  // Patch stale data when an employee is updated from EmployeeDetail
  useEffect(() => {
    const handler = (e) => {
      const updated = e.detail;
      setNodes((prev) => {
        if (!prev.has(updated.id)) return prev;
        const next = new Map(prev);
        next.set(updated.id, updated);
        return next;
      });
      setAllEmployees((prev) => prev.map((emp) => emp.id === updated.id ? updated : emp));
    };
    window.addEventListener('employee-updated', handler);
    return () => window.removeEventListener('employee-updated', handler);
  }, []);

  const loadChildren = useCallback(async (nodeId) => {
    const children = await employeeApi.getTeam(nodeId);
    setNodes((prev) => { const n = new Map(prev); children.forEach((c) => n.set(c.id, c)); return n; });
    setChildrenMap((prev) => { const n = new Map(prev); n.set(nodeId, children.map((c) => c.id)); return n; });
    return children;
  }, []);

  useEffect(() => {
    const init = async () => {
      try {
        const roots = await employeeApi.getOrgChart();
        if (!roots.length) { setTreeLoading(false); return; }
        const nodesAcc    = new Map(roots.map((e) => [e.id, e]));
        const childrenAcc = new Map();
        await Promise.all(
          roots.filter((r) => r.subordinateCount > 0).map((r) =>
            employeeApi.getTeam(r.id).then((ch) => {
              childrenAcc.set(r.id, ch.map((c) => c.id));
              ch.forEach((c) => nodesAcc.set(c.id, c));
            }).catch(() => {}),
          ),
        );
        setNodes(nodesAcc); setChildrenMap(childrenAcc);
        setRootIds(roots.map((r) => r.id));
      } catch { toast.error('Failed to load org chart'); }
      finally { setTreeLoading(false); }
    };
    init();
  }, []);

  const TABS = [
    { label: 'Overview',             icon: <GridViewIcon sx={{ fontSize: 16 }} /> },
    { label: 'Announcements',        icon: <AnnouncementIcon sx={{ fontSize: 16 }} /> },
    { label: 'Employee Tree',        icon: <AccountTreeIcon sx={{ fontSize: 16 }} /> },
    { label: 'Department Tree',      icon: <BusinessIcon sx={{ fontSize: 16 }} /> },
    { label: 'Department Directory', icon: <ListAltIcon sx={{ fontSize: 16 }} /> },
    { label: 'Birthday Folks',       icon: <CakeIcon sx={{ fontSize: 16 }} /> },
    { label: 'Calendar',             icon: <CalendarMonthIcon sx={{ fontSize: 16 }} /> },
  ];

  const loading = tab === 2 ? treeLoading : allLoading;

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2.5 }}>
        <PeopleIcon color="primary" />
        <Typography variant="h5" fontWeight={700}>Organisation</Typography>
      </Box>

      {/* Tabs */}
      <Box sx={{ bgcolor: 'white', borderRadius: 2, border: '1px solid #e2e8f0', boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            px: 2, borderBottom: '1px solid #e2e8f0',
            '& .MuiTab-root': { textTransform: 'none', fontSize: 13, fontWeight: 600, minHeight: 48, gap: 0.75 },
          }}
        >
          {TABS.map((t, i) => (
            <Tab key={i} label={t.label} icon={t.icon} iconPosition="start" />
          ))}
        </Tabs>

        {/* Tab content */}
        <Box sx={{ p: 3 }}>
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}><CircularProgress /></Box>
          ) : (
            <>
              {tab === 0 && <OverviewTab employees={allEmployees} />}
              {tab === 1 && <AnnouncementsTab userRole={user?.role} />}

              {/* Employee Tree */}
              {tab === 2 && (
                <EmployeeTreeView
                  nodes={nodes}
                  childrenMap={childrenMap}
                  rootIds={rootIds}
                  loadChildren={loadChildren}
                  onOpen={(id) => navigate(`/employees/${id}`)}
                />
              )}

              {tab === 3 && <DepartmentTreeTab employees={allEmployees} onNavigate={(id) => navigate(`/employees/${id}`)} />}
              {tab === 4 && <DepartmentDirectoryTab employees={allEmployees} onNavigate={(id) => navigate(`/employees/${id}`)} />}
              {tab === 5 && <BirthdayFolksTab employees={allEmployees} onNavigate={(id) => navigate(`/employees/${id}`)} />}
              {tab === 6 && <CalendarTab employees={allEmployees} />}
            </>
          )}
        </Box>
      </Box>

    </Box>
  );
};

export default OrganisationPage;
