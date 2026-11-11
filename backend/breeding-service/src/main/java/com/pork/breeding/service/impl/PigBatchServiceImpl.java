package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.PigBatchDTO;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.entity.PigBatch;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.mapper.PigBatchMapper;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.service.PigBatchService;
import com.pork.breeding.support.PigTagRange;
import com.pork.breeding.support.PigTagRange.Range;
import com.pork.breeding.vo.PigBatchVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 生猪批次档案服务实现。
 *
 * <p>为什么按批次建档：现实中的商品猪按批次管理（同场、同批进、同批出），
 * 逐头登记品种/性别/出生日期既做不到、也没有价值——耳标芯片里只有编号。
 * 批次档承载「人的知识」（场、批次号、品种、进场日期、来源），
 * 耳标起止号圈定成员区间；登记时区间内的耳标自动展开成个体成员行，
 * 下游按耳标逐头关联的业务（入场查验、检疫证、盖章、白条归批）完全不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PigBatchServiceImpl extends ServiceImpl<PigBatchMapper, PigBatch> implements PigBatchService {

    /** 人工登记来源标记（写入 data_source；养殖系统同步写 API，见 FarmBatchIngestService） */
    private static final String SOURCE_MANUAL = "MANUAL";

    private final PigIndividualMapper pigIndividualMapper;
    private final FarmMapper farmMapper;
    private final SourceGuard sourceGuard;
    private final ChainEventPublisher chainEvents;

    @Override
    public Page<PigBatchVO> pageQuery(long current, long size, String batchNo, Long farmId, String breed) {
        LambdaQueryWrapper<PigBatch> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(batchNo)) {
            wrapper.like(PigBatch::getBatchNo, batchNo.trim());
        }
        if (farmId != null) {
            wrapper.eq(PigBatch::getFarmId, farmId);
        }
        if (StringUtils.hasText(breed)) {
            wrapper.eq(PigBatch::getBreed, breed.trim());
        }
        wrapper.orderByDesc(PigBatch::getCreateTime);

        Page<PigBatch> entityPage = this.page(new Page<>(current, size), wrapper);
        List<PigBatch> records = entityPage.getRecords();

        Map<Long, Farm> farmMap = records.stream()
                .map(PigBatch::getFarmId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), ids ->
                        ids.isEmpty() ? Map.<Long, Farm>of()
                                : farmMapper.selectBatchIds(ids).stream()
                                .collect(Collectors.toMap(Farm::getId, Function.identity()))));

        Page<PigBatchVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records.stream()
                .map(batch -> {
                    PigBatchVO vo = convertToVO(batch);
                    Farm farm = farmMap.get(batch.getFarmId());
                    if (farm != null) vo.setFarmName(farm.getFarmName());
                    return vo;
                }).toList());
        return voPage;
    }

    /**
     * 人工登记批次：批次表单一张，耳标区间内的每头猪自动成为成员行。
     * <p>
     * 校验四道：养殖场必须真实存在；同场内批次号唯一；耳标起止号必须是
     * 「同前缀 + 纯数字尾号」且起不大于止、区间不超过上限；区间内的耳标
     * 不得与已有档案（其他批次的成员）重叠——一头猪只能属于一个批次。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBatch(PigBatchDTO dto) {
        sourceGuard.requireManualAllowed(TraceStage.TAG, "登记批次");
        String batchNo = dto.getBatchNo().trim();
        String tagStart = dto.getEarTagStart().trim();
        String tagEnd = dto.getEarTagEnd().trim();

        if (farmMapper.selectById(dto.getFarmId()) == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "养殖场不存在");
        }
        long dupBatch = this.count(new LambdaQueryWrapper<PigBatch>()
                .eq(PigBatch::getBatchNo, batchNo)
                .eq(PigBatch::getFarmId, dto.getFarmId()));
        if (dupBatch > 0) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "该养殖场下批次号已存在");
        }

        Range range = PigTagRange.of(tagStart, tagEnd);
        List<PigIndividual> overlapped = pigIndividualMapper.selectList(new LambdaQueryWrapper<PigIndividual>()
                .select(PigIndividual::getEarTagNo)
                .ge(PigIndividual::getEarTagNo, range.start())
                .le(PigIndividual::getEarTagNo, range.end())
                .last("LIMIT 5"));
        if (!overlapped.isEmpty()) {
            String samples = overlapped.stream()
                    .map(PigIndividual::getEarTagNo)
                    .collect(Collectors.joining("、"));
            throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS,
                    "耳标 " + samples + " 已存在于其他批次或档案中，一头猪只能属于一个批次");
        }

        LocalDateTime now = LocalDateTime.now();
        PigBatch batch = new PigBatch();
        batch.setBatchNo(batchNo);
        batch.setFarmId(dto.getFarmId());
        batch.setBreed(StringUtils.hasText(dto.getBreed()) ? dto.getBreed().trim() : null);
        batch.setEntryDate(dto.getEntryDate());
        batch.setEarTagStart(range.start());
        batch.setEarTagEnd(range.end());
        batch.setQuantity((int) range.size());
        batch.setSource(StringUtils.hasText(dto.getSource()) ? dto.getSource().trim() : null);
        batch.setDataSource(SOURCE_MANUAL);
        batch.setSourceRef("人工登记批次");
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        this.save(batch);

        // 按耳标区间展开成员行：个体行不再是人工录入对象，只是批次的成员明细
        for (String tag : range.members()) {
            PigIndividual member = new PigIndividual();
            member.setEarTagNo(tag);
            member.setBatchId(batch.getId());
            member.setFarmId(batch.getFarmId());
            member.setBreed(batch.getBreed());
            member.setSource(batch.getSource());
            member.setStatus(1);
            member.setDataSource(SOURCE_MANUAL);
            member.setSourceRef("生猪批次 " + batchNo + "（按耳标区间自动生成）");
            member.setCreateTime(now);
            member.setUpdateTime(now);
            member.setVoidFlag(0);
            member.setDeleted(0);
            pigIndividualMapper.insert(member);
        }
        log.info("pig_batch_created id={} batchNo={} quantity={} operator={}",
                batch.getId(), batchNo, batch.getQuantity(), sourceGuard.currentOperator().display());
        // 批次建档上链存证：整条溯源链的起点（这批猪何时进的场、耳标区间是多少），失败不阻塞登记
        String operator = sourceGuard.currentOperator().display();
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchNo", batchNo);
        payload.put("farmId", batch.getFarmId());
        payload.put("breed", StringUtils.hasText(batch.getBreed()) ? batch.getBreed() : "");
        payload.put("entryDate", batch.getEntryDate() == null ? "" : batch.getEntryDate().toString());
        payload.put("earTagStart", batch.getEarTagStart());
        payload.put("earTagEnd", batch.getEarTagEnd());
        payload.put("quantity", batch.getQuantity());
        payload.put("operator", operator);
        Map<String, Object> hashSource = new HashMap<>(payload);
        hashSource.put("createTime", now.toString());
        chainEvents.publish(ChainTxMessage.create("PIG_BATCH", batch.getId(), batchNo,
                HashUtil.sha256Json(hashSource), payload));
        return batch.getId();
    }

    @Override
    public PigBatchVO getDetail(Long id) {
        PigBatch entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪批次不存在");
        }
        PigBatchVO vo = convertToVO(entity);
        Farm farm = farmMapper.selectById(entity.getFarmId());
        if (farm != null) vo.setFarmName(farm.getFarmName());
        return vo;
    }

    /**
     * 作废批次：实名 + 理由 + 上链；区间展开的成员行一并作废。
     * <p>
     * 批次内已有猪出栏或屠宰时拒绝整批作废——下游已经发生的事实不能被批次的
     * 一句作废抹掉，只能逐头核对后按个体处理。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidBatch(Long id, String reason) {
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.TAG, reason);
        String cleanReason = reason.trim();
        PigBatch entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪批次不存在或已作废");
        }
        long leftFarm = pigIndividualMapper.selectCount(new LambdaQueryWrapper<PigIndividual>()
                .eq(PigIndividual::getBatchId, id)
                .ge(PigIndividual::getStatus, 2));
        if (leftFarm > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "批次内已有 " + leftFarm + " 头生猪出栏或屠宰，不能整批作废；请逐头核对后按个体处理");
        }
        String operator = voidBy.display();
        if (baseMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：批次可能已被他人作废");
        }
        int members = pigIndividualMapper.voidByBatchId(id, "所属批次 " + entity.getBatchNo() + " 已作废：" + cleanReason, operator);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", entity.getBatchNo());
        payload.put("farmId", entity.getFarmId());
        payload.put("quantity", entity.getQuantity());
        payload.put("voidedMembers", members);
        payload.put("action", "VOID");
        payload.put("voidReason", cleanReason);
        payload.put("operator", operator);
        Map<String, Object> hashSource = new HashMap<>(payload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("PIG_BATCH_VOID", id, entity.getBatchNo() + ":VOID",
                HashUtil.sha256Json(hashSource), payload));
        log.info("pig_batch_voided id={} batchNo={} members={} operator={} reason={}",
                id, entity.getBatchNo(), members, operator, cleanReason);
    }

    // ==================== VO 转换 ====================

    private PigBatchVO convertToVO(PigBatch entity) {
        PigBatchVO vo = new PigBatchVO();
        BeanUtils.copyProperties(entity, vo);
        if (Integer.valueOf(1).equals(entity.getVoidFlag())) {
            vo.setVoidReason(entity.getVoidReason());
            vo.setVoidOperator(entity.getVoidOperator());
            vo.setVoidTime(entity.getVoidTime());
        }
        for (Map<String, Object> row : pigIndividualMapper.statusCountsByBatch(entity.getId())) {
            Object status = row.get("status");
            long cnt = ((Number) row.getOrDefault("cnt", 0)).longValue();
            if (Integer.valueOf(1).equals(status)) vo.setOnFarmCount(cnt);
            else if (Integer.valueOf(2).equals(status)) vo.setShippedCount(cnt);
            else if (Integer.valueOf(3).equals(status)) vo.setSlaughteredCount(cnt);
            else if (Integer.valueOf(4).equals(status)) vo.setAbnormalCount(cnt);
        }
        return vo;
    }
}
