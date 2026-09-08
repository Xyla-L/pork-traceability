package com.pork.trace.service;

import com.pork.trace.vo.NotificationVO;

import java.util.List;

public interface NotificationService {
    List<NotificationVO> list(Long userId, Boolean read);
    void markRead(Long userId, Long id);
    void markAllRead(Long userId);
}
