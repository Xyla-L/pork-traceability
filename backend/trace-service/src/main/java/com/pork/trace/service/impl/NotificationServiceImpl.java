package com.pork.trace.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.trace.entity.UserNotification;
import com.pork.trace.mapper.UserNotificationMapper;
import com.pork.trace.service.NotificationService;
import com.pork.trace.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final UserNotificationMapper mapper;

    @Override
    public List<NotificationVO> list(Long userId, Boolean read) {
        Integer status = read == null ? null : read ? 1 : 0;
        return mapper.selectList(Wrappers.<UserNotification>lambdaQuery()
                        .eq(UserNotification::getUserId, userId)
                        .eq(status != null, UserNotification::getReadStatus, status)
                        .orderByAsc(UserNotification::getReadStatus)
                        .orderByDesc(UserNotification::getCreateTime)
                        .last("LIMIT 100"))
                .stream().map(row -> new NotificationVO(row.getId(), row.getTitle(), row.getContent(), row.getType(),
                        Integer.valueOf(1).equals(row.getReadStatus()), row.getCreateTime())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        int changed = mapper.update(null, Wrappers.<UserNotification>lambdaUpdate()
                .eq(UserNotification::getId, id).eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getReadStatus, 0)
                .set(UserNotification::getReadStatus, 1).set(UserNotification::getReadTime, LocalDateTime.now()));
        if (changed == 0 && mapper.selectCount(Wrappers.<UserNotification>lambdaQuery()
                .eq(UserNotification::getId, id).eq(UserNotification::getUserId, userId)) == 0) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "通知不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        mapper.update(null, Wrappers.<UserNotification>lambdaUpdate()
                .eq(UserNotification::getUserId, userId).eq(UserNotification::getReadStatus, 0)
                .set(UserNotification::getReadStatus, 1).set(UserNotification::getReadTime, LocalDateTime.now()));
    }
}
