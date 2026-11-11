package com.pork.trace.vo;

import java.time.LocalDateTime;

/**
 * 通知视图。
 * <p>
 * {@code bizType}/{@code bizId} 是<b>通知指向哪条业务</b>的唯一依据：前端据此决定点开跳到哪一页，
 * 而不是去猜标题里的中文。只带 type（warning/info/success）不够 —— 那是严重程度分类，
 * 不是业务归属。
 */
public record NotificationVO(Long id, String title, String content, String type,
                             boolean read, LocalDateTime createTime,
                             String bizType, Long bizId) {
}
