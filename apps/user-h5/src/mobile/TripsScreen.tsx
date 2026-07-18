import { useState } from 'react';
import { Alert, Badge, Button, EmptyState, Input, NumberInput, SegmentedControl, Text, useToast } from '@fj';
import { Check, CreditCard } from 'lucide-react';
import { describeError } from '../lib/api';
import { formatClock, shortId } from '../lib/format';
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE, PAYMENT_STATUS_LABEL } from '../lib/labels';
import {
  useCancelOrder,
  useCreatePaymentIntent,
  useOrderReviewQuery,
  useOrdersQuery,
  usePaymentIntentQuery,
  useSubmitReview,
  useTripQuery
} from '../lib/queries';
import type { OrderDetail } from '../lib/types';

/** A5 · 我的行程 — segmented 进行中/历史, per-order status timeline, payment & cancel actions. */
export function TripsScreen() {
  const [segment, setSegment] = useState('ongoing');

  const ordersQuery = useOrdersQuery();

  const orders = ordersQuery.data ?? [];
  const ongoing = orders.filter((order) => order.status === 'PENDING_PAYMENT' || order.status === 'SEAT_LOCKED');
  const history = orders.filter((order) => order.status !== 'PENDING_PAYMENT' && order.status !== 'SEAT_LOCKED');
  const visible = segment === 'ongoing' ? ongoing : history;

  return (
    <div className="screen">
      <header className="screen-header">
        <span className="page-title">我的行程</span>
        <SegmentedControl
          full
          value={segment}
          onChange={setSegment}
          options={[
            { value: 'ongoing', label: `进行中${ongoing.length ? ` · ${ongoing.length}` : ''}` },
            { value: 'history', label: '历史' }
          ]}
        />
      </header>

      <div className="screen-body">
        {ordersQuery.isLoading ? (
          <div className="skeleton-stack">
            <div className="skeleton-card tall" />
          </div>
        ) : visible.length > 0 ? (
          visible.map((order) => <OrderCard key={order.orderId} order={order} />)
        ) : (
          <div className="empty-card">
            <EmptyState
              icon="route"
              compact
              title={segment === 'ongoing' ? '暂无进行中的行程' : '暂无历史行程'}
              description={segment === 'ongoing' ? '在首页选择一位顺路车主开始订座。' : undefined}
            />
          </div>
        )}
      </div>
    </div>
  );
}

