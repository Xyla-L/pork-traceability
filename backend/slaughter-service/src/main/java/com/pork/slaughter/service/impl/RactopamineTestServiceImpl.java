package com.pork.slaughter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.source.ManualOperator;
import com.pork.core.source.TraceStage;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.entity.RactopamineTest;
import com.pork.slaughter.mapper.RactopamineTestMapper;
import com.pork.slaughter.service.RactopamineTestService;
import com.pork.slaughter.support.SlaughterStatus;
import com.pork.slaughter.vo.RactopamineTestVO;
import com.pork.web.source.SourceGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RactopamineTestServiceImpl extends ServiceImpl<RactopamineTestMapper, RactopamineTest>
        implements RactopamineTestService {

    /** 人工录入来源标记（与设备采集 DEVICE 对应） */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 允许修改的说明性字段：检测报告地址、备注、附件 */
    private static final String EDITABLE_FIELDS = "报告地址、备注、附件";

    private final SourceGuard sourceGuard;
    private final ChainEventPublisher chainEvents;

    /* 人工新增方法已删除：瘦肉精检测为设备独占环节，写入只走设备通道
       （SlaughterDeviceIngestController /slaughter/internal/ingest/ractopamine）。 */


    /** 作废：逻辑作废 + 理由 + 实名，单独上链一条 VOID 事件，不做物理删除 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean voidTest(Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.RACTOPAMINE, reason);
        String cleanReason = reason.trim();
        RactopamineTest old = this.getById(id);
        if (old == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "瘦肉精检测记录不存在或已作废");
        String operator = voidBy.display();
        if (baseMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：记录可能已被他人作废");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordId", id);
        payload.put("testNo", old.getTestNo());
        payload.put("sampleNo", old.getSampleNo());
        payload.put("action", "VOID");
        payload.put("voidReason", cleanReason);
        payload.put("operator", operator);
        payload.put("originalSource", old.getSource());
        Map<String, Object> hashSource = new HashMap<>(payload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("RACTOPAMINE_VOID", id, "RACTOPAMINE:" + id + ":VOID",
                HashUtil.sha256Json(hashSource), payload));
        log.info("ractopamine_voided id={} operator={} reason={}", id, operator, cleanReason);
        return true;
    }

    @Override
    public Page<RactopamineTestVO> pageQuery(RactopamineTestDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : dto.getPageSize();
        Page<RactopamineTest> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<RactopamineTest> wrapper = new LambdaQueryWrapper<>();
        if (dto.getPigId() != null) {
            wrapper.eq(RactopamineTest::getPigId, dto.getPigId());
        }
        if (dto.getTestNo() != null && !dto.getTestNo().isEmpty()) {
            wrapper.like(RactopamineTest::getTestNo, dto.getTestNo());
        }
        if (dto.getBatchNo() != null && !dto.getBatchNo().isEmpty()) {
            wrapper.like(RactopamineTest::getBatchNo, dto.getBatchNo());
        }
        if (dto.getSampleNo() != null && !dto.getSampleNo().isEmpty()) {
            wrapper.like(RactopamineTest::getSampleNo, dto.getSampleNo());
        }
        if (dto.getTestType() != null && !dto.getTestType().isEmpty()) {
            wrapper.eq(RactopamineTest::getTestType, dto.getTestType());
        }
        if (dto.getEarTagNo() != null && !dto.getEarTagNo().isEmpty()) {
            wrapper.like(RactopamineTest::getEarTagNo, dto.getEarTagNo());
        }
        // 结果支持中文："阴性"→result=1，"阳性"→result=0，"待检测"→status=0
        if (dto.getResult() != null && !dto.getResult().isEmpty()) {
            if ("待检测".equals(dto.getResult())) {
                wrapper.eq(RactopamineTest::getStatus, 0);
            } else {
                wrapper.eq(RactopamineTest::getResult, SlaughterStatus.testResult(dto.getResult()));
            }
        }
        if (dto.getStatus() != null) {
            wrapper.eq(RactopamineTest::getStatus, dto.getStatus());
        }
        if (dto.getStartDate() != null && !dto.getStartDate().isEmpty()) {
            wrapper.ge(RactopamineTest::getTestTime, LocalDate.parse(dto.getStartDate()).atStartOfDay());
        }
        if (dto.getEndDate() != null && !dto.getEndDate().isEmpty()) {
            wrapper.le(RactopamineTest::getTestTime, LocalDate.parse(dto.getEndDate()).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(RactopamineTest::getTestTime);

        Page<RactopamineTest> entityPage = this.page(page, wrapper);

        Page<RactopamineTestVO> voPage = new Page<>();
        BeanUtil.copyProperties(entityPage, voPage, "records");
        voPage.setRecords(BeanUtil.copyToList(entityPage.getRecords(), RactopamineTestVO.class));

        return voPage;
    }
}
