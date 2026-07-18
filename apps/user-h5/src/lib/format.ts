import type { TripOffer } from './types';

export function avatarInitial(value: string) {
  if (!value) return '客';
  // Phone numbers read as noise; show a friendly 我-style glyph for pure digits.
  return /^\d+$/.test(value) ? '我' : value[0].toUpperCase();
}

export function shortId(value: string) {
  // Backend ids carry a type prefix ("order-…", "trip-…", "user-…"): drop it so the
  // short display code shows the distinctive part, e.g. ORD·3F2A91.
  return value.replace(/^[a-z]+-/i, '').replace(/-/g, '').slice(0, 6).toUpperCase();
}

export function routeProviderLabel(trip: TripOffer) {
  return trip.route.providerTrace === 'amap-v5' ? '高德路线快照' : '本地 Mock 路线快照';
}

export function formatDeparture(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

export function formatClock(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}
