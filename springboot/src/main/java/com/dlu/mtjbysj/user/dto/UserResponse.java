package com.dlu.mtjbysj.user.dto;

import lombok.*;
import java.time.LocalDateTime;

/** Simple DTO for exposing public user info */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private String username;
    private String nickname;
    private String phone;
    private String address;
    private String avatarUrl;
    private String role;
    private String userType;
    private LocalDateTime createdAt;
}