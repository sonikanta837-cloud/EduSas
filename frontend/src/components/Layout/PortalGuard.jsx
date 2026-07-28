import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { findOwningPortal } from './portals';
import { portalPermissionsApi } from '../../api/portalPermissionsApi';

// Guards against direct navigation (typed URL, bookmark, stale link) into a
// portal's pages that the user's role isn't authorized for — independent of
// whichever portal the sidebar currently happens to show. Runs on every route
// change and re-verifies with the backend each time, so a permission revoked
// after login is enforced on the very next navigation, not just at login.
const PortalGuard = ({ children }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { allowedPortals } = useSelector((s) => s.portal);

  useEffect(() => {
    const owningPortal = findOwningPortal(location.pathname);
    if (!owningPortal) return; // shared page (e.g. /profile) — not portal-gated

    if (!allowedPortals.includes(owningPortal.id)) {
      navigate('/access-denied', { replace: true });
      return;
    }

    // Defense-in-depth: the client-side list may be stale (revoked since last
    // fetch) — ask the server, which is the actual source of truth.
    let cancelled = false;
    portalPermissionsApi.checkAccess(owningPortal.id).catch((err) => {
      if (!cancelled && err?.response?.status === 403) {
        navigate('/access-denied', { replace: true });
      }
    });
    return () => { cancelled = true; };
  }, [location.pathname, allowedPortals, navigate]);

  return children;
};

export default PortalGuard;
