package com.pork.slaughter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.source.ManualOperator;
import com.pork.core.source.TraceStage;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.mapper.CarcassStampMapper;
import com.pork.slaughter.service.CarcassStampService;
import com.pork.slaughter.support.SlaughterStatus;
import com.pork.slaughter.vo.CarcassStampVO;
import com.pork.web.source.SourceGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarcassStampServiceImpl extends ServiceImpl<CarcassStampMapper, CarcassStamp>
        implements CarcassStampService {

    /** 人工录入来源标记（与设备采集 DEVICE 对应） */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 允许修改的说明性字段：盖章记录的其余字段都是法定授权内容，改不得 */
    private static final String EDITABLE_FIELDS = "备注";

    private final ChainEventPublisher chainEvents;
    private final SourceGuard sourceGuard;

    @Override
    public IPage<CarcassStampVO> pageQuery(Page<CarcassStamp> page, CarcassStampDTO query,
                                           String status, String startDate, String endDate) {
        LambdaQueryWrapper<CarcassStamp> wrapper = new LambdaQueryWrapper<>();

        if (query.getPigId() != null) {
            wrapper.eq(CarcassStamp::getPigId, query.getPigId());
        }
        if (query.getStampNo() != null && !query.getStampNo().isEmpty()) {
            wrapper.like(CarcassStamp::getStampNo, query.getStampNo());
        }
        if (query.getBatchNo() != null && !query.getBatchNo().isEmpty()) {
            wrapper.like(CarcassStamp::getBatchNo, query.getBatchNo());
        }
        if (query.getCarcassNo() != null && !query.getCarcassNo().isEmpty()) {
            wrapper.like(CarcassStamp::getCarcassNo, query.getCarcassNo());
        }
        if (query.getStampType() != null && !query.getStampType().isEmpty()) {
            wrapper.eq(CarcassStamp::getStampType, query.getStampType());
        }
        // 状态支持中文("待盖章/已盖章/已作废")或数字编码
        Integer statusCode = SlaughterStatus.stamp(status);
        if (statusCode != null) {
            wrapper.eq(CarcassStamp::getStatus, statusCode);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(CarcassStamp::getStampTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(CarcassStamp::getStampTime, LocalDate.parse(endDate).atTime(23, 59, 59));
        }

        wrapper.orderByDesc(CarcassStamp::getCreateTime);
        Page<CarcassStamp> entityPage = this.page(page, wrapper);

        List<CarcassStampVO> voList = entityPage.getRecords().stream().map(entity -> {
            CarcassStampVO vo = new CarcassStampVO();
            BeanUtils.copyProperties(entity, vo);
            return vo;
        }).collect(Collectors.toList());
        Page<CarcassStampVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /* 人工新增方法已删除：胴体盖章为设备独占环节，写入只走设备通道
       （SlaughterDeviceIngestController /slaughter/internal/ingest/stamp）。 */


    /** 作废：逻辑作废 + 理由 + 实名，单独上链一条 VOID 事件，不做物理删除 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidStamp(Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.STAMP, reason);
        String cleanReason = reason.trim();
        CarcassStamp old = this.getById(id);
        if (old == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "检疫盖章记录不存在或已作废");
        String operator = voidBy.display();
        if (baseMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：记录可能已被他人作废");
        }
        Map<String, Object> voidPayload = new HashMap<>();
        voidPayload.put("stampNo", old.getStampNo());
        voidPayload.put("pigId", old.getPigId());
        voidPayload.put("action", "VOID");
        voidPayload.put("voidReason", cleanReason);
        voidPayload.put("operator", operator);
        voidPayload.put("originalSource", old.getSource());
        Map<String, Object> hashSource = new HashMap<>(voidPayload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("CARCASS_STAMP_VOID", id, old.getStampNo() + ":VOID",
                HashUtil.sha256Json(hashSource), voidPayload));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStamp(Long id) {
        // 盖章记录已上链存证，物理删除会造成链上哈希无法溯源验证，必须走逻辑作废
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "盖章记录已上链，不可删除，请使用作废功能");
    }
}
