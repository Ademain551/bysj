package com.dlu.mtjbysj.chat;

import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ChatService {

    private final ChatRoomRepository roomRepo;
    private final ChatMembershipRepository membershipRepo;
    private final ChatMessageRepository messageRepo;
    private final UserRepository userRepo;

    public String buildDirectKey(String a, String b) {
        if (a == null || b == null) {
            return null;
        }
        return (a.compareTo(b) <= 0) ? (a + "|" + b) : (b + "|" + a);
    }

    @Transactional
    public ChatRoom ensureDirectRoom(User userA, User userB) {
        if (userA == null || userB == null) {
            throw new IllegalArgumentException("users must not be null");
        }
        String key = buildDirectKey(userA.getUsername(), userB.getUsername());
        Optional<ChatRoom> existing = key != null ? roomRepo.findByDirectKey(key) : Optional.empty();
        ChatRoom room = existing.orElseGet(() -> createDirectRoom(key));
        ensureMember(room, userA);
        ensureMember(room, userB);
        return room;
    }

    @Transactional
    public ChatRoom createGroupRoom(String name) {
        ChatRoom room = ChatRoom.builder()
                .type("group")
                .name(name)
                .createdAt(LocalDateTime.now())
                .build();
        return roomRepo.save(room);
    }

    @Transactional
    public void ensureMember(ChatRoom room, User user) {
        if (room == null || user == null) {
            return;
        }
        if (!membershipRepo.existsByRoomIdAndUser_Id(room.getId(), user.getId())) {
            ChatMembership membership = ChatMembership.builder()
                    .room(room)
                    .user(user)
                    .joinedAt(LocalDateTime.now())
                    .build();
            try {
                membershipRepo.save(membership);
            } catch (DataIntegrityViolationException ignore) {
                // ignore duplicate membership caused by race condition
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> roomDto(ChatRoom room) {
        if (room == null) {
            return Collections.emptyMap();
        }
        List<ChatMembership> memberships = membershipRepo.findByRoomId(room.getId());
        List<Map<String, Object>> members = memberships.stream()
                .map(ChatMembership::getUser)
                .map(this::userBrief)
                .toList();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", room.getId());
        map.put("type", room.getType());
        map.put("name", room.getName());
        // 标记系统通知会话：群聊且名称为“系统通知”
        map.put("system", "group".equals(room.getType()) && "系统通知".equals(room.getName()));
        map.put("createdAt", room.getCreatedAt());
        map.put("members", members);
        return map;
    }

    public Map<String, Object> userBrief(User user) {
        if (user == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("nickname", Optional.ofNullable(user.getNickname()).orElse(""));
        map.put("role", user.getRole());
        map.put("userType", user.getUserType());
        map.put("avatarUrl", Optional.ofNullable(user.getAvatarUrl()).orElse(""));
        map.put("phone", Optional.ofNullable(user.getPhone()).orElse(""));
        return map;
    }

    private ChatRoom createDirectRoom(String key) {
        ChatRoom room = ChatRoom.builder()
                .type("direct")
                .directKey(key)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            return roomRepo.save(room);
        } catch (DataIntegrityViolationException ex) {
            return roomRepo.findByDirectKey(key).orElseThrow(() -> ex);
        }
    }

    /**
     * 确保存在一个名为“系统通知”的群聊房间，用于系统级通知。
     */
    @Transactional
    public ChatRoom ensureSystemNotificationRoom() {
        Optional<ChatRoom> existing = roomRepo.findByTypeAndName("group", "系统通知");
        if (existing.isPresent()) {
            return existing.get();
        }
        ChatRoom room = ChatRoom.builder()
                .type("group")
                .name("系统通知")
                .createdAt(LocalDateTime.now())
                .build();
        return roomRepo.save(room);
    }

    /**
     * 向所有用户发送一条系统通知消息（通过“系统通知”群聊）。
     */
    @Transactional
    public void sendSystemNotificationToAllUsers(String title, String content) {
        ChatRoom room = ensureSystemNotificationRoom();

        List<User> users = userRepo.findAll();
        if (users.isEmpty()) {
            return;
        }
        // 确保所有用户都是系统通知房间的成员
        for (User u : users) {
            ensureMember(room, u);
        }

        // 选择一个合适的发送者（优先 admin 用户）
        User sender = userRepo.findByUsername("admin").orElseGet(() ->
                users.stream()
                        .filter(u -> "admin".equalsIgnoreCase(u.getRole()))
                        .findFirst()
                        .orElse(users.get(0))
        );

        StringBuilder sb = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) {
            sb.append("【系统通知】").append(title.trim());
            if (content != null && !content.trim().isEmpty()) {
                sb.append("\n").append(content.trim());
            }
        } else if (content != null && !content.trim().isEmpty()) {
            sb.append("【系统通知】").append(content.trim());
        } else {
            return;
        }

        ChatMessage msg = ChatMessage.builder()
                .room(room)
                .sender(sender)
                .content(sb.toString())
                .createdAt(LocalDateTime.now())
                .build();
        messageRepo.save(msg);

        // 如果有在线 WebSocket 连接，则通过 WS 实时推送这条系统通知
        ChatWebSocketHandler handler = ChatWebSocketHandler.getInstance();
        if (handler != null) {
            handler.broadcastPersistedMessage(msg);
        }
    }
}
