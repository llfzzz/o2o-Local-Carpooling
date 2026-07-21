import { useMemo, useState } from 'react';
import { Badge, Button, Card, EmptyState, List, Tabs, useToast } from '@fj';
import { describeError } from '../../lib/api';
import { formatClock } from '../../lib/format';
import { MESSAGE_GROUPS, messageCategoryLabel } from '../../lib/labels';
import {
  useInboxInfiniteQuery,
  useMarkAllRead,
  useMarkDeliveryRead,
  useRevealDelivery
} from '../../lib/queries';
import { useChatUnreadQuery } from '../../lib/chat';
import type { ConversationView } from '../../lib/chat';
import { ChatWindow } from '../../components/chat/ChatWindow';
import { ConversationList } from '../../components/chat/ConversationList';
import type { DeliveryRecord, MessageLinkType } from '../../lib/types';

// Names come from fj-ui's built-in iconMask set (not the full lucide catalog).
const CATEGORY_ICON_NAME: { match: RegExp; icon: string }[] = [
  { match: /PAYMENT|PAID/i, icon: 'check-circle' },
  { match: /IDENTITY|LIVENESS|DRIVER_VERIFICATION/i, icon: 'shield-alert' },
  { match: /REVIEW/i, icon: 'file-check-2' },
  { match: /TRIP|ORDER/i, icon: 'route' }
];

function categoryIconName(category: string) {
  return CATEGORY_ICON_NAME.find((entry) => entry.match.test(category))?.icon ?? 'inbox';
}

/** 消息（通知） — production Message Center. Only maskedPreview renders in the list; sensitive
 *  values surface exclusively through the explicit per-item 查看 action. */
export function DesktopInbox({ onOpenLink }: { onOpenLink?: (type: MessageLinkType, id: string) => void }) {
  const toast = useToast();
  const [section, setSection] = useState('notices');
  const [group, setGroup] = useState<string>('all');
  const [activeConversation, setActiveConversation] = useState<ConversationView | null>(null);
  const chatUnread = useChatUnreadQuery();

  const inbox = useInboxInfiniteQuery();
  const records: DeliveryRecord[] = useMemo(
    () => (inbox.data?.pages ?? []).flatMap((page) => page.items),
    [inbox.data]
  );
  const filtered = useMemo(() => {
    if (group === 'all') return records;
    const spec = MESSAGE_GROUPS.find((entry) => entry.key === group);
    return spec ? records.filter((record) => spec.match.test(record.category)) : records;
  }, [records, group]);

  const reveal = useRevealDelivery({
    onSuccess: (response) => toast({ title: `内容：${response.value}`, tone: 'success' }),
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });
  const markRead = useMarkDeliveryRead();
  const markAllRead = useMarkAllRead();

  const hasUnread = records.some((record) => record.readAt == null);

  return (
    <>
      <Tabs
        items={[
          { id: 'notices', label: '通知' },
          { id: 'chats', label: `私信${(chatUnread.data?.unread ?? 0) > 0 ? ` · ${chatUnread.data!.unread}` : ''}` }
        ]}
        value={section}
        onChange={setSection}
      />

      {section === 'chats' ? (
        activeConversation ? (
          <ChatWindow conversation={activeConversation} onClose={() => setActiveConversation(null)} />
        ) : (
          <ConversationList onOpen={setActiveConversation} />
        )
      ) : (
      <>
      <div className="dsk-inbox-head">
        <div className="dsk-inbox-title-row">
          <span className="dsk-section-title">消息中心</span>
          <span className="dsk-count-note">敏感内容默认遮蔽，点「查看」显式取出</span>
        </div>
        <span className="dsk-status-line">
          {['all', ...MESSAGE_GROUPS.map((entry) => entry.key)].map((key) => {
            const label = key === 'all' ? '全部' : MESSAGE_GROUPS.find((entry) => entry.key === key)?.label ?? key;
            return (
              <Button
                key={key}
                size="sm"
                variant={group === key ? 'secondary' : 'ghost'}
                onClick={() => setGroup(key)}
              >
                {label}
              </Button>
            );
          })}
          <Button
            variant="ghost"
            size="sm"
            disabled={!hasUnread || markAllRead.isPending}
            onClick={() => markAllRead.mutate()}
          >
            {markAllRead.isPending ? '处理中…' : '全部已读'}
          </Button>
        </span>
      </div>

      {filtered.length > 0 ? (
        <>
          <List
            items={filtered.map((record) => ({
              id: record.deliveryId,
              icon: categoryIconName(record.category),
              title: (
                <span className="dsk-inbox-title-row">
                  {record.title}
                  {record.readAt == null && <Badge tone="accent">未读</Badge>}
                </span>
              ),
              subtitle: `${messageCategoryLabel(record.category)} · ${record.maskedPreview}`,
              trailing: (
                <span className="dsk-status-line">
                  <span className="dsk-inbox-time">{formatClock(record.createdAt)}</span>
                  {record.readAt == null && (
                    <Button size="sm" variant="ghost" onClick={() => markRead.mutate(record.deliveryId)}>
                      已读
                    </Button>
                  )}
                  {record.revealable && (
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
                  )}
                  {record.linkType && record.linkId && onOpenLink && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        markRead.mutate(record.deliveryId);
                        onOpenLink(record.linkType!, record.linkId!);
                      }}
                    >
                      相关
                    </Button>
                  )}
                </span>
              )
            }))}
          />
          {inbox.hasNextPage && (
            <Button
              variant="secondary"
              size="sm"
              disabled={inbox.isFetchingNextPage}
              onClick={() => inbox.fetchNextPage()}
            >
              {inbox.isFetchingNextPage ? '加载中…' : '加载更多'}
            </Button>
          )}
        </>
      ) : (
        <Card padding="24px">
          <EmptyState
            icon="inbox"
            compact
            title={inbox.isLoading ? '加载中' : group === 'all' ? '暂无消息' : '该分类暂无消息'}
          />
        </Card>
      )}
      </>
      )}
    </>
  );
}
