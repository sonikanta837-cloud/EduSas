import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useSelector } from 'react-redux';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box, Typography, CircularProgress, Tooltip, IconButton, Button,
  Divider, Tabs, Tab, Avatar, Chip, TextField,
  Popover, List, ListItemButton, ListItemIcon, ListItemText,
  Menu, MenuItem, InputAdornment, OutlinedInput,
  Select, FormControl, InputLabel,
  Dialog, DialogTitle, DialogContent, DialogActions,
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
import MoreVertIcon        from '@mui/icons-material/MoreVert';
import PushPinIcon         from '@mui/icons-material/PushPin';
import PushPinOutlinedIcon from '@mui/icons-material/PushPinOutlined';
import EditOutlinedIcon    from '@mui/icons-material/EditOutlined';
import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import PlaceOutlinedIcon    from '@mui/icons-material/PlaceOutlined';
import CampaignIcon        from '@mui/icons-material/Campaign';
import BeachAccessIcon     from '@mui/icons-material/BeachAccess';
import MenuBookIcon        from '@mui/icons-material/MenuBook';
import EmojiEventsIcon     from '@mui/icons-material/EmojiEvents';
import GavelIcon           from '@mui/icons-material/Gavel';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import StarBorderIcon      from '@mui/icons-material/StarBorder';
import StarIcon            from '@mui/icons-material/Star';
import PhoneIcon           from '@mui/icons-material/Phone';
import CallIcon            from '@mui/icons-material/Call';
import ChatIcon            from '@mui/icons-material/Chat';
import { employeeApi }      from '../api/employeeApi';
import { announcementApi }  from '../api/announcementApi';
import { leaveUploadApi }   from '../api/leaveUploadApi';
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
    setHistory((h) => {
      const next = [...h, currentId];
      // Also record the intermediate selected node so it appears in the breadcrumb
      if (selectedChildId !== null) next.push(selectedChildId);
      return next;
    });
    setCurrentId(nodeId);
    setSelectedChildId(null);
  }, [nodes, ensureLoaded, currentId, selectedChildId]);

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

const ANN_CATEGORY = {
  General:     { color: '#475569', bg: '#f1f5f9', border: '#e2e8f0', dot: '#94a3b8', IconComp: CampaignIcon         },
  Holiday:     { color: '#ea580c', bg: '#fff7ed', border: '#fed7aa', dot: '#f97316', IconComp: BeachAccessIcon      },
  Training:    { color: '#2563eb', bg: '#eff6ff', border: '#bfdbfe', dot: '#3b82f6', IconComp: MenuBookIcon         },
  Recognition: { color: '#7c3aed', bg: '#f5f3ff', border: '#ddd6fe', dot: '#8b5cf6', IconComp: EmojiEventsIcon     },
  Policy:      { color: '#dc2626', bg: '#fef2f2', border: '#fecaca', dot: '#ef4444', IconComp: GavelIcon            },
  Important:   { color: '#b45309', bg: '#fffbeb', border: '#fde68a', dot: '#f59e0b', IconComp: NotificationsActiveIcon },
};
const ANN_CAT_LABELS = Object.keys(ANN_CATEGORY);

const ANN_PRIORITY = {
  Normal: { color: '#64748b', bg: '#f8fafc' },
  High:   { color: '#d97706', bg: '#fffbeb' },
  Urgent: { color: '#dc2626', bg: '#fef2f2' },
};

const fmtAnn = (dt) => {
  if (!dt) return '';
  const d    = new Date(dt);
  const diff = Math.floor((Date.now() - d.getTime()) / 1000);
  if (diff < 60)     return 'Just now';
  if (diff < 3600)   return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400)  return `${Math.floor(diff / 3600)}h ago`;
  if (diff < 604800) return `${Math.floor(diff / 86400)}d ago`;
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
};

