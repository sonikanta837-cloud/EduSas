import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Typography, Avatar, CircularProgress,
  IconButton, Tooltip, Button,
} from '@mui/material';
import PersonIcon       from '@mui/icons-material/Person';
import OpenInNewIcon    from '@mui/icons-material/OpenInNew';
import PeopleIcon       from '@mui/icons-material/People';
import UnfoldMoreIcon   from '@mui/icons-material/UnfoldMore';
import UnfoldLessIcon   from '@mui/icons-material/UnfoldLess';
import { employeeApi } from '../api/employeeApi';
import { toast }       from 'react-toastify';

// ── Layout constants ──────────────────────────────────────────────────────────
const CARD_W    = 220;   // card width px
const CARD_H    = 64;    // card height px (fixed)
const CARD_GAP  = 8;     // gap between sibling branches
const BADGE_OVR = 22;    // badge overhangs card right edge by this much
const SPACER    = 22;    // space between card+badge and connector
const CONN_W    = 54;    // connector SVG width
const LINE      = '#cbd5e1';
const BLUE      = '#1976d2';

// ── Subtree height (recursive) ────────────────────────────────────────────────
// Returns the total render height of a branch rooted at nodeId.
function subtreeH(nodeId, childrenMap, expandedSet) {
  if (!expandedSet.has(nodeId)) return CARD_H;
  const childIds = childrenMap.get(nodeId) || [];
  if (!childIds.length) return CARD_H;
  const total = childIds.reduce(
    (sum, cid, i) => sum + subtreeH(cid, childrenMap, expandedSet) + (i > 0 ? CARD_GAP : 0),
    0,
  );
  return Math.max(CARD_H, total);
}

