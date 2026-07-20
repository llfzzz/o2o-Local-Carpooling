// Domain types shared by the mobile Trip Flow shell and the desktop rider console.
// These mirror Gateway response contracts; all state is server-authoritative.
import type { LocationRef } from './location';

export type SessionUser = { userId: string; phone: string; roles: string[] };
export type Session = { accessToken: string; refreshToken: string; user: SessionUser };

export type AuthToken = {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
  refreshExpiresAt: string;
  user: SessionUser;
};

export type RefreshResponse = {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
  refreshExpiresAt: string;
};

export type SmsCodeResponse = { phoneMasked: string; expiresAt: string; message: string };
export type DemoInboxPeek = { phoneMasked: string; maskedPreview: string | null; code: string | null; expiresAt: string | null; message: string };

export type DeliveryRecord = {
  deliveryId: string;
  channel: 'SMS' | 'PUSH' | 'IN_APP';
  category: string;
  title: string;
  maskedPreview: string;
  status: 'QUEUED' | 'DELIVERED' | 'FAILED' | 'RETRYING' | 'READ';
  createdAt: string;
};

export type TripOffer = {
  tripId: string;
  driverId: string;
  originText: string;
  destinationText: string;
  departureAt: string;
  // polyline/origin/destination are null on trips published before structured locations existed.
  route: {
    routeId: string;
    distanceMeters: number;
    durationSeconds: number;
    providerTrace: string;
    polyline: string | null;
    origin: LocationRef | null;
    destination: LocationRef | null;
  };
  inventory: { totalSeats: number; lockedSeats: number };
  seatPrice: { amount: number; currency: string };
  status: 'PUBLISHED' | 'CANCELLED' | 'FINISHED';
};

export type OrderDetail = {
  orderId: string;
  tripId: string;
  riderId: string;
  seats: number;
  amount: { amount: number; currency: string };
  status: 'PENDING_PAYMENT' | 'SEAT_LOCKED' | 'TIMEOUT_CANCELLED' | 'USER_CANCELLED' | 'DRIVER_CANCELLED' | 'OPERATOR_CANCELLED' | 'COMPLETED';
  createdAt: string;
};

export type PaymentIntentStatus = 'REQUIRES_PAYMENT' | 'AUTHORIZED' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'EXPIRED';

export type PaymentIntent = {
  intentId: string;
  orderId: string;
  riderId: string;
  amount: { amount: number; currency: string };
  status: PaymentIntentStatus;
  provider: string;
  providerRef: string;
  createdAt: string;
  updatedAt: string;
};

export type IdentityStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'TIMEOUT' | 'RETRY_REQUIRED';
export type LivenessStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'TIMEOUT' | 'RETRY_REQUIRED';

export type IdentityVerification = {
  verificationId: string;
  userId: string;
  status: IdentityStatus;
  livenessStatus: LivenessStatus;
  provider: string;
  providerRef: string;
  createdAt: string;
  updatedAt: string;
};

export type OrderReview = {
  reviewId: string;
  orderId: string;
  tripId: string;
  reviewerId: string;
  rating: number;
  comment: string | null;
  createdAt: string;
};

export type FileObject = {
  fileObjectId: string;
  ownerId: string;
  bucket: string;
  objectName: string;
  contentType: string;
  privateObject: boolean;
  createdAt: string;
};

export type PresignedUpload = {
  fileObject: FileObject;
  uploadUrl: string;
  method: 'PUT';
  requiredHeaders: Record<string, string>;
  expiresAt: string;
};

export type VerificationState = 'DRAFT' | 'OCR_REVIEWABLE' | 'APPROVED';
