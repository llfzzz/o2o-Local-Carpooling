import { useIsFetching, useIsMutating } from '@tanstack/react-query';
import { DesktopApp } from './desktop/DesktopApp';
import { DesktopLogin } from './desktop/DesktopLogin';
import { useSession } from './lib/session';
import { useDesktopViewport } from './lib/useViewport';
import { LoginScreen } from './mobile/LoginScreen';
import { MobileApp } from './mobile/MobileApp';

/** Viewport-gated dual shell: narrow keeps the H5 Trip Flow, wide gets the rider console.
 *  Both shells share the zustand session and the TanStack Query cache, so a live resize
 *  across the breakpoint swaps chrome without losing auth or refetching warm data. */
export default function App() {
  const session = useSession((state) => state.session);
  const isDesktop = useDesktopViewport();
  return (
    <>
      <GlobalActivityBar />
      {isDesktop
        ? (session ? <DesktopApp session={session} /> : <DesktopLogin />)
        : (session ? <MobileApp session={session} /> : <LoginScreen />)}
    </>
  );
}

/**
 * Thin top progress bar while a user action or first load is in flight, so slow requests never
 * look frozen. Background poll refetches are excluded to avoid a permanently blinking bar.
 */
function GlobalActivityBar() {
  const loading = useIsFetching({ predicate: (query) => query.state.status === 'pending' });
  const busy = loading + useIsMutating() > 0;
  return <div className={`global-activity${busy ? ' on' : ''}`} aria-hidden />;
}
