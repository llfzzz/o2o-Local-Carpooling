import { useEffect, useRef, useState } from 'react';
import { loadAmap, loadAmapPlugins, isAmapConfigured, parsePolyline, onAmapAuthFailure } from '../lib/amapLoader';
import type { AMapMap, AMapNamespace, AMapOverlay } from '../lib/amapLoader';
import type { GeoPoint, LocationRef } from '../lib/location';

type Props = {
  origin?: LocationRef | null;
  destination?: LocationRef | null;
  /** Route geometry from the server snapshot. We never compute a route client-side. */
  polyline?: string | null;
  /** Click-to-place: reports the tapped point for server reverse geocoding. */
  onPinDrop?: (point: GeoPoint) => void;
  /**
   * Interactive mode: adds zoom/scale controls and drag-to-move markers. Markers report their
   * dropped position via {@code onMarkerDragEnd} for server reverse geocoding — the map never
   * resolves a place itself.
   */
  interactive?: boolean;
  onMarkerDragEnd?: (which: 'origin' | 'destination', point: GeoPoint) => void;
  /** Draws a translucent accuracy radius (meters) around a point — used by the locate flow. */
  accuracyCircle?: { point: GeoPoint; radiusMeters: number } | null;
  className?: string;
  /** Rendered over the map — used for the demo badge and live status pills. */
  children?: React.ReactNode;
};

type MapState = 'idle' | 'loading' | 'ready' | 'unconfigured' | 'error' | 'offline' | 'rejected';

/**
 * The real map.
 *
 * Draws only what the server already decided: resolved endpoints and the route geometry from the
 * authoritative snapshot. When the SDK cannot load — no key, blocked script, offline — it shows an
 * explicit state rather than a fake map, so a provider failure can never look like a real result.
 *
 * In interactive mode AMap handles gestures and marker dragging, but the browser still resolves
 * nothing: a dragged marker or a tapped point becomes a GCJ-02 GeoPoint that the caller sends to
 * map-service for reverse geocoding.
 */
