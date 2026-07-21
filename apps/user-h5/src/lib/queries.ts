// Shared TanStack Query hooks consumed by both shells (mobile Trip Flow + desktop rider
// console). Hooks own the query keys, fetchers, polling intervals and cache invalidation;
// components own toasts and navigation via the optional callback objects. All query-key
// literals live in this file only, so a viewport switch lands on a warm shared cache.
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiRequestError, rawApi } from './api';
import { isResolved } from './location';
import type { GeoPoint, LocationRef, MapCities } from './location';
import { useSession } from './session';
import type {
  AuthToken,
  DemoLoginCodePeek,
  FileObject,
  InboxPage,
  RoutePreview,
  IdentityVerification,
  OrderDetail,
  OrderReview,
  PaymentIntent,
  PresignedUpload,
  SmsCodeResponse,
  TripOffer,
  VerificationState
} from './types';

export type MutationCallbacks<TData = unknown> = {
  onSuccess?: (data: TData) => void;
  onError?: (error: unknown) => void;
};

/* ---- Auth (unauthenticated, rawApi) ---- */

export function useSendSmsCode(phone: string, cb?: MutationCallbacks<SmsCodeResponse>) {
  return useMutation({
    mutationFn: () => rawApi<SmsCodeResponse>('/api/auth/sms-code', { method: 'POST', body: { phone } }),
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

/**
 * Demo-only login-page peek. POST keeps the phone out of URLs; the challengeId from the
 * matching sms-code response is required — without it the server reveals nothing.
 */
export function useDemoPeekLoginCode(phone: string, challengeId: string | null, cb?: MutationCallbacks<DemoLoginCodePeek>) {
  return useMutation({
    mutationFn: () => rawApi<DemoLoginCodePeek>('/api/auth/sms-code/demo-peek', {
      method: 'POST',
      body: { phone, challengeId }
    }),
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

export function useLogin(phone: string, code: string, cb?: MutationCallbacks<AuthToken>) {
  const setSession = useSession((state) => state.setSession);
  return useMutation({
    mutationFn: () => rawApi<AuthToken>('/api/auth/login', { method: 'POST', body: { phone, code } }),
    onSuccess: (token) => {
      setSession({ accessToken: token.accessToken, refreshToken: token.refreshToken, user: token.user });
      cb?.onSuccess?.(token);
    },
    onError: cb?.onError
  });
}

/* ---- Message Center (/api/inbox, production) ---- */

/** Keyset-paged inbox; 15s poll keeps new notifications flowing in without manual refresh. */
export function useInboxInfiniteQuery() {
  return useInfiniteQuery({
    queryKey: ['inbox'],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ limit: '20' });
      if (pageParam != null) params.set('cursor', String(pageParam));
      return api<InboxPage>(`/api/inbox?${params.toString()}`);
    },
    initialPageParam: null as number | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    refetchInterval: 15000
  });
}

/** Cheap dedicated count for the tab badges — polled independently of the list. */
export function useUnreadCountQuery() {
  return useQuery({
    queryKey: ['inbox-unread'],
    queryFn: () => api<{ unread: number }>('/api/inbox/unread-count'),
    refetchInterval: 15000
  });
}

export function useRevealDelivery(cb?: MutationCallbacks<{ deliveryId: string; value: string }>) {
  return useMutation({
    mutationFn: (deliveryId: string) => api<{ deliveryId: string; value: string }>(`/api/inbox/${deliveryId}/reveal`, { method: 'POST' }),
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

export function useMarkDeliveryRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (deliveryId: string) => api(`/api/inbox/${deliveryId}/read`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inbox'] });
      queryClient.invalidateQueries({ queryKey: ['inbox-unread'] });
    }
  });
}

export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api<{ updated: number }>('/api/inbox/read-all', { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inbox'] });
      queryClient.invalidateQueries({ queryKey: ['inbox-unread'] });
    }
  });
}

/* ---- Map + location ---- */

/** Supported cities, plus whether the demo provider is active (drives the demo badge). */
export function useCitiesQuery() {
  return useQuery({
    queryKey: ['map-cities'],
    queryFn: () => api<MapCities>('/api/maps/cities'),
    staleTime: 60 * 60 * 1000,
    retry: 1
  });
}

/**
 * Keyword autocomplete. Debouncing is the caller's job (see LocationSearchSheet) — every
 * keystroke that reaches here costs provider quota.
 */
export function usePlaceSuggest(keyword: string, cityCode: string | null, bias: GeoPoint | null) {
  const trimmed = keyword.trim();
  return useQuery({
    queryKey: ['place-suggest', trimmed, cityCode, bias?.latitude ?? null, bias?.longitude ?? null],
    queryFn: () => {
      const params = new URLSearchParams({ keyword: trimmed });
      if (cityCode) params.set('cityCode', cityCode);
      if (bias) {
        params.set('lat', String(bias.latitude));
        params.set('lng', String(bias.longitude));
        params.set('datum', bias.datum);
      }
      return api<LocationRef[]>(`/api/maps/place/suggest?${params.toString()}`);
    },
    enabled: trimmed.length > 0,
    staleTime: 5 * 60 * 1000
  });
}

/**
 * Coordinates to a structured place. Used by "use my location" and by a dragged map pin — the
 * datum travels with the point, so a WGS84 browser fix is converted server-side.
 */
export function useReverseGeocode(cb?: MutationCallbacks<LocationRef>) {
  return useMutation({
    mutationFn: (point: GeoPoint) => api<LocationRef>('/api/maps/reverse-geocode', {
      method: 'POST',
      body: { lat: point.latitude, lng: point.longitude, datum: point.datum }
    }),
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

/* ---- Trips ---- */

/**
 * Geographic trip search. Only runs once both endpoints are resolved places: an unresolved
 * value has no coordinates and could not be matched anyway.
 */
export function useTripSearchQuery(
  origin: LocationRef | null,
  destination: LocationRef | null,
  options?: { departAt?: string | null; minSeats?: number }
) {
  const ready = isResolved(origin) && isResolved(destination);
  const departAt = options?.departAt ?? null;
  const minSeats = options?.minSeats ?? 1;
  return useQuery({
    queryKey: ['trip-search', origin?.point, destination?.point, departAt, minSeats],
    queryFn: () => {
      const params = new URLSearchParams({
        originLat: String(origin!.point.latitude),
        originLng: String(origin!.point.longitude),
        destinationLat: String(destination!.point.latitude),
        destinationLng: String(destination!.point.longitude),
        datum: origin!.point.datum,
        minSeats: String(minSeats)
      });
      if (departAt) params.set('departAt', departAt);
      return api<TripOffer[]>(`/api/trips/search?${params.toString()}`);
    },
    enabled: ready
  });
}

/**
 * Rider route confirmation: one authoritative map-service route + the per-seat fare breakdown
 * from the same server pricing policy that prices published trips. The client renders the
 * returned breakdown verbatim — it never computes a price.
 */
export function useRoutePreview(cb?: MutationCallbacks<RoutePreview>) {
  return useMutation({
    mutationFn: (input: { origin: LocationRef; destination: LocationRef }) =>
      api<RoutePreview>('/api/trips/route-preview', { method: 'POST', body: input }),
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

export function useTripQuery(tripId: string) {
  return useQuery({
    queryKey: ['trip', tripId],
    queryFn: () => api<TripOffer>(`/api/trips/${tripId}`),
    staleTime: 5 * 60 * 1000,
    retry: 1
  });
}

export function usePublishTrip(
  params: {
    userId: string;
    origin: string;
    destination: string;
    city: string;
    originRef?: LocationRef | null;
    destinationRef?: LocationRef | null;
  },
  cb?: MutationCallbacks<TripOffer>
) {
  const queryClient = useQueryClient();
  return useMutation({
    // No driverId: the server binds the trip to the authenticated principal. Sending one would
    // be ignored anyway, and sending it invited the spoofing hole this replaced.
    mutationFn: () => api<TripOffer>('/api/trips', {
      method: 'POST',
      body: {
        // Structured endpoints when we have them; the text fields stay for the legacy path.
        origin: params.originRef ?? null,
        destination: params.destinationRef ?? null,
        originText: params.origin,
        destinationText: params.destination,
        city: params.city,
        departureAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        totalSeats: 3,
        idempotencyKey: crypto.randomUUID()
      }
    }),
    onSuccess: (trip) => {
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      queryClient.invalidateQueries({ queryKey: ['trip-search'] });
      cb?.onSuccess?.(trip);
    },
    onError: cb?.onError
  });
}

/* ---- Demo virtual trips (demo profile only; 404 otherwise) ---- */

/**
 * Generate demo virtual offers for the confirmed route. Server-side: same PricingPolicy as real
 * trips, deterministic, labelled DEMO. 404 outside demo mode (the caller hides the button then).
 */
export function useGenerateDemoTrips(cb?: MutationCallbacks<TripOffer[]>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { origin: LocationRef; destination: LocationRef }) =>
      api<TripOffer[]>('/api/demo/trips/generate', { method: 'POST', body: input }),
    onSuccess: (trips) => {
      queryClient.invalidateQueries({ queryKey: ['trip-search'] });
      cb?.onSuccess?.(trips);
    },
    onError: cb?.onError
  });
}

/** Generate demo offers for a random route (two fixture places in the given city). */
export function useGenerateRandomDemoTrips(cb?: MutationCallbacks<TripOffer[]>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (cityCode: string | null) =>
      api<TripOffer[]>('/api/demo/trips/random', { method: 'POST', body: { cityCode } }),
    onSuccess: (trips) => {
      queryClient.invalidateQueries({ queryKey: ['trip-search'] });
      cb?.onSuccess?.(trips);
    },
    onError: cb?.onError
  });
}

/* ---- Orders + payment ---- */

export function useOrdersQuery() {
  return useQuery({
    queryKey: ['orders'],
    queryFn: () => api<OrderDetail[]>('/api/orders'),
    // Poll so an operator/PSP-driven signed payment callback, or a payment timeout,
    // surfaces here without a manual refresh. The order status is server-authoritative.
    refetchInterval: 5000
  });
}

// Places the order: the server locks seats and the order enters PENDING_PAYMENT. Payment is
// initiated from the order card and stays callback-driven end to end.
export function useCreateOrder(trip: TripOffer, seats: number, cb?: MutationCallbacks<OrderDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => {
      const idempotencyKey = `book-${trip.tripId}-${Date.now()}`;
      return api<OrderDetail>('/api/orders', {
        method: 'POST',
        body: { tripId: trip.tripId, riderId: useSession.getState().session?.user.userId, seats, idempotencyKey }
      });
    },
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      cb?.onSuccess?.(order);
    },
    onError: cb?.onError
  });
}

