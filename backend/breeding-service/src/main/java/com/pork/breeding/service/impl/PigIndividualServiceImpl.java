package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.PigIndividualDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.vo.PigIndividualVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PigIndividualServiceImpl extends ServiceImpl<PigIndividualMapper, PigIndividual> implements PigIndividualService {
    private final FarmMapper farmMapper;

    @Override
    public Page<PigIndividualVO> pageQuery(Long current, Long size, String earTagNo, Integer status) {
        Page<PigIndividual> page = new Page<>(current, size);
        LambdaQueryWrapper<PigIndividual> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(earTagNo)) {
            wrapper.like(PigIndividual::getEarTagNo, earTagNo);
        }
        if (status != null) {
            wrapper.eq(PigIndividual::getStatus, status);
        }
        wrapper.orderByDesc(PigIndividual::getCreateTime);

        Page<PigIndividual> entityPage = this.page(page, wrapper);

        // Entity 转 VO
        Page<PigIndividualVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(entity -> {
            PigIndividualVO vo = new PigIndividualVO();
            BeanUtils.copyProperties(entity, vo);
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
        BeanUtils.copyProperties(dto, entity);
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
        PigIndividualVO vo = new PigIndividualVO();
        BeanUtils.copyProperties(entity, vo);
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
}
