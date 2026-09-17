package com.pork.slaughter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.client.BreedingClient;
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
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SlaughterInspectionServiceImpl extends ServiceImpl<SlaughterInspectionMapper, SlaughterInspection>
        implements SlaughterInspectionService {

    private final ChainEventPublisher chainEvents;
    private final BreedingClient breedingClient;

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
        // 宰后/同步检验意味着生猪已完成屠宰：同步推进生猪档案状态为 3=已屠宰（失败不阻塞业务）
        if (entity.getPigId() != null && entity.getInspectType() != null && entity.getInspectType() >= 2) {
            breedingClient.advanceStatus(entity.getPigId(), 3);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateInspection(Long id, SlaughterInspectionDTO dto) {
        SlaughterInspection old = this.getById(id);
        if (old == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "屠宰检验记录不存在");
        if (old.getStatus() != null && old.getStatus() == 3)
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已作废记录不可编辑，请重新录入");
        SlaughterInspection entity = new SlaughterInspection();
        BeanUtil.copyProperties(dto, entity, "inspectType", "status");
        entity.setId(id);
        entity.setInspectType(SlaughterStatus.inspectType(dto.getInspectType()));
        Integer newStatus = SlaughterStatus.inspection(dto.getStatus());
        if (newStatus != null && newStatus == 3)
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废请使用作废功能，不可通过编辑设置");
        entity.setStatus(newStatus);
        entity.setContentHash(HashUtil.sha256Json(dto));
        if (!this.updateById(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "屠宰检验更新失败");
        // 已上链数据：编辑后重新发布新哈希（链上按 bizKey 保留版本历史，验真取最新），避免链上哈希与业务数据不一致
        Map<String, Object> payload = new HashMap<>();
        payload.put("inspectNo", old.getInspectNo());
        payload.put("pigId", old.getPigId());
        chainEvents.publish(ChainTxMessage.create("SLAUGHTER_INSPECT", id, old.getInspectNo(),
                entity.getContentHash(), payload));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidInspection(Long id) {
        SlaughterInspection old = this.getById(id);
        if (old == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "屠宰检验记录不存在");
        if (old.getStatus() != null && old.getStatus() == 3) return; // 已作废，幂等
        SlaughterInspection entity = new SlaughterInspection();
        entity.setId(id);
        entity.setStatus(3);
        if (!this.updateById(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "屠宰检验作废失败");
        // 作废动作本身单独上链存证（独立 bizKey，不影响原记录哈希验真）
        String voidBizKey = old.getInspectNo() + ":VOID";
        String voidHash = HashUtil.sha256Json(Map.of("bizKey", old.getInspectNo(), "action", "VOID",
                "voidTime", LocalDateTime.now().toString()));
        Map<String, Object> voidPayload = new HashMap<>();
        voidPayload.put("inspectNo", old.getInspectNo());
        voidPayload.put("pigId", old.getPigId());
        voidPayload.put("action", "VOID");
        chainEvents.publish(ChainTxMessage.create("SLAUGHTER_INSPECT_VOID", id, voidBizKey, voidHash, voidPayload));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteInspection(Long id) {
        // 检验记录已上链存证，物理删除会造成链上哈希无法溯源验证，必须走逻辑作废
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "检验记录已上链，不可删除，请使用作废功能");
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
