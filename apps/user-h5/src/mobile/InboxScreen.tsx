import type { ReactNode } from 'react';
import { Button, EmptyState, useToast } from '@fj';
import { Bell, CreditCard, MessageSquare, ShieldCheck, Sparkles } from 'lucide-react';
import { describeError } from '../lib/api';
import { formatClock } from '../lib/format';
import { useMarkAllRead, useMarkDeliveryRead, useRevealDelivery } from '../lib/queries';
import type { DeliveryRecord } from '../lib/types';

const CATEGORY_ICON: { match: RegExp; icon: ReactNode; className: string }[] = [
  { match: /PAYMENT/i, icon: <CreditCard size={19} />, className: 'tint-success' },
  { match: /IDENTITY|LIVENESS/i, icon: <ShieldCheck size={19} />, className: 'tint-accent' },
  { match: /SMS|CODE/i, icon: <MessageSquare size={19} />, className: 'tint-neutral' },
  { match: /REVIEW/i, icon: <Sparkles size={19} />, className: 'tint-accent' }
];

function categoryIcon(category: string) {
  const hit = CATEGORY_ICON.find((entry) => entry.match.test(category));
  return hit ?? { icon: <Bell size={19} />, className: 'tint-neutral' };
}

/** A6 · 消息 — demo inbox; sensitive values stay masked until an explicit 查看. */
export function InboxScreen({ records, loading }: { records: DeliveryRecord[]; loading: boolean }) {
  const toast = useToast();

  const reveal = useRevealDelivery({
    onSuccess: (response) => toast({ title: `内容：${response.value}`, tone: 'success' }),
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  const markRead = useMarkDeliveryRead();

  const unread = records.filter((record) => record.status !== 'READ');
  const markAllRead = useMarkAllRead(unread);

  return (
    <div className="screen">
      <header className="screen-header row">
        <span className="page-title">消息</span>
        <button
          className="link-btn"
          disabled={unread.length === 0 || markAllRead.isPending}
          onClick={() => markAllRead.mutate()}
        >
          {markAllRead.isPending ? '处理中…' : '全部已读'}
        </button>
      </header>

      <div className="screen-body">
        <span className="section-eyebrow">演示收件箱 · 敏感内容默认遮蔽，点「查看」显式取出</span>
        {records.length > 0 ? (
          records.map((record) => {
            const { icon, className } = categoryIcon(record.category);
            return (
              <div key={record.deliveryId} className={`inbox-item${record.status !== 'READ' ? ' unread' : ''}`}>
                <span className={`inbox-icon ${className}`}>{icon}</span>
                <div className="inbox-item-body">
                  <div className="inbox-item-head">
                    <strong>{record.title}</strong>
                    <span className="mono">{formatClock(record.createdAt)}</span>
                  </div>
                  <div className="inbox-item-preview">{record.category} · {record.maskedPreview}</div>
                </div>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    reveal.mutate(record.deliveryId);
                    markRead.mutate(record.deliveryId);
                  }}
                >
                  查看
                </Button>
              </div>
            );
          })
        ) : (
          <div className="empty-card">
            <EmptyState icon="inbox" compact title={loading ? '加载中' : '暂无消息'} />
          </div>
        )}
      </div>
    </div>
  );
}
