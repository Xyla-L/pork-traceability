package com.pork.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public final class AuthRequests {
    private AuthRequests() { }

    public record Refresh(@NotBlank String refreshToken) { }
    public record Profile(@Size(max = 64) String nickname, @Size(max = 32) String realName,
                          @Pattern(regexp = "^$|^[0-9+() -]{6,20}$", message = "手机号格式错误") String phone,
                          @Email @Size(max = 128) String email) { }
    public record Password(@NotBlank String oldPassword, @NotBlank @Size(min = 8, max = 64) String newPassword) { }
}
