package com.pork.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_org")
public class SysOrg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String type;
    private String name;
    private String manager;
    private String phone;
    private String address;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
