package com.pork.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SystemRequests {
    private SystemRequests() { }

    public record UserCreate(@NotBlank @Size(min = 3, max = 32) String username,
                             @NotBlank @Size(min = 8, max = 64) String password,
                             String nickname, String realName, String phone, @Email String email,
                             Long orgId, @NotBlank String role, Integer status) { }
    public record UserUpdate(String nickname, String realName, String phone, @Email String email,
                             Long orgId, String role, Integer status) { }
    public record Status(@NotNull @Min(0) @Max(1) Integer status) { }
    public record OrgCreate(Long parentId, @NotBlank String type, @NotBlank String name,
                            String manager, String phone, String address, String remark) { }

    /**
     * 业务档案（养殖场等）→ 机构表的同步请求。
     * <p>{@code previousName} 用于「改名」场景：按旧名定位已有机构，再改成新名；
     * 为空表示按 {@code name} 定位（新增或覆盖更新）。</p>
     */
    public record SyncOrg(@NotBlank String type,
                          @NotBlank String name,
                          String previousName,
                          String manager,
                          String phone,
                          String address,
                          String remark) { }
}
