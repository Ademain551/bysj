package com.dlu.mtjbysj.friend;

import com.dlu.mtjbysj.auth.ApiResponse;
import com.dlu.mtjbysj.chat.ChatRoom;
import com.dlu.mtjbysj.chat.ChatService;
import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
@SuppressWarnings("null")
public class FriendController {

    private final UserRepository userRepository;
    private final UserFriendshipRepository friendshipRepository;
    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<?> listFriends(@RequestParam String username) {
        Optional<User> requester = userRepository.findByUsername(username.trim());
        if (requester.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("用户不存在");
        }
        List<User> friends;
        try {
            friends = friendshipRepository.findFriendsOf(requester.get());
        } catch (Exception e) {
            log.error("查询好友列表失败: {}", e.getMessage(), e);
            friends = Collections.emptyList();
        }
        List<Map<String, Object>> payload = friends.stream()
                .map(chatService::userBrief)
                .toList();
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/add")
    @Transactional
    public ResponseEntity<ApiResponse> addFriend(@RequestBody Map<String, String> body) {
        String requesterName = Optional.ofNullable(body.get("requester")).map(String::trim).orElse("");
        String targetKey = Optional.ofNullable(body.get("target")).map(String::trim).orElse("");
        if (requesterName.isEmpty() || targetKey.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "请求参数不完整"));
        }
        Optional<User> requesterOpt = userRepository.findByUsername(requesterName);
        if (requesterOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "请求用户不存在"));
        }
        User requester = requesterOpt.get();
        Optional<User> targetOpt;
        if (targetKey.matches("\\d{11}")) {
            targetOpt = userRepository.findByPhone(targetKey);
        } else {
            targetOpt = userRepository.findByUsername(targetKey);
        }
        if (targetOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(false, "未找到目标用户（请使用账号或手机号）"));
        }
        User target = targetOpt.get();
        if (Objects.equals(requester.getId(), target.getId())) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "不能添加自己为好友"));
        }

        User first = requester.getId() < target.getId() ? requester : target;
        User second = first == requester ? target : requester;

        boolean alreadyFriends = friendshipRepository.existsBetween(requester, target);
        if (!alreadyFriends) {
            friendshipRepository.save(UserFriendship.builder()
                    .user(first)
                    .friend(second)
                    .build());
        }

        ChatRoom room = chatService.ensureDirectRoom(requester, target);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("friend", chatService.userBrief(target));
        payload.put("roomId", room.getId());
        String message = alreadyFriends ? "已是好友" : "添加好友成功";
        return ResponseEntity.ok(new ApiResponse(true, message, payload));
    }
}
