import { Badge, EmptyState } from '@fj';
import { MessageCircle } from 'lucide-react';
import { formatClock } from '../../lib/format';
import { useConversationsQuery } from '../../lib/chat';
import type { ConversationView } from '../../lib/chat';

/** Conversation list for the Message Center 私信 segment (both shells). */
export function ConversationList({ onOpen }: { onOpen: (conversation: ConversationView) => void }) {
  const conversationsQuery = useConversationsQuery();
  const conversations = conversationsQuery.data ?? [];

  if (conversations.length === 0) {
    return (
      <div className="empty-card">
        <EmptyState
          icon="inbox"
          compact
          title={conversationsQuery.isLoading ? '加载中' : '暂无会话'}
          description={conversationsQuery.isLoading ? undefined : '下单后可在订单卡片联系司机。'}
        />
      </div>
    );
  }

  return (
    <>
      {conversations.map((conversation) => (
        <button key={conversation.conversationId} className="chat-list-item" onClick={() => onOpen(conversation)}>
          <span className="inbox-icon tint-accent"><MessageCircle size={19} /></span>
          <span className="chat-list-body">
            <span className="chat-list-head">
              <strong>{conversation.counterpartLabel} · {conversation.originText} → {conversation.destinationText}</strong>
              {conversation.lastMessageAt && <span className="mono">{formatClock(conversation.lastMessageAt)}</span>}
            </span>
            <span className="chat-list-preview">
              {conversation.lastMessagePreview ?? '还没有消息，打个招呼吧'}
            </span>
          </span>
          {conversation.unreadCount > 0 && <Badge tone="accent">{conversation.unreadCount}</Badge>}
        </button>
      ))}
    </>
  );
}
