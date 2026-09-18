package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            // 实体 weightKg 为 Double，VO 为 BigDecimal，copyProperties 类型不匹配会跳过，需手动转换
            if (apply.getWeightKg() != null) {
                vo.setWeightKg(BigDecimal.valueOf(apply.getWeightKg()));
            }
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
    public Map<Integer, Long> countByStatus() {
        // 一次 GROUP BY 查出各状态数量，缺失的状态补 0
        List<Map<String, Object>> rows = this.listMaps(
                new QueryWrapper<SlaughterApply>()
                        .select("approval_status AS status", "COUNT(*) AS cnt")
                        .groupBy("approval_status")
        );
        Map<Integer, Long> result = new HashMap<>();
        result.put(STATUS_PENDING, 0L);
        result.put(STATUS_APPROVED, 0L);
        result.put(STATUS_REJECTED, 0L);
        for (Map<String, Object> row : rows) {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            if (status != null && cnt != null) {
                result.put(((Number) status).intValue(), ((Number) cnt).longValue());
            }
        }
        return result;
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

        // 数据库 UK(uk_pig) 保证同一头猪仅一条申报记录：
        // 待审/已通过 一律拦截；已驳回(2) 允许在同一行上重新提交，避免驳回后这头猪再也申报不了
        SlaughterApply exists = this.getOne(
                Wrappers.<SlaughterApply>lambdaQuery()
                        .eq(SlaughterApply::getPigId, dto.getPigId())
                        .orderByDesc(SlaughterApply::getId)
                        .last("LIMIT 1"));
        if (exists != null && exists.getApprovalStatus() != null && exists.getApprovalStatus() != STATUS_REJECTED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, exists.getApprovalStatus() == STATUS_APPROVED
                    ? "该生猪出栏申报已通过，不能重复申报"
                    : "该生猪已存在待审批的出栏申报，请等待审批结果");
        }

        Double weightKg = dto.getWeightKg() == null ? null : dto.getWeightKg().doubleValue();
        if (exists != null) {
            // 驳回后重新申报：复用原行（uk_pig 只允许一行），重置为待审并清空上一次的审批结论
            boolean updated = this.update(Wrappers.<SlaughterApply>lambdaUpdate()
                    .eq(SlaughterApply::getId, exists.getId())
                    .set(SlaughterApply::getApplyNo, BusinessNoGenerator.next("SA"))
                    .set(SlaughterApply::getApplyTime, LocalDateTime.now())
                    .set(SlaughterApply::getWeightKg, weightKg)
                    .set(SlaughterApply::getTargetSlaughterhouse, dto.getTargetSlaughterhouse())
                    .set(SlaughterApply::getApprovalStatus, STATUS_PENDING)
                    .set(SlaughterApply::getApprover, null)
                    .set(SlaughterApply::getApprovalTime, null)
                    .set(SlaughterApply::getRejectReason, null));
            if (!updated) {
                throw new BusinessException(ErrorCode.DATABASE_ERROR, "出栏申报重新提交失败");
            }
            return exists.getId();
        }

        SlaughterApply apply = new SlaughterApply();
        apply.setPigId(dto.getPigId());
        apply.setApplyNo(BusinessNoGenerator.next("SA"));
        apply.setApplyTime(LocalDateTime.now());
        apply.setWeightKg(weightKg);
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
    public void approve(Long id, SlaughterApplyApproveDTO dto, String approverId) {
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
        // X-User-Id 由网关注入，值为登录用户名（可能为 null，如绕过网关直连）
        apply.setApprover(approverId == null || approverId.isBlank() ? "系统" : approverId);
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