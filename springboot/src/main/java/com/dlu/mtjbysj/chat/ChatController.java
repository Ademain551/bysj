package com.dlu.mtjbysj.chat;

import com.dlu.mtjbysj.friend.UserFriendshipRepository;
import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

/**
 * 
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ChatController {

    private final ChatRoomRepository roomRepo;
    private final ChatMembershipRepository membershipRepo;
    private final ChatMessageRepository messageRepo;
    private final UserRepository userRepo;
    private final UserFriendshipRepository friendshipRepo;
    private final ChatService chatService;

    @PostMapping("/rooms/direct")
    @Transactional
    public ResponseEntity<?> createOrGetDirect(@RequestBody Map<String, String> body) {
        String userA = Optional.ofNullable(body.get("userA")).map(String::trim).orElse(null);
        String userB = Optional.ofNullable(body.get("userB")).map(String::trim).orElse(null);
        if (userA == null || userB == null) return ResponseEntity.badRequest().body("缺少用户");

        // 先校验用户是否存在，避免后续部分创建导致 500
        Optional<User> ua = userRepo.findByUsername(userA);
        Optional<User> ub = userRepo.findByUsername(userB);
        if (ua.isEmpty() || ub.isEmpty()) {
            String missing = ua.isEmpty() ? userA : userB;
            return ResponseEntity.badRequest().body("用户不存在: " + missing);
        }
        if (!friendshipRepo.existsBetween(ua.get(), ub.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("双方不是好友");
        }

        ChatRoom room = chatService.ensureDirectRoom(ua.get(), ub.get());
        return ResponseEntity.ok(chatService.roomDto(room));
    }

    @PostMapping("/rooms/group")
    public ResponseEntity<?> createGroup(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) body.getOrDefault("members", Collections.emptyList());
        if (name == null || members.isEmpty()) return ResponseEntity.badRequest().body("参数错误");
        ChatRoom room = chatService.createGroupRoom(name);
        for (String uname : members) {
            Optional<User> u = userRepo.findByUsername(uname);
            if (u.isPresent()) {
                chatService.ensureMember(room, u.get());
            }
        }
        return ResponseEntity.ok(chatService.roomDto(room));
    }

    @GetMapping("/rooms")
    public ResponseEntity<?> listRooms(@RequestParam String username) {
        List<ChatRoom> rooms = roomRepo.findByMember(username);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatRoom r : rooms) {
            result.add(chatService.roomDto(r));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> listMessages(@PathVariable Long roomId, @RequestParam(required = false) Integer limit) {
        int n = limit != null ? Math.max(1, Math.min(200, limit)) : 50;
        List<ChatMessage> msgs = messageRepo.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, n));
        Collections.reverse(msgs); // 按时间升序返回
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage m : msgs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("roomId", m.getRoom().getId());
            item.put("sender", m.getSender().getUsername());
            item.put("senderInfo", chatService.userBrief(m.getSender()));
            item.put("content", m.getContent());
            item.put("createdAt", m.getCreatedAt().toString());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable Long roomId, @RequestBody Map<String, String> body) {
        String sender = body.get("sender");
        String content = body.get("content");
        if (sender == null || content == null || content.trim().isEmpty()) return ResponseEntity.badRequest().body("参数错误");

        Optional<User> s = userRepo.findByUsername(sender);
        Optional<ChatRoom> r = roomRepo.findById(roomId);
        if (s.isEmpty() || r.isEmpty()) return ResponseEntity.badRequest().body("用户或房间不存在");
        if (!membershipRepo.existsByRoomIdAndUser_Username(roomId, sender)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("用户不在该房间");
        }
        ChatMessage msg = ChatMessage.builder()
                .room(r.get()).sender(s.get()).content(content.trim()).createdAt(LocalDateTime.now()).build();
        messageRepo.save(msg);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", msg.getId());
        payload.put("roomId", msg.getRoom().getId());
        payload.put("sender", msg.getSender().getUsername());
        payload.put("senderInfo", chatService.userBrief(msg.getSender()));
        payload.put("content", msg.getContent());
        payload.put("createdAt", msg.getCreatedAt().toString());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/messages/{messageId}/recall")
    @Transactional
    public ResponseEntity<?> recallMessage(@PathVariable Long messageId, @RequestBody Map<String, String> body) {
        String username = Optional.ofNullable(body.get("username")).map(String::trim).orElse(null);
        if (username == null || username.isEmpty()) {
            return ResponseEntity.badRequest().body("缺少用户名");
        }

        Optional<ChatMessage> opt = messageRepo.findById(messageId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("消息不存在");
        }
        ChatMessage msg = opt.get();
        Long roomId = msg.getRoom().getId();

        if (!membershipRepo.existsByRoomIdAndUser_Username(roomId, username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("用户不在该房间");
        }
        if (!Objects.equals(msg.getSender().getUsername(), username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("只能撤回自己发送的消息");
        }

        msg.setContent("此消息已撤回");
        messageRepo.save(msg);

        ChatWebSocketHandler handler = ChatWebSocketHandler.getInstance();
        if (handler != null) {
            handler.broadcastMessageRecalled(msg);
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", msg.getId());
        item.put("roomId", roomId);
        item.put("sender", msg.getSender().getUsername());
        item.put("senderInfo", chatService.userBrief(msg.getSender()));
        item.put("content", msg.getContent());
        item.put("createdAt", msg.getCreatedAt().toString());
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/messages/{messageId}")
    @Transactional
    public ResponseEntity<?> deleteMessage(@PathVariable Long messageId, @RequestParam String username) {
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("缺少用户名");
        }

        Optional<ChatMessage> opt = messageRepo.findById(messageId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("消息不存在");
        }
        ChatMessage msg = opt.get();
        Long roomId = msg.getRoom().getId();

        if (!membershipRepo.existsByRoomIdAndUser_Username(roomId, username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("用户不在该房间");
        }
        if (!Objects.equals(msg.getSender().getUsername(), username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("只能删除自己发送的消息");
        }

        Long mid = msg.getId();
        messageRepo.delete(msg);

        ChatWebSocketHandler handler = ChatWebSocketHandler.getInstance();
        if (handler != null && mid != null) {
            handler.broadcastMessageDeleted(roomId, mid);
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/attachments")
    public ResponseEntity<?> uploadAttachment(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("未选择文件");
        }

        String uploadDir = "uploads/chat";
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("无法创建上传目录");
        }

        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("file");
        String ext = "";
        int idx = originalName.lastIndexOf('.');
        if (idx != -1) {
            ext = originalName.substring(idx);
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = Path.of(uploadDir, filename);
        Files.createDirectories(target.getParent());
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String url = "/" + uploadDir.replace('\\', '/') + "/" + filename;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", url);
        out.put("originalName", originalName);
        out.put("size", file.getSize());
        out.put("contentType", file.getContentType());
        return ResponseEntity.ok(out);
    }
}