export function useCreatePaymentIntent(orderId: string, cb?: MutationCallbacks<PaymentIntent>) {
  return useMutation({
    mutationFn: () => api<PaymentIntent>('/api/payments/intents', {
      method: 'POST',
      body: { orderId, idempotencyKey: `intent-${orderId}` }
    }),
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

// Poll the intent so its status (REQUIRES_PAYMENT → SUCCEEDED/…) reflects the callback outcome.
export function usePaymentIntentQuery(intentId: string | null) {
  return useQuery({
    queryKey: ['payment-intent', intentId],
    queryFn: () => api<PaymentIntent>(`/api/payments/intents/${intentId}`),
    enabled: !!intentId,
    refetchInterval: 4000
  });
}

export function useCancelOrder(orderId: string, cb?: MutationCallbacks<OrderDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api<OrderDetail>(`/api/orders/${orderId}/cancel`, { method: 'POST' }),
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      cb?.onSuccess?.(order);
    },
    onError: cb?.onError
  });
}

/* ---- Reviews ---- */

export function useOrderReviewQuery(orderId: string) {
  return useQuery({
    queryKey: ['order-review', orderId],
    // The rider may not have reviewed yet: a 404 means "no review", not an error.
    queryFn: async () => {
      try {
        return await api<OrderReview>(`/api/orders/${orderId}/review`);
      } catch (error) {
        if (error instanceof ApiRequestError && error.status === 404) {
          return null;
        }
        throw error;
      }
    }
  });
}

