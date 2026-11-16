package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.VaccineBatch;
import com.pork.breeding.entity.VaccineBatchPig;
import com.pork.breeding.mapper.VaccineBatchMapper;
import com.pork.breeding.mapper.VaccineBatchPigMapper;
import com.pork.breeding.service.VaccineBatchService;
import com.pork.core.util.BusinessNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaccineBatchServiceImpl implements VaccineBatchService {

    /** 归批口径：同一天的免疫算一次作业（同场+同疫苗+同生产批号+同一天） */
    private static final LocalTime DAY_START = LocalTime.MIN;

    private final VaccineBatchMapper batchMapper;
    private final VaccineBatchPigMapper memberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VaccineBatch attach(Long farmId, String vaccineName, String prodBatchNo, String manufacturer,
                               LocalDateTime injectTime, String operator, String source, String sourceRef,
                               PigIndividual pig) {
        LocalDateTime at = injectTime == null ? LocalDateTime.now() : injectTime;
        LocalDateTime dayStart = at.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        VaccineBatch batch = findExisting(farmId, vaccineName, prodBatchNo, dayStart, dayEnd);
        if (batch == null) {
            batch = new VaccineBatch();
            batch.setFarmId(farmId);
            batch.setBatchNo(BusinessNoGenerator.next("YH"));
            batch.setVaccineName(vaccineName);
            batch.setProdBatchNo(prodBatchNo);
            batch.setManufacturer(manufacturer);
            batch.setInjectTime(at);
            batch.setOperator(operator);
            batch.setPigCount(0);
            batch.setDataSource(StringUtils.hasText(source) ? source : "DEVICE");
            batch.setSourceRef(sourceRef);
            batch.setVoidFlag(0);
            batch.setCreateTime(LocalDateTime.now());
            try {
                batchMapper.insert(batch);
            } catch (DuplicateKeyException e) {
                // 并发同秒建单：唯一键 (farm_id, batch_no) 兜底，退回读既有单
                batch = findExisting(farmId, vaccineName, prodBatchNo, dayStart, dayEnd);
                if (batch == null) throw e;
            }
        }

        if (pig != null) addMember(batch.getId(), pig.getId(), pig.getEarTagNo());
        refreshCount(batch.getId());
        return batch;
    }

    private VaccineBatch findExisting(Long farmId, String vaccineName, String prodBatchNo,
                                      LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<VaccineBatch> found = batchMapper.selectList(Wrappers.<VaccineBatch>lambdaQuery()
                .eq(VaccineBatch::getFarmId, farmId)
                .eq(VaccineBatch::getVaccineName, vaccineName)
                .and(w -> {
                    if (StringUtils.hasText(prodBatchNo)) {
                        w.eq(VaccineBatch::getProdBatchNo, prodBatchNo);
                    } else {
                        // 生产批号缺失时以「也没填」为同一口径，不能把「未记录」并到某个具体批号上
                        w.isNull(VaccineBatch::getProdBatchNo).or().eq(VaccineBatch::getProdBatchNo, "");
                    }
                })
                .ge(VaccineBatch::getInjectTime, dayStart)
                .lt(VaccineBatch::getInjectTime, dayEnd)
                .eq(VaccineBatch::getVoidFlag, 0)
                .orderByAsc(VaccineBatch::getId)
                .last("LIMIT 1"));
        return found.isEmpty() ? null : found.get(0);
    }

    private void addMember(Long batchId, Long pigId, String earTagNo) {
        Long exists = memberMapper.selectCount(Wrappers.<VaccineBatchPig>lambdaQuery()
                .eq(VaccineBatchPig::getBatchId, batchId)
                .eq(VaccineBatchPig::getPigId, pigId));
        if (exists != null && exists > 0) return;
        VaccineBatchPig member = new VaccineBatchPig();
        member.setBatchId(batchId);
        member.setPigId(pigId);
        member.setEarTagNo(earTagNo);
        member.setVoidFlag(0);
        member.setCreateTime(LocalDateTime.now());
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            log.debug("vaccine_batch_member_exists batchId={} pigId={}", batchId, pigId);
        }
    }

    @Override
    public void markMemberVoid(Long batchId, Long pigId) {
        if (batchId == null || pigId == null) return;
        memberMapper.update(null, Wrappers.<VaccineBatchPig>lambdaUpdate()
                .eq(VaccineBatchPig::getBatchId, batchId)
                .eq(VaccineBatchPig::getPigId, pigId)
                .set(VaccineBatchPig::getVoidFlag, 1));
    }

    @Override
    public void refreshCount(Long batchId) {
        Long count = memberMapper.selectCount(Wrappers.<VaccineBatchPig>lambdaQuery()
                .eq(VaccineBatchPig::getBatchId, batchId));
        batchMapper.update(null, Wrappers.<VaccineBatch>lambdaUpdate()
                .eq(VaccineBatch::getId, batchId)
                .set(VaccineBatch::getPigCount, count == null ? 0 : count.intValue()));
    }
}