export function TripMap({
  origin,
  destination,
  polyline,
  onPinDrop,
  interactive = false,
  onMarkerDragEnd,
  accuracyCircle,
  className,
  children
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<AMapMap | null>(null);
  const amapRef = useRef<AMapNamespace | null>(null);
  const overlaysRef = useRef<AMapOverlay[]>([]);
  const [state, setState] = useState<MapState>('idle');
  // Latest drag/pin handlers, read through refs so we never rebind (which would destroy the map).
  const onPinDropRef = useRef(onPinDrop);
  const onMarkerDragEndRef = useRef(onMarkerDragEnd);
  onPinDropRef.current = onPinDrop;
  onMarkerDragEndRef.current = onMarkerDragEnd;

  // Load the SDK and create the map exactly once.
  useEffect(() => {
    let cancelled = false;

    if (!isAmapConfigured()) {
      setState('unconfigured');
      return;
    }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      setState('offline');
      return;
    }

    setState('loading');
    // A rejected key still constructs a working-looking map, so this is the only honest signal.
    const unsubscribe = onAmapAuthFailure(() => {
      if (!cancelled) setState('rejected');
    });

    loadAmap()
      .then(async (amap) => {
        if (cancelled || !containerRef.current) return;
        amapRef.current = amap;
        mapRef.current = new amap.Map(containerRef.current, {
          zoom: 12,
          // GCJ-02 throughout: the same datum the backend stores and AMap expects, so nothing
          // needs converting between what we draw and what we routed.
          viewMode: '2D',
          resizeEnable: true,
          // Gestures are only worth enabling when the surface is meant to be driven.
          dragEnable: interactive,
          zoomEnable: interactive,
          doubleClickZoom: interactive
        });
        // Click-to-pick (thumbnail or expanded): report the tapped point for reverse geocoding.
        mapRef.current.on('click', (event) => {
          onPinDropRef.current?.({
            latitude: event.lnglat.getLat(),
            longitude: event.lnglat.getLng(),
            datum: 'GCJ02'
          });
        });

        if (interactive) {
          await loadAmapPlugins(amap, ['AMap.ToolBar', 'AMap.Scale']);
          if (cancelled || !mapRef.current) return;
          try {
            if (amap.ToolBar) mapRef.current.addControl(new amap.ToolBar({ position: 'RB' }));
            if (amap.Scale) mapRef.current.addControl(new amap.Scale());
          } catch {
            // Controls are a nicety; their absence never blocks the map.
          }
        }

        // Only `complete` proves the key was accepted and tiles actually rendered. Constructing
        // the map succeeds even when AMap rejects the key (INVALID_USER_DOMAIN, quota, disabled),
        // so treating construction as success would show a blank frame as if it worked.
        // `complete` fires even for a rejected key, so it only advances us out of `loading` —
        // it is never treated as proof the key was accepted.
        mapRef.current.on('complete', () => {
          if (!cancelled) setState((prev) => (prev === 'rejected' ? prev : 'ready'));
        });
      })
      .catch(() => {
        if (!cancelled) setState('error');
      });

    return () => {
      cancelled = true;
      unsubscribe();
      overlaysRef.current = [];
      mapRef.current?.destroy();
      mapRef.current = null;
    };
    // Handlers are read via refs; interactive is fixed per mount. Rebinding would tear down the map.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Redraw markers, route and accuracy circle whenever the endpoints or geometry change.
  useEffect(() => {
    const map = mapRef.current;
    const amap = amapRef.current;
    if (state !== 'ready' || !map || !amap) return;

    map.remove(overlaysRef.current);
    const overlays: AMapOverlay[] = [];
    const accent = getComputedStyle(document.documentElement)
      .getPropertyValue('--accent')
      .trim() || '#137A63';

    const addMarker = (ref: LocationRef, which: 'origin' | 'destination') => {
      const marker = new amap.Marker({
        position: [ref.point.longitude, ref.point.latitude],
        title: ref.displayName,
        draggable: interactive,
        cursor: interactive ? 'move' : 'default'
      });
      if (interactive && marker.on) {
        marker.on('dragend', (event) => {
          onMarkerDragEndRef.current?.(which, {
            latitude: event.lnglat.getLat(),
            longitude: event.lnglat.getLng(),
            datum: 'GCJ02'
          });
        });
      }
      overlays.push(marker);
    };

    if (origin) addMarker(origin, 'origin');
    if (destination) addMarker(destination, 'destination');

    if (accuracyCircle && accuracyCircle.radiusMeters > 0) {
      overlays.push(new amap.Circle({
        center: [accuracyCircle.point.longitude, accuracyCircle.point.latitude],
        radius: accuracyCircle.radiusMeters,
        strokeColor: accent,
        strokeOpacity: 0.5,
        strokeWeight: 1,
        fillColor: accent,
        fillOpacity: 0.12
      }));
    }

    const path = parsePolyline(polyline);
    if (path.length >= 2) {
      overlays.push(new amap.Polyline({
        path,
        strokeWeight: 5,
        // Reads the product accent token rather than hardcoding the teal.
        strokeColor: accent,
        strokeOpacity: 0.9,
        lineJoin: 'round'
      }));
    }

    if (overlays.length > 0) {
      map.add(overlays);
      map.setFitView(overlays, false, [40, 40, 40, 40]);
    }
    overlaysRef.current = overlays;
  }, [state, origin, destination, polyline, interactive, accuracyCircle]);

  return (
    <div className={`trip-map${className ? ` ${className}` : ''}`}>
      <div ref={containerRef} className="trip-map-canvas" aria-hidden={state !== 'ready'} />
      {state !== 'ready' && (
        <div className="trip-map-state" role="status">
          {state === 'loading' && <span>地图加载中…</span>}
          {state === 'unconfigured' && <span>地图未配置（缺少 VITE_AMAP_JS_KEY）</span>}
          {state === 'offline' && <span>网络已断开，地图不可用</span>}
          {state === 'error' && <span>地图加载失败，行程信息仍可正常使用</span>}
          {/* Almost always a domain-whitelist miss: the key is valid but this origin is not
              authorised for it. Naming the likely cause saves a long debugging session. */}
          {state === 'rejected' && (
            <span>地图密钥未授权当前域名<br />请在高德控制台的域名白名单中加入本站域名</span>
          )}
          {state === 'idle' && <span>地图准备中…</span>}
        </div>
      )}
      {children}
    </div>
  );
}
