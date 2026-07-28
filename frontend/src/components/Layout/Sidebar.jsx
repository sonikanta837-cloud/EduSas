import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { Box, Tooltip, Typography, IconButton, Collapse, Drawer, useMediaQuery, useTheme } from '@mui/material';
import ChevronLeftIcon  from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import ExpandMoreIcon   from '@mui/icons-material/ExpandMore';
import { toggleSidebar, setSidebarOpen, setMobileSidebarOpen } from '../../store/uiSlice';
import { getPortalById } from './portals';
import { HEADER_HEIGHT } from './Header';
import { PORTAL_BAR_HEIGHT } from './PortalSwitcherBar';

export const SIDEBAR_W_OPEN   = 272;
export const SIDEBAR_W_CLOSED = 72;

const SIDEBAR_TOP = HEADER_HEIGHT + PORTAL_BAR_HEIGHT;
const SIDEBAR_TRANSITION_MS = 280;

const EXPANDED_GROUP_STORAGE_KEY = 'sidebar_expanded_group';

const isPathActive = (pathname, path) =>
  pathname === path || (path !== '/dashboard' && pathname.startsWith(path + '/'));

const scrollbarSx = {
  scrollbarWidth: 'thin',
  scrollbarColor: 'rgba(255,255,255,0.15) transparent',
  '&::-webkit-scrollbar': { width: 4 },
  '&::-webkit-scrollbar-track': { background: 'transparent' },
  '&::-webkit-scrollbar-thumb': { background: 'rgba(255,255,255,0.15)', borderRadius: 4 },
  '&::-webkit-scrollbar-thumb:hover': { background: 'rgba(255,255,255,0.3)' },
};

