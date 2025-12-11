package com.dlu.mtjbysj.user.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    private String nickname;
    private String phone;
    private String address;
    private String avatarUrl;
}