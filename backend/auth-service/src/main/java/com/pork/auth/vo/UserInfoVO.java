package com.pork.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息VO
 */
@Data
@Builder
@Schema(description = "用户信息")
public class UserInfoVO {
    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "真实姓名", example = "管理员")
    private String realName;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    private String email;

    private Long orgId;

    private String orgName;

    @Schema(description = "角色", example = "ADMIN")
    private String role;

    private Integer status;

    private LocalDateTime lastLoginTime;

    private List<String> permissions;
}
