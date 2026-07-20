import { Navigation } from 'lucide-react';
import { useDriverLocationStream } from '../lib/useDriverLocation';

/** Beyond this the fix is old enough that showing it as "live" would be misleading. */
const STALE_AFTER_SECONDS = 45;

/**
 * The driver's live position on a booked order.
 *
 * Only rendered once a seat is actually locked — the server enforces the same rule, so a rider
 * browsing trips can never see where drivers are. When the driver is not sharing, or the last fix
 * has gone stale, this says so plainly rather than showing an old position as current.
 */
export function DriverLiveStatus({ tripId, active }: { tripId: string; active: boolean }) {
  const { snapshot } = useDriverLocationStream(tripId, active);

  if (!active) return null;

  const stale = Boolean(snapshot?.sharing && (snapshot.ageSeconds ?? 0) > STALE_AFTER_SECONDS);
  const live = Boolean(snapshot?.sharing) && !stale;

  return (
    <div className={`driver-live${live ? ' is-live' : ''}`}>
      <Navigation size={14} color={live ? 'var(--accent)' : 'var(--text-muted)'} />
      <span>
        {live
          ? `车主位置实时更新中（${snapshot?.ageSeconds ?? 0} 秒前）`
          : stale
            ? '车主位置已过期，等待重新上报'
            : '车主尚未共享位置'}
      </span>
    </div>
  );
}
