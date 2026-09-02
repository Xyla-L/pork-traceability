package com.pork.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 获取当前用户信息响应对象
 */
@Data
@Builder
public class GetUserInfoVO {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 角色 */
    private String role;

    /** 手机号 */
    private String phone;

    /** 所属机构 */
    private String orgName;

    /** 状态（0-禁用 1-启用） */
    private Integer status;

    /** 上次登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}