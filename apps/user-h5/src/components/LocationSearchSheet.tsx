import { useEffect, useMemo, useRef, useState } from 'react';
import { MapPin, Crosshair, Loader2 } from 'lucide-react';
import { usePlaceSuggest, useReverseGeocode } from '../lib/queries';
import { isDemoLocation } from '../lib/location';
import type { GeoPoint, LocationRef } from '../lib/location';
import { useGeolocation } from '../lib/useGeolocation';

/** Keystrokes are debounced before they cost provider quota. */
const DEBOUNCE_MS = 250;

type Props = {
  title: string;
  cityCode: string | null;
  /** Ranks suggestions around the rider, when we know where they are. */
  bias?: GeoPoint | null;
  onPick: (location: LocationRef) => void;
  onClose: () => void;
  /** Rendered above results — used to surface the demo-provider badge. */
  notice?: React.ReactNode;
};

/**
 * Resolve a place by typing, or by using the current position.
 *
 * Nothing leaves here except a server-resolved LocationRef, so a caller can never end up
 * searching or publishing with free text that has no coordinates.
 */
export function LocationSearchSheet({ title, cityCode, bias, onPick, onClose, notice }: Props) {
  const [keyword, setKeyword] = useState('');
  const [debounced, setDebounced] = useState('');
  const [locateError, setLocateError] = useState<string | null>(null);
  // Captured before the geolocation state is reset, so the low-accuracy warning survives.
  const [poorAccuracyMeters, setPoorAccuracyMeters] = useState<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const geolocation = useGeolocation();
  const suggest = usePlaceSuggest(debounced, cityCode, bias ?? null);
  const reverseGeocode = useReverseGeocode({
    onSuccess: onPick,
    onError: () => setLocateError('无法解析当前位置，请手动搜索')
  });

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(keyword), DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  // A granted fix is only a suggestion until the server turns it into a real place.
  useEffect(() => {
    if (geolocation.status === 'granted' && geolocation.point && !reverseGeocode.isPending) {
      // Remember a poor fix before reset() clears the status, so the warning can persist.
      setPoorAccuracyMeters(geolocation.poorAccuracy ? geolocation.accuracyMeters : null);
      reverseGeocode.mutate(geolocation.point);
      geolocation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [geolocation.status, geolocation.point]);

  // Support is knowable without asking, so an unsupported environment says so immediately rather
  // than only after the rider taps a button that could never have worked. 'insecure' (plain
  // http) is called out separately because the fix is different (use HTTPS).
  const insecure = !geolocation.secure || geolocation.status === 'insecure';
  const unavailable = insecure || !geolocation.supported || geolocation.status === 'unavailable';

  const locateLabel = useMemo(() => {
    if (insecure) return '需要 HTTPS 才能定位';
    if (unavailable) return '此设备不支持定位';
    switch (geolocation.status) {
      case 'locating':
        return '定位中…';
      case 'denied':
        return '已拒绝定位权限';
      case 'timedout':
        return '定位超时，重试';
      case 'error':
        return '定位失败，重试';
      default:
        return '使用我的当前位置';
    }
  }, [geolocation.status, unavailable, insecure]);

  const locateDisabled =
    unavailable
    || geolocation.status === 'locating'
    || geolocation.status === 'denied'
    || reverseGeocode.isPending;

  const results = suggest.data ?? [];

  return (
    <div className="loc-sheet" role="dialog" aria-label={title}>
      <header className="loc-sheet-head">
        <strong>{title}</strong>
        <button type="button" className="loc-sheet-close" onClick={onClose} aria-label="关闭">
          ✕
        </button>
      </header>

      <input
        ref={inputRef}
        className="loc-sheet-input"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        placeholder="搜索地点、地址或建筑名"
        aria-label="搜索地点"
      />

      <button type="button" className="loc-locate" disabled={locateDisabled} onClick={() => {
        setLocateError(null);
        geolocation.request({ isRetry: true });
      }}>
        {geolocation.status === 'locating' || reverseGeocode.isPending
          ? <Loader2 size={16} className="loc-spin" />
          : <Crosshair size={16} />}
        <span>{locateLabel}</span>
      </button>

      {geolocation.status === 'denied' && (
        <p className="loc-hint">
          浏览器已拒绝定位权限。可以直接搜索地点，或在浏览器设置里重新允许。
        </p>
      )}
      {unavailable && (
        <p className="loc-hint">
          {insecure ? '当前为非安全连接（需要 HTTPS）才能定位。请直接搜索地点。' : '当前环境不支持定位。请直接搜索地点。'}
        </p>
      )}
      {poorAccuracyMeters != null && (
        <p className="loc-hint loc-hint-warn">
          定位精度较低（约 {poorAccuracyMeters} 米），已按此位置解析，如不准确请手动搜索校正。
        </p>
      )}
      {locateError && <p className="loc-hint loc-hint-error">{locateError}</p>}

      {notice}

      <div className="loc-results">
        {suggest.isFetching && <p className="loc-hint">搜索中…</p>}
        {!suggest.isFetching && debounced && results.length === 0 && (
          <p className="loc-hint">没有找到匹配的地点</p>
        )}
        {results.map((place) => (
          <button
            key={`${place.providerPlaceId ?? place.displayName}-${place.point.latitude}`}
            type="button"
            className="loc-result"
            onClick={() => onPick(place)}
          >
            <MapPin size={16} color="var(--accent)" />
            <span className="loc-result-text">
              <strong>{place.displayName}</strong>
              <small>{place.formattedAddress}</small>
            </span>
            {isDemoLocation(place) && <span className="loc-demo-tag">演示</span>}
          </button>
        ))}
      </div>
    </div>
  );
}
