package com.pork.slaughter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.mapper.CarcassStampMapper;
import com.pork.slaughter.service.CarcassStampService;
import com.pork.slaughter.support.SlaughterStatus;
import com.pork.slaughter.vo.CarcassStampVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarcassStampServiceImpl extends ServiceImpl<CarcassStampMapper, CarcassStamp>
        implements CarcassStampService {
    private final ChainEventPublisher chainEvents;

    @Override
    public IPage<CarcassStampVO> pageQuery(Page<CarcassStamp> page, CarcassStampDTO query,
                                           String status, String startDate, String endDate) {
        LambdaQueryWrapper<CarcassStamp> wrapper = new LambdaQueryWrapper<>();

        if (query.getPigId() != null) {
            wrapper.eq(CarcassStamp::getPigId, query.getPigId());
        }
        if (query.getStampNo() != null && !query.getStampNo().isEmpty()) {
            wrapper.like(CarcassStamp::getStampNo, query.getStampNo());
        }
        if (query.getBatchNo() != null && !query.getBatchNo().isEmpty()) {
            wrapper.like(CarcassStamp::getBatchNo, query.getBatchNo());
        }
        if (query.getCarcassNo() != null && !query.getCarcassNo().isEmpty()) {
            wrapper.like(CarcassStamp::getCarcassNo, query.getCarcassNo());
        }
        if (query.getStampType() != null && !query.getStampType().isEmpty()) {
            wrapper.eq(CarcassStamp::getStampType, query.getStampType());
        }
        // 状态支持中文("待盖章/已盖章/已作废")或数字编码
        Integer statusCode = SlaughterStatus.stamp(status);
        if (statusCode != null) {
            wrapper.eq(CarcassStamp::getStatus, statusCode);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(CarcassStamp::getStampTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(CarcassStamp::getStampTime, LocalDate.parse(endDate).atTime(23, 59, 59));
        }

        wrapper.orderByDesc(CarcassStamp::getCreateTime);
        Page<CarcassStamp> entityPage = this.page(page, wrapper);

        List<CarcassStampVO> voList = entityPage.getRecords().stream().map(entity -> {
            CarcassStampVO vo = new CarcassStampVO();
            BeanUtils.copyProperties(entity, vo);
            return vo;
        }).collect(Collectors.toList());
        Page<CarcassStampVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
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
