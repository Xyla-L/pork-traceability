package com.pork.slaughter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.mapper.CarcassStampMapper;
import com.pork.slaughter.service.CarcassStampService;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CarcassStampServiceImpl extends ServiceImpl<CarcassStampMapper, CarcassStamp>
        implements CarcassStampService {
    private final ChainEventPublisher chainEvents;

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
    @Transactional(rollbackFor = Exception.class)
    public boolean addStamp(CarcassStampDTO dto) {
        CarcassStamp stamp = new CarcassStamp();
        BeanUtils.copyProperties(dto, stamp);
        stamp.setCreateTime(LocalDateTime.now());
        stamp.setContentHash(HashUtil.sha256Json(dto));
        if (!this.save(stamp)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "检疫盖章保存失败");
        chainEvents.publish(ChainTxMessage.create("SLAUGHTER_INSPECT", stamp.getId(), stamp.getStampNo(),
                stamp.getContentHash(), Map.of("stampNo", stamp.getStampNo(), "pigId", stamp.getPigId())));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStamp(CarcassStampDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "ID不能为空");
        }
        if (this.getById(dto.getId()) == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "检疫盖章记录不存在");
        CarcassStamp stamp = new CarcassStamp();
        BeanUtils.copyProperties(dto, stamp);
        stamp.setContentHash(HashUtil.sha256Json(dto));
        if (!this.updateById(stamp)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "检疫盖章更新失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStamp(Long id) {
        if (!this.removeById(id)) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "检疫盖章记录不存在");
        return true;
    }
}
