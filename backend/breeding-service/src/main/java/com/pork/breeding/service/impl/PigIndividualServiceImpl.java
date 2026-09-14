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
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.vo.PigIndividualVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.beans.FeatureDescriptor;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PigIndividualServiceImpl extends ServiceImpl<PigIndividualMapper, PigIndividual> implements PigIndividualService {
    private final FarmMapper farmMapper;

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
        Integer statusCode = parseStatus(status);
        if (statusCode != null) {
            wrapper.eq(PigIndividual::getStatus, statusCode);
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
    @Transactional(rollbackFor = Exception.class)
    public void addIndividual(PigIndividualDTO dto) {
        validateReferences(dto, null);
        PigIndividual entity = new PigIndividual();
        BeanUtils.copyProperties(dto, entity);
        entity.setStatus(1);
        entity.setCreateTime(java.time.LocalDateTime.now());
        entity.setUpdateTime(java.time.LocalDateTime.now());
        boolean saved = this.save(entity);
        if (!saved) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIndividual(PigIndividualDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        PigIndividual entity = this.getById(dto.getId());
        if (entity == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        validateReferences(dto, dto.getId());
        // 部分更新：仅覆盖 DTO 中非 null 的字段，避免清空未提交的原值
        BeanUtils.copyProperties(dto, entity, getNullPropertyNames(dto));
        entity.setUpdateTime(java.time.LocalDateTime.now());
        boolean updated = this.updateById(entity);
        if (!updated) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
    }

    @Override
    public PigIndividualVO getDetail(Long id) {
        PigIndividual entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        PigIndividualVO vo = convertToVO(entity);
        if (entity.getFarmId() != null) {
            Farm farm = farmMapper.selectById(entity.getFarmId());
            if (farm != null) {
                vo.setFarmName(farm.getFarmName());
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeIndividual(Long id) {
        boolean removed = this.removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
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
     * 解析前端状态参数：优先按中文枚举（在养/已出栏/已屠宰/异常），兼容纯数字字符串
     */
    private Integer parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String trimmed = status.trim();
        Integer code = STATUS_NAME_TO_CODE.get(trimmed);
        if (code != null) {
            return code;
        }
        if (trimmed.matches("\\d+")) {
            return Integer.valueOf(trimmed);
        }
        return null;
    }

    private String[] getNullPropertyNames(Object source) {
        BeanWrapper src = new BeanWrapperImpl(source);
        return java.util.Arrays.stream(src.getPropertyDescriptors())
                .map(FeatureDescriptor::getName)
                .filter(name -> src.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }
}
