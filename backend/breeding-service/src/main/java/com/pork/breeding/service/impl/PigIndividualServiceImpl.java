package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.PigIndividualDTO;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.QuarantineCertMapper;
import com.pork.breeding.mapper.VaccineRecordMapper;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.vo.PigIndividualVO;
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
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.beans.FeatureDescriptor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PigIndividualServiceImpl extends ServiceImpl<PigIndividualMapper, PigIndividual> implements PigIndividualService {

    /** 人工录入来源标记（写入 data_source 列，与设备采集 DEVICE 对应） */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 允许修改的流转性字段：圈舍随生产实际调整，改错不影响建档结论 */
    private static final String EDITABLE_FIELDS = "圈舍编号";

    /**
     * 品种字典的「预置来源」：常见商业猪种。
     * 与库内实际出现的品种合并去重——预置值保证下拉在空库时也能用，
     * 库内值保证设备上报的任何品种都能被选到。
     */
    private static final List<String> STANDARD_BREEDS = List.of(
            "长白猪", "大白猪", "杜洛克", "皮特兰", "三元杂", "大约克夏", "巴克夏", "汉普夏");

    private final FarmMapper farmMapper;
    private final VaccineRecordMapper vaccineRecordMapper;
    private final QuarantineCertMapper quarantineCertMapper;
    private final SourceGuard sourceGuard;
    private final ChainEventPublisher chainEvents;

    private static final Map<String, Integer> STATUS_NAME_TO_CODE = Map.of(
            "在养", 1,
            "已出栏", 2,
            "已屠宰", 3,
            "异常", 4
    );

    private static final Map<Integer, String> STATUS_CODE_TO_LABEL = Map.of(
            1, "在养",
            2, "已出栏",
            3, "已屠宰",
            4, "异常"
    );

    @Override
    public Page<PigIndividualVO> pageQuery(Long current, Long size, String earTagNo, Long farmId, String breed,
                                           String status, String birthDateStart, String birthDateEnd) {
        Page<PigIndividual> page = new Page<>(current, size);
        LambdaQueryWrapper<PigIndividual> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(earTagNo)) {
            wrapper.like(PigIndividual::getEarTagNo, earTagNo);
        }
        if (farmId != null) {
            wrapper.eq(PigIndividual::getFarmId, farmId);
        }
        if (StringUtils.hasText(breed)) {
            wrapper.eq(PigIndividual::getBreed, breed);
        }
        List<Integer> statusCodes = parseStatuses(status);
        if (!statusCodes.isEmpty()) {
            wrapper.in(PigIndividual::getStatus, statusCodes);
        }
        if (StringUtils.hasText(birthDateStart)) {
            wrapper.ge(PigIndividual::getBirthDate, LocalDate.parse(birthDateStart.trim()));
        }
        if (StringUtils.hasText(birthDateEnd)) {
            wrapper.le(PigIndividual::getBirthDate, LocalDate.parse(birthDateEnd.trim()));
        }
        wrapper.orderByDesc(PigIndividual::getCreateTime);

        Page<PigIndividual> entityPage = this.page(page, wrapper);

        // 批量查询本页涉及的养殖场，避免逐条查询
        Map<Long, Farm> farmMap = entityPage.getRecords().stream()
                .map(PigIndividual::getFarmId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), ids ->
                        ids.isEmpty() ? Map.<Long, Farm>of()
                                : farmMapper.selectBatchIds(ids).stream()
                                .collect(Collectors.toMap(Farm::getId, Function.identity()))));

        // Entity 转 VO
        Page<PigIndividualVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(entity -> {
            PigIndividualVO vo = convertToVO(entity);
            Farm farm = farmMap.get(entity.getFarmId());
            if (farm != null) {
                vo.setFarmName(farm.getFarmName());
            }
            return vo;
        }).toList());

        return voPage;
    }

    @Override
    public List<String> breedOptions() {
        // 预置品种保底，库内实际值补齐——两边都进，前端下拉就不会漏掉设备上报的新品种
        LinkedHashSet<String> merged = new LinkedHashSet<>(STANDARD_BREEDS);
        List<String> stored = baseMapper.selectDistinctBreeds();
        if (stored != null) {
            merged.addAll(stored);
        }
        return List.copyOf(merged);
    }

    /* 人工建档方法已删除：养殖建档为设备独占环节，写入只走设备通道
       （BreedingDeviceIngestController /breeding/internal/ingest/tag）。 */


    @Override
    public PigIndividualVO getDetail(Long id) {
        PigIndividual entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        PigIndividualVO vo = convertToVO(entity);
        fillFarmName(vo, entity.getFarmId());
        return vo;
    }

    /**
     * 追溯专用：只取当前有效档案；档案已作废时返回带标注的空壳，而不是抛「不存在」。
     * <p>
     * 生猪档案是整条追溯链的起点，被作废后逻辑删除会让它从常规查询里彻底消失。
     * 追溯方看到的应当是一个解释得通的断点（「此档案已作废，理由…」），
     * 而不是一个无从追问的空缺——两者的可信度完全不同。
     */
    @Override
    public PigIndividualVO getTraceDetail(Long id) {
        PigIndividual entity = this.getById(id);
        if (entity != null) {
            PigIndividualVO vo = convertToVO(entity);
            fillFarmName(vo, entity.getFarmId());
            // 档案本身有效，但链上的免疫记录、检疫证可能被作废过，一并标注出来
            List<Map<String, Object>> voided = new ArrayList<>();
            collectVoided(voided, vaccineRecordMapper.selectVoidedByPig(id), "VACCINE", "免疫注射");
            collectVoided(voided, quarantineCertMapper.selectVoidedByPig(id), "QUARANTINE", "产地检疫出证");
            if (!voided.isEmpty()) vo.setVoidedRecords(voided);
            return vo;
        }
        PigIndividual voidedEntity = baseMapper.selectVoidedById(id);
        if (voidedEntity == null) return null;
        PigIndividualVO vo = new PigIndividualVO();
        vo.setId(voidedEntity.getId());
        vo.setEarTagNo(voidedEntity.getEarTagNo());
        vo.setFarmId(voidedEntity.getFarmId());
        fillFarmName(vo, voidedEntity.getFarmId());
        vo.setBreed(voidedEntity.getBreed());
        vo.setBirthDate(voidedEntity.getBirthDate());
        vo.setGender(voidedEntity.getGender());
        vo.setPenNo(voidedEntity.getPenNo());
        vo.setSource(voidedEntity.getSource());
        vo.setDataSource(voidedEntity.getDataSource());
        vo.setSourceRef(voidedEntity.getSourceRef());
        vo.setCreateTime(voidedEntity.getCreateTime());
        vo.setUpdateTime(voidedEntity.getUpdateTime());
        vo.setVoided(true);
        vo.setVoidReason(voidedEntity.getVoidReason());
        vo.setVoidOperator(voidedEntity.getVoidOperator());
        vo.setVoidTime(voidedEntity.getVoidTime());
        return vo;
    }

    private void fillFarmName(PigIndividualVO vo, Long farmId) {
        if (farmId == null) return;
        Farm farm = farmMapper.selectById(farmId);
        if (farm != null) vo.setFarmName(farm.getFarmName());
    }

    private void collectVoided(List<Map<String, Object>> target, List<Map<String, Object>> rows,
                               String stage, String stageLabel) {
        if (rows == null || rows.isEmpty()) return;
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", stage);
            item.put("stageLabel", stageLabel);
            item.putAll(row);
            target.add(item);
        }
    }

    /**
     * 作废（原「删除」）：不做物理删除，只把 void_flag 翻成 1 并写下理由与操作人，同时上链。
     * <p>
     * 生猪档案是追溯链的起点，删掉一条记录，链上以它为起点的事件全成了悬空哈希——
     * 所以这里只允许作废，让「这条档案为什么不算数」有据可查。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidIndividual(Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.TAG, reason);
        String cleanReason = reason.trim();
        PigIndividual entity = this.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在或已作废");
        String operator = voidBy.display();
        if (baseMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：档案可能已被他人作废");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("earTagNo", entity.getEarTagNo());
        payload.put("farmId", entity.getFarmId());
        payload.put("action", "VOID");
        payload.put("voidReason", cleanReason);
        payload.put("operator", operator);
        payload.put("originalSource", entity.getDataSource());
        Map<String, Object> hashSource = new HashMap<>(payload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("PIG_INDIVIDUAL_VOID", id, entity.getEarTagNo() + ":VOID",
                HashUtil.sha256Json(hashSource), payload));
        log.info("pig_individual_voided id={} earTagNo={} operator={} reason={}",
                id, entity.getEarTagNo(), operator, cleanReason);
    }

    private void validateReferences(PigIndividualDTO dto, Long currentId) {
        if (farmMapper.selectById(dto.getFarmId()) == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "养殖场不存在");
        }
        long duplicates = this.count(Wrappers.<PigIndividual>lambdaQuery()
                .eq(PigIndividual::getEarTagNo, dto.getEarTagNo())
                .ne(currentId != null, PigIndividual::getId, currentId));
        if (duplicates > 0) throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "耳标号已存在");
    }

    private PigIndividualVO convertToVO(PigIndividual entity) {
        PigIndividualVO vo = new PigIndividualVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setStatusLabel(STATUS_CODE_TO_LABEL.get(entity.getStatus()));
        return vo;
    }

    /**
     * 解析前端状态参数：支持逗号分隔多状态（如 "1,2" 或 "在养,已出栏"），
     * 单值行为与原逻辑一致；无法解析的部分忽略
     */
    private List<Integer> parseStatuses(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of();
        }
        List<Integer> codes = new ArrayList<>();
        for (String part : status.split(",")) {
            String trimmed = part.trim();
            if (!StringUtils.hasText(trimmed)) continue;
            Integer code = STATUS_NAME_TO_CODE.get(trimmed);
            if (code == null && trimmed.matches("\\d+")) {
                code = Integer.valueOf(trimmed);
            }
            if (code != null && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    private String[] getNullPropertyNames(Object source) {
        BeanWrapper src = new BeanWrapperImpl(source);
        return java.util.Arrays.stream(src.getPropertyDescriptors())
                .map(FeatureDescriptor::getName)
                .filter(name -> src.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }
}
