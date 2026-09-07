package com.pork.slaughter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.mapper.CarcassStampMapper;
import com.pork.slaughter.service.CarcassStampService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CarcassStampServiceImpl extends ServiceImpl<CarcassStampMapper, CarcassStamp>
        implements CarcassStampService {

    @Override
    public IPage<CarcassStamp> pageQuery(Page<CarcassStamp> page, CarcassStamp query) {
        QueryWrapper<CarcassStamp> wrapper = new QueryWrapper<>();

        if (query.getPigId() != null) {
            wrapper.eq("pig_id", query.getPigId());
        }
        if (query.getStampNo() != null && !query.getStampNo().isEmpty()) {
            wrapper.like("stamp_no", query.getStampNo());
        }

        wrapper.orderByDesc("create_time");
        return this.page(page, wrapper);
    }

    @Override
    public boolean addStamp(CarcassStampDTO dto) {
        CarcassStamp stamp = new CarcassStamp();
        BeanUtils.copyProperties(dto, stamp);
        // 注意：这里只有 create_time，没有 update_time
        stamp.setCreateTime(LocalDateTime.now());
        return this.save(stamp);
    }

    @Override
    public boolean updateStamp(CarcassStampDTO dto) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        CarcassStamp stamp = new CarcassStamp();
        BeanUtils.copyProperties(dto, stamp);
        // 表中没有 update_time 字段，所以不需要设置
        return this.updateById(stamp);
    }

    @Override
    public boolean deleteStamp(Long id) {
        return this.removeById(id);
    }
}