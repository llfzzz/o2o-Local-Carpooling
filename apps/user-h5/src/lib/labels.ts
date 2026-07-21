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

/* ---- Message Center ---- */

/** Human labels for known notification categories; unknown categories fall back to the raw key. */
export const MESSAGE_CATEGORY_LABEL: Record<string, string> = {
  ORDER_CREATED: '订单已创建',
  ORDER_PAID: '支付成功',
  ORDER_PAYMENT_TIMEOUT: '支付超时',
  ORDER_CANCELLED_BY_USER: '乘客取消',
  ORDER_CANCELLED_BY_DRIVER: '司机取消',
  ORDER_CANCELLED_BY_OPERATOR: '运营取消',
  ORDER_COMPLETED: '行程完成',
  ORDER_REVIEW_INVITATION: '评价邀请',
  TRIP_SEAT_LOCKED: '新订座',
  TRIP_SEAT_RELEASED: '座位释放',
  TRIP_DEPARTURE_REMINDER: '出发提醒',
  IDENTITY_VERIFICATION_RESULT: '实名认证结果',
  DRIVER_VERIFICATION_RESULT: '司机审核结果',
  SYSTEM_NOTICE: '系统通知'
};

export function messageCategoryLabel(category: string): string {
  return MESSAGE_CATEGORY_LABEL[category] ?? category;
}

/** Coarse filter groups for the Message Center chips (client-side over loaded pages). */
export const MESSAGE_GROUPS: { key: string; label: string; match: RegExp }[] = [
  { key: 'order', label: '订单', match: /^ORDER_/ },
  { key: 'trip', label: '行程', match: /^TRIP_/ },
  { key: 'verify', label: '认证', match: /IDENTITY|LIVENESS|DRIVER_VERIFICATION/ },
  { key: 'system', label: '系统', match: /^SYSTEM_/ }
];
