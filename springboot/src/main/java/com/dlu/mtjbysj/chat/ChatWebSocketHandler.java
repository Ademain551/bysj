package com.dlu.mtjbysj.chat;

import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 处理器，负责处理 WebSocket 连接和消息。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatRoomRepository roomRepo;
    private final ChatMembershipRepository membershipRepo;
    private final ChatMessageRepository messageRepo;
    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    // username -> sessions (支持一个用户多个连接)
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    // 静态实例，方便非 WebSocket 代码（如 ChatService）在发送系统通知后进行广播
    private static ChatWebSocketHandler instance;

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static ChatWebSocketHandler getInstance() {
        return instance;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String username = getUsername(session);
        if (username == null) {
            session.close();
            return;
        }
        userSessions.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WS connected: {}", username);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull org.springframework.web.socket.CloseStatus status) throws Exception {
        String username = getUsername(session);
        if (username != null) {
            Set<WebSocketSession> set = userSessions.get(username);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) userSessions.remove(username);
            }
            log.info("WS disconnected: {}", username);
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String username = getUsername(session);
        if (username == null) return;
        Map<String, Object> payloadMap = parseJson(message.getPayload());
        Long roomId = asLong(payloadMap.get("roomId"));
        String content = (String) payloadMap.get("content");
        if (roomId == null || content == null || content.trim().isEmpty()) return;

        // 保存消息
        var senderOpt = userRepo.findByUsername(username);
        if (senderOpt.isEmpty()) return;
        var roomOpt = roomRepo.findById(roomId);
        if (roomOpt.isEmpty()) return;
        if (!membershipRepo.existsByRoomIdAndUser_Username(roomId, username)) {
            return;
        }
        User sender = senderOpt.get();
        ChatMessage msg = ChatMessage.builder()
                .room(roomOpt.get())
                .sender(sender)
                .content(content.trim())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepo.save(msg);

        // 广播到房间成员
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("id", msg.getId());
        outbound.put("type", "message");
        outbound.put("roomId", roomId);
        outbound.put("sender", username);
        outbound.put("senderInfo", chatService.userBrief(sender));
        outbound.put("content", msg.getContent());
        outbound.put("createdAt", msg.getCreatedAt().toString());
        broadcastToRoom(roomId, outbound);
    }

    /**
     * 供业务代码在保存 ChatMessage 之后调用，用于将已持久化的消息广播给对应房间的所有在线成员。
     */
    public void broadcastPersistedMessage(ChatMessage msg) {
        if (msg == null || msg.getRoom() == null || msg.getSender() == null) {
            return;
        }
        Long roomId = msg.getRoom().getId();
        String username = msg.getSender().getUsername();
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("id", msg.getId());
        outbound.put("type", "message");
        outbound.put("roomId", roomId);
        outbound.put("sender", username);
        outbound.put("senderInfo", chatService.userBrief(msg.getSender()));
        outbound.put("content", msg.getContent());
        outbound.put("createdAt", msg.getCreatedAt().toString());
        broadcastToRoom(roomId, outbound);
    }

    /**
     * 广播一条消息已被撤回的事件。
     */
    public void broadcastMessageRecalled(ChatMessage msg) {
        if (msg == null || msg.getRoom() == null || msg.getId() == null) {
            return;
        }
        Long roomId = msg.getRoom().getId();
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "recall");
        outbound.put("roomId", roomId);
        outbound.put("messageId", msg.getId());
        outbound.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
        outbound.put("content", msg.getContent());
        broadcastToRoom(roomId, outbound);
    }

    /**
     * 广播一条消息已被删除的事件。
     */
    public void broadcastMessageDeleted(Long roomId, Long messageId) {
        if (roomId == null || messageId == null) {
            return;
        }
        Map<String, Object> outbound = new LinkedHashMap<>();
        outbound.put("type", "delete");
        outbound.put("roomId", roomId);
        outbound.put("messageId", messageId);
        broadcastToRoom(roomId, outbound);
    }

    private void broadcastToRoom(Long roomId, Map<String, Object> data) {
        List<ChatMembership> members = membershipRepo.findByRoomId(roomId);
        for (ChatMembership m : members) {
            String uname = m.getUser().getUsername();
            Set<WebSocketSession> sessions = userSessions.getOrDefault(uname, Collections.emptySet());
            for (WebSocketSession s : sessions) {
                try {
                    s.sendMessage(new TextMessage(toJson(data)));
                } catch (IOException e) {
                    log.warn("WS send failed to {}: {}", uname, e.getMessage());
                }
            }
        }
    }

    private String getUsername(WebSocketSession session) {
        java.net.URI uri = session.getUri();
        String query = (uri != null) ? uri.getQuery() : null;
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=");
            if (kv.length == 2 && Objects.equals(kv[0], "username")) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // 使用 Jackson 解析/生成 JSON，兼容复杂内容
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String text) {
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception e) {
            log.warn("WS parse json failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private @NonNull String toJson(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            if (json == null) {
                return "{}";
            }
            return json;
        } catch (Exception e) {
            return "{}";
        }
    }

    private Long asLong(Object o) {
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return o != null ? Long.parseLong(o.toString()) : null; } catch (Exception e) { return null; }
    }

}