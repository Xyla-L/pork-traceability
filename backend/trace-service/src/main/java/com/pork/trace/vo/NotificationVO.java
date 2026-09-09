package com.pork.trace.vo;

import java.time.LocalDateTime;

public record NotificationVO(Long id, String title, String content, String type,
                             boolean read, LocalDateTime createTime) {
}
