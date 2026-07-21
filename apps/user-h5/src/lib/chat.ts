// Passenger–driver chat hooks (shared by both shells). Realtime is short-interval polling of
// bearer-authenticated endpoints — the same pattern as driver-location (EventSource cannot send
// an Authorization header). All identities are server-derived: the API never accepts user ids,
// and responses expose the counterpart only as a role label plus a per-message `mine` flag.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';
import type { MutationCallbacks } from './queries';

export type ConversationView = {
  conversationId: string;
  orderId: string;
  tripId: string;
  myRole: 'DRIVER' | 'RIDER';
  counterpartLabel: string;
  originText: string;
  destinationText: string;
  status: string;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
  unreadCount: number;
};

export type ChatMessage = {
  messageId: string;
  cursor: number;
  mine: boolean;
  body: string;
  createdAt: string;
};

/** Create-or-return the conversation for one of the caller's orders. */
export function useOpenConversation(cb?: MutationCallbacks<ConversationView>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderId: string) => api<ConversationView>('/api/conversations', {
      method: 'POST',
      body: { orderId }
    }),
    onSuccess: (conversation) => {
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      cb?.onSuccess?.(conversation);
    },
    onError: cb?.onError
  });
}

export function useConversationsQuery() {
  return useQuery({
    queryKey: ['conversations'],
    queryFn: () => api<ConversationView[]>('/api/conversations?limit=50'),
    refetchInterval: 15000
  });
}

export function useChatUnreadQuery() {
  return useQuery({
    queryKey: ['chat-unread'],
    queryFn: () => api<{ unread: number }>('/api/conversations/unread-count'),
    refetchInterval: 15000
  });
}

/** Latest page of a conversation, polled while the window is open. */
export function useMessagesQuery(conversationId: string | null) {
  return useQuery({
    queryKey: ['chat-messages', conversationId],
    queryFn: () => api<ChatMessage[]>(`/api/conversations/${conversationId}/messages?limit=30`),
    enabled: !!conversationId,
    refetchInterval: 5000
  });
}

export async function fetchOlderMessages(conversationId: string, beforeId: number) {
  return api<ChatMessage[]>(`/api/conversations/${conversationId}/messages?beforeId=${beforeId}&limit=30`);
}

/**
 * Send with a caller-supplied stable clientMsgId: a failed send retried with the same id is
 * idempotent server-side, so the retry button can never duplicate a message.
 */
export function useSendChatMessage(conversationId: string | null, cb?: MutationCallbacks<ChatMessage>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { clientMsgId: string; body: string }) =>
      api<ChatMessage>(`/api/conversations/${conversationId}/messages`, {
        method: 'POST',
        body: input
      }),
    onSuccess: (message) => {
      queryClient.invalidateQueries({ queryKey: ['chat-messages', conversationId] });
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      cb?.onSuccess?.(message);
    },
    onError: cb?.onError
  });
}

export function useMarkConversationRead(conversationId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api(`/api/conversations/${conversationId}/read`, { method: 'POST', body: {} }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      queryClient.invalidateQueries({ queryKey: ['chat-unread'] });
    }
  });
}
