import type { PriceBreakdown } from '../lib/types';

/**
 * Renders the server-authoritative per-seat fare breakdown. Every number comes straight from
 * the server (`PriceBreakdown`); the only client arithmetic is multiplying the final per-seat
 * total by the seat count for the line total — the per-seat fare itself is never recomputed.
 *
 * `variant` selects the host's class prefix so this works inside both the mobile price panel
 * (`price-row`) and the desktop booking pane (`dsk-price-row`).
 */
export function PriceBreakdownRows({
  breakdown,
  seats,
  variant = 'mobile'
}: {
  breakdown: PriceBreakdown;
  seats: number;
  variant?: 'mobile' | 'desktop';
}) {
  const row = variant === 'desktop' ? 'dsk-price-row' : 'price-row';
  const strong = 'strong';
  const perSeat = Number(breakdown.total.amount);
  const lineTotal = (perSeat * seats).toFixed(2);
  const included = Number(breakdown.includedKm);
  const chargeable = Number(breakdown.chargeableKm);

  return (
    <>
      <div className={`${row} muted`}>
        <span>路线距离</span>
        <span className={strong}>{(breakdown.distanceMeters / 1000).toFixed(1)} km</span>
      </div>
      <div className={`${row} muted`}>
        <span>起步价（含 {included} km）</span>
        <span className={strong}>¥{Number(breakdown.baseFare).toFixed(2)}</span>
      </div>
      <div className={`${row} muted`}>
        <span>超程 {chargeable.toFixed(1)} km</span>
        <span className={strong}>¥{Number(breakdown.extraCharge).toFixed(2)}</span>
      </div>
      <div className={`${row} muted`}>
        <span>每座合计 × {seats}</span>
        <span className={strong}>¥{perSeat.toFixed(2)} × {seats} = ¥{lineTotal}</span>
      </div>
    </>
  );
}