// ── NodeCard ──────────────────────────────────────────────────────────────────
const NodeCard = ({ emp, hasChildren, isExpanded, isLoading, onToggle, onOpen }) => (
  <Box sx={{ position: 'relative', flexShrink: 0, width: CARD_W }}>
    {/* Card body */}
    <Box
      onClick={hasChildren ? onToggle : undefined}
      sx={{
        display: 'flex', alignItems: 'center', gap: 1.5,
        height: CARD_H, px: 1.5,
        border: '1.5px solid', borderColor: '#d1d5db',
        borderRadius: '10px', bgcolor: 'white',
        boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
        cursor: hasChildren ? 'pointer' : 'default',
        transition: 'border-color 0.15s, box-shadow 0.15s',
        position: 'relative',
        '&:hover .open-btn': { opacity: 1 },
        ...(hasChildren ? {
          '&:hover': { borderColor: BLUE, boxShadow: '0 2px 12px rgba(25,118,210,0.18)' },
        } : {}),
      }}
    >
      <Avatar sx={{
        width: 38, height: 38,
        bgcolor: '#f0f2f5', color: '#9aa0a6',
        border: '1px solid #e5e7eb', flexShrink: 0,
      }}>
        <PersonIcon sx={{ fontSize: 24 }} />
      </Avatar>

      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Typography sx={{ fontSize: 12.5, fontWeight: 700, lineHeight: 1.3, color: '#1e293b' }} noWrap>
          {emp.fullName}
        </Typography>
        <Typography sx={{ fontSize: 11, color: '#64748b', lineHeight: 1.3 }} noWrap>
          {emp.position || emp.role || '—'}
        </Typography>
      </Box>

      <Tooltip title="View profile">
        <IconButton
          className="open-btn"
          size="small"
          onClick={(e) => { e.stopPropagation(); onOpen(); }}
          sx={{ opacity: 0, p: 0.3, transition: 'opacity 0.15s', flexShrink: 0 }}
        >
          <OpenInNewIcon sx={{ fontSize: 12 }} />
        </IconButton>
      </Tooltip>
    </Box>

    {/* Count badge — overhangs right edge */}
    {hasChildren && (
      <Box
        onClick={onToggle}
        sx={{
          position: 'absolute',
          right: -(BADGE_OVR + 2), top: '50%', transform: 'translateY(-50%)',
          minWidth: 30, px: 0.8, py: 0.2,
          bgcolor: isExpanded ? '#64748b' : BLUE, color: 'white',
          borderRadius: '6px', fontSize: 11.5, fontWeight: 700,
          textAlign: 'center', lineHeight: 1.7,
          cursor: 'pointer', zIndex: 2,
          boxShadow: '0 1px 4px rgba(0,0,0,0.25)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}
      >
        {isLoading
          ? <CircularProgress size={10} sx={{ color: 'white' }} />
          : emp.subordinateCount}
      </Box>
    )}
  </Box>
);

// ── SVG connector ─────────────────────────────────────────────────────────────
// childHeights: actual render heights of each child branch (may include subtrees)
const Connector = ({ childHeights }) => {
  const n = childHeights.length;
  if (!n) return null;

  // Accumulate Y centers for each child
  let y = 0;
  const centers = childHeights.map((h) => {
    const cy = y + h / 2;
    y += h + CARD_GAP;
    return cy;
  });

  const totalH = y - CARD_GAP;
  const rootY  = totalH / 2;
  const spX    = CONN_W / 2;

  return (
    <svg
      width={CONN_W}
      height={totalH}
      style={{ flexShrink: 0, display: 'block', overflow: 'visible' }}
    >
      {/* Horizontal line from root center to spine */}
      <line x1={0} y1={rootY} x2={spX} y2={rootY} stroke={LINE} strokeWidth={1.5} />

      {/* Vertical spine (only when > 1 child) */}
      {n > 1 && (
        <line
          x1={spX} y1={centers[0]}
          x2={spX} y2={centers[n - 1]}
          stroke={LINE} strokeWidth={1.5}
        />
      )}

      {/* Horizontal stub to each child center */}
      {centers.map((cy, i) => (
        <line key={i} x1={spX} y1={cy} x2={CONN_W} y2={cy} stroke={LINE} strokeWidth={1.5} />
      ))}
    </svg>
  );
};

// ── Branch (recursive) ────────────────────────────────────────────────────────
const Branch = ({ nodeId, nodes, childrenMap, expandedIds, loadingIds, onToggle, onOpen }) => {
  const emp = nodes.get(nodeId);
  if (!emp) return null;

  const isExpanded  = expandedIds.has(nodeId);
  const isLoading   = loadingIds.has(nodeId);
  const childIds    = childrenMap.get(nodeId) || [];
  const hasChildren = emp.subordinateCount > 0;
  const showKids    = isExpanded && childIds.length > 0;

  // Compute each child's subtree height for the connector
  const childHeights = showKids
    ? childIds.map((id) => subtreeH(id, childrenMap, expandedIds))
    : [];

  // This branch's own total height
  const myH = showKids
    ? Math.max(
        CARD_H,
        childHeights.reduce((s, h, i) => s + h + (i > 0 ? CARD_GAP : 0), 0),
      )
    : CARD_H;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: myH, flexShrink: 0 }}>
      {/* Node card — vertically centred inside myH */}
      <NodeCard
        emp={emp}
        hasChildren={hasChildren}
        isExpanded={isExpanded}
        isLoading={isLoading}
        onToggle={() => onToggle(nodeId, emp)}
        onOpen={() => onOpen(nodeId)}
      />

      {showKids && (
        <>
          {/* Space for badge overhang */}
          <Box sx={{ width: SPACER, flexShrink: 0 }} />

          {/* SVG connector */}
          <Connector childHeights={childHeights} />

          {/* Children column */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: `${CARD_GAP}px`, flexShrink: 0 }}>
            {childIds.map((cid) => (
              <Branch
                key={cid}
                nodeId={cid}
                nodes={nodes}
                childrenMap={childrenMap}
                expandedIds={expandedIds}
                loadingIds={loadingIds}
                onToggle={onToggle}
                onOpen={onOpen}
              />
            ))}
          </Box>
        </>
      )}
    </Box>
  );
};

