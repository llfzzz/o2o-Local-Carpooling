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
  LngLat: new (lng: number, lat: number) => unknown;
};

export type AMapMap = {
  add(overlay: AMapOverlay | AMapOverlay[]): void;
  remove(overlay: AMapOverlay | AMapOverlay[]): void;
  setFitView(overlays?: AMapOverlay[] | null, immediately?: boolean, avoid?: number[]): void;
  setCenter(position: unknown): void;
  setZoom(zoom: number): void;
  destroy(): void;
  on(event: string, handler: (event: { lnglat: { getLng(): number; getLat(): number } }) => void): void;
};

export type AMapOverlay = {
  setPosition?(position: unknown): void;
  on?(event: string, handler: (event: { lnglat: { getLng(): number; getLat(): number } }) => void): void;
};

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