/** One order: route rail + status timeline + callback-driven payment actions. */
function OrderCard({ order }: { order: OrderDetail }) {
  const toast = useToast();
  const [intentId, setIntentId] = useState<string | null>(null);
  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  // The trip snapshot gives the route text for the rail; orders only carry tripId.
  const tripQuery = useTripQuery(order.tripId);
  const trip = tripQuery.data;

  const createIntent = useCreatePaymentIntent(order.orderId, {
    onSuccess: (intent) => {
      setIntentId(intent.intentId);
      toast({ title: '已发起支付，等待签名回调', tone: 'info' });
    },
    onError: showError
  });

  const intentQuery = usePaymentIntentQuery(intentId);

  const cancelOrder = useCancelOrder(order.orderId, {
    onSuccess: () => toast({ title: '订单已取消，座位已释放', tone: 'success' }),
    onError: showError
  });

  const intent = intentQuery.data;
  const canPay = order.status === 'PENDING_PAYMENT';
  const canCancel = order.status === 'PENDING_PAYMENT' || order.status === 'SEAT_LOCKED';
  const cancelled = order.status === 'TIMEOUT_CANCELLED' || order.status === 'USER_CANCELLED'
    || order.status === 'DRIVER_CANCELLED' || order.status === 'OPERATOR_CANCELLED';

  return (
    <section className="panel order-card">
      <div className="order-card-head">
        <span className="mono order-id" title={order.orderId}>ORD·{shortId(order.orderId)}</span>
        <Badge tone={ORDER_STATUS_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</Badge>
      </div>

      <div className="order-rail">
        <strong>{trip ? trip.originText : `行程 ${shortId(order.tripId)}`}</strong>
        <span className="order-rail-line" />
        <strong>{trip ? trip.destinationText : ''}</strong>
      </div>

      <div className="otl">
        <TimelineStep state="done" title="已下单" meta={`${formatClock(order.createdAt)} · ${order.seats} 座 · ¥${Number(order.amount.amount).toFixed(2)}`} />
        {cancelled ? (
          <TimelineStep state="danger" title={ORDER_STATUS_LABEL[order.status]} meta="座位已释放" last />
        ) : (
          <>
            <TimelineStep
              state={order.status === 'PENDING_PAYMENT' ? 'current' : 'done'}
              title={order.status === 'PENDING_PAYMENT' ? '等待支付回调' : '支付成功 · 座位锁定'}
              meta={order.status === 'PENDING_PAYMENT'
                ? (intent ? `支付意图 ${PAYMENT_STATUS_LABEL[intent.status]}` : '发起支付后由签名回调驱动')
                : undefined}
            />
            <TimelineStep
              state={order.status === 'SEAT_LOCKED' ? 'current' : order.status === 'COMPLETED' ? 'done' : 'pending'}
              title={trip ? `待出发 · ${formatClock(trip.departureAt)}` : '待出发'}
              meta={order.status === 'SEAT_LOCKED' ? '等待行程完成（由运营确认）' : undefined}
            />
            <TimelineStep state={order.status === 'COMPLETED' ? 'done' : 'pending'} title="已完成" last />
          </>
        )}
      </div>

      {canPay && (
        <>
          <Button
            full
            variant="primary"
            iconLeft={<CreditCard size={16} />}
            disabled={createIntent.isPending || !!intentId}
            onClick={() => createIntent.mutate()}
          >
            {intentId ? '支付已发起 · 等待回调' : createIntent.isPending ? '发起中…' : '发起支付'}
          </Button>
          <p className="order-note">超时未支付将自动取消并释放座位。支付结果由供应商签名回调驱动，此处状态自动刷新。</p>
        </>
      )}

      {order.status === 'COMPLETED' && <OrderReviewSection orderId={order.orderId} />}

      {canCancel && (
        <Button full variant="ghost" disabled={cancelOrder.isPending} onClick={() => cancelOrder.mutate()}>
          {cancelOrder.isPending ? '取消中…' : '取消订单'}
        </Button>
      )}
    </section>
  );
}

function TimelineStep({ state, title, meta, last = false }: {
  state: 'done' | 'current' | 'pending' | 'danger';
  title: string;
  meta?: string;
  last?: boolean;
}) {
  return (
    <div className={`otl-step ${state}`}>
      <div className="otl-rail">
        <span className="otl-node">{state === 'done' && <Check size={12} />}</span>
        {!last && <span className="otl-line" />}
      </div>
      <div className="otl-body">
        <div className="otl-title">{title}</div>
        {meta && <div className="otl-meta">{meta}</div>}
      </div>
    </div>
  );
}

function OrderReviewSection({ orderId }: { orderId: string }) {
  const toast = useToast();
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');

  const reviewQuery = useOrderReviewQuery(orderId);

  const submit = useSubmitReview(orderId, {
    onSuccess: () => toast({ title: '评价已提交，谢谢！', tone: 'success' }),
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  const review = reviewQuery.data;

  return (
    <div className="review-block">
      <Alert tone="success" title="行程已完成">给这次行程打个分吧。</Alert>
      {review ? (
        <div className="status-line">
          <Badge tone="success">已评价 {review.rating}★</Badge>
          {review.comment && <span>{review.comment}</span>}
        </div>
      ) : reviewQuery.isLoading ? (
        <Text variant="small" style={{ color: 'var(--text-subtle)' }}>加载评价…</Text>
      ) : (
        <>
          <div className="price-row">
            <span>评分（1-5）</span>
            <NumberInput value={rating} min={1} max={5} onChange={(value) => setRating(Number(value))} />
          </div>
          <Input label="评价（可选）" value={comment} onChange={(event) => setComment(event.target.value)} />
          <Button full variant="primary" disabled={submit.isPending} onClick={() => submit.mutate({ rating, comment })}>
            {submit.isPending ? '提交中…' : '提交评价'}
          </Button>
        </>
      )}
    </div>
  );
}
