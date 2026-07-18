// Shared TanStack Query hooks consumed by both shells (mobile Trip Flow + desktop rider
// console). Hooks own the query keys, fetchers, polling intervals and cache invalidation;
// components own toasts and navigation via the optional callback objects. All query-key
// literals live in this file only, so a viewport switch lands on a warm shared cache.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiRequestError, rawApi } from './api';
import { useSession } from './session';
import type {
  AuthToken,
  DeliveryRecord,
  DemoInboxPeek,
  FileObject,
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

export function usePeekDemoInbox(phone: string, cb?: MutationCallbacks<DemoInboxPeek>) {
  return useMutation({
    mutationFn: () => rawApi<DemoInboxPeek>(`/api/auth/sms-code/demo-inbox?phone=${encodeURIComponent(phone)}`),
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

/* ---- Demo inbox ---- */

export function useInboxQuery() {
  return useQuery({
    queryKey: ['demo-inbox'],
    queryFn: () => api<DeliveryRecord[]>('/api/demo/inbox'),
    refetchInterval: 5000
  });
}

export function useRevealDelivery(cb?: MutationCallbacks<{ deliveryId: string; value: string }>) {
  return useMutation({
    mutationFn: (deliveryId: string) => api<{ deliveryId: string; value: string }>(`/api/demo/inbox/${deliveryId}/reveal`, { method: 'POST' }),
    onSuccess: cb?.onSuccess,
    onError: cb?.onError
  });
}

export function useMarkDeliveryRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (deliveryId: string) => api(`/api/demo/inbox/${deliveryId}/read`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['demo-inbox'] })
  });
}

export function useMarkAllRead(unread: DeliveryRecord[]) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      for (const record of unread) {
        await api(`/api/demo/inbox/${record.deliveryId}/read`, { method: 'POST' });
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['demo-inbox'] })
  });
}

/* ---- Trips ---- */

export function useTripsQuery(origin: string, destination: string, userId: string) {
  return useQuery({
    queryKey: ['trips', origin, destination, userId],
    queryFn: () => api<TripOffer[]>(`/api/trips?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}`)
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
  params: { userId: string; origin: string; destination: string; city: string },
  cb?: MutationCallbacks<TripOffer>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api<TripOffer>('/api/trips', {
      method: 'POST',
      body: {
        driverId: params.userId,
        originText: params.origin,
        destinationText: params.destination,
        city: params.city,
        departureAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        totalSeats: 3
      }
    }),
    onSuccess: (trip) => {
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      cb?.onSuccess?.(trip);
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
