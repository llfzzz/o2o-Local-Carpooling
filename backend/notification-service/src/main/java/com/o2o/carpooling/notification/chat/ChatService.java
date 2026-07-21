package com.o2o.carpooling.notification.chat;

import com.o2o.carpooling.common.domain.OrderStatus;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.FixedWindowRateLimiter;
import feign.FeignException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passenger–driver chat bound to a legitimate order. Participant identities are derived
 * exclusively from the authenticated principal plus authoritative order/trip records —
 * client-supplied user ids are never accepted. Every read/write requires membership; a
 * non-participant receives 404 {@code CONVERSATION_NOT_FOUND} on every endpoint, so a
 * conversation's existence cannot be probed (same model as the driver-location watch).
 */
@Service
class ChatService {

    private static final int MAX_BODY = 500;
    private static final int MAX_PAGE = 50;
    private static final int SEND_LIMIT_PER_WINDOW = 20;
    private static final Duration SEND_WINDOW = Duration.ofMinutes(1);
    private static final int CREATE_LIMIT_PER_WINDOW = 10;
    private static final Duration CREATE_WINDOW = Duration.ofMinutes(1);
    private static final Duration ORDER_STATUS_CACHE_TTL = Duration.ofSeconds(60);
    /** Orders in these states may not open or continue a conversation. */
    private static final List<OrderStatus> CLOSED_ORDER_STATES = List.of(
        OrderStatus.USER_CANCELLED, OrderStatus.DRIVER_CANCELLED,
        OrderStatus.OPERATOR_CANCELLED, OrderStatus.TIMEOUT_CANCELLED);

    private final ChatRepository repository;
    private final OrderFeignClient orderClient;
    private final TripFeignClient tripClient;
    private final FixedWindowRateLimiter rateLimiter;
    private final Clock clock;
    private final Map<String, CachedOrderStatus> orderStatusCache = new ConcurrentHashMap<>();

