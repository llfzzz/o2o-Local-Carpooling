import { useState } from 'react';
import { Alert, Badge, Button, Card, EmptyState, Input, NumberInput, Stat, Tabs, Timeline, useToast } from '@fj';
import type { TimelineItem } from '@fj';
import { CreditCard, MessageCircle, X } from 'lucide-react';
import { describeError } from '../../lib/api';
import { formatClock, shortId } from '../../lib/format';
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE, PAYMENT_STATUS_LABEL } from '../../lib/labels';
import {
  useCancelOrder,
  useCreatePaymentIntent,
  useOrderReviewQuery,
  useOrdersQuery,
  usePaymentIntentQuery,
  useSubmitReview,
  useTripQuery
} from '../../lib/queries';
import { useOpenConversation } from '../../lib/chat';
import type { ConversationView } from '../../lib/chat';
import { ChatWindow } from '../../components/chat/ChatWindow';
import type { OrderDetail } from '../../lib/types';

const DONE = 'var(--success-500)';
const CURRENT = 'var(--accent)';
const PENDING = 'var(--border-strong)';
const DANGER = 'var(--danger-500)';

/** 我的行程 — Stat row + orders master list with a docked order-detail pane. */
export function DesktopTrips() {
  const [tab, setTab] = useState('ongoing');
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);

  const ordersQuery = useOrdersQuery();
  const orders = ordersQuery.data ?? [];
  const ongoing = orders.filter((order) => order.status === 'PENDING_PAYMENT' || order.status === 'SEAT_LOCKED');
  const history = orders.filter((order) => order.status !== 'PENDING_PAYMENT' && order.status !== 'SEAT_LOCKED');
  const visible = tab === 'ongoing' ? ongoing : history;
  // Derive from the 5s-polled list so the pane always reflects the server-authoritative status.
  const selected = orders.find((order) => order.orderId === selectedOrderId) ?? null;

  const pendingPayment = orders.filter((order) => order.status === 'PENDING_PAYMENT').length;
  const completed = orders.filter((order) => order.status === 'COMPLETED').length;
  const cancelled = orders.length - ongoing.length - completed;

  return (
    <>
      <div className="dsk-stat-grid">
        {/* icon names come from fj-ui's built-in iconMask set, not the full lucide catalog */}
        <Stat label="进行中" value={ordersQuery.isLoading ? '—' : ongoing.length} icon="car-front" sublabel="待支付 + 座位锁定" />
        <Stat label="待支付" value={ordersQuery.isLoading ? '—' : pendingPayment} icon="alarm-clock" sublabel="超时将自动取消" />
        <Stat label="已完成" value={ordersQuery.isLoading ? '—' : completed} icon="check-circle" sublabel="可评价" />
        <Stat label="已取消" value={ordersQuery.isLoading ? '—' : cancelled} icon="minus" sublabel="座位已释放" />
      </div>

      <div className="dsk-master">
        <div className="dsk-master-pane">
          <div className="dsk-list-head">
            <Tabs
              items={[
                { id: 'ongoing', label: `进行中${ongoing.length ? ` · ${ongoing.length}` : ''}` },
                { id: 'history', label: '历史' }
              ]}
              value={tab}
              onChange={setTab}
            />
          </div>

          {ordersQuery.isLoading ? (
            <>
              <div className="dsk-skeleton-card" />
              <div className="dsk-skeleton-card" />
            </>
          ) : visible.length > 0 ? (
            visible.map((order) => (
              <button
                key={order.orderId}
                className={`dsk-order-row${selectedOrderId === order.orderId ? ' selected' : ''}`}
                onClick={() => setSelectedOrderId(order.orderId)}
              >
                <div className="dsk-order-main">
                  <span className="dsk-order-id" title={order.orderId}>ORD·{shortId(order.orderId)}</span>
                  <span className="dsk-order-meta">{formatClock(order.createdAt)} 下单 · {order.seats} 座</span>
                </div>
                <Badge tone={ORDER_STATUS_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</Badge>
                <span className="dsk-order-amount">¥{Number(order.amount.amount).toFixed(2)}</span>
              </button>
            ))
          ) : (
            <Card padding="24px">
              <EmptyState
                icon="car-front"
                compact
                title={tab === 'ongoing' ? '暂无进行中的行程' : '暂无历史行程'}
                description={tab === 'ongoing' ? '在「找车」选择一位顺路车主开始订座。' : undefined}
              />
            </Card>
          )}
        </div>

        {selected ? (
          <OrderPane key={selected.orderId} order={selected} onClose={() => setSelectedOrderId(null)} />
        ) : (
          <aside className="dsk-detail empty">
            <div className="dsk-detail-head">订单详情</div>
            <div className="dsk-detail-placeholder">
              <EmptyState icon="file" compact title="选择左侧订单查看详情" description="支付、取消与评价都在这里完成。" />
            </div>
          </aside>
        )}
      </div>
    </>
  );
}

