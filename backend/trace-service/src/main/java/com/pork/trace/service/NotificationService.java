package com.pork.trace.service;

import com.pork.trace.dto.NotificationCreate;
import com.pork.trace.vo.NotificationVO;

import java.util.List;

public interface NotificationService {
    List<NotificationVO> list(Long userId, Boolean read);
    void markRead(Long userId, Long id);
    void markAllRead(Long userId);

    /**
     * 内部接口：给一批用户推同一条通知（召回/拒收这类「有事找谁」的业务通知走这里）。
     * 返回实际写入条数；userIds 为空时返回 0，不报错 —— 没有收件人不是异常，是配置还没到位。
     */
    int createAll(NotificationCreate request);
}
