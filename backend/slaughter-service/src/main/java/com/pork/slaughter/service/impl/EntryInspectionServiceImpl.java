package com.pork.slaughter.service.impl;

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
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.entity.EntryInspection;
import com.pork.slaughter.mapper.EntryInspectionMapper;
import com.pork.slaughter.service.EntryInspectionService;
import com.pork.slaughter.support.SlaughterStatus;
import com.pork.slaughter.vo.EntryInspectionVO;
import com.pork.web.source.SourceGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryInspectionServiceImpl extends ServiceImpl<EntryInspectionMapper, EntryInspection> implements EntryInspectionService {

    /** 人工录入来源标记（与设备采集 DEVICE 对应） */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 允许修改的说明性字段：不参与查验结论判定，改错也不影响生猪能否入厂 */
    private static final String EDITABLE_FIELDS = "车辆号、备注、附件";

    private final SourceGuard sourceGuard;
    private final ChainEventPublisher chainEvents;

    /* 人工新增方法已删除：入场查验为设备独占环节，写入只走设备通道
       （SlaughterDeviceIngestController /slaughter/internal/ingest/entry）。 */


    /**
     * 作废：不做物理删除，只把 void_flag 翻成 1 并写下理由与操作人。
     * <p>
     * 作废是最后一次改写结论的机会，所以理由必填、操作人实名，并且单独上链一条 VOID 事件——
     * 「这条合格结论为什么不算数了」这个问题，链上必须答得出来。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean voidEntry(Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.ENTRY, reason);
        String cleanReason = reason.trim();
        EntryInspection old = this.getById(id);
        if (old == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "入场查验记录不存在或已作废");
        String operator = voidBy.display();
        if (baseMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：记录可能已被他人作废");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordId", id);
        payload.put("batchNo", old.getBatchNo());
        payload.put("earTagNo", old.getEarTagNo());
        payload.put("action", "VOID");
        payload.put("voidReason", cleanReason);
        payload.put("operator", operator);
        payload.put("originalSource", old.getSource());
        Map<String, Object> hashSource = new HashMap<>(payload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("ENTRY_VOID", id, "ENTRY:" + id + ":VOID",
                HashUtil.sha256Json(hashSource), payload));
        log.info("entry_voided id={} operator={} reason={}", id, operator, cleanReason);
        return true;
    }

    @Override
    public Page<EntryInspectionVO> pageQuery(EntryInspectionDTO dto, int pageNum, int pageSize) {
        // 1. 构建分页对象
        Page<EntryInspection> page = new Page<>(pageNum, pageSize);

        // 2. 构建查询条件（void_flag 由逻辑删除自动过滤，此处只看得到当前有效记录）
        LambdaQueryWrapper<EntryInspection> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.getPigId() != null) {
                wrapper.eq(EntryInspection::getPigId, dto.getPigId());
            }
            if (dto.getBatchNo() != null && !dto.getBatchNo().isEmpty()) {
                wrapper.like(EntryInspection::getBatchNo, dto.getBatchNo());
            }
            if (dto.getEarTagNo() != null && !dto.getEarTagNo().isEmpty()) {
                wrapper.like(EntryInspection::getEarTagNo, dto.getEarTagNo());
            }
            if (dto.getSourceFarm() != null && !dto.getSourceFarm().isEmpty()) {
                wrapper.like(EntryInspection::getSourceFarm, dto.getSourceFarm());
            }
            if (dto.getVehicleNo() != null && !dto.getVehicleNo().isEmpty()) {
                wrapper.like(EntryInspection::getVehicleNo, dto.getVehicleNo());
            }
            if (dto.getHealthCheck() != null) {
                wrapper.eq(EntryInspection::getHealthCheck, dto.getHealthCheck());
            }
            if (dto.getCertVerified() != null) {
                wrapper.eq(EntryInspection::getCertVerified, dto.getCertVerified());
            }
            // 状态支持中文("待查验/合格/不合格")或数字编码
            Integer status = SlaughterStatus.entry(dto.getStatus());
            if (status != null) {
                wrapper.eq(EntryInspection::getStatus, status);
            }
            if (dto.getStartDate() != null && !dto.getStartDate().isEmpty()) {
                wrapper.ge(EntryInspection::getArriveTime, LocalDate.parse(dto.getStartDate()).atStartOfDay());
            }
            if (dto.getEndDate() != null && !dto.getEndDate().isEmpty()) {
                wrapper.le(EntryInspection::getArriveTime, LocalDate.parse(dto.getEndDate()).atTime(23, 59, 59));
            }
        }
        wrapper.orderByDesc(EntryInspection::getCreateTime);

        // 3. 执行分页查询
        Page<EntryInspection> resultPage = this.page(page, wrapper);

        // 4. Entity 转 VO
        List<EntryInspectionVO> voList = resultPage.getRecords().stream().map(entity -> {
            EntryInspectionVO vo = new EntryInspectionVO();
            BeanUtils.copyProperties(entity, vo);
            vo.setHealthCheckLabel(entity.getHealthCheck() == null ? null : entity.getHealthCheck() == 1 ? "通过" : "异常");
            vo.setCertVerifiedLabel(entity.getCertVerified() == null ? null : entity.getCertVerified() == 1 ? "通过" : "异常");
            return vo;
        }).collect(Collectors.toList());

        // 5. 封装返回
        Page<EntryInspectionVO> voPage = new Page<>(pageNum, pageSize, resultPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
}