const Sidebar = () => {
  const dispatch  = useDispatch();
  const navigate  = useNavigate();
  const location  = useLocation();
  const theme     = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));

  const { user } = useSelector((s) => s.auth);
  const { sidebarOpen, mobileSidebarOpen } = useSelector((s) => s.ui);
  const { activePortal } = useSelector((s) => s.portal);
  const role = user?.role || 'EMPLOYEE';
  const isSuperUser = role === 'ADMIN' || role === 'DIRECTOR';

  const activePortalConfig = getPortalById(activePortal);
  const navConfig = useMemo(() => activePortalConfig?.menu || [], [activePortalConfig]);

  const allowedPaths = useMemo(() => {
    if (isSuperUser || !user?.allowedModules) return null;
    try { return JSON.parse(user.allowedModules); } catch { return null; }
  }, [isSuperUser, user?.allowedModules]);

  const isVisible = useCallback((leaf) => {
    if (leaf.superUserOnly) return isSuperUser;
    if (allowedPaths) return allowedPaths.includes(leaf.path);
    return leaf.roles.includes(role);
  }, [isSuperUser, allowedPaths, role]);

  // Role-filtered nav — a group survives only if at least one child is visible
  const navItems = useMemo(() => navConfig
    .map((entry) => {
      if (entry.type === 'item') return isVisible(entry) ? entry : null;
      const children = entry.children.filter(isVisible);
      return children.length > 0 ? { ...entry, children } : null;
    })
    .filter(Boolean), [navConfig, isVisible]);

  const [expandedGroup, setExpandedGroup] = useState(() => {
    try { return localStorage.getItem(EXPANDED_GROUP_STORAGE_KEY) || null; } catch { return null; }
  });

  // Switching portals swaps the whole menu tree — any expanded group id from
  // the previous portal is meaningless (and may collide) in the new one.
  useEffect(() => {
    setExpandedGroup(null);
  }, [activePortal]);

  // Auto-expand the parent whose child page is active (covers direct nav + refresh)
  useEffect(() => {
    const activeGroup = navItems.find(
      (entry) => entry.type === 'group' && entry.children.some((c) => isPathActive(location.pathname, c.path))
    );
    if (activeGroup) setExpandedGroup(activeGroup.id);
  }, [location.pathname, navItems]);

  // Persist the open section across refreshes
  useEffect(() => {
    try {
      if (expandedGroup) localStorage.setItem(EXPANDED_GROUP_STORAGE_KEY, expandedGroup);
      else localStorage.removeItem(EXPANDED_GROUP_STORAGE_KEY);
    } catch { /* localStorage unavailable — non-fatal */ }
  }, [expandedGroup]);

  // Icon-only desktop rail has no room for inline submenus; labels only show
  // once the rail is expanded (desktop) or always on the mobile drawer.
  const showLabels = isDesktop ? sidebarOpen : true;

  // Roving tabIndex bookkeeping for arrow-key navigation across all visible rows
  const itemRefs = useRef([]);
  itemRefs.current = [];
  const registerRef = (el) => { if (el) itemRefs.current.push(el); };

  const handleActivateKeyDown = (e, action) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      action();
    }
  };

  const handleContainerKeyDown = (e) => {
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) return;
    const idx = itemRefs.current.indexOf(document.activeElement);
    if (idx === -1) return;
    e.preventDefault();
    const last = itemRefs.current.length - 1;
    let nextIdx = idx;
    if (e.key === 'ArrowDown') nextIdx = idx === last ? 0 : idx + 1;
    else if (e.key === 'ArrowUp') nextIdx = idx === 0 ? last : idx - 1;
    else if (e.key === 'Home') nextIdx = 0;
    else if (e.key === 'End') nextIdx = last;
    itemRefs.current[nextIdx]?.focus();
  };

  const handleNavigate = (path) => {
    navigate(path);
    if (!isDesktop) dispatch(setMobileSidebarOpen(false));
  };

  const handleGroupClick = (group) => {
    // Collapsed desktop rail: expand the rail itself before opening the section
    if (isDesktop && !sidebarOpen) {
      dispatch(setSidebarOpen(true));
      setExpandedGroup(group.id);
      return;
    }
    setExpandedGroup((prev) => (prev === group.id ? null : group.id));
  };

  const renderRow = ({ key, icon, label, active, onClick, isGroupHeader, expanded, controlsId, isChild }) => {
    const content = (
      <Box
        ref={registerRef}
        role="button"
        tabIndex={0}
        aria-expanded={isGroupHeader ? expanded : undefined}
        aria-controls={isGroupHeader ? controlsId : undefined}
        aria-current={!isGroupHeader && active ? 'page' : undefined}
        onClick={onClick}
        onKeyDown={(e) => handleActivateKeyDown(e, onClick)}
        sx={{
          width: '100%',
          minWidth: 0,
          display: 'flex',
          flexDirection: showLabels ? 'row' : 'column',
          alignItems: 'center',
          justifyContent: showLabels ? 'flex-start' : 'center',
          py: showLabels ? 0.55 : 0.5,
          px: showLabels ? 1.25 : 0.5,
          borderRadius: '10px',
          cursor: 'pointer',
          position: 'relative',
          color: active ? '#14b8a6' : 'rgba(255,255,255,0.55)',
          bgcolor: active ? 'rgba(20,184,166,0.15)' : 'transparent',
          transition: 'all 0.15s ease',
          outline: 'none',
          '&:hover': {
            bgcolor: active ? 'rgba(20,184,166,0.2)' : 'rgba(255,255,255,0.06)',
            color: active ? '#14b8a6' : 'rgba(255,255,255,0.9)',
          },
          '&:focus-visible': {
            boxShadow: 'inset 0 0 0 2px rgba(20,184,166,0.6)',
          },
          '&::before': active && !isGroupHeader ? {
            content: '""',
            position: 'absolute',
            left: 0,
            top: '20%',
            height: '60%',
            width: 3,
            borderRadius: '0 3px 3px 0',
            bgcolor: '#14b8a6',
          } : {},
        }}
      >
        <Box sx={{ '& svg': { fontSize: isChild ? 19 : 21, display: 'block' }, flexShrink: 0, mr: showLabels ? 1.25 : 0 }}>
          {icon}
        </Box>

        {showLabels && (
          <Typography
            sx={{
              fontSize: isChild ? '0.8rem' : '0.86rem',
              fontWeight: active ? 600 : 400,
              lineHeight: 1.25,
              // Single line whenever it fits; wraps to a 2nd line only for
              // labels too long for the rail, instead of ellipsis-truncating.
              whiteSpace: 'normal',
              wordBreak: 'break-word',
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              color: 'inherit',
              flex: 1,
              minWidth: 0,
            }}
          >
            {label}
          </Typography>
        )}

        {showLabels && isGroupHeader && (
          <ExpandMoreIcon
            sx={{
              fontSize: 19,
              ml: 0.5,
              flexShrink: 0,
              color: 'inherit',
              transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
              transition: 'transform 0.25s ease',
            }}
          />
        )}
      </Box>
    );

    return showLabels ? (
      <Box key={key} sx={{ width: '100%' }}>{content}</Box>
    ) : (
      <Tooltip key={key} title={label} placement="right" arrow>
        {content}
      </Tooltip>
    );
  };

  const navContent = (
    <>
      {/* Nav items */}
      <Box
        component="nav"
        aria-label="Main navigation"
        onKeyDown={handleContainerKeyDown}
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: showLabels ? 'flex-start' : 'center',
          width: '100%',
          px: showLabels ? 1.25 : 0.5,
          pt: 1.5,
          gap: 0.25,
          flex: 1,
        }}
      >
        {navItems.map((entry) => {
          if (entry.type === 'item') {
            const active = isPathActive(location.pathname, entry.path);
            return renderRow({
              key: entry.path,
              icon: entry.icon,
              label: entry.label,
              active,
              onClick: () => handleNavigate(entry.path),
            });
          }

          const isExpanded  = showLabels && expandedGroup === entry.id;
          const groupActive = entry.children.some((c) => isPathActive(location.pathname, c.path));
          const controlsId  = `submenu-${entry.id}`;

          return (
            <Box key={entry.id} sx={{ width: '100%' }}>
              {renderRow({
                icon: entry.icon,
                label: entry.label,
                active: groupActive,
                onClick: () => handleGroupClick(entry),
                isGroupHeader: true,
                expanded: isExpanded,
                controlsId,
              })}
              {showLabels && (
                <Collapse in={isExpanded} timeout={250} unmountOnExit>
                  <Box
                    id={controlsId}
                    role="group"
                    aria-label={entry.label}
                    sx={{ display: 'flex', flexDirection: 'column', gap: 0.25, pl: 1.75, pt: 0.25, pb: 0.5 }}
                  >
                    {entry.children.map((child) => {
                      const active = isPathActive(location.pathname, child.path);
                      return renderRow({
                        key: child.path,
                        icon: child.icon,
                        label: child.label,
                        active,
                        onClick: () => handleNavigate(child.path),
                        isChild: true,
                      });
                    })}
                  </Box>
                </Collapse>
              )}
            </Box>
          );
        })}
      </Box>

      {/* Version */}
      <Box sx={{ py: 1.5, flexShrink: 0, width: '100%', textAlign: 'center' }}>
        <Typography sx={{ fontSize: 8.5, color: 'rgba(255,255,255,0.18)', letterSpacing: '0.04em' }}>
          v1.0
        </Typography>
      </Box>
    </>
  );

  // ── Tablet / mobile: slide-in drawer with overlay ──────────────────────────
  if (!isDesktop) {
    return (
      <Drawer
        variant="temporary"
        anchor="left"
        open={mobileSidebarOpen && Boolean(activePortal)}
        onClose={() => dispatch(setMobileSidebarOpen(false))}
        transitionDuration={SIDEBAR_TRANSITION_MS}
        ModalProps={{ keepMounted: true }}
        sx={{ zIndex: (t) => t.zIndex.drawer + 2 }}
        PaperProps={{
          sx: {
            width: SIDEBAR_W_OPEN,
            top: SIDEBAR_TOP,
            height: `calc(100% - ${SIDEBAR_TOP}px)`,
            bgcolor: '#0f172a',
            display: 'flex',
            flexDirection: 'column',
            overflowY: 'auto',
            overflowX: 'hidden',
            ...scrollbarSx,
          },
        }}
      >
        {navContent}
      </Drawer>
    );
  }

  // ── Desktop: fixed, collapsible rail ────────────────────────────────────────
  // No portal selected → rail slides fully shut (width 0); nothing to toggle.
  const width = !activePortal ? 0 : (sidebarOpen ? SIDEBAR_W_OPEN : SIDEBAR_W_CLOSED);

  return (
    <>
      {/* Toggle button — separate fixed element so overflow:hidden doesn't clip it */}
      {activePortal && (
        <IconButton
          onClick={() => dispatch(toggleSidebar())}
          size="small"
          aria-label={sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar'}
          sx={{
            position: 'fixed',
            top: SIDEBAR_TOP + 14,
            left: width - 13,
            zIndex: 1250,
            width: 26,
            height: 26,
            borderRadius: 0,
            bgcolor: '#1e293b',
            border: '2px solid rgba(255,255,255,0.2)',
            color: '#14b8a6',
            transition: `left ${SIDEBAR_TRANSITION_MS}ms ease`,
            '&:hover': { bgcolor: 'rgba(20,184,166,0.25)', borderColor: '#14b8a6' },
            boxShadow: '0 2px 8px rgba(0,0,0,0.4)',
          }}
        >
          {sidebarOpen ? <ChevronLeftIcon sx={{ fontSize: 16 }} /> : <ChevronRightIcon sx={{ fontSize: 16 }} />}
        </IconButton>
      )}

      <Box
        sx={{
          position: 'fixed',
          top: SIDEBAR_TOP,
          left: 0,
          width,
          height: `calc(100vh - ${SIDEBAR_TOP}px)`,
          bgcolor: '#0f172a',
          display: 'flex',
          flexDirection: 'column',
          alignItems: showLabels ? 'flex-start' : 'center',
          zIndex: 1200,
          boxShadow: activePortal ? '2px 0 8px rgba(0,0,0,0.18)' : 'none',
          overflowY: 'auto',
          overflowX: 'hidden',
          transition: `width ${SIDEBAR_TRANSITION_MS}ms ease`,
          ...scrollbarSx,
        }}
      >
        {navContent}
      </Box>
    </>
  );
};

export default Sidebar;
