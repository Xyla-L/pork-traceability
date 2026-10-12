package com.pork.breeding.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.VaccineRecordDTO;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.VaccineRecordMapper;
import com.pork.breeding.service.VaccineRecordService;
import com.pork.breeding.vo.VaccineRecordVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.source.ManualOperator;
import com.pork.core.source.TraceStage;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.web.source.SourceGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaccineRecordServiceImpl extends ServiceImpl<VaccineRecordMapper, VaccineRecord> implements VaccineRecordService {

    /** 人工录入来源标记（与设备采集 DEVICE 对应） */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 允许修改的说明性字段：免疫记录只有附件属于可补充的说明性内容 */
    private static final String EDITABLE_FIELDS = "接种凭证附件";

    private final PigIndividualMapper pigMapper;
    private final SourceGuard sourceGuard;
    private final ChainEventPublisher chainEvents;

    @Override
    public Page<VaccineRecordVO> pageQuery(Long current, Long size, Long pigId) {
        Page<VaccineRecord> page = this.page(
                new Page<>(current, size),
                Wrappers.<VaccineRecord>lambdaQuery().eq(pigId != null, VaccineRecord::getPigId, pigId)
        );
        Page<VaccineRecordVO> voPage = new Page<>(current, size);
        BeanUtils.copyProperties(page, voPage);
        voPage.setRecords(convertToVOList(page.getRecords()));
        return voPage;
    }

    /* 人工录入方法已删除：免疫注射为设备独占环节，写入只走设备通道
       （BreedingDeviceIngestController /breeding/internal/ingest/vaccine）。 */


    @Override
    public VaccineRecordVO getDetail(Long pigId, Long id) {
        VaccineRecord record = getRecordOfPig(pigId, id);
        return convertToVO(record);
    }

    /** 作废（原「删除」）：实名 + 理由必填，上链留证，不做物理删除 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidRecord(Long pigId, Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.VACCINE, reason);
        String cleanReason = reason.trim();
        VaccineRecord record = getRecordOfPig(pigId, id);
        String operator = voidBy.display();
        if (baseMapper.voidById(record.getId(), cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：记录可能已被他人作废");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("pigId", record.getPigId());
        payload.put("vaccineName", record.getVaccineName());
        payload.put("batchNo", record.getBatchNo());
        payload.put("action", "VOID");
        payload.put("voidReason", cleanReason);
        payload.put("operator", operator);
        payload.put("originalSource", record.getSource());
        Map<String, Object> hashSource = new HashMap<>(payload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("VACCINE_RECORD_VOID", record.getId(),
                "VACCINE:" + record.getId() + ":VOID", HashUtil.sha256Json(hashSource), payload));
        log.info("vaccine_voided id={} pigId={} operator={} reason={}", id, pigId, operator, cleanReason);
    }

    @Override
    public List<VaccineRecordVO> listByPigId(Long pigId) {
        return listByPigId(pigId, false);
    }

    @Override
    public List<VaccineRecordVO> listByPigId(Long pigId, boolean includeVoid) {
        requirePig(pigId);
        // includeVoid 走原生 SQL 取全量：void_flag 是逻辑删除标记，常规查询会把作废记录整条藏掉，
        // 列表页需要它们留在原位并打「已作废」标，避免「上报成功却看不到」的误解。
        List<VaccineRecord> records = includeVoid
                ? baseMapper.selectByPigIncludeVoid(pigId)
                : this.list(Wrappers.<VaccineRecord>lambdaQuery().eq(VaccineRecord::getPigId, pigId));
        return convertToVOList(records);
    }

    private void requirePig(Long pigId) {
        if (pigMapper.selectById(pigId) == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在");
        }
    }

    /**
     * 查询疫苗记录并校验其归属的生猪，防止跨猪操作
     */
    private VaccineRecord getRecordOfPig(Long pigId, Long id) {
        VaccineRecord record = this.getById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "疫苗记录不存在或已作废");
        }
        if (pigId != null && !pigId.equals(record.getPigId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "疫苗记录不属于指定生猪");
        }
        return record;
    }

    private List<VaccineRecordVO> convertToVOList(List<VaccineRecord> records) {
        return records.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    private VaccineRecordVO convertToVO(VaccineRecord record) {
        VaccineRecordVO vo = new VaccineRecordVO();
        BeanUtils.copyProperties(record, vo);
        vo.setFileIds(parseFileIds(record.getFileIds()));
        return vo;
    }

    /**
     * 数据库中 fileIds 以 JSON 字符串存储，反序列化为 List<String>；为空或解析失败时返回空列表
     */
    private List<String> parseFileIds(String fileIds) {
        if (!StringUtils.hasText(fileIds)) {
            return List.of();
        }
        try {
            List<String> ids = JSON.parseArray(fileIds, String.class);
            return ids == null ? List.of() : ids;
        } catch (Exception e) {
            return List.of();
        }
    }
}
