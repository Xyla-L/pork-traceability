package com.pork.slaughter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.entity.EntryInspection;
import com.pork.slaughter.mapper.EntryInspectionMapper;
import com.pork.slaughter.service.EntryInspectionService;
import com.pork.slaughter.vo.EntryInspectionVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
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
            if (dto.getVehicleNo() != null && !dto.getVehicleNo().isEmpty()) {
                wrapper.like(EntryInspection::getVehicleNo, dto.getVehicleNo());
            }
        }
        wrapper.orderByDesc(EntryInspection::getCreateTime);

        // 3. 执行分页查询
        Page<EntryInspection> resultPage = this.page(page, wrapper);

        // 4. Entity 转 VO
        List<EntryInspectionVO> voList = resultPage.getRecords().stream().map(entity -> {
            EntryInspectionVO vo = new EntryInspectionVO();
            BeanUtils.copyProperties(entity, vo);
            return vo;
        }).collect(Collectors.toList());

        // 5. 封装返回
        Page<EntryInspectionVO> voPage = new Page<>(pageNum, pageSize, resultPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }
}
