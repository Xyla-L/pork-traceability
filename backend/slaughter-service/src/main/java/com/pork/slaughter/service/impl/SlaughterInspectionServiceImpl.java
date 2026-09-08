package com.pork.slaughter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.entity.SlaughterInspection;
import com.pork.slaughter.mapper.SlaughterInspectionMapper;
import com.pork.slaughter.service.SlaughterInspectionService;
import com.pork.slaughter.vo.SlaughterInspectionVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SlaughterInspectionServiceImpl extends ServiceImpl<SlaughterInspectionMapper, SlaughterInspection>
        implements SlaughterInspectionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addInspection(SlaughterInspectionDTO dto) {
        SlaughterInspection entity = new SlaughterInspection();
        BeanUtil.copyProperties(dto, entity);
        entity.setCreateTime(LocalDateTime.now());
        entity.setContentHash(HashUtil.sha256Json(dto));
        if (!this.save(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "屠宰检验保存失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateInspection(Long id, SlaughterInspectionDTO dto) {
        if (this.getById(id) == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "屠宰检验记录不存在");
        SlaughterInspection entity = new SlaughterInspection();
        BeanUtil.copyProperties(dto, entity);
        entity.setId(id);
        entity.setContentHash(HashUtil.sha256Json(dto));
        if (!this.updateById(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "屠宰检验更新失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteInspection(Long id) {
        if (!this.removeById(id)) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "屠宰检验记录不存在");
        return true;
    }

    @Override
    public Page<SlaughterInspectionVO> pageQuery(SlaughterInspectionDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : dto.getPageSize();
        Page<SlaughterInspection> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<SlaughterInspection> wrapper = new LambdaQueryWrapper<>();
        // 可以根据 pigId 或 inspectType 进行筛选
        if (dto.getPigId() != null) {
            wrapper.eq(SlaughterInspection::getPigId, dto.getPigId());
        }
        if (dto.getInspectType() != null) {
            wrapper.eq(SlaughterInspection::getInspectType, dto.getInspectType());
        }
        wrapper.orderByDesc(SlaughterInspection::getInspectTime);

        Page<SlaughterInspection> entityPage = this.page(page, wrapper);

        Page<SlaughterInspectionVO> voPage = new Page<>();
        BeanUtil.copyProperties(entityPage, voPage, "records");
        voPage.setRecords(BeanUtil.copyToList(entityPage.getRecords(), SlaughterInspectionVO.class));

        return voPage;
    }
}
