import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import type { CoordinateDatum } from './location';

// Live pickup tracking, both directions.
//
// Uploads are plain HTTP on a ~10s cadence; the rider receives Server-Sent Events. SSE is
// one-directional (all we need), survives proxies, and adds no infrastructure — which matters on
// a host that is already memory-constrained.

export type DriverLocationSnapshot = {
  sharing: boolean;
  lat?: number;
  lng?: number;
  datum?: CoordinateDatum;
  capturedAt?: string;
  /** Seconds since the fix was taken. Large values mean the position is going stale. */
  ageSeconds?: number;
};

const UPLOAD_INTERVAL_MS = 10_000;

/**
 * Driver side: reports position while sharing is on.
 *
 * Pauses when the tab is hidden. Mobile browsers throttle background timers to the point of
 * uselessness anyway, and continuing to ship a driver's coordinates from a backgrounded tab is
 * not something they agreed to.
 */
export function useDriverLocationBroadcast(tripId: string | null, enabled: boolean) {
  const [error, setError] = useState<string | null>(null);
  const [lastSentAt, setLastSentAt] = useState<string | null>(null);
  const watchIdRef = useRef<number | null>(null);
  const timerRef = useRef<number | null>(null);
  const latestRef = useRef<GeolocationPosition | null>(null);

  useEffect(() => {
    if (!tripId || !enabled || !navigator.geolocation) return;

    watchIdRef.current = navigator.geolocation.watchPosition(
      (position) => {
        latestRef.current = position;
      },
      () => setError('无法获取定位，共享已暂停'),
      { enableHighAccuracy: true, maximumAge: 5_000, timeout: 15_000 }
    );

    const send = async () => {
      if (document.visibilityState !== 'visible') return;
      const position = latestRef.current;
      if (!position) return;
      try {
        await api(`/api/trips/${tripId}/driver-location`, {
          method: 'POST',
          body: {
            // WGS84: the browser's datum. The server converts before storing.
            lat: position.coords.latitude,
            lng: position.coords.longitude,
            datum: 'WGS84',
            headingDegrees: Number.isFinite(position.coords.heading) ? position.coords.heading : null,
            speedMetersPerSecond: Number.isFinite(position.coords.speed) ? position.coords.speed : null,
            capturedAt: new Date(position.timestamp).toISOString()
          }
        });
        setError(null);
        setLastSentAt(new Date().toISOString());
      } catch {
        setError('位置上报失败，将继续重试');
      }
    };

    void send();
    timerRef.current = window.setInterval(send, UPLOAD_INTERVAL_MS);

    return () => {
      if (watchIdRef.current !== null) navigator.geolocation.clearWatch(watchIdRef.current);
      if (timerRef.current !== null) window.clearInterval(timerRef.current);
      watchIdRef.current = null;
      timerRef.current = null;
    };
  }, [tripId, enabled]);

  return { error, lastSentAt };
}

/**
 * Rider side: subscribes to the driver's position.
 *
 * A stale or absent position surfaces as `sharing: false`, never as a last-known point dressed up
 * as live — showing a driver somewhere they left ten minutes ago is worse than showing nothing.
 */
export function useDriverLocationStream(tripId: string | null, enabled: boolean) {
  const [snapshot, setSnapshot] = useState<DriverLocationSnapshot | null>(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (!tripId || !enabled) {
      setSnapshot(null);
      setConnected(false);
      return;
    }

    let cancelled = false;
    let timer: number | null = null;

    // Polling rather than EventSource: EventSource cannot send an Authorization header, and this
    // API is bearer-authenticated. The cadence matches the SSE heartbeat, and the endpoint is the
    // same one the stream reads, so switching to SSE later needs no server change.
    const poll = async () => {
      try {
        const next = await api<DriverLocationSnapshot>(`/api/trips/${tripId}/driver-location`);
        if (!cancelled) {
          setSnapshot(next);
          setConnected(true);
        }
      } catch {
        if (!cancelled) {
          setSnapshot(null);
          setConnected(false);
        }
      }
    };

    void poll();
    timer = window.setInterval(poll, 5_000);

    return () => {
      cancelled = true;
      if (timer !== null) window.clearInterval(timer);
    };
  }, [tripId, enabled]);

  return { snapshot, connected };
}
