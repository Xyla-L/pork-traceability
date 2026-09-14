package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.SlaughterApplyApproveDTO;
import com.pork.breeding.dto.SlaughterApplyDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.SlaughterApply;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.SlaughterApplyMapper;
import com.pork.breeding.service.SlaughterApplyService;
import com.pork.breeding.vo.SlaughterApplyVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.BusinessNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SlaughterApplyServiceImpl extends ServiceImpl<SlaughterApplyMapper, SlaughterApply> implements SlaughterApplyService {

    /** 审批状态：待审 */
    private static final int STATUS_PENDING = 0;
    /** 审批状态：通过 */
    private static final int STATUS_APPROVED = 1;
    /** 审批状态：驳回 */
    private static final int STATUS_REJECTED = 2;

    /** 生猪状态：在养 */
    private static final int PIG_STATUS_FARMING = 1;
    /** 生猪状态：已出栏 */
    private static final int PIG_STATUS_SLAUGHTERED_OUT = 2;

    private final PigIndividualMapper pigIndividualMapper;

    @Override
    public Page<SlaughterApplyVO> pageQuery(Long current, Long size, Integer approvalStatus) {
        Page<SlaughterApply> page = this.page(
                new Page<>(current, size),
                Wrappers.<SlaughterApply>lambdaQuery().eq(approvalStatus != null, SlaughterApply::getApprovalStatus, approvalStatus)
        );
        Page<SlaughterApplyVO> voPage = new Page<>(current, size);
        BeanUtils.copyProperties(page, voPage);
        voPage.setRecords(page.getRecords().stream().map(apply -> {
            SlaughterApplyVO vo = new SlaughterApplyVO();
            BeanUtils.copyProperties(apply, vo);
            // 关联查询耳标号
            PigIndividual pig = pigIndividualMapper.selectById(apply.getPigId());
            if (pig != null) {
                vo.setEarTagNo(pig.getEarTagNo());
            }
            return vo;
        }).toList());
        return voPage;
    }

    @Override
    public SlaughterApplyVO getDetail(Long id) {
        SlaughterApply apply = this.getById(id);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "出栏申报记录不存在");
        }
        return convertToVO(apply);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createApply(SlaughterApplyDTO dto) {
        PigIndividual pig = pigIndividualMapper.selectById(dto.getPigId());
        if (pig == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在");
        }

        // 数据库 UK(uk_pig) 保证同一头猪仅一条申报，这里提前给出友好提示
        Long exists = this.baseMapper.selectCount(
                Wrappers.<SlaughterApply>lambdaQuery().eq(SlaughterApply::getPigId, dto.getPigId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该生猪已存在出栏申报记录");
        }

        SlaughterApply apply = new SlaughterApply();
        apply.setPigId(dto.getPigId());
        apply.setApplyNo(BusinessNoGenerator.next("SA"));
        apply.setApplyTime(LocalDateTime.now());
        apply.setWeightKg(dto.getWeightKg() == null ? null : dto.getWeightKg().doubleValue());
        apply.setTargetSlaughterhouse(dto.getTargetSlaughterhouse());
        apply.setApprovalStatus(STATUS_PENDING);
        apply.setCreateTime(LocalDateTime.now());
        if (!this.save(apply)) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "出栏申报保存失败");
        }
        return apply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, SlaughterApplyApproveDTO dto, Long approverId) {
        SlaughterApply apply = this.getById(id);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "出栏申报记录不存在");
        }
        if (apply.getApprovalStatus() != null && apply.getApprovalStatus() != STATUS_PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该申报已审批，不能重复操作");
        }

        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        apply.setApprovalStatus(approved ? STATUS_APPROVED : STATUS_REJECTED);
        apply.setApprovalTime(LocalDateTime.now());
        apply.setApprover(approverId == null ? "系统" : String.valueOf(approverId));
        apply.setRejectReason(approved ? null : dto.getComment());
        if (!this.updateById(apply)) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "出栏申报审批失败");
        }

        // 审批通过：将生猪状态推进为「已出栏」
        if (approved) {
            PigIndividual pig = pigIndividualMapper.selectById(apply.getPigId());
            if (pig != null && (pig.getStatus() == null || pig.getStatus() == PIG_STATUS_FARMING)) {
                pig.setStatus(PIG_STATUS_SLAUGHTERED_OUT);
                pig.setUpdateTime(LocalDateTime.now());
                pigIndividualMapper.updateById(pig);
            }
        }
    }

    private SlaughterApplyVO convertToVO(SlaughterApply apply) {
        SlaughterApplyVO vo = new SlaughterApplyVO();
        BeanUtils.copyProperties(apply, vo);
        if (apply.getWeightKg() != null) {
            vo.setWeightKg(BigDecimal.valueOf(apply.getWeightKg()));
        }
        // 关联查询耳标号
        PigIndividual pig = pigIndividualMapper.selectById(apply.getPigId());
        if (pig != null) {
            vo.setEarTagNo(pig.getEarTagNo());
        }
        return vo;
    }
}