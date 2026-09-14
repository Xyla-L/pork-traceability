package com.pork.slaughter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.entity.SlaughterInspection;
import com.pork.slaughter.mapper.SlaughterInspectionMapper;
import com.pork.slaughter.service.SlaughterInspectionService;
import com.pork.slaughter.support.SlaughterStatus;
import com.pork.slaughter.vo.SlaughterInspectionVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SlaughterInspectionServiceImpl extends ServiceImpl<SlaughterInspectionMapper, SlaughterInspection>
        implements SlaughterInspectionService {

    private final ChainEventPublisher chainEvents;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addInspection(SlaughterInspectionDTO dto) {
        SlaughterInspection entity = new SlaughterInspection();
        BeanUtil.copyProperties(dto, entity, "inspectType", "status");
        entity.setInspectType(SlaughterStatus.inspectType(dto.getInspectType()));
        entity.setStatus(SlaughterStatus.inspection(dto.getStatus()));
        entity.setCreateTime(LocalDateTime.now());
        entity.setContentHash(HashUtil.sha256Json(dto));
        if (!this.save(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "屠宰检验保存失败");
        chainEvents.publish(ChainTxMessage.create("SLAUGHTER_INSPECT", entity.getId(), entity.getInspectNo(),
                entity.getContentHash(), Map.of("inspectNo", entity.getInspectNo(), "pigId", entity.getPigId())));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateInspection(Long id, SlaughterInspectionDTO dto) {
        if (this.getById(id) == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "屠宰检验记录不存在");
        SlaughterInspection entity = new SlaughterInspection();
        BeanUtil.copyProperties(dto, entity, "inspectType", "status");
        entity.setId(id);
        entity.setInspectType(SlaughterStatus.inspectType(dto.getInspectType()));
        entity.setStatus(SlaughterStatus.inspection(dto.getStatus()));
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
        if (dto.getPigId() != null) {
            wrapper.eq(SlaughterInspection::getPigId, dto.getPigId());
        }
        if (dto.getInspectNo() != null && !dto.getInspectNo().isEmpty()) {
            wrapper.like(SlaughterInspection::getInspectNo, dto.getInspectNo());
        }
        if (dto.getBatchNo() != null && !dto.getBatchNo().isEmpty()) {
            wrapper.like(SlaughterInspection::getBatchNo, dto.getBatchNo());
        }
        if (dto.getEarTagNo() != null && !dto.getEarTagNo().isEmpty()) {
            wrapper.like(SlaughterInspection::getEarTagNo, dto.getEarTagNo());
        }
        // 检验类型支持中文("宰前检验/宰后检验")或数字编码，"同步检验"不加条件
        Integer inspectType = SlaughterStatus.inspectType(dto.getInspectType());
        if (inspectType != null && inspectType != 3) {
            wrapper.eq(SlaughterInspection::getInspectType, inspectType);
        }
        // 状态支持中文("待检验/合格/不合格")或数字编码
        Integer status = SlaughterStatus.inspection(dto.getStatus());
        if (status != null) {
            wrapper.eq(SlaughterInspection::getStatus, status);
        }
        if (dto.getStartDate() != null && !dto.getStartDate().isEmpty()) {
            wrapper.ge(SlaughterInspection::getInspectTime, LocalDate.parse(dto.getStartDate()).atStartOfDay());
        }
        if (dto.getEndDate() != null && !dto.getEndDate().isEmpty()) {
            wrapper.le(SlaughterInspection::getInspectTime, LocalDate.parse(dto.getEndDate()).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(SlaughterInspection::getInspectTime);

        Page<SlaughterInspection> entityPage = this.page(page, wrapper);

        Page<SlaughterInspectionVO> voPage = new Page<>();
        BeanUtil.copyProperties(entityPage, voPage, "records");
        voPage.setRecords(BeanUtil.copyToList(entityPage.getRecords(), SlaughterInspectionVO.class));

        return voPage;
    }
}
