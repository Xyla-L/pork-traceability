package com.pork.slaughter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.entity.EntryInspection;
import com.pork.slaughter.mapper.EntryInspectionMapper;
import com.pork.slaughter.service.EntryInspectionService;
import com.pork.slaughter.support.SlaughterStatus;
import com.pork.slaughter.vo.EntryInspectionVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryInspectionServiceImpl extends ServiceImpl<EntryInspectionMapper, EntryInspection> implements EntryInspectionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addEntry(EntryInspectionDTO dto) {
        EntryInspection entity = new EntryInspection();
        BeanUtils.copyProperties(dto, entity);
        entity.setStatus(SlaughterStatus.entry(dto.getStatus()));
        entity.setCreateTime(LocalDateTime.now());
        if (!this.save(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "入场查验保存失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateEntry(Long id, EntryInspectionDTO dto) {
        if (this.getById(id) == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "入场查验记录不存在");
        EntryInspection entity = new EntryInspection();
        entity.setId(id);
        BeanUtils.copyProperties(dto, entity);
        entity.setStatus(SlaughterStatus.entry(dto.getStatus()));
        if (!this.updateById(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "入场查验更新失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteEntry(Long id) {
        if (!this.removeById(id)) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "入场查验记录不存在");
        return true;
    }

    @Override
    public Page<EntryInspectionVO> pageQuery(EntryInspectionDTO dto, int pageNum, int pageSize) {
        // 1. 构建分页对象
        Page<EntryInspection> page = new Page<>(pageNum, pageSize);

        // 2. 构建查询条件
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
