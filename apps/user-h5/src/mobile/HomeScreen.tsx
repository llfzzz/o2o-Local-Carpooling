import { useState } from 'react';
import { Button, EmptyState, useToast } from '@fj';
import { Maximize2 } from 'lucide-react';
import { describeError } from '../lib/api';
import { avatarInitial, formatDeparture, routeProviderLabel, shortId } from '../lib/format';
import { TRIP_STATUS_LABEL } from '../lib/labels';
import {
  useCitiesQuery,
  useGenerateDemoTrips,
  useGenerateRandomDemoTrips,
  usePublishTrip,
  useTripSearchQuery
} from '../lib/queries';
import { isResolved } from '../lib/location';
import { useRouteSelection } from '../lib/routeSelection';
import { useCityPreference } from '../lib/useCityPreference';
import { CityPicker } from '../components/CityPicker';
import { DemoProviderBadge } from '../components/DemoProviderBadge';
import { ExpandedMapModal } from '../components/ExpandedMapModal';
import { LocationSearchSheet } from '../components/LocationSearchSheet';
import { TripMap } from '../components/TripMap';
import type { Session, TripOffer } from '../lib/types';

/** A2 · 首页 / 找车 — map hero + route rail card + 顺路车主 list. Tapping a trip opens booking. */
export function HomeScreen({ session, onBook }: { session: Session; onBook: (trip: TripOffer) => void }) {
  const toast = useToast();
  const { city, setCity } = useCityPreference();
  // Route selection is shared with the expanded map (and the desktop shell): both edit the same
  // store, so the thumbnail and the expanded map always show the same picked route.
  const { origin, destination, preview, setOrigin, setDestination } = useRouteSelection();
  const [editing, setEditing] = useState<'origin' | 'destination' | null>(null);
  const [expanded, setExpanded] = useState(false);

  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const tripsQuery = useTripSearchQuery(origin, destination);
  const trips = tripsQuery.data ?? [];
  const ready = isResolved(origin) && isResolved(destination);

  // Demo generation is only offered when the demo map provider is active.
  const citiesQuery = useCitiesQuery();
  const demoMode = citiesQuery.data?.demoProvider ?? false;
  const generateDemo = useGenerateDemoTrips({
    onSuccess: (generated) => toast({ title: `已生成 ${generated.offers.length} 条演示行程，已按该路线搜索`, tone: 'success' }),
    onError: showError
  });
  const generateRandom = useGenerateRandomDemoTrips({
    onSuccess: (generated) => toast({ title: `已生成 ${generated.offers.length} 条随机演示行程，已按该路线搜索`, tone: 'success' }),
    onError: showError
  });

  const publishTrip = usePublishTrip(
    {
      userId: session.user.userId,
      origin: origin?.displayName ?? '',
      destination: destination?.displayName ?? '',
      city: city?.name ?? '',
      originRef: origin,
      destinationRef: destination
    },
    {
      onSuccess: () => toast({ title: '示例行程已发布', tone: 'success' }),
      onError: showError
    }
  );

  return (
    <div className="screen">
      <header className="home-header">
        <CityPicker city={city} onChange={setCity} />
        <span className="avatar avatar-sm" title={session.user.phone}>{avatarInitial(session.user.phone)}</span>
      </header>

      <div className="screen-body">
        <TripMap
          className="hero-band"
          origin={origin}
          destination={destination}
          polyline={preview?.route.polyline ?? trips[0]?.route.polyline ?? null}
        >
          <div className="hero-pill fj-glass-strong">
            <span className="live-dot" />
            <span>
              {tripsQuery.isError
                ? 'Gateway 未连接'
                : ready
                  ? `${origin!.displayName} · ${trips.length} 位车主顺路`
                  : '选择出发与到达地点'}
            </span>
          </div>
          <button className="map-expand-btn" onClick={() => setExpanded(true)}>
            <Maximize2 size={13} />
            <span>展开地图</span>
          </button>
        </TripMap>

        <section className="route-card">
          <div className="route-rail">
            <span className="rail-dot rail-dot-start" />
            <span className="rail-line" />
            <span className="rail-dot rail-dot-end" />
          </div>
          <div className="route-fields">
            <button type="button" className="route-field route-field-button" onClick={() => setEditing('origin')}>
              <span>出发</span>
              <strong className={origin ? '' : 'route-field-empty'}>
                {origin?.displayName ?? '选择出发地点'}
              </strong>
            </button>
            <div className="route-divider" />
            <button type="button" className="route-field route-field-button" onClick={() => setEditing('destination')}>
              <span>到达</span>
              <strong className={destination ? '' : 'route-field-empty'}>
                {destination?.displayName ?? '选择到达地点'}
              </strong>
            </button>
          </div>
        </section>

        <DemoProviderBadge />

        <div className="list-head">
          <span className="list-title">顺路车主</span>
          {demoMode ? (
            <span className="status-line">
              <Button
                variant="ghost"
                size="sm"
                disabled={generateDemo.isPending || !ready}
                onClick={() => generateDemo.mutate({ origin: origin!, destination: destination! })}
              >
                {generateDemo.isPending ? '生成中…' : '生成演示行程'}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={generateRandom.isPending || !city}
                onClick={() => generateRandom.mutate(city?.cityCode ?? null)}
              >
                {generateRandom.isPending ? '生成中…' : '随机路线'}
              </Button>
            </span>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              disabled={publishTrip.isPending || !ready}
              onClick={() => publishTrip.mutate()}
            >
              {publishTrip.isPending ? '发布中…' : '+ 发布示例行程'}
            </Button>
          )}
        </div>

        {!ready ? (
          <div className="empty-card">
            <EmptyState icon="map-pin" compact title="先选择起点和终点" description="选好后会显示附近顺路的车主。" />
          </div>
        ) : tripsQuery.isLoading ? (
          <div className="skeleton-stack">
            <div className="skeleton-card" />
            <div className="skeleton-card" />
            <div className="skeleton-card" />
          </div>
        ) : trips.length > 0 ? (
          <div className="trip-list">
            {trips.map((trip) => {
              const seatsLeft = trip.inventory.totalSeats - trip.inventory.lockedSeats;
              const bookable = trip.status === 'PUBLISHED' && seatsLeft > 0;
              return (
                <button
                  key={trip.tripId}
                  className="trip-card"
                  disabled={!bookable}
                  onClick={() => onBook(trip)}
                >
                  <div className="trip-card-top">
                    <div className="trip-driver">
                      <span className="avatar avatar-sm">{avatarInitial(trip.driverId)}</span>
                      <div className="trip-driver-meta">
                        <strong>
                          {shortId(trip.driverId)}
                          {trip.source === 'DEMO' && <span className="loc-demo-tag" style={{ marginLeft: 6 }}>演示</span>}
                        </strong>
                        <span>{routeProviderLabel(trip)} · {TRIP_STATUS_LABEL[trip.status]}</span>
                      </div>
                    </div>
                    <div className="trip-price">
                      <span className={`trip-price-num${bookable ? ' accent' : ''}`}>¥{Number(trip.seatPrice.amount).toFixed(0)}</span>
                      <span className="trip-price-unit">/座</span>
                    </div>
                  </div>
                  <div className="trip-card-foot">
                    <strong>{formatDeparture(trip.departureAt)} 出发</strong>
                    <span>·</span>
                    <span>{(trip.route.distanceMeters / 1000).toFixed(1)}km</span>
                    <span>·</span>
                    <span>剩 {seatsLeft} 座</span>
                  </div>
                </button>
              );
            })}
          </div>
        ) : (
          <div className="empty-card">
            <EmptyState icon="car-front" compact title="附近暂无顺路行程" description="可以换个时间或地点再看看。" />
          </div>
        )}
      </div>

      {editing && (
        <div className="loc-sheet-scrim" onClick={() => setEditing(null)}>
          <div onClick={(event) => event.stopPropagation()}>
            <LocationSearchSheet
              title={editing === 'origin' ? '选择出发地点' : '选择到达地点'}
              cityCode={city?.cityCode ?? null}
              bias={origin?.point ?? null}
              notice={<DemoProviderBadge />}
              onPick={(place) => {
                if (editing === 'origin') setOrigin(place);
                else setDestination(place);
                setEditing(null);
              }}
              onClose={() => setEditing(null)}
            />
          </div>
        </div>
      )}

      {expanded && <ExpandedMapModal cityCode={city?.cityCode ?? null} onClose={() => setExpanded(false)} />}
    </div>
  );
}
