// Loads the AMap JS API 2.0 once per page.
//
// Rendering only. Every POI lookup, geocode and route quote goes through map-service (see
// queries.ts), so this key is only ever used to draw tiles and shapes. That keeps a leaked
// browser key from being usable to farm place data, and keeps search results server-authoritative.
//
// The security JS code is NOT inlined here. AMap's documented production pattern is to proxy its
// service host, so nginx holds the code and the browser never sees it (deploy/nginx).

declare global {
  interface Window {
    AMap?: AMapNamespace;
    _AMapSecurityConfig?: { serviceHost?: string; securityJsCode?: string };
  }
}

/** The slice of the AMap API this app uses. Deliberately narrow — we only render. */
export type AMapNamespace = {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => AMapMap;
  Marker: new (options: Record<string, unknown>) => AMapOverlay;
  Polyline: new (options: Record<string, unknown>) => AMapOverlay;
  Circle: new (options: Record<string, unknown>) => AMapOverlay;
  LngLat: new (lng: number, lat: number) => unknown;
  /** Async plugin loader (ToolBar, Scale, …). Only used to enrich interaction, never to resolve. */
  plugin: (names: string[], onReady: () => void) => void;
  ToolBar?: new (options?: Record<string, unknown>) => unknown;
  Scale?: new (options?: Record<string, unknown>) => unknown;
};

export type AMapMap = {
  add(overlay: AMapOverlay | AMapOverlay[]): void;
  remove(overlay: AMapOverlay | AMapOverlay[]): void;
  setFitView(overlays?: AMapOverlay[] | null, immediately?: boolean, avoid?: number[]): void;
  setCenter(position: unknown): void;
  setZoom(zoom: number): void;
  addControl(control: unknown): void;
  destroy(): void;
  on(event: string, handler: (event: { lnglat: { getLng(): number; getLat(): number } }) => void): void;
  /** Fires once the first tile set has actually rendered. */
  on(event: 'complete', handler: () => void): void;
};

/**
 * AMap key-rejection detection.
 *
 * When the key is rejected — wrong domain, exhausted quota, disabled key — AMap does NOT fail in
 * any way a caller can observe: the script loads, `new AMap.Map()` succeeds, the attribution bar
 * renders, and the map even fires its `complete` event. The only signal is an async
 * `console.error`. Verified against a real domain-restricted key: the map reports itself fully
 * ready while rendering no tiles at all.
 *
 * There is no callback or promise for this in the JS API, so the console is genuinely the only
 * channel. We wrap it narrowly: match AMap's specific auth codes, always forward to the original
 * console.error (nothing is swallowed), and never inspect anything else.
 */
const AMAP_AUTH_ERROR_CODES = [
  'INVALID_USER_DOMAIN',      // origin not in the key's whitelist — by far the most common
  'INVALID_USER_KEY',
  'USER_KEY_RECYCLED',
  'INVALID_USER_SCODE',
  'USERKEY_PLAT_NOMATCH',
  'DAILY_QUERY_OVER_LIMIT',
  'SERVICE_NOT_AVAILABLE'
];

let authFailureCode: string | null = null;
const authFailureListeners = new Set<(code: string) => void>();
let consoleHooked = false;

function hookConsoleForAuthFailures() {
  if (consoleHooked || typeof console === 'undefined') return;
  consoleHooked = true;
  const original = console.error.bind(console);
  console.error = (...args: unknown[]) => {
    original(...args);   // never swallow — this is observation only
    try {
      const text = args.map((a) => (typeof a === 'string' ? a : '')).join(' ');
      const matched = AMAP_AUTH_ERROR_CODES.find((code) => text.includes(code));
      if (matched && !authFailureCode) {
        authFailureCode = matched;
        authFailureListeners.forEach((listener) => listener(matched));
      }
    } catch {
      // Detection must never itself break logging.
    }
  };
}

/**
 * Subscribes to AMap key rejection. Fires immediately if it already happened, since the error
 * often lands before a given map instance mounts.
 */
export function onAmapAuthFailure(listener: (code: string) => void): () => void {
  hookConsoleForAuthFailures();
  if (authFailureCode) listener(authFailureCode);
  authFailureListeners.add(listener);
  return () => authFailureListeners.delete(listener);
}

export type AMapOverlay = {
  setPosition?(position: unknown): void;
  setCenter?(position: unknown): void;
  setRadius?(radius: number): void;
  getPosition?(): { getLng(): number; getLat(): number } | null;
  on?(event: string, handler: (event: { lnglat: { getLng(): number; getLat(): number } }) => void): void;
};

/**
 * Loads render-only interaction plugins (zoom toolbar, scale bar) after the SDK is up. Best-effort:
 * a plugin that fails to load must never break the map, so it resolves regardless. These add UI
 * controls only — no data resolution happens client-side.
 */
export function loadAmapPlugins(amap: AMapNamespace, names: string[]): Promise<void> {
  if (typeof amap.plugin !== 'function' || names.length === 0) return Promise.resolve();
  return new Promise((resolve) => {
    try {
      amap.plugin(names, () => resolve());
    } catch {
      resolve();
    }
  });
}

const SCRIPT_ID = 'amap-jsapi';
const VERSION = '2.0';

let loadPromise: Promise<AMapNamespace> | null = null;

export function amapKey(): string {
  return (import.meta.env.VITE_AMAP_JS_KEY ?? '').trim();
}

export function isAmapConfigured(): boolean {
  return amapKey().length > 0;
}

/**
 * Resolves with the AMap namespace, or rejects when the key is missing or the script fails.
 *
 * A rejection surfaces as an explicit error state in the UI — never a fabricated map. Repeated
 * calls share one promise, so React StrictMode's double-mount does not load the script twice.
 */
export function loadAmap(): Promise<AMapNamespace> {
  if (window.AMap) return Promise.resolve(window.AMap);
  if (loadPromise) return loadPromise;

  const key = amapKey();
  if (!key) {
    return Promise.reject(new Error('AMAP_JS_KEY_MISSING'));
  }

  // Install before the script runs so an early auth rejection is not missed.
  hookConsoleForAuthFailures();

  loadPromise = new Promise<AMapNamespace>((resolve, reject) => {
    // Route AMap's own service calls through our origin so the security code stays server-side.
    window._AMapSecurityConfig = {
      serviceHost: `${window.location.origin}/_AMapService`
    };

    const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null;
    const script = existing ?? document.createElement('script');
    script.id = SCRIPT_ID;
    script.async = true;
    script.src = `https://webapi.amap.com/maps?v=${VERSION}&key=${encodeURIComponent(key)}`;

    script.addEventListener('load', () => {
      if (window.AMap) resolve(window.AMap);
      else reject(new Error('AMAP_LOAD_INCOMPLETE'));
    });
    script.addEventListener('error', () => {
      loadPromise = null;   // allow a later retry
      reject(new Error('AMAP_LOAD_FAILED'));
    });

    if (!existing) document.head.appendChild(script);
  });

  return loadPromise;
}

/** Parses the provider's `"lng,lat;lng,lat"` polyline into coordinate pairs. */
export function parsePolyline(polyline: string | null | undefined): Array<[number, number]> {
  if (!polyline) return [];
  return polyline
    .split(';')
    .map((pair) => pair.split(','))
    .filter((parts) => parts.length === 2)
    .map(([lng, lat]) => [Number(lng), Number(lat)] as [number, number])
    .filter(([lng, lat]) => Number.isFinite(lng) && Number.isFinite(lat));
}
