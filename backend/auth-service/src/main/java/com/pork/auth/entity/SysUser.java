package com.pork.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    // ✅ 关键：告诉 MP，这个 Java 字段对应数据库的 password_hash 列
    @TableField("password_hash")
    private String password;

    private String role;

    private String phone;

    // ✅ 数据库里没有 ca_cert_no 这列，告诉 MP 忽略它
    @TableField(exist = false)
    private String caCertNo;

    // ✅ 数据库里没有 org_name 这列，实际叫 org_id
    @TableField("org_id")
    private String orgName;

    // ✅ 数据库有 real_name 列，补上这个字段
    @TableField("real_name")
    private String realName;

    private Integer status;

    // ✅ 数据库有 last_login_time 列，补上
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    // ✅ 数据库有 create_time 列，补上
    @TableField("create_time")
    private LocalDateTime createTime;

    // ✅ 数据库有 update_time 列，补上
    @TableField("update_time")
    private LocalDateTime updateTime;
}