import { useEffect, useState } from 'react';

// 1024px: the desktop console needs 216px sider + paddings + a 344px docked detail pane,
// which leaves a comfortable ~408px master pane at exactly 1024. iPad portrait and phone
// landscape stay on the mobile shell (which renders well as a centered 480px column).
export const DESKTOP_MEDIA_QUERY = '(min-width: 1024px)';

/** True on wide viewports; live-updates on resize. Pure CSR — no SSR guard needed. */
export function useDesktopViewport(): boolean {
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia(DESKTOP_MEDIA_QUERY).matches);
  useEffect(() => {
    const mql = window.matchMedia(DESKTOP_MEDIA_QUERY);
    const sync = () => setIsDesktop(mql.matches);
    mql.addEventListener('change', sync);
    // CDP viewport emulation (Playwright, in-app browser previews) resizes the page without
    // dispatching resize/matchMedia change events; the timer keeps the gate honest there.
    // React bails out when the boolean is unchanged, so the poll is render-free.
    const timer = window.setInterval(sync, 500);
    return () => {
      mql.removeEventListener('change', sync);
      window.clearInterval(timer);
    };
  }, []);
  return isDesktop;
}
