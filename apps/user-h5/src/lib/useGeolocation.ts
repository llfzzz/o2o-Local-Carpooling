import { useCallback, useEffect, useRef, useState } from 'react';
import type { GeoPoint } from './location';

// Browser location permission, modelled as an explicit state machine.
//
// The detected position is always a SUGGESTION: the rider reviews or changes it before searching,
// publishing, or ordering. Denial is a fully supported path, not a dead end — the caller falls
// back to the city picker. We never re-prompt automatically: once a user says no, asking again on
// every render is both useless (the browser remembers) and hostile.

export type GeolocationStatus =
  /** Nothing asked yet. Nothing has been prompted; no permission dialog has been shown. */
  | 'idle'
  /** Waiting for the user to answer the browser's permission dialog, or for a fix. */
  | 'locating'
  /** We have a position. */
  | 'granted'
  /** The user declined. Do not ask again in this session. */
  | 'denied'
  /** The Geolocation API is missing (old browser, disabled). Not retryable. */
  | 'unavailable'
  /** The page is not a secure context (plain http). Geolocation cannot run here at all. */
  | 'insecure'
  /** The request ran out of time without a fix. Retryable. */
  | 'timedout'
  /** The device could not determine a position (no GPS/wifi/cell fix). Retryable. */
  | 'error';

/** Fixes coarser than this are flagged so the user can correct the point on the map. */
export const POOR_ACCURACY_METERS = 100;

export type GeolocationState = {
  status: GeolocationStatus;
  /** WGS84 — what the browser reports. Convert before handing to a GCJ-02 provider. */
  point: GeoPoint | null;
  accuracyMeters: number | null;
  /** When the fix was taken, so callers can decide it is too old to trust. */
  capturedAt: number | null;
  /** True when the fix is older than the staleness threshold. */
  isStale: boolean;
  /** True when accuracy is worse than POOR_ACCURACY_METERS — show a radius + correction prompt. */
  poorAccuracy: boolean;
  /**
   * Best-effort Permissions API state, when the browser exposes it: 'granted' | 'denied' |
   * 'prompt'. null means unknown (Safari historically lacks it) — never treated as a denial.
   */
  permission: PermissionState | null;
};

const TIMEOUT_MS = 10_000;
/** Accept a cached fix up to a minute old rather than spinning up the radio again. */
const MAX_AGE_MS = 60_000;
/** Beyond this, the fix is shown as stale and the user is offered a refresh. */
const STALE_AFTER_MS = 5 * 60_000;

function isSecure(): boolean {
  return typeof window === 'undefined' || window.isSecureContext !== false;
}

function hasApi(): boolean {
  // Truthiness rather than `'geolocation' in navigator`: the key can be present but undefined.
  return typeof navigator !== 'undefined'
    && Boolean(navigator.geolocation)
    && typeof navigator.geolocation.getCurrentPosition === 'function';
}

function isSupported(): boolean {
  // Geolocation is gated on a secure context in every current browser; check both explicitly so
  // callers can distinguish "old browser" (unavailable) from "plain http" (insecure).
  return hasApi() && isSecure();
}

export function useGeolocation() {
  const [state, setState] = useState<GeolocationState>({
    status: 'idle',
    point: null,
    accuracyMeters: null,
    capturedAt: null,
    isStale: false,
    poorAccuracy: false,
    permission: null
  });
  const retriedRef = useRef(false);
  const mountedRef = useRef(true);

  // Must re-arm on mount, not only disarm on unmount: StrictMode double-invokes effects, so a
  // cleanup-only version leaves the ref false forever and every callback bails out silently.
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  // Non-blocking permission preflight: reflect the OS/browser state where the Permissions API
  // exists, so the UI can pre-empt a doomed prompt (already denied) without ever asking. This
  // NEVER triggers a location request — that stays a deliberate user action.
  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.permissions?.query) return;
    let cancelled = false;
    navigator.permissions.query({ name: 'geolocation' as PermissionName })
      .then((status) => {
        if (cancelled) return;
        const apply = () => mountedRef.current && setState((prev) => ({
          ...prev,
          permission: status.state,
          // Mirror a settings-level denial into our sticky 'denied' state (unless we already
          // have a fix), so the button shows "blocked" without the user tapping first.
          status: status.state === 'denied' && prev.status !== 'granted' ? 'denied' : prev.status
        }));
        apply();
        status.addEventListener('change', apply);
      })
      .catch(() => undefined); // Permissions API is best-effort; unknown is not a denial.
    return () => {
      cancelled = true;
    };
  }, []);

  const request = useCallback((options?: { isRetry?: boolean }) => {
    if (!hasApi()) {
      setState((prev) => ({ ...prev, status: 'unavailable' }));
      return;
    }
    if (!isSecure()) {
      setState((prev) => ({ ...prev, status: 'insecure' }));
      return;
    }
    // A denial is final for this session; the browser would not re-prompt anyway.
    setState((prev) => {
      if (prev.status === 'denied' && !options?.isRetry) return prev;
      return { ...prev, status: 'locating' };
    });

    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (!mountedRef.current) return;
        retriedRef.current = false;
        const accuracyMeters = Number.isFinite(position.coords.accuracy)
          ? Math.round(position.coords.accuracy)
          : null;
        setState((prev) => ({
          status: 'granted',
          point: {
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            datum: 'WGS84'
          },
          accuracyMeters,
          capturedAt: position.timestamp,
          isStale: Date.now() - position.timestamp > STALE_AFTER_MS,
          poorAccuracy: accuracyMeters != null && accuracyMeters > POOR_ACCURACY_METERS,
          permission: prev.permission
        }));
      },
      (error) => {
        if (!mountedRef.current) return;
        if (error.code === error.PERMISSION_DENIED) {
          setState((prev) => ({ ...prev, status: 'denied' }));
          return;
        }
        // One automatic retry for a transient failure; after that it is the user's call.
        if (!retriedRef.current) {
          retriedRef.current = true;
          window.setTimeout(() => mountedRef.current && request({ isRetry: true }), 500);
          return;
        }
        retriedRef.current = false;
        setState((prev) => ({
          ...prev,
          status: error.code === error.TIMEOUT ? 'timedout' : 'error'
        }));
      },
      { enableHighAccuracy: true, timeout: TIMEOUT_MS, maximumAge: MAX_AGE_MS }
    );
  }, []);

  const reset = useCallback(() => {
    retriedRef.current = false;
    setState((prev) => ({
      status: 'idle', point: null, accuracyMeters: null, capturedAt: null,
      isStale: false, poorAccuracy: false, permission: prev.permission
    }));
  }, []);

  return { ...state, supported: isSupported(), secure: isSecure(), request, reset };
}
