package com.pork.trace.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.trace.dto.NotificationCreate;
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
                        Integer.valueOf(1).equals(row.getReadStatus()), row.getCreateTime(),
                        row.getBizType(), row.getBizId())).toList();
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

    /**
     * 内部接口：给一批用户推同一条通知。
     * <p>
     * 一条一行地写：收件人是「谁的待办」，不是一条广播 —— 通知中心按 userId 查，
     * 共用一个收件人列表就没法分别标已读。写失败直接抛，让调用方决定要不要重试
     * （召回的池子已经落库，通知失败不该把召回整单回滚，所以调用方那边是尽力而为）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createAll(NotificationCreate request) {
        if (request == null || request.getUserIds() == null || request.getUserIds().isEmpty()) return 0;
        LocalDateTime now = LocalDateTime.now();
        int written = 0;
        for (Long userId : request.getUserIds().stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            UserNotification row = new UserNotification();
            row.setUserId(userId);
            row.setTitle(request.getTitle());
            row.setContent(request.getContent());
            row.setType(request.getType() == null ? "BUSINESS" : request.getType());
            row.setReadStatus(0);
            row.setBizType(request.getBizType());
            row.setBizId(request.getBizId());
            row.setCreateTime(now);
            mapper.insert(row);
            written++;
        }
        return written;
    }
}