    ChatService(
        ChatRepository repository,
        OrderFeignClient orderClient,
        TripFeignClient tripClient,
        FixedWindowRateLimiter rateLimiter,
        Clock clock
    ) {
        this.repository = repository;
        this.orderClient = orderClient;
        this.tripClient = tripClient;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * Open (create-or-return) the conversation for an order. The caller must be the order's
     * rider or the trip's driver; anyone else gets the same 404 as a nonexistent order.
     */
    ChatRepository.Conversation openForOrder(String userId, String orderId) {
        requireUser(userId);
        if (!StringUtils.hasText(orderId)) {
            throw notFound();
        }
        var existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return requireParticipant(existing.get(), userId);
        }
        if (!rateLimiter.allow("chat:create:" + userId, CREATE_LIMIT_PER_WINDOW, CREATE_WINDOW)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "操作过于频繁，请稍后再试");
        }
        OrderFeignClient.OrderInfo order = fetchOrder(orderId);
        TripFeignClient.TripInfo trip = fetchTrip(order.tripId());
        // Membership is decided BEFORE revealing anything about the order's conversation state.
        if (!userId.equals(order.riderId()) && !userId.equals(trip.driverId())) {
            throw notFound();
        }
        if (CLOSED_ORDER_STATES.contains(order.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONVERSATION_UNAVAILABLE",
                "该订单已取消，无法发起会话");
        }
        ChatRepository.Conversation conversation = new ChatRepository.Conversation(
            "conv-" + UUID.randomUUID(),
            order.orderId(),
            order.tripId(),
            trip.driverId(),
            order.riderId(),
            trip.originText(),
            trip.destinationText(),
            "ACTIVE",
            0,
            0,
            null,
            null,
            clock.instant()
        );
        try {
            repository.saveConversation(conversation);
        } catch (DuplicateKeyException raced) {
            // Both participants opened simultaneously; the winner's row is the conversation.
            return repository.findByOrderId(orderId).map(found -> requireParticipant(found, userId))
                .orElseThrow(() -> raced);
        }
        return conversation;
    }

    List<ChatRepository.Conversation> listForUser(String userId, int limit) {
        requireUser(userId);
        return repository.listForUser(userId, clamp(limit));
    }

    long unreadCount(String userId) {
        requireUser(userId);
        return repository.countUnreadForUser(userId);
    }

    long unreadInConversation(ChatRepository.Conversation conversation, String userId) {
        return repository.countUnreadInConversation(conversation, userId);
    }

    ChatRepository.Conversation requireConversation(String userId, String conversationId) {
        requireUser(userId);
        ChatRepository.Conversation conversation = repository.findByConversationId(
                StringUtils.hasText(conversationId) ? conversationId : "")
            .orElseThrow(this::notFound);
        return requireParticipant(conversation, userId);
    }

    List<ChatRepository.Message> messages(String userId, String conversationId, Long beforeId, int limit) {
        ChatRepository.Conversation conversation = requireConversation(userId, conversationId);
        return repository.listMessages(conversation.conversationId(), beforeId, clamp(limit));
    }

    ChatRepository.Message send(String userId, String conversationId, String clientMsgId, String body) {
        ChatRepository.Conversation conversation = requireConversation(userId, conversationId);
        String trimmed = validateBody(body);
        String normalizedClientMsgId = validateClientMsgId(clientMsgId);
        // Idempotent retry of a failed send: same clientMsgId returns the original message.
        var duplicate = repository.findMessageByClientMsgId(conversation.conversationId(), userId, normalizedClientMsgId);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        if (!rateLimiter.allow("chat:send:" + userId, SEND_LIMIT_PER_WINDOW, SEND_WINDOW)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "发送过于频繁，请稍后再试");
        }
        requireOrderStillOpen(conversation.orderId());
        String messageId = "msg-" + UUID.randomUUID();
        Instant now = clock.instant();
        try {
            repository.saveMessage(messageId, conversation.conversationId(), userId, trimmed, normalizedClientMsgId, now);
        } catch (DuplicateKeyException raced) {
            return repository.findMessageByClientMsgId(conversation.conversationId(), userId, normalizedClientMsgId)
                .orElseThrow(() -> raced);
        }
        repository.touchConversation(conversation.conversationId(),
            trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed, now);
        return repository.findMessageByClientMsgId(conversation.conversationId(), userId, normalizedClientMsgId)
            .orElseThrow();
    }

    /** Mark everything up to {@code lastReadMessageId} (or the latest message) as read. */
    long markRead(String userId, String conversationId, Long lastReadMessageId) {
        ChatRepository.Conversation conversation = requireConversation(userId, conversationId);
        long target = lastReadMessageId != null
            ? lastReadMessageId
            : repository.latestMessageId(conversation.conversationId());
        repository.advanceReadCursor(conversation.conversationId(),
            conversation.driverId().equals(userId), target, clock.instant());
        return target;
    }

    /* ---- helpers ---- */

    private ChatRepository.Conversation requireParticipant(ChatRepository.Conversation conversation, String userId) {
        if (!conversation.isParticipant(userId)) {
            throw notFound();
        }
        return conversation;
    }

    /**
     * Sending re-checks the order is still active (60s in-process cache keeps the hot path off
     * Feign). Fail-closed: if order-service is unreachable and no cached status exists, the send
     * is refused — the client's failed-send retry covers transient outages.
     */
    private void requireOrderStillOpen(String orderId) {
        Instant now = clock.instant();
        CachedOrderStatus cached = orderStatusCache.get(orderId);
        OrderStatus status;
        if (cached != null && cached.expiresAt().isAfter(now)) {
            status = cached.status();
        } else {
            try {
                status = fetchOrder(orderId).status();
            } catch (BusinessException notFoundOrDown) {
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_ORDER_UNVERIFIABLE",
                    "暂时无法校验订单状态，请稍后重试");
            }
            orderStatusCache.put(orderId, new CachedOrderStatus(status, now.plus(ORDER_STATUS_CACHE_TTL)));
        }
        if (CLOSED_ORDER_STATES.contains(status)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONVERSATION_UNAVAILABLE",
                "该订单已取消，会话已停用");
        }
    }

    private OrderFeignClient.OrderInfo fetchOrder(String orderId) {
        try {
            return orderClient.order(orderId);
        } catch (FeignException.NotFound missing) {
            throw notFound();
        } catch (FeignException unavailable) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_ORDER_UNVERIFIABLE",
                "暂时无法读取订单信息，请稍后重试");
        }
    }

    private TripFeignClient.TripInfo fetchTrip(String tripId) {
        try {
            return tripClient.trip(tripId);
        } catch (FeignException.NotFound missing) {
            throw notFound();
        } catch (FeignException unavailable) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_ORDER_UNVERIFIABLE",
                "暂时无法读取行程信息，请稍后重试");
        }
    }

    private String validateBody(String body) {
        String trimmed = body == null ? "" : body.strip();
        if (trimmed.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CHAT_MESSAGE_EMPTY", "消息内容不能为空");
        }
        if (trimmed.length() > MAX_BODY) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CHAT_MESSAGE_TOO_LONG",
                "消息长度不能超过 " + MAX_BODY + " 字");
        }
        // Reject control characters except newline: chat is plain text, not markup or terminals.
        for (int i = 0; i < trimmed.length(); i++) {
            char character = trimmed.charAt(i);
            if (character != '\n' && Character.isISOControl(character)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CHAT_MESSAGE_INVALID", "消息包含非法字符");
            }
        }
        return trimmed;
    }

    private String validateClientMsgId(String clientMsgId) {
        String trimmed = clientMsgId == null ? "" : clientMsgId.strip();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CHAT_CLIENT_MSG_ID_INVALID",
                "clientMsgId is required (≤64 chars)");
        }
        return trimmed;
    }

    private void requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "missing authenticated user");
        }
    }

    private int clamp(int limit) {
        return Math.min(Math.max(limit, 1), MAX_PAGE);
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "conversation not found");
    }

    private record CachedOrderStatus(OrderStatus status, Instant expiresAt) {
    }
}
