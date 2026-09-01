package com.pork.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * SM3密码哈希
     */
    private String password;

    /**
     * 角色：ADMIN/FARMER/SLAUGHTER_OP/DISTRIBUTOR/RETAILER/SUPERVISOR
     */
    private String role;

    /**
     * 手机号
     */
    private String phone;

    /**
     * CA证书编号（监管/屠宰操作员专用）
     */
    private String caCertNo;

    /**
     * 企业/个体名称
     */
    private String orgName;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}