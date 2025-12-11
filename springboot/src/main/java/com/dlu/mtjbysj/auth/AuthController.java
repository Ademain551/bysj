package com.dlu.mtjbysj.auth;

import com.dlu.mtjbysj.user.User;
import com.dlu.mtjbysj.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin
@SuppressWarnings("null")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterRequest req) {
        String username = Optional.ofNullable(req.getUsername()).orElse("").trim();
        if (!username.matches("\\d{8}")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "账号需为8位数字"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "账号已存在"));
        }
        Optional<String> normalizedType = normalizeUserType(req.getUserType());
        if (normalizedType.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "用户类型无效"));
        }
        String type = normalizedType.get();
        String nickname = Optional.ofNullable(req.getNickname()).map(String::trim).orElse("");
        if (nickname.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "昵称不能为空"));
        }
        String phone = Optional.ofNullable(req.getPhone()).map(String::trim).orElse("");
        if (!phone.matches("\\d{11}")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "手机号需为11位数字"));
        }
        if (userRepository.existsByPhone(phone)) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "手机号已被使用"));
        }
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .createdAt(LocalDateTime.now())
                .nickname(nickname)
                .phone(phone)
                .userType(type)
                .role("user")
                .build();
        userRepository.save(user);
        return ResponseEntity.ok(new ApiResponse(true, "注册成功", userPayload(user)));
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> registerMultipart(@RequestParam("username") String username,
                                                         @RequestParam("password") String password,
                                                         @RequestParam("userType") String userType,
                                                         @RequestParam("nickname") String nickname,
                                                         @RequestParam("phone") String phone,
                                                         @RequestParam(value = "avatar", required = false) MultipartFile avatar) {
        username = Optional.ofNullable(username).orElse("").trim();
        if (!username.matches("\\d{8}")) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "账号需为8位数字"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "账号已存在"));
        }
        Optional<String> normalizedType = normalizeUserType(userType);
        if (normalizedType.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "用户类型无效"));
        }
        String type = normalizedType.get();
        nickname = Optional.ofNullable(nickname).map(String::trim).orElse("");
        if (nickname.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "昵称不能为空"));
        }
        phone = Optional.ofNullable(phone).map(String::trim).orElse("");
        if (!phone.matches("\\d{11}")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "手机号需为11位数字"));
        }
        if (userRepository.existsByPhone(phone)) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "手机号已被使用"));
        }

        String avatarUrl = null;
        try {
            if (avatar != null && !avatar.isEmpty()) {
                avatarUrl = storeAvatar(avatar);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse(false, "头像上传失败: " + e.getMessage()));
        }

        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .createdAt(LocalDateTime.now())
                .nickname(nickname)
                .phone(phone)
                .userType(type)
                .role("user")
                .avatarUrl(avatarUrl)
                .build();
        userRepository.save(user);
        return ResponseEntity.ok(new ApiResponse(true, "注册成功", userPayload(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String loginId = Optional.ofNullable(req.getUsername()).orElse("").trim();
        Optional<User> opt;
        if (loginId.matches("\\d{11}")) {
            opt = userRepository.findByPhone(loginId);
        } else {
            opt = userRepository.findByUsername(loginId);
        }

        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "账号或密码错误"));
        }

        User user = opt.get();
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "账号或密码错误"));
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("LOGIN_USER", userPayload(user));

        return ResponseEntity.ok(new ApiResponse(true, "登录成功", userPayload(user)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(new ApiResponse(true, "已退出登录"));
    }

    private Optional<String> normalizeUserType(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "expert", "农林专家", "expertise" -> Optional.of("expert");
            case "farmer", "种植户", "planter" -> Optional.of("farmer");
            default -> Optional.empty();
        };
    }

    private Map<String, Object> userPayload(User u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", u.getUsername());
        map.put("role", u.getRole());
        map.put("userType", u.getUserType());
        map.put("nickname", Optional.ofNullable(u.getNickname()).orElse(""));
        map.put("avatarUrl", Optional.ofNullable(u.getAvatarUrl()).orElse(""));
        map.put("phone", Optional.ofNullable(u.getPhone()).orElse(""));
        map.put("createdAt", u.getCreatedAt());
        return map;
    }

    private String storeAvatar(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("空文件");
        }
        String ct = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        boolean ok = ct.startsWith("image/");
        if (!ok) {
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
            ok = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp");
        }
        if (!ok) {
            throw new IllegalArgumentException("仅支持图片格式: jpg, jpeg, png, gif, webp, bmp");
        }
        Path dir = Path.of("uploads", "avatars");
        Files.createDirectories(dir);
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("avatar");
        String ext = "";
        int idx = original.lastIndexOf('.');
        if (idx != -1) {
            ext = original.substring(idx).toLowerCase(Locale.ROOT);
        } else {
            if (ct.contains("jpeg")) ext = ".jpg";
            else if (ct.contains("png")) ext = ".png";
            else if (ct.contains("gif")) ext = ".gif";
            else if (ct.contains("webp")) ext = ".webp";
            else if (ct.contains("bmp")) ext = ".bmp";
        }
        String filename = UUID.randomUUID().toString().replaceAll("-", "") + (ext.isEmpty() ? ".jpg" : ext);
        Path target = dir.resolve(filename);
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return "/uploads/avatars/" + filename;
    }
}