const AnnCard = ({ item, featured, canPost, onMenu, onViewers }) => {
  const cc = ANN_CATEGORY[item.category || 'General'] || ANN_CATEGORY.General;
  const pc = ANN_PRIORITY[item.priority || 'Normal']  || ANN_PRIORITY.Normal;
  return (
    <Box sx={{
      bgcolor: '#fff', borderRadius: '16px',
      border: `1px solid ${featured ? cc.border : '#f1f5f9'}`,
      boxShadow: featured ? '0 4px 24px rgba(0,0,0,0.09)' : '0 1px 8px rgba(0,0,0,0.05)',
      overflow: 'hidden',
      transition: 'box-shadow .2s, transform .15s',
      '&:hover': { boxShadow: '0 6px 28px rgba(0,0,0,0.10)', transform: 'translateY(-2px)' },
    }}>
      {featured && <Box sx={{ height: 3, bgcolor: cc.dot }} />}
      <Box sx={{ p: 2.5 }}>

        {/* ── Row 1: badges + menu ── */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5, gap: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: .75, flexWrap: 'wrap' }}>
            {/* Category badge with icon */}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: .5, px: 1.1, py: .3, borderRadius: '20px', bgcolor: cc.bg, border: `1px solid ${cc.border}` }}>
              <cc.IconComp sx={{ fontSize: 11, color: cc.color, flexShrink: 0 }} />
              <Typography sx={{ fontSize: 11, fontWeight: 700, color: cc.color, letterSpacing: '.3px' }}>
                {item.category || 'General'}
              </Typography>
            </Box>
            {/* Priority (only if not Normal) */}
            {(item.priority || 'Normal') !== 'Normal' && (
              <Box sx={{ px: 1.25, py: .3, borderRadius: '20px', bgcolor: pc.bg, border: `1px solid ${pc.color}33` }}>
                <Typography sx={{ fontSize: 11, fontWeight: 700, color: pc.color }}>
                  {item.priority === 'Urgent' ? '🔴 Urgent' : '⚠ High'}
                </Typography>
              </Box>
            )}
            {/* Pinned */}
            {item.pinned && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: .4, px: 1, py: .3, borderRadius: '20px', bgcolor: '#eff6ff', border: '1px solid #bfdbfe' }}>
                <PushPinIcon sx={{ fontSize: 10, color: '#2563eb' }} />
                <Typography sx={{ fontSize: 11, fontWeight: 700, color: '#2563eb' }}>Pinned</Typography>
              </Box>
            )}
          </Box>
          {canPost && (
            <IconButton size="small" onClick={e => onMenu(e, item)}
              sx={{ width: 28, height: 28, color: '#94a3b8', flexShrink: 0, '&:hover': { bgcolor: '#f1f5f9', color: '#374151' } }}>
              <MoreVertIcon sx={{ fontSize: 18 }} />
            </IconButton>
          )}
        </Box>

        {/* ── Title with category icon ── */}
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.25, mb: .75 }}>
          <Box sx={{
            width: 36, height: 36, borderRadius: '10px', flexShrink: 0,
            bgcolor: cc.dot,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: `0 2px 8px ${cc.dot}55`,
          }}>
            <cc.IconComp sx={{ fontSize: 19, color: '#fff' }} />
          </Box>
          <Typography sx={{ fontSize: featured ? 15.5 : 14, fontWeight: 700, color: '#0f172a', lineHeight: 1.4, pt: .55, flex: 1 }}>
            {item.title}
          </Typography>
        </Box>

        {/* ── Body ── */}
        {item.body && (
          <Typography sx={{
            fontSize: 13.5, color: '#64748b', lineHeight: 1.65, mb: 2,
            display: '-webkit-box', WebkitLineClamp: featured ? 4 : 3,
            WebkitBoxOrient: 'vertical', overflow: 'hidden',
          }}>
            {item.body}
          </Typography>
        )}

        {/* ── Location ── */}
        {item.location && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: .6, mb: 1.5 }}>
            <PlaceOutlinedIcon sx={{ fontSize: 14, color: '#94a3b8', flexShrink: 0 }} />
            <Typography sx={{ fontSize: 12.5, color: '#64748b' }}>{item.location}</Typography>
          </Box>
        )}

        {/* ── Footer: author + date + view count ── */}
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pt: 1.5, borderTop: '1px solid #f8fafc' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Avatar sx={{ width: 28, height: 28, fontSize: '0.72rem', fontWeight: 700, bgcolor: '#14b8a6', color: '#fff' }}>
              {(item.authorName || 'S').charAt(0).toUpperCase()}
            </Avatar>
            <Box>
              <Typography sx={{ fontSize: 12.5, fontWeight: 600, color: '#374151', lineHeight: 1.2 }}>
                {item.authorName || 'System'}
              </Typography>
              <Typography sx={{ fontSize: 11, color: '#94a3b8' }}>{fmtAnn(item.createdAt)}</Typography>
            </Box>
          </Box>
          {/* Viewed by X/Y — admin / HR only */}
          {canPost && (
            <Box
              onClick={() => onViewers(item)}
              sx={{
                display: 'flex', alignItems: 'center', gap: .6,
                px: 1.25, py: .4, borderRadius: '20px',
                bgcolor: '#f8fafc', border: '1px solid #e2e8f0',
                cursor: 'pointer',
                transition: 'background .15s, border-color .15s',
                '&:hover': { bgcolor: '#eff6ff', borderColor: '#bfdbfe' },
              }}
            >
              <VisibilityOutlinedIcon sx={{ fontSize: 13, color: '#64748b' }} />
              <Typography sx={{ fontSize: 11.5, fontWeight: 600, color: '#475569', whiteSpace: 'nowrap' }}>
                {item.viewedByCount ?? 0} / {item.totalActiveEmployees ?? 0} viewed
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  );
};

