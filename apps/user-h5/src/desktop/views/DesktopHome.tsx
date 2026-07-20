import { useState } from 'react';
import { Button, Card, EmptyState, NumberInput, Tag, useToast } from '@fj';
import { CreditCard, ShieldCheck, X } from 'lucide-react';
import { describeError } from '../../lib/api';
import { avatarInitial, formatClock, formatDeparture, routeProviderLabel, shortId } from '../../lib/format';
import { TRIP_STATUS_LABEL } from '../../lib/labels';
import { useCreateOrder, usePublishTrip, useTripSearchQuery } from '../../lib/queries';
import { isResolved } from '../../lib/location';
import type { LocationRef } from '../../lib/location';
import { useCityPreference } from '../../lib/useCityPreference';
import { CityPicker } from '../../components/CityPicker';
import { DemoProviderBadge } from '../../components/DemoProviderBadge';
import { LocationSearchSheet } from '../../components/LocationSearchSheet';
import { TripMap } from '../../components/TripMap';
import type { Session, TripOffer } from '../../lib/types';

/** 找车 — search card + trip list (master) with the booking flow docked as the detail pane. */
export function DesktopHome({ session, onBooked }: { session: Session; onBooked: () => void }) {
  const toast = useToast();
  const { city, setCity } = useCityPreference();
  // No hardcoded seeds: both endpoints are resolved places chosen by the rider.
  const [origin, setOrigin] = useState<LocationRef | null>(null);
  const [destination, setDestination] = useState<LocationRef | null>(null);
  const [editing, setEditing] = useState<'origin' | 'destination' | null>(null);
  const [selectedTripId, setSelectedTripId] = useState<string | null>(null);

  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const tripsQuery = useTripSearchQuery(origin, destination);
  const trips = tripsQuery.data ?? [];
  const ready = isResolved(origin) && isResolved(destination);
  // Derive from the polled list so the pane never shows a stale snapshot.
  const selected = trips.find((trip) => trip.tripId === selectedTripId) ?? null;

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
    <div className="dsk-master">
      <div className="dsk-master-pane">
        <Card padding="18px">
          <div className="dsk-search-row">
            <div className="dsk-search-field city">
              <CityPicker city={city} onChange={setCity} />
            </div>
            <div className="dsk-search-field">
              <button type="button" className="dsk-loc-field" onClick={() => setEditing('origin')}>
                <span>出发</span>
                <strong className={origin ? '' : 'dsk-loc-empty'}>{origin?.displayName ?? '选择出发地点'}</strong>
              </button>
            </div>
            <div className="dsk-search-field">
              <button type="button" className="dsk-loc-field" onClick={() => setEditing('destination')}>
                <span>到达</span>
                <strong className={destination ? '' : 'dsk-loc-empty'}>{destination?.displayName ?? '选择到达地点'}</strong>
              </button>
            </div>
          </div>
          <DemoProviderBadge />
        </Card>

        <TripMap
          className="dsk-map"
          origin={origin}
          destination={destination}
          polyline={selected?.route.polyline ?? trips[0]?.route.polyline ?? null}
        />

        <div className="dsk-list-head">
          <span className="dsk-count-note">
            {tripsQuery.isError
              ? 'Gateway 未连接'
              : ready
                ? <>顺路车主 <strong>{trips.length}</strong> 位</>
                : '选择起点和终点后开始搜索'}
          </span>
          <Button
            variant="ghost"
            size="sm"
            disabled={publishTrip.isPending || !ready}
            onClick={() => publishTrip.mutate()}
          >
            {publishTrip.isPending ? '发布中…' : '+ 发布示例行程'}
          </Button>
        </div>

        {!ready ? (
          <EmptyState icon="map-pin" title="先选择起点和终点" description="选好后会显示附近顺路的车主。" />
        ) : tripsQuery.isLoading ? (
          <>
            <div className="dsk-skeleton-card" />
            <div className="dsk-skeleton-card" />
            <div className="dsk-skeleton-card" />
          </>
        ) : trips.length > 0 ? (
          <div className="dsk-trip-list">
            {trips.map((trip) => {
              const seatsLeft = trip.inventory.totalSeats - trip.inventory.lockedSeats;
              const bookable = trip.status === 'PUBLISHED' && seatsLeft > 0;
              return (
                <button
                  key={trip.tripId}
                  className={`dsk-trip-row${selectedTripId === trip.tripId ? ' selected' : ''}`}
                  disabled={!bookable}
                  onClick={() => setSelectedTripId(trip.tripId)}
                >
                  <span className="dsk-trip-avatar">{avatarInitial(trip.driverId)}</span>
                  <div className="dsk-trip-main">
                    <div className="dsk-trip-route">
                      <span>{trip.originText}</span>
                      <span className="dsk-trip-route-line" />
                      <span>{trip.destinationText}</span>
                    </div>
                    <div className="dsk-trip-meta">
                      <span>{shortId(trip.driverId)}</span>
                      <span>·</span>
                      <span>{formatDeparture(trip.departureAt)} 出发</span>
                      <span>·</span>
                      <span>{(trip.route.distanceMeters / 1000).toFixed(1)}km</span>
                      <span>·</span>
                      <span>剩 {seatsLeft} 座</span>
                      <span>·</span>
                      <span>{TRIP_STATUS_LABEL[trip.status]}</span>
                    </div>
                  </div>
                  <div className="dsk-trip-price">
                    <span className={`dsk-trip-price-num${bookable ? ' accent' : ''}`}>¥{Number(trip.seatPrice.amount).toFixed(0)}</span>
                    <span className="dsk-trip-price-unit">/座</span>
                  </div>
                </button>
              );
            })}
          </div>
        ) : (
          <Card padding="24px">
            <EmptyState icon="car-front" compact title="暂无可订行程" description="可先发布一条示例行程再来订座。" />
          </Card>
        )}
      </div>

      {selected ? (
        <BookingPane
          key={selected.tripId}
          trip={selected}
          onClose={() => setSelectedTripId(null)}
          onBooked={() => {
            setSelectedTripId(null);
            onBooked();
          }}
        />
      ) : (
        <aside className="dsk-detail empty">
          <div className="dsk-detail-head">确认订座</div>
          <div className="dsk-detail-placeholder">
            <EmptyState icon="car-front" compact title="选择左侧行程开始订座" description="订座后在「我的行程」发起支付。" />
          </div>
        </aside>
      )}

      {editing && (
        <div className="dsk-loc-scrim" onClick={() => setEditing(null)}>
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
    </div>
  );
}

/** Docked booking pane — the Dispatch detail-drawer pattern; keyed by tripId so seats reset. */
function BookingPane({ trip, onClose, onBooked }: { trip: TripOffer; onClose: () => void; onBooked: () => void }) {
  const toast = useToast();
  const [seats, setSeats] = useState(1);
  const seatsLeft = trip.inventory.totalSeats - trip.inventory.lockedSeats;
  const total = (Number(trip.seatPrice.amount) * seats).toFixed(2);
  const arrivalAt = new Date(new Date(trip.departureAt).getTime() + trip.route.durationSeconds * 1000);

  const confirm = useCreateOrder(trip, seats, {
    onSuccess: () => {
      toast({ title: '已下单锁座，请在「我的行程」发起支付', tone: 'success' });
      onBooked();
    },
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  return (
    <aside className="dsk-detail">
      <div className="dsk-detail-head">
        确认订座
        <button className="dsk-icon-btn" onClick={onClose} aria-label="关闭">
          <X size={16} />
        </button>
      </div>
      <div className="dsk-detail-body">
        <div className="dsk-route-summary">
          <div className="dsk-route-stop">
            <div className="dsk-route-stop-main">
              <strong>{trip.originText}</strong>
              <span>集合点</span>
            </div>
            <span className="dsk-route-stop-time accent">{formatClock(trip.departureAt)}</span>
          </div>
          <div className="dsk-route-stop">
            <div className="dsk-route-stop-main">
              <strong>{trip.destinationText}</strong>
              <span>{(trip.route.distanceMeters / 1000).toFixed(1)}km</span>
            </div>
            <span className="dsk-route-stop-time">{formatClock(arrivalAt.toISOString())}</span>
          </div>
        </div>

        <div className="dsk-divider" />

        <div className="dsk-driver-row">
          <span className="dsk-trip-avatar">{avatarInitial(trip.driverId)}</span>
          <div className="dsk-driver-meta">
            <strong>{shortId(trip.driverId)}</strong>
            <span>{routeProviderLabel(trip)}</span>
          </div>
          <Tag accent="coral" dot>{TRIP_STATUS_LABEL[trip.status]}</Tag>
        </div>

        <div className="dsk-divider" />

        <div className="dsk-price-row">
          <span>座位数</span>
          <NumberInput size="sm" value={seats} min={1} max={Math.max(1, seatsLeft)} onChange={(value) => setSeats(Number(value))} />
        </div>
        <div className="dsk-price-row">
          <span>座位单价 × {seats}</span>
          <span className="strong">¥{total}</span>
        </div>
        <div className="dsk-price-row">
          <span>平台服务费</span>
          <span className="strong">¥0.00</span>
        </div>

        <div className="dsk-divider" />

        <div className="dsk-price-row total">
          <span>合计</span>
          <span className="dsk-price-total">¥{total}</span>
        </div>

        <div className="dsk-note">
          <ShieldCheck size={14} />
          <span>支付由已签名回调驱动，超时未支付自动取消并释放座位。</span>
        </div>
      </div>
      <div className="dsk-detail-foot">
        <Button
          full
          variant="primary"
          iconLeft={<CreditCard size={16} />}
          disabled={seatsLeft <= 0 || confirm.isPending}
          onClick={() => confirm.mutate()}
        >
          {confirm.isPending ? '处理中…' : `下单锁座 ¥${total}`}
        </Button>
      </div>
    </aside>
  );
}
