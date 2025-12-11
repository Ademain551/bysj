package com.dlu.mtjbysj.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Pattern(regexp = "\\d{8}", message = "账号需为8位数字")
    private String username; // 账号，8位数字

    @NotBlank
    @Size(min = 6, max = 128)
    private String password;

    @NotBlank
    private String userType;

    @NotBlank
    @Size(min = 1, max = 64)
    private String nickname;

    @NotBlank
    @Pattern(regexp = "\\d{11}", message = "手机号需为11位数字")
    private String phone;
}