export function useSubmitReview(orderId: string, cb?: MutationCallbacks<OrderReview>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { rating: number; comment: string }) => api<OrderReview>(`/api/orders/${orderId}/review`, {
      method: 'POST',
      body: input
    }),
    onSuccess: (review) => {
      queryClient.invalidateQueries({ queryKey: ['order-review', orderId] });
      cb?.onSuccess?.(review);
    },
    onError: cb?.onError
  });
}

/* ---- Identity + driver onboarding ---- */

export function useStartIdentityVerification(cb?: MutationCallbacks<IdentityVerification>) {
  return useMutation({
    mutationFn: (input: { realName: string; idNumber: string }) => api<IdentityVerification>('/api/identity/verifications', {
      method: 'POST',
      body: input
    }),
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

// Poll the session so the operator/provider-driven outcome (liveness + approval) surfaces here.
export function useIdentityVerificationQuery(verificationId: string | null) {
  return useQuery({
    queryKey: ['identity-verification', verificationId],
    queryFn: () => api<IdentityVerification>(`/api/identity/verifications/${verificationId}`),
    enabled: !!verificationId,
    refetchInterval: 4000
  });
}

export function useSubmitDriverCase(cb?: MutationCallbacks<{ status: VerificationState }>) {
  return useMutation({
    mutationFn: async (input: { drivingLicenseFile: File | null; vehicleLicenseFile: File | null }) => {
      if (!input.drivingLicenseFile || !input.vehicleLicenseFile) {
        throw new Error('请选择驾驶证和行驶证文件');
      }
      const drivingLicense = await uploadDriverDocument(input.drivingLicenseFile);
      const vehicleLicense = await uploadDriverDocument(input.vehicleLicenseFile);
      return api<{ status: VerificationState }>('/api/drivers/verification-cases', {
        method: 'POST',
        body: {
          userId: useSession.getState().session?.user.userId,
          drivingLicenseFileId: drivingLicense.fileObjectId,
          vehicleLicenseFileId: vehicleLicense.fileObjectId
        }
      });
    },
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

export async function uploadDriverDocument(file: File) {
  const presigned = await api<PresignedUpload>('/api/files/presign-upload', {
    method: 'POST',
    body: { objectName: file.name, contentType: file.type || 'application/octet-stream', contentLength: file.size }
  });
  const uploadResponse = await fetch(presigned.uploadUrl, {
    method: presigned.method,
    headers: presigned.requiredHeaders,
    body: file
  });
  if (!uploadResponse.ok) {
    throw new Error(`文件上传失败：HTTP ${uploadResponse.status}`);
  }
  return api<FileObject>(`/api/files/${presigned.fileObject.fileObjectId}/complete`, { method: 'POST' });
}
