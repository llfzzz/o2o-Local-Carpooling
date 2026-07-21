package com.o2o.carpooling.notification.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Passenger–driver chat API (JWT at the Gateway; identity = injected X-User-Id only).
 *
 * <p>Privacy: responses never expose the counterpart's user id or phone — the view model
 * carries the caller's role and a role label for the other side, and each message carries a
 * server-computed {@code mine} flag instead of sender ids.
 */
@RestController
@RequestMapping("/api/conversations")
class ChatController {

    private final ChatService chatService;

    ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Create-or-return the conversation for one of the caller's orders. */
    @PostMapping
    ConversationView open(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestBody OpenRequest request
    ) {
        ChatRepository.Conversation conversation = chatService.openForOrder(currentUserId, request.orderId());
        return view(conversation, currentUserId);
    }

    @GetMapping
    List<ConversationView> list(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @RequestParam(required = false, defaultValue = "20") int limit
    ) {
        return chatService.listForUser(currentUserId, limit).stream()
            .map(conversation -> view(conversation, currentUserId))
            .toList();
    }

    @GetMapping("/unread-count")
    UnreadCount unreadCount(@RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        return new UnreadCount(chatService.unreadCount(currentUserId));
    }

    @GetMapping("/{conversationId}/messages")
    List<MessageView> messages(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String conversationId,
        @RequestParam(required = false) Long beforeId,
        @RequestParam(required = false, defaultValue = "30") int limit
    ) {
        return chatService.messages(currentUserId, conversationId, beforeId, limit).stream()
            .map(message -> messageView(message, currentUserId))
            .toList();
    }

    @PostMapping("/{conversationId}/messages")
    MessageView send(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String conversationId,
        @RequestBody SendRequest request
    ) {
        return messageView(chatService.send(currentUserId, conversationId, request.clientMsgId(), request.body()),
            currentUserId);
    }

    @PostMapping("/{conversationId}/read")
    ReadResponse markRead(
        @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
        @PathVariable String conversationId,
        @RequestBody(required = false) ReadRequest request
    ) {
        Long lastReadMessageId = request == null ? null : request.lastReadMessageId();
        return new ReadResponse(conversationId, chatService.markRead(currentUserId, conversationId, lastReadMessageId));
    }

    private ConversationView view(ChatRepository.Conversation conversation, String currentUserId) {
        boolean isDriver = conversation.driverId().equals(currentUserId);
        return new ConversationView(
            conversation.conversationId(),
            conversation.orderId(),
            conversation.tripId(),
            isDriver ? "DRIVER" : "RIDER",
            isDriver ? "乘客" : "司机",
            conversation.originText(),
            conversation.destinationText(),
            conversation.status(),
            conversation.lastMessageAt(),
            conversation.lastMessagePreview(),
            chatService.unreadInConversation(conversation, currentUserId)
        );
    }

    private MessageView messageView(ChatRepository.Message message, String currentUserId) {
        return new MessageView(
            message.messageId(),
            message.cursor(),
            message.senderId().equals(currentUserId),
            message.body(),
            message.createdAt()
        );
    }

    record OpenRequest(String orderId) {
    }

    record SendRequest(String clientMsgId, String body) {
    }

    record ReadRequest(Long lastReadMessageId) {
    }

    record ReadResponse(String conversationId, long lastReadMessageId) {
    }

    record UnreadCount(long unread) {
    }

    record ConversationView(
        String conversationId,
        String orderId,
        String tripId,
        String myRole,
        String counterpartLabel,
        String originText,
        String destinationText,
        String status,
        Instant lastMessageAt,
        String lastMessagePreview,
        long unreadCount
    ) {
    }

    record MessageView(
        String messageId,
        long cursor,
        boolean mine,
        String body,
        Instant createdAt
    ) {
    }
}
