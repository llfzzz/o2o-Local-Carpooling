import { useEffect, useState } from 'react';
import { Button, useToast } from '@fj';
import { ArrowLeftRight, Crosshair, MapPin, Search, X } from 'lucide-react';
import { describeError } from '../lib/api';
import { isResolved } from '../lib/location';
import type { GeoPoint } from '../lib/location';
import { useReverseGeocode, useRoutePreview } from '../lib/queries';
import { useRouteSelection } from '../lib/routeSelection';
import { useGeolocation } from '../lib/useGeolocation';
import { LocationSearchSheet } from './LocationSearchSheet';
import { DemoProviderBadge } from './DemoProviderBadge';
import { PriceBreakdownRows } from './PriceBreakdownRows';
import { TripMap } from './TripMap';

/**
 * Full-screen interactive route picker shared by both shells. AMap renders and handles gestures;
 * every place resolution (drag, click, locate, search) goes through map-service, and the route +
 * price come from the server route-preview — the browser computes no authoritative geography.
 *
 * On 确认路线 the confirmed origin/destination/preview are written to the shared route-selection
 * store, so the home thumbnail reflects exactly what was picked here.
 */
export function ExpandedMapModal({ cityCode, onClose }: { cityCode: string | null; onClose: () => void }) {
  const toast = useToast();
  const { origin, destination, setOrigin, setDestination, swap, clearOrigin, clearDestination, setPreview } =
    useRouteSelection();
  const [searching, setSearching] = useState<'origin' | 'destination' | null>(null);
  const [pinTarget, setPinTarget] = useState<'origin' | 'destination'>('origin');
  const [accuracy, setAccuracy] = useState<{ point: GeoPoint; radiusMeters: number } | null>(null);
  // Captured before geolocation.reset() clears the status, so the low-accuracy prompt persists.
  const [poorAccuracyMeters, setPoorAccuracyMeters] = useState<number | null>(null);

  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });
  const geolocation = useGeolocation();

  const reverseGeocode = useReverseGeocode({
    onSuccess: (place) => {
      if (pinTarget === 'origin') setOrigin(place);
      else setDestination(place);
    },
    onError: () => toast({ title: '无法解析该位置，请手动搜索', tone: 'danger' })
  });

  const routePreview = useRoutePreview({
    onSuccess: (preview) => {
      setPreview(preview);
      toast({ title: '路线已确认', tone: 'success' });
      onClose();
    },
    onError: showError
  });

  // A granted fix is a suggestion: reverse-geocode it into a real place for the active endpoint,
  // and draw its accuracy radius so a poor fix can be seen and corrected on the map.
  useEffect(() => {
    if (geolocation.status === 'granted' && geolocation.point && !reverseGeocode.isPending) {
      setAccuracy(geolocation.accuracyMeters ? { point: geolocation.point, radiusMeters: geolocation.accuracyMeters } : null);
      setPoorAccuracyMeters(geolocation.poorAccuracy ? geolocation.accuracyMeters : null);
      reverseGeocode.mutate(geolocation.point);
      geolocation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [geolocation.status, geolocation.point]);

  const ready = isResolved(origin) && isResolved(destination);
  const locateDisabled = !geolocation.supported || geolocation.status === 'locating' || reverseGeocode.isPending;

  return (
    <div className="map-modal" role="dialog" aria-label="选择路线">
      <header className="map-modal-head">
        <strong>选择路线</strong>
        <button className="map-modal-close" aria-label="关闭" onClick={onClose}><X size={20} /></button>
      </header>

      <div className="map-modal-map">
        <TripMap
          interactive
          origin={origin}
          destination={destination}
          polyline={null}
          accuracyCircle={accuracy}
          onPinDrop={(point) => {
            setAccuracy(null);
            reverseGeocode.mutate(point);
          }}
          onMarkerDragEnd={(which, point) => {
            setPinTarget(which);
            setAccuracy(null);
            reverseGeocode.mutate(point);
          }}
        >
          <div className="map-modal-pin-toggle">
            <button className={pinTarget === 'origin' ? 'active' : ''} onClick={() => setPinTarget('origin')}>点选出发</button>
            <button className={pinTarget === 'destination' ? 'active' : ''} onClick={() => setPinTarget('destination')}>点选到达</button>
          </div>
        </TripMap>
      </div>

      <div className="map-modal-panel">
        <div className="map-modal-endpoints">
          <button className="map-endpoint" onClick={() => setSearching('origin')}>
            <MapPin size={16} color="var(--accent)" />
            <span className={origin ? '' : 'map-endpoint-empty'}>{origin?.displayName ?? '选择出发地点'}</span>
            {origin && <X size={14} onClick={(event) => { event.stopPropagation(); clearOrigin(); }} />}
          </button>
          <button className="map-swap" aria-label="交换起终点" disabled={!origin && !destination} onClick={swap}>
            <ArrowLeftRight size={16} />
          </button>
          <button className="map-endpoint" onClick={() => setSearching('destination')}>
            <MapPin size={16} color="var(--danger-500)" />
            <span className={destination ? '' : 'map-endpoint-empty'}>{destination?.displayName ?? '选择到达地点'}</span>
            {destination && <X size={14} onClick={(event) => { event.stopPropagation(); clearDestination(); }} />}
          </button>
        </div>

        <div className="map-modal-actions">
          <Button variant="secondary" size="sm" iconLeft={<Crosshair size={14} />} disabled={locateDisabled}
            onClick={() => { setPinTarget('origin'); geolocation.request({ isRetry: true }); }}>
            {geolocation.status === 'locating' ? '定位中…' : '定位起点'}
          </Button>
          <Button variant="secondary" size="sm" iconLeft={<Search size={14} />} onClick={() => setSearching(pinTarget)}>
            搜索地点
          </Button>
        </div>

        {poorAccuracyMeters != null && (
          <p className="map-modal-warn">
            定位精度较低（约 {poorAccuracyMeters} 米）。地图上的圆圈表示误差范围，可拖动标记或点选地图校正。
          </p>
        )}
        {geolocation.status === 'denied' && (
          <p className="map-modal-warn">已拒绝定位权限，可直接搜索或点选地图选择地点。</p>
        )}

        {origin && (
          <div className="map-modal-detail">
            <strong>{origin.displayName}</strong>
            <small>{origin.formattedAddress}</small>
          </div>
        )}

        {routePreview.data && (
          <section className="map-modal-quote">
            <div className="map-modal-quote-row">
              <span>距离</span><strong>{(routePreview.data.route.distanceMeters / 1000).toFixed(1)} km</strong>
            </div>
            <div className="map-modal-quote-row">
              <span>预计时长</span><strong>{Math.round(routePreview.data.route.durationSeconds / 60)} 分钟</strong>
            </div>
            <PriceBreakdownRows breakdown={routePreview.data.pricing} seats={1} />
            <div className="map-modal-quote-row total">
              <span>每座价格</span>
              <strong className="map-modal-price">¥{Number(routePreview.data.pricing.total.amount).toFixed(2)}</strong>
            </div>
          </section>
        )}

        <DemoProviderBadge />

        <Button full variant="primary" size="lg" disabled={!ready || routePreview.isPending}
          onClick={() => routePreview.mutate({ origin: origin!, destination: destination! })}>
          {routePreview.isPending ? '试算中…' : ready ? '确认路线' : '请选择起点和终点'}
        </Button>
      </div>

      {searching && (
        <div className="loc-sheet-scrim" onClick={() => setSearching(null)}>
          <div onClick={(event) => event.stopPropagation()}>
            <LocationSearchSheet
              title={searching === 'origin' ? '选择出发地点' : '选择到达地点'}
              cityCode={cityCode}
              bias={origin?.point ?? null}
              notice={<DemoProviderBadge />}
              onPick={(place) => {
                if (searching === 'origin') setOrigin(place);
                else setDestination(place);
                setSearching(null);
              }}
              onClose={() => setSearching(null)}
            />
          </div>
        </div>
      )}
    </div>
  );
}
