package com.pork.trace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 内部接口：给一批用户推同一条通知。
 * <p>
 * 通知是「谁的事」写死在收件人上，所以调用方必须先解析出 userIds（销售服务按机构/角色查），
 * 本服务不做「猜谁该收到」——猜错就是漏通知，比不发更糟。
 * <p>
 * bizType 决定点开去哪一页（前端 notificationLink 按它定落点），不许用标题里的中文猜。
 */
@Data
public class NotificationCreate {

    @NotEmpty(message = "通知必须有收件人")
    private List<Long> userIds;

    @NotBlank(message = "通知标题不能为空")
    private String title;

    private String content;

    /** 通知类型：SYSTEM / BUSINESS / ALERT（沿用现有 type 列口径） */
    private String type;

    /** 业务类型：决定点击落点（如 recall_store / recall_center） */
    private String bizType;

    /** 业务主键（召回单 id） */
    private Long bizId;
}
