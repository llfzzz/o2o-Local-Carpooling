import type { IdentityStatus, LivenessStatus, OrderDetail, PaymentIntentStatus, TripOffer } from './types';

export const TRIP_STATUS_LABEL: Record<TripOffer['status'], string> = {
  PUBLISHED: '可订',
  CANCELLED: '已取消',
  FINISHED: '已结束'
};

export const ORDER_STATUS_LABEL: Record<OrderDetail['status'], string> = {
  PENDING_PAYMENT: '待支付',
  SEAT_LOCKED: '已支付 · 座位锁定',
  TIMEOUT_CANCELLED: '支付超时已取消',
  USER_CANCELLED: '已取消（本人）',
  DRIVER_CANCELLED: '已取消（司机）',
  OPERATOR_CANCELLED: '已取消（运营）',
  COMPLETED: '已完成'
};

export const ORDER_STATUS_TONE: Record<OrderDetail['status'], 'accent' | 'success' | 'danger'> = {
  PENDING_PAYMENT: 'accent',
  SEAT_LOCKED: 'success',
  COMPLETED: 'success',
  TIMEOUT_CANCELLED: 'danger',
  USER_CANCELLED: 'danger',
  DRIVER_CANCELLED: 'danger',
  OPERATOR_CANCELLED: 'danger'
};

export const PAYMENT_STATUS_LABEL: Record<PaymentIntentStatus, string> = {
  REQUIRES_PAYMENT: '待支付',
  AUTHORIZED: '已授权',
  SUCCEEDED: '支付成功',
  FAILED: '支付失败',
  CANCELED: '已取消',
  EXPIRED: '已过期'
};

export const IDENTITY_STATUS_LABEL: Record<IdentityStatus, string> = {
  PENDING: '认证中',
  APPROVED: '认证通过',
  REJECTED: '认证被驳回',
  TIMEOUT: '认证超时',
  RETRY_REQUIRED: '需要重试'
};

export const IDENTITY_STATUS_TONE: Record<IdentityStatus, 'accent' | 'success' | 'danger'> = {
  PENDING: 'accent',
  APPROVED: 'success',
  REJECTED: 'danger',
  TIMEOUT: 'danger',
  RETRY_REQUIRED: 'danger'
};

export const LIVENESS_STATUS_LABEL: Record<LivenessStatus, string> = {
  PENDING: '待检测',
  PASSED: '活体通过',
  FAILED: '活体失败',
  TIMEOUT: '活体超时',
  RETRY_REQUIRED: '需要重试'
};