const AnnouncementsTab = ({ userRole }) => {
  const canPost = userRole === 'ADMIN' || userRole === 'HR';

  const [items,      setItems]      = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [searchText, setSearchText] = useState('');
  const [catFilter,  setCatFilter]  = useState('');

  const [locationOptions,  setLocationOptions]  = useState([]);
  const [newLocInput,      setNewLocInput]      = useState('');
  const [showNewLocField,  setShowNewLocField]  = useState(false);

  const [postOpen, setPostOpen] = useState(false);
  const [postForm, setPostForm] = useState({ title: '', body: '', location: '', category: 'General', priority: 'Normal', pinned: false });
  const [saving,   setSaving]   = useState(false);

  const [editOpen,   setEditOpen]   = useState(false);
  const [editTarget, setEditTarget] = useState(null);
  const [editForm,   setEditForm]   = useState({});
  const [updating,   setUpdating]   = useState(false);

  const [menuAnchor,   setMenuAnchor]   = useState(null);
  const [menuItem,     setMenuItem]     = useState(null);
  const [viewersOpen,  setViewersOpen]  = useState(false);
  const [viewersItem,  setViewersItem]  = useState(null);
  const [viewersData,  setViewersData]  = useState(null);
  const [viewersLoading, setViewersLoading] = useState(false);

  // Fetch branch locations once on mount
  useEffect(() => {
    employeeApi.getLocations()
      .then(data => setLocationOptions(Array.isArray(data) ? data : []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    announcementApi.getAll()
      .then(data => {
        const list = Array.isArray(data) ? data : [];
        setItems(list);
        // Mark all unread announcements as read silently
        const unread = list.filter(i => !i.readByCurrentUser);
        if (unread.length > 0) {
          Promise.all(unread.map(i => announcementApi.markAsRead(i.id).catch(() => {})))
            .then(() => {
              // Update local state + notify Header to clear badge
              setItems(prev => prev.map(i => ({ ...i, readByCurrentUser: true })));
              window.dispatchEvent(new Event('announcements-read'));
            });
        }
      })
      .catch(() => toast.error('Failed to load announcements'))
      .finally(() => setLoading(false));
  }, []);

  const post = async () => {
    if (!postForm.title.trim()) return;
    setSaving(true);
    try {
      const created = await announcementApi.create(postForm);
      setItems(prev => [created, ...prev]);
      setPostOpen(false);
      setShowNewLocField(false);
      setPostForm({ title: '', body: '', location: '', category: 'General', priority: 'Normal', pinned: false });
      toast.success('Announcement posted');
    } catch { toast.error('Failed to post announcement'); }
    finally  { setSaving(false); }
  };

  const doUpdate = async () => {
    setUpdating(true);
    try {
      const updated = await announcementApi.update(editTarget.id, { ...editTarget, ...editForm });
      setItems(prev => prev.map(i => i.id === editTarget.id ? { ...i, ...updated } : i));
      setEditOpen(false); setEditTarget(null); setShowNewLocField(false);
      toast.success('Announcement updated');
    } catch { toast.error('Failed to update announcement'); }
    finally  { setUpdating(false); }
  };

  const remove = async (id) => {
    try {
      await announcementApi.delete(id);
      setItems(prev => prev.filter(i => i.id !== id));
      toast.success('Deleted');
    } catch { toast.error('Failed to delete announcement'); }
  };

  const togglePin = async (item) => {
    try {
      const updated = await announcementApi.update(item.id, { ...item, pinned: !item.pinned });
      setItems(prev => {
        const next = prev.map(i => i.id === item.id ? { ...i, ...updated } : i);
        return [...next].sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0));
      });
      toast.success(item.pinned ? 'Unpinned' : 'Pinned to top');
    } catch { toast.error('Failed to update announcement'); }
  };

  const archive = async (item) => {
    try {
      await announcementApi.update(item.id, { ...item, archived: true });
      setItems(prev => prev.filter(i => i.id !== item.id));
      toast.success('Archived');
    } catch { toast.error('Failed to archive'); }
  };

  const openMenu  = (e, item) => { setMenuAnchor(e.currentTarget); setMenuItem(item); };
  const closeMenu = ()         => { setMenuAnchor(null); setMenuItem(null); };

  const openViewers = (item) => {
    setViewersItem(item);
    setViewersData(null);
    setViewersOpen(true);
    setViewersLoading(true);
    announcementApi.getViewers(item.id)
      .then(data => setViewersData(data))
      .catch(() => toast.error('Failed to load viewer data'))
      .finally(() => setViewersLoading(false));
  };

  const filtered = items.filter(item => {
    if (catFilter && (item.category || 'General') !== catFilter) return false;
    if (searchText) {
      const q = searchText.toLowerCase();
      return (item.title      || '').toLowerCase().includes(q) ||
             (item.body       || '').toLowerCase().includes(q) ||
             (item.authorName || '').toLowerCase().includes(q);
    }
    return true;
  });

  const pinned  = filtered.filter(i => i.pinned);
  const regular = filtered.filter(i => !i.pinned);

  const SectionRule = ({ label }) => (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
      <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '.8px', whiteSpace: 'nowrap' }}>
        {label}
      </Typography>
      <Box sx={{ flex: 1, height: '1px', bgcolor: '#f1f5f9' }} />
    </Box>
  );

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}><CircularProgress sx={{ color: '#14b8a6' }} /></Box>;

  return (
    <Box>

      {/* ── Top bar ── */}
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2.5, gap: 1.5, flexWrap: 'wrap' }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.25, flex: 1 }}>
          {/* Search */}
          <OutlinedInput
            placeholder="Search announcements…"
            value={searchText}
            onChange={e => setSearchText(e.target.value)}
            startAdornment={<InputAdornment position="start"><SearchIcon sx={{ fontSize: 17, color: '#94a3b8' }} /></InputAdornment>}
            endAdornment={searchText ? (
              <InputAdornment position="end">
                <IconButton size="small" onClick={() => setSearchText('')}><CloseIcon sx={{ fontSize: 14 }} /></IconButton>
              </InputAdornment>
            ) : null}
            sx={{
              height: 38, fontSize: 13, borderRadius: '10px', maxWidth: 360, bgcolor: '#fff',
              '& fieldset': { borderColor: '#e2e8f0' },
              '&:hover fieldset': { borderColor: '#cbd5e1' },
              '&.Mui-focused fieldset': { borderColor: '#14b8a6' },
            }}
          />
          {/* Category filter chips */}
          <Box sx={{ display: 'flex', gap: .75, flexWrap: 'wrap' }}>
            {['All', ...ANN_CAT_LABELS].map(cat => {
              const active = cat === 'All' ? catFilter === '' : catFilter === cat;
              const cc     = cat !== 'All' ? ANN_CATEGORY[cat] : null;
              return (
                <Chip
                  key={cat} label={cat} size="small"
                  onClick={() => setCatFilter(cat === 'All' ? '' : (catFilter === cat ? '' : cat))}
                  sx={{
                    height: 26, fontSize: 11.5, fontWeight: active ? 700 : 500, cursor: 'pointer',
                    bgcolor: active ? (cat === 'All' ? '#14b8a6' : cc.bg)    : '#f8fafc',
                    color:   active ? (cat === 'All' ? '#fff'    : cc.color) : '#64748b',
                    border: '1.5px solid',
                    borderColor: active ? (cat === 'All' ? '#14b8a6' : cc.border) : 'transparent',
                    '&:hover': { bgcolor: cat === 'All' ? (active ? '#0d9488' : '#f0fdfa') : (cc?.bg || '#f0fdfa') },
                  }}
                />
              );
            })}
          </Box>
        </Box>

        {canPost && (
          <Button
            variant="contained" startIcon={<AddIcon />} onClick={() => setPostOpen(true)}
            sx={{
              bgcolor: '#14b8a6', '&:hover': { bgcolor: '#0d9488' }, borderRadius: '10px',
              fontWeight: 700, fontSize: 13.5, boxShadow: '0 4px 12px rgba(20,184,166,0.3)',
              px: 2.5, height: 38, flexShrink: 0,
            }}
          >
            Post Announcement
          </Button>
        )}
      </Box>

      {/* ── Featured / Pinned section ── */}
      {pinned.length > 0 && (
        <Box sx={{ mb: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: .75, mb: 1.5 }}>
            <PushPinIcon sx={{ fontSize: 13, color: '#2563eb' }} />
            <Typography sx={{ fontSize: 10.5, fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', letterSpacing: '.8px' }}>
              Featured
            </Typography>
            <Box sx={{ flex: 1, height: '1px', bgcolor: '#dbeafe' }} />
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
            {pinned.map(item => <AnnCard key={item.id} item={item} featured canPost={canPost} onMenu={openMenu} onViewers={openViewers} />)}
          </Box>
        </Box>
      )}

      {/* ── Regular announcements ── */}
      {regular.length > 0 && (
        <Box>
          {pinned.length > 0 && <SectionRule label="Latest Updates" />}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {regular.map(item => <AnnCard key={item.id} item={item} canPost={canPost} onMenu={openMenu} onViewers={openViewers} />)}
          </Box>
        </Box>
      )}

      {/* ── Empty state ── */}
      {filtered.length === 0 && (
        <Box sx={{ textAlign: 'center', py: 9, bgcolor: '#fff', borderRadius: '16px', border: '1px dashed #e2e8f0' }}>
          <AnnouncementIcon sx={{ fontSize: 52, color: '#e2e8f0', display: 'block', mx: 'auto', mb: 1.5 }} />
          <Typography sx={{ color: '#94a3b8', fontSize: 15, fontWeight: 600 }}>
            {searchText || catFilter ? 'No announcements match your filters' : 'No announcements yet'}
          </Typography>
          {(searchText || catFilter) && (
            <Button onClick={() => { setSearchText(''); setCatFilter(''); }}
              sx={{ mt: 1.5, color: '#14b8a6', fontSize: 13, fontWeight: 600, textTransform: 'none' }}>
              Clear filters
            </Button>
          )}
        </Box>
      )}

      {/* ── Three-dot action menu ── */}
      <Menu
        anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={closeMenu}
        PaperProps={{ sx: { borderRadius: '12px', boxShadow: '0 4px 20px rgba(0,0,0,0.12)', border: '1px solid #f1f5f9', minWidth: 172 } }}
        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
      >
        <MenuItem onClick={() => {
          setEditForm({ title: menuItem.title, body: menuItem.body || '', location: menuItem.location || '', category: menuItem.category || 'General', priority: menuItem.priority || 'Normal', pinned: menuItem.pinned });
          setEditTarget(menuItem); setEditOpen(true); closeMenu();
        }} sx={{ fontSize: 13.5, gap: 1.25, py: 1.1, color: '#374151' }}>
          <EditOutlinedIcon sx={{ fontSize: 17, color: '#64748b' }} /> Edit
        </MenuItem>
        <MenuItem onClick={() => { togglePin(menuItem); closeMenu(); }} sx={{ fontSize: 13.5, gap: 1.25, py: 1.1, color: '#374151' }}>
          {menuItem?.pinned
            ? <><PushPinIcon         sx={{ fontSize: 17, color: '#64748b' }} /> Unpin</>
            : <><PushPinOutlinedIcon sx={{ fontSize: 17, color: '#64748b' }} /> Pin to top</>
          }
        </MenuItem>
        <MenuItem onClick={() => { archive(menuItem); closeMenu(); }} sx={{ fontSize: 13.5, gap: 1.25, py: 1.1, color: '#374151' }}>
          <ArchiveOutlinedIcon sx={{ fontSize: 17, color: '#64748b' }} /> Archive
        </MenuItem>
        <Divider sx={{ my: .5 }} />
        <MenuItem onClick={() => { remove(menuItem.id); closeMenu(); }} sx={{ fontSize: 13.5, gap: 1.25, py: 1.1, color: '#dc2626' }}>
          <DeleteIcon sx={{ fontSize: 17, color: '#dc2626' }} /> Delete
        </MenuItem>
      </Menu>

      {/* ── Post Announcement Dialog ── */}
      <Dialog open={postOpen} onClose={() => setPostOpen(false)} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16.5, pb: 1 }}>New Announcement</DialogTitle>
        <Divider />
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
            <TextField label="Title" required fullWidth value={postForm.title}
              onChange={e => setPostForm(f => ({ ...f, title: e.target.value }))} />
            <TextField label="Message" fullWidth multiline rows={4} value={postForm.body}
              onChange={e => setPostForm(f => ({ ...f, body: e.target.value }))} />

            {/* Location dropdown */}
            <Box>
              <FormControl fullWidth>
                <InputLabel>Location (optional)</InputLabel>
                <Select
                  value={showNewLocField ? '__add_new__' : (postForm.location || '')}
                  label="Location (optional)"
                  onChange={e => {
                    if (e.target.value === '__add_new__') {
                      setShowNewLocField(true);
                      setNewLocInput('');
                    } else {
                      setShowNewLocField(false);
                      setPostForm(f => ({ ...f, location: e.target.value }));
                    }
                  }}
                >
                  <MenuItem value=""><em>All Locations</em></MenuItem>
                  {locationOptions.map(loc => <MenuItem key={loc} value={loc}>{loc}</MenuItem>)}
                  <Divider sx={{ my: .5 }} />
                  <MenuItem value="__add_new__" sx={{ color: '#14b8a6', fontWeight: 600 }}>
                    + Add New Location
                  </MenuItem>
                </Select>
              </FormControl>
              {showNewLocField && (
                <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                  <TextField
                    size="small" fullWidth autoFocus
                    placeholder="Type new location name…"
                    value={newLocInput}
                    onChange={e => setNewLocInput(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === 'Enter' && newLocInput.trim()) {
                        const loc = newLocInput.trim();
                        if (!locationOptions.includes(loc)) setLocationOptions(prev => [...prev, loc]);
                        setPostForm(f => ({ ...f, location: loc }));
                        setShowNewLocField(false);
                        setNewLocInput('');
                      }
                    }}
                  />
                  <Button
                    variant="contained" size="small"
                    disabled={!newLocInput.trim()}
                    onClick={() => {
                      const loc = newLocInput.trim();
                      if (loc) {
                        if (!locationOptions.includes(loc)) setLocationOptions(prev => [...prev, loc]);
                        setPostForm(f => ({ ...f, location: loc }));
                        setShowNewLocField(false);
                        setNewLocInput('');
                      }
                    }}
                    sx={{ bgcolor: '#14b8a6', '&:hover': { bgcolor: '#0d9488' }, whiteSpace: 'nowrap', textTransform: 'none', fontWeight: 600 }}
                  >
                    Add
                  </Button>
                  <Button size="small" onClick={() => { setShowNewLocField(false); setNewLocInput(''); }}
                    sx={{ textTransform: 'none', color: '#64748b' }}>
                    Cancel
                  </Button>
                </Box>
              )}
            </Box>
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
              <FormControl fullWidth>
                <InputLabel>Category</InputLabel>
                <Select value={postForm.category} label="Category"
                  onChange={e => setPostForm(f => ({ ...f, category: e.target.value }))}>
                  {ANN_CAT_LABELS.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                </Select>
              </FormControl>
              <FormControl fullWidth>
                <InputLabel>Priority</InputLabel>
                <Select value={postForm.priority} label="Priority"
                  onChange={e => setPostForm(f => ({ ...f, priority: e.target.value }))}>
                  <MenuItem value="Normal">Normal</MenuItem>
                  <MenuItem value="High">⚠ High</MenuItem>
                  <MenuItem value="Urgent">🔴 Urgent</MenuItem>
                </Select>
              </FormControl>
            </Box>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setPostOpen(false)} disabled={saving}
            sx={{ borderRadius: '10px', textTransform: 'none', fontWeight: 600, color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={post} disabled={saving || !postForm.title.trim()}
            sx={{ borderRadius: '10px', bgcolor: '#14b8a6', '&:hover': { bgcolor: '#0d9488' }, textTransform: 'none', fontWeight: 700 }}>
            {saving ? <CircularProgress size={16} sx={{ mr: 1, color: '#fff' }} /> : null}
            Post
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Edit Announcement Dialog ── */}
      <Dialog open={editOpen} onClose={() => { setEditOpen(false); setEditTarget(null); }} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: '16px' } }}>
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16.5, pb: 1 }}>Edit Announcement</DialogTitle>
        <Divider />
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
            <TextField label="Title" required fullWidth value={editForm.title || ''}
              onChange={e => setEditForm(f => ({ ...f, title: e.target.value }))} />
            <TextField label="Message" fullWidth multiline rows={4} value={editForm.body || ''}
              onChange={e => setEditForm(f => ({ ...f, body: e.target.value }))} />

            {/* Location dropdown */}
            <Box>
              <FormControl fullWidth>
                <InputLabel>Location (optional)</InputLabel>
                <Select
                  value={showNewLocField ? '__add_new__' : (editForm.location || '')}
                  label="Location (optional)"
                  onChange={e => {
                    if (e.target.value === '__add_new__') {
                      setShowNewLocField(true);
                      setNewLocInput('');
                    } else {
                      setShowNewLocField(false);
                      setEditForm(f => ({ ...f, location: e.target.value }));
                    }
                  }}
                >
                  <MenuItem value=""><em>All Locations</em></MenuItem>
                  {locationOptions.map(loc => <MenuItem key={loc} value={loc}>{loc}</MenuItem>)}
                  <Divider sx={{ my: .5 }} />
                  <MenuItem value="__add_new__" sx={{ color: '#14b8a6', fontWeight: 600 }}>
                    + Add New Location
                  </MenuItem>
                </Select>
              </FormControl>
              {showNewLocField && (
                <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                  <TextField
                    size="small" fullWidth autoFocus
                    placeholder="Type new location name…"
                    value={newLocInput}
                    onChange={e => setNewLocInput(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === 'Enter' && newLocInput.trim()) {
                        const loc = newLocInput.trim();
                        if (!locationOptions.includes(loc)) setLocationOptions(prev => [...prev, loc]);
                        setEditForm(f => ({ ...f, location: loc }));
                        setShowNewLocField(false);
                        setNewLocInput('');
                      }
                    }}
                  />
                  <Button
                    variant="contained" size="small"
                    disabled={!newLocInput.trim()}
                    onClick={() => {
                      const loc = newLocInput.trim();
                      if (loc) {
                        if (!locationOptions.includes(loc)) setLocationOptions(prev => [...prev, loc]);
                        setEditForm(f => ({ ...f, location: loc }));
                        setShowNewLocField(false);
                        setNewLocInput('');
                      }
                    }}
                    sx={{ bgcolor: '#14b8a6', '&:hover': { bgcolor: '#0d9488' }, whiteSpace: 'nowrap', textTransform: 'none', fontWeight: 600 }}
                  >
                    Add
                  </Button>
                  <Button size="small" onClick={() => { setShowNewLocField(false); setNewLocInput(''); }}
                    sx={{ textTransform: 'none', color: '#64748b' }}>
                    Cancel
                  </Button>
                </Box>
              )}
            </Box>
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
              <FormControl fullWidth>
                <InputLabel>Category</InputLabel>
                <Select value={editForm.category || 'General'} label="Category"
                  onChange={e => setEditForm(f => ({ ...f, category: e.target.value }))}>
                  {ANN_CAT_LABELS.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
                </Select>
              </FormControl>
              <FormControl fullWidth>
                <InputLabel>Priority</InputLabel>
                <Select value={editForm.priority || 'Normal'} label="Priority"
                  onChange={e => setEditForm(f => ({ ...f, priority: e.target.value }))}>
                  <MenuItem value="Normal">Normal</MenuItem>
                  <MenuItem value="High">⚠ High</MenuItem>
                  <MenuItem value="Urgent">🔴 Urgent</MenuItem>
                </Select>
              </FormControl>
            </Box>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => { setEditOpen(false); setEditTarget(null); }} disabled={updating}
            sx={{ borderRadius: '10px', textTransform: 'none', fontWeight: 600, color: '#64748b' }}>Cancel</Button>
          <Button variant="contained" onClick={doUpdate} disabled={updating || !(editForm.title || '').trim()}
            sx={{ borderRadius: '10px', bgcolor: '#14b8a6', '&:hover': { bgcolor: '#0d9488' }, textTransform: 'none', fontWeight: 700 }}>
            {updating ? <CircularProgress size={16} sx={{ mr: 1, color: '#fff' }} /> : null}
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Viewer List Dialog (admin / HR only) ── */}
      <Dialog
        open={viewersOpen}
        onClose={() => { setViewersOpen(false); setViewersItem(null); setViewersData(null); }}
        maxWidth="sm" fullWidth
        PaperProps={{ sx: { borderRadius: '16px' } }}
      >
        <DialogTitle sx={{ fontWeight: 700, fontSize: 16.5, pb: 1 }}>
          Announcement Reach
          {viewersData && (
            <Typography component="span" sx={{ fontSize: 13, fontWeight: 500, color: '#64748b', ml: 1.5 }}>
              — "{viewersItem?.title}"
            </Typography>
          )}
        </DialogTitle>
        <Divider />
        <DialogContent sx={{ p: 0 }}>
          {viewersLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress sx={{ color: '#14b8a6' }} />
            </Box>
          ) : viewersData ? (
            <Box>
              {/* Summary bar */}
              <Box sx={{ px: 3, py: 2, bgcolor: '#f8fafc', borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', gap: 3 }}>
                <Box sx={{ textAlign: 'center' }}>
                  <Typography sx={{ fontSize: 24, fontWeight: 900, color: '#14b8a6', lineHeight: 1 }}>
                    {viewersData.totalViewed}
                  </Typography>
                  <Typography sx={{ fontSize: 11.5, color: '#64748b', fontWeight: 600 }}>Viewed</Typography>
                </Box>
                <Box sx={{ flex: 1, height: 8, bgcolor: '#e2e8f0', borderRadius: 4, overflow: 'hidden' }}>
                  <Box sx={{
                    height: '100%', borderRadius: 4, bgcolor: '#14b8a6',
                    width: viewersData.totalActive > 0
                      ? `${Math.round((viewersData.totalViewed / viewersData.totalActive) * 100)}%`
                      : '0%',
                    transition: 'width .6s ease',
                  }} />
                </Box>
                <Box sx={{ textAlign: 'center' }}>
                  <Typography sx={{ fontSize: 24, fontWeight: 900, color: '#94a3b8', lineHeight: 1 }}>
                    {viewersData.totalActive - viewersData.totalViewed}
                  </Typography>
                  <Typography sx={{ fontSize: 11.5, color: '#64748b', fontWeight: 600 }}>Not Seen</Typography>
                </Box>
                <Box sx={{ textAlign: 'center' }}>
                  <Typography sx={{ fontSize: 24, fontWeight: 900, color: '#0f172a', lineHeight: 1 }}>
                    {viewersData.totalActive > 0
                      ? `${Math.round((viewersData.totalViewed / viewersData.totalActive) * 100)}%`
                      : '0%'}
                  </Typography>
                  <Typography sx={{ fontSize: 11.5, color: '#64748b', fontWeight: 600 }}>Reach</Typography>
                </Box>
              </Box>

              {/* Viewed list */}
              {viewersData.viewed.length > 0 && (
                <Box sx={{ px: 3, pt: 2, pb: 1 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.25 }}>
                    <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: '#10b981' }} />
                    <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#10b981', textTransform: 'uppercase', letterSpacing: '.6px' }}>
                      Viewed ({viewersData.viewed.length})
                    </Typography>
                  </Box>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: .75 }}>
                    {viewersData.viewed.map(v => (
                      <Box key={v.employeeId} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: .75, px: 1.5, borderRadius: '10px', bgcolor: '#f0fdf4', border: '1px solid #bbf7d0' }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                          <Avatar sx={{ width: 30, height: 30, fontSize: '0.72rem', fontWeight: 700, bgcolor: '#14b8a6', color: '#fff' }}>
                            {(v.employeeName || 'E').charAt(0).toUpperCase()}
                          </Avatar>
                          <Box>
                            <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#0f172a', lineHeight: 1.2 }}>
                              {v.employeeName}
                            </Typography>
                            {v.department && (
                              <Typography sx={{ fontSize: 11, color: '#64748b' }}>{v.department}</Typography>
                            )}
                          </Box>
                        </Box>
                        <Typography sx={{ fontSize: 11, color: '#94a3b8', flexShrink: 0 }}>
                          {v.viewedAt ? fmtAnn(v.viewedAt) : ''}
                        </Typography>
                      </Box>
                    ))}
                  </Box>
                </Box>
              )}

              {/* Not Viewed list */}
              {viewersData.notViewed.length > 0 && (
                <Box sx={{ px: 3, pt: 1.5, pb: 2.5 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.25 }}>
                    <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: '#94a3b8' }} />
                    <Typography sx={{ fontSize: 11.5, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '.6px' }}>
                      Not Viewed ({viewersData.notViewed.length})
                    </Typography>
                  </Box>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: .75 }}>
                    {viewersData.notViewed.map(v => (
                      <Box key={v.employeeId} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: .75, px: 1.5, borderRadius: '10px', bgcolor: '#f8fafc', border: '1px solid #f1f5f9' }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
                          <Avatar sx={{ width: 30, height: 30, fontSize: '0.72rem', fontWeight: 700, bgcolor: '#e2e8f0', color: '#94a3b8' }}>
                            {(v.employeeName || 'E').charAt(0).toUpperCase()}
                          </Avatar>
                          <Box>
                            <Typography sx={{ fontSize: 13, fontWeight: 600, color: '#374151', lineHeight: 1.2 }}>
                              {v.employeeName}
                            </Typography>
                            {v.department && (
                              <Typography sx={{ fontSize: 11, color: '#94a3b8' }}>{v.department}</Typography>
                            )}
                          </Box>
                        </Box>
                        <Typography sx={{ fontSize: 11, color: '#cbd5e1' }}>Not seen</Typography>
                      </Box>
                    ))}
                  </Box>
                </Box>
              )}
            </Box>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => { setViewersOpen(false); setViewersItem(null); setViewersData(null); }}
            sx={{ borderRadius: '10px', textTransform: 'none', fontWeight: 600, color: '#64748b' }}>
            Close
          </Button>
        </DialogActions>
      </Dialog>

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
  const [year,     setYear]     = useState(now.getFullYear());
  const [month,    setMonth]    = useState(now.getMonth());
  const [holidays, setHolidays] = useState([]);

  useEffect(() => {
    leaveUploadApi.getHolidays()
      .then(setHolidays)
      .catch(() => {});
  }, []);

  // birthdays for current month
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

  // holidays for current month+year — holiday.date is "yyyy-MM-dd"
  const holidaysByDay = useMemo(() => {
    const m = new Map();
    holidays.forEach((h) => {
      const [y, mo, d] = h.date.split('-').map(Number);
      if (y === year && mo === month + 1) {
        if (!m.has(d)) m.set(d, []);
        m.get(d).push(h.name);
      }
    });
    return m;
  }, [holidays, month, year]);

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
        {/* Legend */}
        <Box sx={{ ml: 'auto', display: 'flex', gap: 2, alignItems: 'center' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Box sx={{ width: 10, height: 10, borderRadius: 0.5, bgcolor: '#dcfce7' }} />
            <Typography fontSize={11} color="text.secondary">Public Holiday</Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Box sx={{ width: 10, height: 10, borderRadius: 0.5, bgcolor: '#fef9c3' }} />
            <Typography fontSize={11} color="text.secondary">Birthday</Typography>
          </Box>
        </Box>
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
          const isToday  = day === now.getDate() && month === now.getMonth() && year === now.getFullYear();
          const bdays    = bdaysByDay.get(day)   || [];
          const hols     = holidaysByDay.get(day) || [];
          const isHoliday = hols.length > 0;
          return (
            <Box key={day} sx={{
              minHeight: 76, p: '6px 8px', borderRadius: 1.5,
              bgcolor: isToday ? '#eff6ff' : isHoliday ? '#f0fdf4' : 'white',
              border: isToday ? '1.5px solid #1976d2' : isHoliday ? '1px solid #bbf7d0' : '1px solid #f1f5f9',
            }}>
              <Typography fontSize={12} fontWeight={isToday ? 800 : 500}
                sx={{ color: isToday ? '#1976d2' : isHoliday ? '#15803d' : 'inherit', mb: 0.5 }}>
                {day}
              </Typography>
              {/* Public holidays */}
              {hols.map((name, idx) => (
                <Tooltip key={idx} title={`🎉 ${name}`} arrow>
                  <Box sx={{ fontSize: 9.5, fontWeight: 600, bgcolor: '#dcfce7', color: '#15803d', borderRadius: 0.5, px: 0.5, py: 0.1, mb: 0.25, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', cursor: 'default' }}>
                    🎉 {name}
                  </Box>
                </Tooltip>
              ))}
              {/* Birthdays */}
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
  const [searchParams, setSearchParams] = useSearchParams();
  const tab    = parseInt(searchParams.get('tab') || '0', 10);
  const setTab = (v) => setSearchParams({ tab: v }, { replace: false });

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