/** Docked order pane: status timeline + callback-driven payment, cancel and review. */
function OrderPane({ order, onClose }: { order: OrderDetail; onClose: () => void }) {
  const toast = useToast();
  const [intentId, setIntentId] = useState<string | null>(null);
  const [conversation, setConversation] = useState<ConversationView | null>(null);
  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const openConversation = useOpenConversation({
    onSuccess: setConversation,
    onError: showError
  });

  // The trip snapshot gives the route text; orders only carry tripId.
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
  const intent = intentQuery.data;

  const cancelOrder = useCancelOrder(order.orderId, {
    onSuccess: () => toast({ title: '订单已取消，座位已释放', tone: 'success' }),
    onError: showError
  });

  const canPay = order.status === 'PENDING_PAYMENT';
  const canCancel = order.status === 'PENDING_PAYMENT' || order.status === 'SEAT_LOCKED';
  const isCancelled = order.status === 'TIMEOUT_CANCELLED' || order.status === 'USER_CANCELLED'
    || order.status === 'DRIVER_CANCELLED' || order.status === 'OPERATOR_CANCELLED';

  // Icon-less items render as solid accent dots — state reads from the color alone.
  const timeline: TimelineItem[] = [
    {
      title: '已下单',
      time: formatClock(order.createdAt),
      body: `${order.seats} 座 · ¥${Number(order.amount.amount).toFixed(2)}`,
      accent: DONE
    }
  ];
  if (isCancelled) {
    timeline.push({ title: ORDER_STATUS_LABEL[order.status], body: '座位已释放', accent: DANGER });
  } else {
    timeline.push(
      order.status === 'PENDING_PAYMENT'
        ? {
            title: '等待支付回调',
            body: intent ? `支付意图 ${PAYMENT_STATUS_LABEL[intent.status]}` : '发起支付后由签名回调驱动',
            accent: CURRENT
          }
        : { title: '支付成功 · 座位锁定', accent: DONE },
      {
        title: trip ? `待出发 · ${formatClock(trip.departureAt)}` : '待出发',
        body: order.status === 'SEAT_LOCKED' ? '等待行程完成（由运营确认）' : undefined,
        accent: order.status === 'SEAT_LOCKED' ? CURRENT : order.status === 'COMPLETED' ? DONE : PENDING
      },
      { title: '已完成', accent: order.status === 'COMPLETED' ? DONE : PENDING }
    );
  }

  return (
    <aside className="dsk-detail">
      <div className="dsk-detail-head">
        <span className="dsk-mono" title={order.orderId}>ORD·{shortId(order.orderId)}</span>
        <button className="dsk-icon-btn" onClick={onClose} aria-label="关闭">
          <X size={16} />
        </button>
      </div>
      <div className="dsk-detail-body">
        <div className="dsk-status-line">
          <Badge tone={ORDER_STATUS_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</Badge>
        </div>

        <div className="dsk-trip-route">
          <span>{trip ? trip.originText : `行程 ${shortId(order.tripId)}`}</span>
          <span className="dsk-trip-route-line" />
          <span>{trip ? trip.destinationText : ''}</span>
        </div>

        <Timeline items={timeline} />

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
            <div className="dsk-note">
              <span>超时未支付将自动取消并释放座位。支付结果由供应商签名回调驱动，此处状态自动刷新。</span>
            </div>
          </>
        )}

        {!isCancelled && (
          conversation ? (
            <ChatWindow conversation={conversation} onClose={() => setConversation(null)} />
          ) : (
            <Button
              full
              variant="secondary"
              iconLeft={<MessageCircle size={16} />}
              disabled={openConversation.isPending}
              onClick={() => openConversation.mutate(order.orderId)}
            >
              {openConversation.isPending ? '打开会话中…' : '联系司机'}
            </Button>
          )
        )}

        {order.status === 'COMPLETED' && <ReviewSection orderId={order.orderId} />}
      </div>
      {canCancel && (
        <div className="dsk-detail-foot">
          <Button full variant="ghost" disabled={cancelOrder.isPending} onClick={() => cancelOrder.mutate()}>
            {cancelOrder.isPending ? '取消中…' : '取消订单'}
          </Button>
        </div>
      )}
    </aside>
  );
}

function ReviewSection({ orderId }: { orderId: string }) {
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
    <>
      <div className="dsk-divider" />
      <Alert tone="success" title="行程已完成">给这次行程打个分吧。</Alert>
      {review ? (
        <div className="dsk-status-line">
          <Badge tone="success">已评价 {review.rating}★</Badge>
          {review.comment && <span>{review.comment}</span>}
        </div>
      ) : reviewQuery.isLoading ? (
        <span className="dsk-count-note">加载评价…</span>
      ) : (
        <>
          <div className="dsk-price-row">
            <span>评分（1-5）</span>
            <NumberInput size="sm" value={rating} min={1} max={5} onChange={(value) => setRating(Number(value))} />
          </div>
          <Input label="评价（可选）" value={comment} onChange={(event) => setComment(event.target.value)} />
          <Button full variant="primary" disabled={submit.isPending} onClick={() => submit.mutate({ rating, comment })}>
            {submit.isPending ? '提交中…' : '提交评价'}
          </Button>
        </>
      )}
    </>
  );
}
