package com.pork.breeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.dto.BreedingDeviceIngestRequests;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.VaccineRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备通道写入（养殖建档 / 免疫注射）。
 * <p>
 * 两条底线：
 * <ol>
 *   <li><b>无批号不入库</b>：疫苗批号是溯源疫苗来源的唯一线索，缺失一律拒绝，设备不得默认值。</li>
 *   <li><b>来源必须可追</b>：source/source_ref/raw_payload 三件套强制落库。
 *       pig_individual 例外用 data_source——它的 source 列是业务上的"自繁/外购"，早被占用。</li>
 * </ol>
 * 可人工兜底的情况统一 {@code written=false + retryable=true} 回执，由接入层转待人工队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BreedingDeviceIngestService {

    private static final String SOURCE_DEVICE = "DEVICE";

    private final PigIndividualMapper pigMapper;
    private final VaccineRecordMapper vaccineMapper;
    private final FarmMapper farmMapper;

    /** 养殖建档：耳标读写器，佩戴即建档；耳标已存在按幂等返回既有档案 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestTag(BreedingDeviceIngestRequests.DeviceTag request) {
        Farm farm = farmMapper.selectOne(Wrappers.<Farm>lambdaQuery()
                .eq(Farm::getFarmName, request.getFarmName()).last("LIMIT 1"));
        if (farm == null) {
            // 养殖场未登记是业务事实，人工在页面登记后可确认入账（retryable）
            return manual("养殖场「" + request.getFarmName() + "」未登记，请先到养殖场管理登记后再建档");
        }

        PigIndividual exists = pigMapper.selectOne(Wrappers.<PigIndividual>lambdaQuery()
                .eq(PigIndividual::getEarTagNo, request.getEarTagNo()).last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_tag_duplicate earTagNo={} id={}", request.getEarTagNo(), exists.getId());
            return written(exists.getId(), "该耳标已建档，返回既有档案");
        }

        PigIndividual pig = new PigIndividual();
        pig.setEarTagNo(request.getEarTagNo());
        pig.setFarmId(farm.getId());
        pig.setBreed(request.getBreed());
        pig.setBirthDate(request.getBirthDate());
        pig.setGender(request.getGender() == null ? 1 : request.getGender());
        pig.setPenNo(request.getPenNo());
        pig.setSource(request.getOrigin());
        pig.setStatus(1);
        pig.setDataSource(SOURCE_DEVICE);
        pig.setSourceRef(request.getSourceRef());
        pig.setRawPayload(request.getRawPayload());
        pig.setCreateTime(LocalDateTime.now());
        pig.setUpdateTime(LocalDateTime.now());
        pigMapper.insert(pig);
        return written(pig.getId(), "生猪档案已建立（耳标 " + request.getEarTagNo() + "）");
    }

    /** 免疫注射：疫苗批号必填（缺批号拒收），同一头猪同批号同时间已记录则幂等返回 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestVaccine(BreedingDeviceIngestRequests.DeviceVaccine request) {
        if (request.getPigId() == null) {
            return manual("未关联到生猪档案（耳标 " + request.getEarTagNo() + "），请人工核对");
        }

        VaccineRecord exists = vaccineMapper.selectOne(Wrappers.<VaccineRecord>lambdaQuery()
                .eq(VaccineRecord::getPigId, request.getPigId())
                .eq(VaccineRecord::getVaccineName, request.getVaccineName())
                .eq(VaccineRecord::getBatchNo, request.getVaccineBatchNo())
                .eq(request.getInjectTime() != null, VaccineRecord::getInjectTime, request.getInjectTime())
                .last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_vaccine_duplicate pigId={} id={}", request.getPigId(), exists.getId());
            return written(exists.getId(), "该免疫记录已存在，返回既有记录");
        }

        VaccineRecord record = new VaccineRecord();
        record.setPigId(request.getPigId());
        record.setVaccineName(request.getVaccineName());
        record.setBatchNo(request.getVaccineBatchNo());
        record.setManufacturer(request.getManufacturer());
        record.setInjectTime(request.getInjectTime() == null ? LocalDateTime.now() : request.getInjectTime());
        record.setDosage(request.getDosage());
        record.setInjectSite(request.getInjectSite());
        record.setOperator(StringUtils.hasText(request.getOperator()) ? request.getOperator() : "设备自动采集");
        record.setSource(SOURCE_DEVICE);
        record.setSourceRef(request.getSourceRef());
        record.setRawPayload(request.getRawPayload());
        record.setCreateTime(LocalDateTime.now());
        vaccineMapper.insert(record);
        return written(record.getId(), "免疫记录已入账");
    }

    private Map<String, Object> written(Long id, String reason) {
        return result(true, id, reason, false);
    }

    private Map<String, Object> manual(String reason) {
        return result(false, null, reason, true);
    }

    private Map<String, Object> result(boolean written, Long id, String reason, boolean retryable) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("written", written);
        body.put("id", id);
        body.put("reason", reason);
        body.put("retryable", retryable);
        return body;
    }
}
