package com.dlu.mtjbysj.user;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        // 创建/更新系统管理员账户 admin601
        userRepository.findByUsername("admin601").ifPresentOrElse(user -> {
            boolean changed = false;
            // 更新密码为 88888888
            if (!passwordEncoder.matches("88888888", user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode("88888888"));
                changed = true;
            }
            if (user.getCreatedAt() == null) {
                user.setCreatedAt(LocalDateTime.now());
                changed = true;
            }
            if (user.getNickname() == null || user.getNickname().isBlank()) {
                user.setNickname("系统管理员");
                changed = true;
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                user.setEmail("admin601@example.com");
                changed = true;
            }
            if (!"admin".equals(user.getRole())) {
                user.setRole("admin");
                changed = true;
            }
            if (!"admin".equals(user.getUserType())) {
                user.setUserType("admin");
                changed = true;
            }
            if (!user.isEnabled()) {
                user.setEnabled(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
                System.out.println("已更新系统管理员账户: admin601");
            }
        }, () -> {
            // 创建新的管理员账户
            User admin = User.builder()
                    .username("admin601")
                    .passwordHash(passwordEncoder.encode("88888888"))
                    .createdAt(LocalDateTime.now())
                    .nickname("系统管理员")
                    .email("admin601@example.com")
                    .role("admin")
                    .userType("admin")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            System.out.println("已创建系统管理员账户: admin601 (密码: 88888888)");
        });
    }
}
