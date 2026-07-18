import { Badge, Button, Card, EmptyState, List, useToast } from '@fj';
import { describeError } from '../../lib/api';
import { formatClock } from '../../lib/format';
import { useMarkAllRead, useMarkDeliveryRead, useRevealDelivery } from '../../lib/queries';
import type { DeliveryRecord } from '../../lib/types';

// Names come from fj-ui's built-in iconMask set (not the full lucide catalog).
const CATEGORY_ICON_NAME: { match: RegExp; icon: string }[] = [
  { match: /PAYMENT/i, icon: 'check-circle' },
  { match: /IDENTITY|LIVENESS/i, icon: 'shield-alert' },
  { match: /SMS|CODE/i, icon: 'lock' },
  { match: /REVIEW/i, icon: 'file-check-2' }
];

function categoryIconName(category: string) {
  return CATEGORY_ICON_NAME.find((entry) => entry.match.test(category))?.icon ?? 'inbox';
}

/** 消息 — demo inbox. Only maskedPreview renders in the list; the sensitive value surfaces
 *  exclusively through the explicit per-item 查看 action (the existing /reveal POST). */
export function DesktopInbox({ records, loading }: { records: DeliveryRecord[]; loading: boolean }) {
  const toast = useToast();

  const reveal = useRevealDelivery({
    onSuccess: (response) => toast({ title: `内容：${response.value}`, tone: 'success' }),
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  const markRead = useMarkDeliveryRead();

  const unread = records.filter((record) => record.status !== 'READ');
  const markAllRead = useMarkAllRead(unread);

  return (
    <>
      <div className="dsk-inbox-head">
        <div className="dsk-inbox-title-row">
          <span className="dsk-section-title">演示收件箱</span>
          <span className="dsk-count-note">敏感内容默认遮蔽，点「查看」显式取出</span>
        </div>
        <Button
          variant="ghost"
          size="sm"
          disabled={unread.length === 0 || markAllRead.isPending}
          onClick={() => markAllRead.mutate()}
        >
          {markAllRead.isPending ? '处理中…' : '全部已读'}
        </Button>
      </div>

      {records.length > 0 ? (
        <List
          items={records.map((record) => ({
            id: record.deliveryId,
            icon: categoryIconName(record.category),
            title: (
              <span className="dsk-inbox-title-row">
                {record.title}
                {record.status !== 'READ' && <Badge tone="accent">未读</Badge>}
              </span>
            ),
            subtitle: `${record.category} · ${record.maskedPreview}`,
            trailing: (
              <span className="dsk-status-line">
                <span className="dsk-inbox-time">{formatClock(record.createdAt)}</span>
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
              </span>
            )
          }))}
        />
      ) : (
        <Card padding="24px">
          <EmptyState icon="inbox" compact title={loading ? '加载中' : '暂无消息'} />
        </Card>
      )}
    </>
  );
}
