import { useState } from 'react';
import { Button, EmptyState, useToast } from '@fj';
import { MapPin } from 'lucide-react';
import { describeError } from '../lib/api';
import { avatarInitial, formatDeparture, routeProviderLabel, shortId } from '../lib/format';
import { TRIP_STATUS_LABEL } from '../lib/labels';
import { usePublishTrip, useTripsQuery } from '../lib/queries';
import type { Session, TripOffer } from '../lib/types';

/** A2 · 首页 / 找车 — map hero + route rail card + 顺路车主 list. Tapping a trip opens booking. */
export function HomeScreen({ session, onBook }: { session: Session; onBook: (trip: TripOffer) => void }) {
  const toast = useToast();
  const [origin, setOrigin] = useState('软件园三期');
  const [destination, setDestination] = useState('集美大学');
  const [city, setCity] = useState('厦门');

  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const tripsQuery = useTripsQuery(origin, destination, session.user.userId);
  const trips = tripsQuery.data ?? [];

  const publishTrip = usePublishTrip(
    { userId: session.user.userId, origin, destination, city },
    {
      onSuccess: () => toast({ title: '示例行程已发布', tone: 'success' }),
      onError: showError
    }
  );

  return (
    <div className="screen">
      <header className="home-header">
        <div className="city-chip">
          <MapPin size={16} color="var(--accent)" />
          <input
            className="city-input"
            value={city}
            onChange={(event) => setCity(event.target.value)}
            aria-label="城市"
          />
        </div>
        <span className="avatar avatar-sm" title={session.user.phone}>{avatarInitial(session.user.phone)}</span>
      </header>

      <div className="screen-body">
        <section className="hero-band">
          <span className="hero-node hero-node-start" />
          <span className="hero-node hero-node-end" />
          <span className="hero-route" />
          <div className="hero-pill fj-glass-strong">
            <span className="live-dot" />
            <span>
              {tripsQuery.isError
                ? 'Gateway 未连接'
                : `${origin} · ${trips.length} 位车主顺路`}
            </span>
          </div>
        </section>

        <section className="route-card">
          <div className="route-rail">
            <span className="rail-dot rail-dot-start" />
            <span className="rail-line" />
            <span className="rail-dot rail-dot-end" />
          </div>
          <div className="route-fields">
            <label className="route-field">
              <span>出发</span>
              <input value={origin} onChange={(event) => setOrigin(event.target.value)} />
            </label>
            <div className="route-divider" />
            <label className="route-field">
              <span>到达</span>
              <input value={destination} onChange={(event) => setDestination(event.target.value)} />
            </label>
          </div>
        </section>

        <div className="list-head">
          <span className="list-title">顺路车主</span>
          <Button variant="ghost" size="sm" disabled={publishTrip.isPending} onClick={() => publishTrip.mutate()}>
            {publishTrip.isPending ? '发布中…' : '+ 发布示例行程'}
          </Button>
        </div>

        {tripsQuery.isLoading ? (
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
                        <strong>{shortId(trip.driverId)}</strong>
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
            <EmptyState icon="car-front" compact title="暂无可订行程" description="可先发布一条示例行程再来订座。" />
          </div>
        )}
      </div>
    </div>
  );
}