// ── Page ──────────────────────────────────────────────────────────────────────
const OrgChartPage = () => {
  const navigate = useNavigate();

  const [nodes,       setNodes]       = useState(new Map()); // id → emp DTO
  const [childrenMap, setChildrenMap] = useState(new Map()); // id → [childId]
  const [rootIds,     setRootIds]     = useState([]);
  const [expandedIds, setExpandedIds] = useState(new Set());
  const [loadingIds,  setLoadingIds]  = useState(new Set());
  const [pageLoading, setPageLoading] = useState(true);

  // Load the direct reports for a node (if not already loaded)
  const loadChildren = useCallback(async (nodeId) => {
    const children = await employeeApi.getTeam(nodeId);
    setNodes((prev) => {
      const next = new Map(prev);
      children.forEach((c) => next.set(c.id, c));
      return next;
    });
    setChildrenMap((prev) => {
      const next = new Map(prev);
      next.set(nodeId, children.map((c) => c.id));
      return next;
    });
    return children;
  }, []);

  // Toggle expand / collapse for a node
  const toggleNode = useCallback(async (nodeId, emp) => {
    if (expandedIds.has(nodeId)) {
      setExpandedIds((prev) => { const s = new Set(prev); s.delete(nodeId); return s; });
      return;
    }
    // Need to expand — load children first if missing
    if (emp.subordinateCount > 0 && !childrenMap.has(nodeId)) {
      setLoadingIds((prev) => new Set([...prev, nodeId]));
      try { await loadChildren(nodeId); }
      catch { toast.error('Failed to load team'); }
      finally { setLoadingIds((prev) => { const s = new Set(prev); s.delete(nodeId); return s; }); }
    }
    setExpandedIds((prev) => new Set([...prev, nodeId]));
  }, [expandedIds, childrenMap, loadChildren]);

  // Initial load: top level + their direct reports (2 levels)
  useEffect(() => {
    const init = async () => {
      try {
        const roots = await employeeApi.getOrgChart();
        if (!roots.length) { setPageLoading(false); return; }

        const nodesAcc      = new Map(roots.map((e) => [e.id, e]));
        const childrenAcc   = new Map();
        const expandedAcc   = new Set();

        // Load first-level children for every root in parallel
        // Use individual .catch so one failure doesn't blank the whole chart
        await Promise.all(
          roots
            .filter((r) => r.subordinateCount > 0)
            .map((r) =>
              employeeApi.getTeam(r.id)
                .then((children) => {
                  childrenAcc.set(r.id, children.map((c) => c.id));
                  children.forEach((c) => nodesAcc.set(c.id, c));
                  expandedAcc.add(r.id);
                })
                .catch(() => {}),
            ),
        );

        setNodes(nodesAcc);
        setChildrenMap(childrenAcc);
        setRootIds(roots.map((r) => r.id));
        setExpandedIds(expandedAcc);
      } catch {
        toast.error('Failed to load org chart');
      } finally {
        setPageLoading(false);
      }
    };
    init();
  }, []);

  // Expand all: load every unloaded node level-by-level (BFS), then expand all
  const expandAll = async () => {
    setPageLoading(true);
    try {
      // Work on local copies to avoid repeated state reads
      const nodesAcc    = new Map(nodes);
      const childrenAcc = new Map(childrenMap);

      let toLoad = [...nodesAcc.keys()].filter(
        (id) => nodesAcc.get(id)?.subordinateCount > 0 && !childrenAcc.has(id),
      );

      while (toLoad.length) {
        const results = await Promise.all(
          toLoad.map((id) =>
            employeeApi.getTeam(id)
              .then((ch) => ({ id, ch }))
              .catch(() => ({ id, ch: [] })),
          ),
        );
        const nextLoad = [];
        results.forEach(({ id, ch }) => {
          childrenAcc.set(id, ch.map((c) => c.id));
          ch.forEach((c) => {
            nodesAcc.set(c.id, c);
            if (c.subordinateCount > 0 && !childrenAcc.has(c.id)) nextLoad.push(c.id);
          });
        });
        toLoad = nextLoad;
      }

      setNodes(nodesAcc);
      setChildrenMap(childrenAcc);
      setExpandedIds(new Set(nodesAcc.keys()));
    } catch {
      toast.error('Failed to expand');
    } finally {
      setPageLoading(false);
    }
  };

  const collapseAll = () => setExpandedIds(new Set());

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2, flexWrap: 'wrap' }}>
        <PeopleIcon color="primary" />
        <Typography variant="h5" fontWeight={700}>Organisation Chart</Typography>
        <Box sx={{ flexGrow: 1 }} />
        <Button
          size="small" variant="outlined" startIcon={<UnfoldMoreIcon />}
          onClick={expandAll} disabled={pageLoading}
        >
          Expand All
        </Button>
        <Button
          size="small" variant="outlined" startIcon={<UnfoldLessIcon />}
          onClick={collapseAll} disabled={pageLoading}
        >
          Collapse All
        </Button>
      </Box>

      {/* Tree container */}
      <Box sx={{
        bgcolor: 'white', borderRadius: 2,
        border: '1px solid #e2e8f0',
        boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
        p: { xs: 3, md: 5 },
        overflowX: 'auto', overflowY: 'auto',
        minHeight: 300,
      }}>
        {pageLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 240 }}>
            <CircularProgress />
          </Box>
        ) : !rootIds.length ? (
          <Typography color="text.secondary" sx={{ textAlign: 'center', mt: 4 }}>
            No employees found
          </Typography>
        ) : (
          <Box sx={{
            display: 'inline-flex', flexDirection: 'column',
            gap: `${CARD_GAP * 4}px`,
          }}>
            {rootIds.map((rid) => (
              <Branch
                key={rid}
                nodeId={rid}
                nodes={nodes}
                childrenMap={childrenMap}
                expandedIds={expandedIds}
                loadingIds={loadingIds}
                onToggle={toggleNode}
                onOpen={(id) => navigate(`/employees/${id}`)}
              />
            ))}
          </Box>
        )}
      </Box>

    </Box>
  );
};

export default OrgChartPage;
