import { useEffect, useMemo, useRef, useState } from 'react';
import { Badge, Button } from '@fj';
import { RefreshCw, Send, X } from 'lucide-react';
import { formatClock } from '../../lib/format';
import {
  fetchOlderMessages,
  useMarkConversationRead,
  useMessagesQuery,
  useSendChatMessage
} from '../../lib/chat';
import type { ChatMessage, ConversationView } from '../../lib/chat';

type PendingMessage = { clientMsgId: string; body: string; status: 'sending' | 'failed' };

/**
 * Shared passenger–driver chat window (mobile overlay + desktop pane). Send status is explicit:
 * a failed send stays visible with a retry button that reuses the same clientMsgId, which the
 * server dedupes — retrying can never double-send.
 */
export function ChatWindow({ conversation, onClose }: { conversation: ConversationView; onClose: () => void }) {
  const [draft, setDraft] = useState('');
  const [pending, setPending] = useState<PendingMessage[]>([]);
  const [older, setOlder] = useState<ChatMessage[]>([]);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const messagesQuery = useMessagesQuery(conversation.conversationId);
  const markRead = useMarkConversationRead(conversation.conversationId);

  const send = useSendChatMessage(conversation.conversationId, {
    onSuccess: (message) => {
      setPending((current) => current.filter((entry) => entry.body !== message.body || entry.status !== 'sending'));
    }
  });

  const latest = useMemo(() => messagesQuery.data ?? [], [messagesQuery.data]);
  const messages = useMemo(() => {
    const seen = new Set(latest.map((message) => message.messageId));
    return [...older.filter((message) => !seen.has(message.messageId)), ...latest];
  }, [older, latest]);

  // Confirmed sends drop their pending twins (poll may confirm before the mutation returns).
  useEffect(() => {
    if (latest.length === 0) return;
    setPending((current) => current.filter(
      (entry) => entry.status === 'failed' || !latest.some((message) => message.mine && message.body === entry.body)
    ));
  }, [latest]);

  // Opening the window (and every new incoming page) marks the conversation read.
  useEffect(() => {
    if (conversation.unreadCount > 0 || latest.length > 0) {
      markRead.mutate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.conversationId, latest.length]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [messages.length, pending.length]);

  const sendDraft = () => {
    const body = draft.trim();
    if (!body || send.isPending) return;
    const clientMsgId = crypto.randomUUID();
    setPending((current) => [...current, { clientMsgId, body, status: 'sending' }]);
    setDraft('');
    send.mutate({ clientMsgId, body }, {
      onError: () => setPending((current) => current.map(
        (entry) => entry.clientMsgId === clientMsgId ? { ...entry, status: 'failed' } : entry
      ))
    });
  };

  const retry = (entry: PendingMessage) => {
    setPending((current) => current.map(
      (item) => item.clientMsgId === entry.clientMsgId ? { ...item, status: 'sending' } : item
    ));
    // Same clientMsgId: idempotent on the server, so a late-landing first attempt is harmless.
    send.mutate({ clientMsgId: entry.clientMsgId, body: entry.body }, {
      onSuccess: () => setPending((current) => current.filter((item) => item.clientMsgId !== entry.clientMsgId)),
      onError: () => setPending((current) => current.map(
        (item) => item.clientMsgId === entry.clientMsgId ? { ...item, status: 'failed' } : item
      ))
    });
  };

  const loadOlder = async () => {
    const oldest = messages[0];
    if (!oldest || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const page = await fetchOlderMessages(conversation.conversationId, oldest.cursor);
      setOlder((current) => [...page, ...current]);
    } finally {
      setLoadingOlder(false);
    }
  };

  return (
    <div className="chat-window">
      <header className="chat-head">
        <div className="chat-head-copy">
          <strong>与{conversation.counterpartLabel}的会话</strong>
          <span className="chat-head-route">{conversation.originText} → {conversation.destinationText}</span>
        </div>
        <button className="chat-close" aria-label="关闭会话" onClick={onClose}>
          <X size={18} />
        </button>
      </header>

      <div className="chat-scroll" ref={scrollRef}>
        {messages.length >= 30 && (
          <button className="chat-load-older" disabled={loadingOlder} onClick={() => void loadOlder()}>
            {loadingOlder ? '加载中…' : '加载更早的消息'}
          </button>
        )}
        {messagesQuery.isLoading && <div className="chat-empty">加载中…</div>}
        {!messagesQuery.isLoading && messages.length === 0 && pending.length === 0 && (
          <div className="chat-empty">打个招呼，和{conversation.counterpartLabel}确认上车信息吧。</div>
        )}
        {messages.map((message) => (
          <div key={message.messageId} className={`chat-bubble-row${message.mine ? ' mine' : ''}`}>
            <div className="chat-bubble">
              <span className="chat-bubble-body">{message.body}</span>
              <span className="chat-bubble-time">{formatClock(message.createdAt)}</span>
            </div>
          </div>
        ))}
        {pending.map((entry) => (
          <div key={entry.clientMsgId} className="chat-bubble-row mine">
            <div className={`chat-bubble pending${entry.status === 'failed' ? ' failed' : ''}`}>
              <span className="chat-bubble-body">{entry.body}</span>
              <span className="chat-bubble-time">
                {entry.status === 'failed' ? (
                  <>
                    <Badge tone="danger">发送失败</Badge>
                    <Button size="sm" variant="ghost" iconLeft={<RefreshCw size={12} />} onClick={() => retry(entry)}>
                      重试
                    </Button>
                  </>
                ) : '发送中…'}
              </span>
            </div>
          </div>
        ))}
      </div>

      <footer className="chat-input-row">
        <input
          className="chat-input"
          value={draft}
          maxLength={500}
          placeholder="输入消息（最多 500 字）"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault();
              sendDraft();
            }
          }}
        />
        <Button variant="primary" size="sm" iconLeft={<Send size={14} />} disabled={!draft.trim()} onClick={sendDraft}>
          发送
        </Button>
      </footer>
    </div>
  );
}
