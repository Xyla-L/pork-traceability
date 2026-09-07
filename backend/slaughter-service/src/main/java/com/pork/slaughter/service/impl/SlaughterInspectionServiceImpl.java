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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SlaughterInspectionServiceImpl extends ServiceImpl<SlaughterInspectionMapper, SlaughterInspection>
        implements SlaughterInspectionService {

    @Override
    public boolean addInspection(SlaughterInspectionDTO dto) {
        SlaughterInspection entity = new SlaughterInspection();
        BeanUtil.copyProperties(dto, entity);
        return this.save(entity);
    }

    @Override
    public boolean updateInspection(Long id, SlaughterInspectionDTO dto) {
        SlaughterInspection entity = new SlaughterInspection();
        BeanUtil.copyProperties(dto, entity);
        entity.setId(id);
        return this.updateById(entity);
    }

    @Override
    public boolean deleteInspection(Long id) {
        return this.removeById(id);
    }

    @Override
    public Page<SlaughterInspectionVO> pageQuery(SlaughterInspectionDTO dto) {
        Page<SlaughterInspection> page = new Page<>(dto.getPageNum(), dto.getPageSize());

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