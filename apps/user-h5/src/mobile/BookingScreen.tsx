import { useState } from 'react';
import { Button, Tag, useToast } from '@fj';
import { ChevronLeft, CreditCard, ShieldCheck } from 'lucide-react';
import { describeError } from '../lib/api';
import { avatarInitial, formatClock, routeProviderLabel, shortId } from '../lib/format';
import { TRIP_STATUS_LABEL } from '../lib/labels';
import { useCreateOrder } from '../lib/queries';
import { PriceBreakdownRows } from '../components/PriceBreakdownRows';
import type { TripOffer } from '../lib/types';

/** A3 · 订座 + 支付 — route/driver summary, seat stepper, price breakdown, sticky pay bar. */
export function BookingScreen({ trip, onBack, onBooked }: { trip: TripOffer; onBack: () => void; onBooked: () => void }) {
  const toast = useToast();
  const [seats, setSeats] = useState(1);
  const seatsLeft = trip.inventory.totalSeats - trip.inventory.lockedSeats;
  // The per-seat price is server-authoritative (trip.seatPrice); only the line total multiplies.
  const total = (Number(trip.seatPrice.amount) * seats).toFixed(2);
  const arrivalAt = new Date(new Date(trip.departureAt).getTime() + trip.route.durationSeconds * 1000);

  const confirm = useCreateOrder(trip, seats, {
    onSuccess: () => {
      toast({ title: '已下单锁座，请在行程页发起支付', tone: 'success' });
      onBooked();
    },
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  return (
    <div className="screen booking-screen">
      <header className="page-header">
        <button className="back-btn" onClick={onBack} aria-label="返回">
          <ChevronLeft size={22} />
        </button>
        <span className="page-title">确认订座</span>
      </header>

      <div className="screen-body">
        <section className="panel">
          <div className="route-summary">
            <div className="route-rail">
              <span className="rail-dot rail-dot-start" />
              <span className="rail-line" />
              <span className="rail-dot rail-dot-end" />
            </div>
            <div className="route-summary-stops">
              <div className="route-stop">
                <div>
                  <strong>{trip.originText}</strong>
                  <span>集合点</span>
                </div>
                <span className="stop-time accent">{formatClock(trip.departureAt)}</span>
              </div>
              <div className="route-stop">
                <div>
                  <strong>{trip.destinationText}</strong>
                  <span>{(trip.route.distanceMeters / 1000).toFixed(1)}km</span>
                </div>
                <span className="stop-time">{formatClock(arrivalAt.toISOString())}</span>
              </div>
            </div>
          </div>
          <div className="panel-divider" />
          <div className="driver-row">
            <span className="avatar">{avatarInitial(trip.driverId)}</span>
            <div className="driver-meta">
              <strong>{shortId(trip.driverId)}</strong>
              <span>{routeProviderLabel(trip)}</span>
            </div>
            <Tag accent="coral" dot>{TRIP_STATUS_LABEL[trip.status]}</Tag>
          </div>
        </section>

        <section className="panel price-panel">
          <div className="price-row">
            <span>座位数</span>
            <div className="seat-stepper">
              <button onClick={() => setSeats((n) => Math.max(1, n - 1))} disabled={seats <= 1} aria-label="减少座位">−</button>
              <strong>{seats}</strong>
              <button className="plus" onClick={() => setSeats((n) => Math.min(Math.max(1, seatsLeft), n + 1))} disabled={seats >= seatsLeft} aria-label="增加座位">+</button>
            </div>
          </div>
          {trip.priceBreakdown ? (
            <PriceBreakdownRows breakdown={trip.priceBreakdown} seats={seats} />
          ) : (
            <div className="price-row muted">
              <span>座位单价 × {seats}</span>
              <span className="strong">¥{total}</span>
            </div>
          )}
          <div className="price-row muted">
            <span>平台服务费</span>
            <span className="strong">¥0.00</span>
          </div>
          <div className="panel-divider" />
          <div className="price-row total">
            <span>合计</span>
            <span className="price-total">¥{total}</span>
          </div>
        </section>

        <div className="safety-note">
          <ShieldCheck size={15} />
          <span>支付由已签名回调驱动，超时未支付自动取消并释放座位。</span>
        </div>
      </div>

      <div className="sticky-bar">
        <Button
          full
          variant="primary"
          size="lg"
          iconLeft={<CreditCard size={18} />}
          disabled={seatsLeft <= 0 || confirm.isPending}
          onClick={() => confirm.mutate()}
        >
          {confirm.isPending ? '处理中…' : `下单锁座 ¥${total}`}
        </Button>
      </div>
    </div>
  );
}
