import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Button, EmptyState, SegmentedControl, useToast } from '@fj';
import { Bell, CreditCard, ExternalLink, Route as RouteIcon, ShieldCheck, Sparkles } from 'lucide-react';
import { describeError } from '../lib/api';
import { formatClock } from '../lib/format';
import { MESSAGE_GROUPS, messageCategoryLabel } from '../lib/labels';
import {
  useInboxInfiniteQuery,
  useMarkAllRead,
  useMarkDeliveryRead,
  useRevealDelivery
} from '../lib/queries';
import { useChatUnreadQuery } from '../lib/chat';
import type { ConversationView } from '../lib/chat';
import { ChatWindow } from '../components/chat/ChatWindow';
import { ConversationList } from '../components/chat/ConversationList';
import type { DeliveryRecord, MessageLinkType } from '../lib/types';

const CATEGORY_ICON: { match: RegExp; icon: ReactNode; className: string }[] = [
  { match: /PAYMENT|PAID/i, icon: <CreditCard size={19} />, className: 'tint-success' },
  { match: /IDENTITY|LIVENESS|DRIVER_VERIFICATION/i, icon: <ShieldCheck size={19} />, className: 'tint-accent' },
  { match: /REVIEW/i, icon: <Sparkles size={19} />, className: 'tint-accent' },
  { match: /TRIP|ORDER/i, icon: <RouteIcon size={19} />, className: 'tint-neutral' }
];

function categoryIcon(category: string) {
  const hit = CATEGORY_ICON.find((entry) => entry.match.test(category));
  return hit ?? { icon: <Bell size={19} />, className: 'tint-neutral' };
}

/**
 * A6 · 消息（通知） — production Message Center list: category filter chips, unread states,
 * mark one/all read, incremental loading, deep links to the related order/trip. Sensitive
 * values stay masked until an explicit 查看.
 */
export function InboxScreen({ onOpenLink }: { onOpenLink?: (type: MessageLinkType, id: string) => void }) {
  const toast = useToast();
  const [section, setSection] = useState<'notices' | 'chats'>('notices');
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
    <div className="screen">
      <header className="screen-header row">
        <span className="page-title">消息</span>
        {section === 'notices' && (
          <button
            className="link-btn"
            disabled={!hasUnread || markAllRead.isPending}
            onClick={() => markAllRead.mutate()}
          >
            {markAllRead.isPending ? '处理中…' : '全部已读'}
          </button>
        )}
      </header>

      <div className="screen-body">
        <SegmentedControl
          full
          value={section}
          onChange={(value) => setSection(value as 'notices' | 'chats')}
          options={[
            { value: 'notices', label: '通知' },
            { value: 'chats', label: `私信${(chatUnread.data?.unread ?? 0) > 0 ? ` · ${chatUnread.data!.unread}` : ''}` }
          ]}
        />

        {section === 'chats' ? (
          <>
            <ConversationList onOpen={setActiveConversation} />
            {activeConversation && (
              <div className="chat-overlay" onClick={() => setActiveConversation(null)}>
                <div onClick={(event) => event.stopPropagation()} style={{ display: 'contents' }}>
                  <ChatWindow conversation={activeConversation} onClose={() => setActiveConversation(null)} />
                </div>
              </div>
            )}
          </>
        ) : (
        <>
        <div className="inbox-filter-row">
          <button className={`chip${group === 'all' ? ' active' : ''}`} onClick={() => setGroup('all')}>全部</button>
          {MESSAGE_GROUPS.map((entry) => (
            <button
              key={entry.key}
              className={`chip${group === entry.key ? ' active' : ''}`}
              onClick={() => setGroup(entry.key)}
            >
              {entry.label}
            </button>
          ))}
        </div>

        {filtered.length > 0 ? (
          <>
            {filtered.map((record) => {
              const { icon, className } = categoryIcon(record.category);
              const unread = record.readAt == null;
              return (
                <div
                  key={record.deliveryId}
                  className={`inbox-item${unread ? ' unread' : ''}`}
                  onClick={() => unread && markRead.mutate(record.deliveryId)}
                >
                  <span className={`inbox-icon ${className}`}>{icon}</span>
                  <div className="inbox-item-body">
                    <div className="inbox-item-head">
                      <strong>{record.title}</strong>
                      <span className="mono">{formatClock(record.createdAt)}</span>
                    </div>
                    <div className="inbox-item-preview">
                      {messageCategoryLabel(record.category)} · {record.maskedPreview}
                    </div>
                  </div>
                  {record.revealable && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={(event) => {
                        event.stopPropagation();
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
                      iconLeft={<ExternalLink size={14} />}
                      onClick={(event) => {
                        event.stopPropagation();
                        markRead.mutate(record.deliveryId);
                        onOpenLink(record.linkType!, record.linkId!);
                      }}
                    >
                      相关
                    </Button>
                  )}
                </div>
              );
            })}
            {inbox.hasNextPage && (
              <Button
                full
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
          <div className="empty-card">
            <EmptyState
              icon="inbox"
              compact
              title={inbox.isLoading ? '加载中' : group === 'all' ? '暂无消息' : '该分类暂无消息'}
            />
          </div>
        )}
        </>
        )}
      </div>
    </div>
  );
}
