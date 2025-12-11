package com.dlu.mtjbysj.user;

import com.dlu.mtjbysj.user.dto.ChangePasswordRequest;
import com.dlu.mtjbysj.user.dto.UpdateProfileRequest;
import com.dlu.mtjbysj.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin
@SuppressWarnings("null")
public class UserController {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/{username}")
    public ResponseEntity<?> getUser(@PathVariable String username) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User u = opt.get();
        UserResponse resp = UserResponse.builder()
                .username(u.getUsername())
                .nickname(u.getNickname())
                .phone(u.getPhone())
                .address(u.getAddress())
                .avatarUrl(u.getAvatarUrl())
                .role(u.getRole())
                .userType(u.getUserType())
                .createdAt(u.getCreatedAt())
                .build();
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/experts")
    public ResponseEntity<?> listExperts() {
        List<User> experts = userRepository.findByUserTypeAndEnabledTrue("expert");
        List<Map<String, Object>> items = new ArrayList<>();
        for (User u : experts) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("avatarUrl", u.getAvatarUrl());
            m.put("createdAt", u.getCreatedAt());
            items.add(m);
        }
        return ResponseEntity.ok(items);
    }

    @PostMapping(value = "/{username}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateAvatar(@PathVariable String username,
                                          @RequestPart("avatar") MultipartFile avatar) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (avatar == null || avatar.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("请选择头像文件");
        }
        try {
            String url = storeAvatar(avatar);
            User u = opt.get();
            u.setAvatarUrl(url);
            userRepository.save(u);
            return ResponseEntity.ok(Map.of("avatarUrl", url));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("头像上传失败: " + e.getMessage());
        }
    }

    private String storeAvatar(MultipartFile file) throws IOException {
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
    @PutMapping("/{username}")
    public ResponseEntity<?> updateProfile(@PathVariable String username, @RequestBody UpdateProfileRequest req) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User u = opt.get();
        if (req.getNickname() != null) {
            String nickname = req.getNickname().trim();
            u.setNickname(nickname.isEmpty() ? null : nickname);
        }
        if (req.getPhone() != null) {
            String phone = req.getPhone().trim();
            if (!phone.isEmpty()) {
                if (!phone.matches("\\d{11}")) {
                    return ResponseEntity.badRequest().body("手机号需为11位数字");
                }
                if (userRepository.existsByPhoneAndIdNot(phone, u.getId())) {
                    return ResponseEntity.badRequest().body("手机号已被使用");
                }
            }
            u.setPhone(phone.isEmpty() ? null : phone);
        }
        if (req.getAddress() != null) {
            String address = req.getAddress().trim();
            u.setAddress(address.isEmpty() ? null : address);
        }
        if (req.getAvatarUrl() != null) {
            String avatar = req.getAvatarUrl().trim();
            u.setAvatarUrl(avatar.isEmpty() ? null : avatar);
        }
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/password")
    public ResponseEntity<?> changePassword(@PathVariable String username, @RequestBody ChangePasswordRequest req) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User u = opt.get();
        if (!passwordEncoder.matches(req.getOldPassword(), u.getPasswordHash())) {
            return ResponseEntity.badRequest().body("旧密码不正确");
        }
        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body("新密码至少6位");
        }
        u.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }
}