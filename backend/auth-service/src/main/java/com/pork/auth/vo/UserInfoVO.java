package com.pork.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息响应 VO（登录 / 获取当前用户信息 / 更新个人信息 共用）
 */
@Data
@Builder
public class UserInfoVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 所属机构ID
     */
    private Long orgId;

    /**
     * 所属机构名称
     */
    private String orgName;

    /**
     * 角色
     */
    private String role;

    /**
     * 状态（0-禁用 1-启用）
     */
    private Integer status;

    /**
     * 上次登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 权限标识列表
     */
    private List<String> permissions;
}
