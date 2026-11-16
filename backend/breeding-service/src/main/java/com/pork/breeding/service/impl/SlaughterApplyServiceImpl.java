package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.SlaughterApplyApproveDTO;
import com.pork.breeding.dto.SlaughterApplyDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.SlaughterApply;
import com.pork.breeding.entity.SlaughterApplyPig;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.SlaughterApplyMapper;
import com.pork.breeding.mapper.SlaughterApplyPigMapper;
import com.pork.breeding.service.SlaughterApplyService;
import com.pork.breeding.support.FarmScope;
import com.pork.breeding.vo.SlaughterApplyVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.source.ManualOperator;
import com.pork.core.source.TraceStage;
import com.pork.core.util.BusinessNoGenerator;
import com.pork.web.source.SourceGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 出栏申报（单据化）：一张申报单含多头。
 * <p>
 * 改造前 uk_pig 限制一头猪一生只能有一条申报，等于把「一车猪一次申报」拆成了一头一单。
 * 现在申报号唯一在单据上，成员在 {@code slaughter_apply_pig}；审批是单据级，
 * 通过后把成员逐头推进为「已出栏」（逐头状态才是下游入场查验认的东西）。
 */
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

    /** 数据来源：人工申报（出栏口地磅自动登记走设备通道，记 DEVICE） */
    private static final String SOURCE_MANUAL = "MANUAL";

    private final PigIndividualMapper pigIndividualMapper;
    private final SlaughterApplyPigMapper applyPigMapper;
    private final SourceGuard sourceGuard;
    private final FarmScope farmScope;

    @Override
    public Page<SlaughterApplyVO> pageQuery(Long current, Long size, Integer approvalStatus) {
        // 机构隔离：单据带 farm_id，直接等值下推，不再靠 pig_id 反查（拆单/并单都不影响归属）
        Long scopedFarmId = farmScope.scopedFarmId();
        Page<SlaughterApply> page = this.page(
                new Page<>(current, size),
                Wrappers.<SlaughterApply>lambdaQuery()
                        .eq(approvalStatus != null, SlaughterApply::getApprovalStatus, approvalStatus)
                        .eq(scopedFarmId != null, SlaughterApply::getFarmId, scopedFarmId)
                        .orderByDesc(SlaughterApply::getId)
        );
        Page<SlaughterApplyVO> voPage = new Page<>(current, size);
        BeanUtils.copyProperties(page, voPage);
        voPage.setRecords(page.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Map<Integer, Long> countByStatus() {
        // 一次 GROUP BY 查出各状态数量，缺失的状态补 0；机构隔离与列表同口径
        Long scopedFarmId = farmScope.scopedFarmId();
        List<Map<String, Object>> rows = this.listMaps(
                new QueryWrapper<SlaughterApply>()
                        .select("approval_status AS status", "COUNT(*) AS cnt")
                        .eq(scopedFarmId != null, "farm_id", scopedFarmId)
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
        // 来源管控：申请出栏以出栏口地磅自动登记为主路径；人工申报是设备覆盖不到时的补录，须实名并上链留痕。
        // 这是全项目第二个人工采集入口（另一个是产地检疫出证），写入强制实名并标注来源为人工。
        ManualOperator operator = sourceGuard.requireManualAllowed(TraceStage.EXIT_APPLY, "申请出栏");

        List<Long> pigIds = resolvePigIds(dto);
        List<PigIndividual> pigs = new ArrayList<>();
        for (Long pigId : pigIds) {
            PigIndividual pig = pigIndividualMapper.selectById(pigId);
            if (pig == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在：" + pigId);
            if (pig.getStatus() != null && pig.getStatus() != PIG_STATUS_FARMING) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "生猪 " + pig.getEarTagNo() + " 当前不是「在养」状态，不能申报出栏");
            }
            pigs.add(pig);
        }
        Long farmId = pigs.get(0).getFarmId();
        for (PigIndividual pig : pigs) {
            if (farmId != null && pig.getFarmId() != null && !farmId.equals(pig.getFarmId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "一张申报单只能覆盖同一个养殖场的生猪（" + pig.getEarTagNo() + " 不属于本场）");
            }
        }

        // 同一头猪不允许同时挂在两张未完成的申报单上：待审/已通过 一律拦截；
        // 已驳回可以重新申报（新建一张单，旧单保留为「曾被驳回」的事实，可追溯）
        List<String> busy = new ArrayList<>();
        for (PigIndividual pig : pigs) {
            SlaughterApply open = latestOpenApplyOfPig(pig.getId());
            if (open != null) {
                busy.add(pig.getEarTagNo() + "（" + open.getApplyNo() + " "
                        + (open.getApprovalStatus() == STATUS_APPROVED ? "已通过" : "待审批") + "）");
            }
        }
        if (!busy.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "以下生猪已有未完成的出栏申报，不能重复申报：" + String.join("、", busy));
        }

        SlaughterApply apply = new SlaughterApply();
        apply.setFarmId(farmId);
        apply.setApplyNo(BusinessNoGenerator.next("SA"));
        apply.setApplyTime(LocalDateTime.now());
        apply.setPigCount(pigs.size());
        apply.setWeightKg(dto.getWeightKg() == null ? null : dto.getWeightKg().doubleValue());
        apply.setTotalWeightKg(dto.getWeightKg());
        apply.setTargetSlaughterhouse(dto.getTargetSlaughterhouse());
        apply.setApprovalStatus(STATUS_PENDING);
        apply.setSource(SOURCE_MANUAL);
        apply.setSourceRef(operator.display());
        apply.setCreateTime(LocalDateTime.now());
        if (!this.save(apply)) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "出栏申报保存失败");
        }
        for (PigIndividual pig : pigs) {
            SlaughterApplyPig member = new SlaughterApplyPig();
            member.setApplyId(apply.getId());
            member.setPigId(pig.getId());
            member.setEarTagNo(pig.getEarTagNo());
            member.setCreateTime(LocalDateTime.now());
            applyPigMapper.insert(member);
        }
        return apply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, SlaughterApplyApproveDTO dto) {
        SlaughterApply apply = this.getById(id);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "出栏申报记录不存在");
        }
        if (apply.getApprovalStatus() != null && apply.getApprovalStatus() != STATUS_PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该申报已审批，不能重复操作");
        }

        // 审批的产物是「结论 + 责任人」：设备只能提供依据，签名必须落到实名的人身上。
        // 拿不到实名（令牌里只有用户ID）就拦下——否则记录上写的是一串编号，等于没人负责。
        ManualOperator operator = sourceGuard.currentOperator();
        if (!operator.hasRealName()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "出栏审批必须实名：当前登录身份未登记真实姓名，请先在「个人中心」补全真实姓名后再审批");
        }

        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        apply.setApprovalStatus(approved ? STATUS_APPROVED : STATUS_REJECTED);
        apply.setApprovalTime(LocalDateTime.now());
        // 审批人取登录人的真实姓名（不再取 X-User-Id，那是一串用户ID，读不出是谁）
        apply.setApprover(operator.display());
        apply.setRejectReason(approved ? null : dto.getComment());
        if (!this.updateById(apply)) {
            throw new BusinessException(ErrorCode.DATABASE_ERROR, "出栏申报审批失败");
        }

        // 审批通过：把这张单上的每一头都推进为「已出栏」——逐头状态才是入场查验认的结论
        if (approved) {
            for (SlaughterApplyPig member : membersOf(apply.getId())) {
                PigIndividual pig = pigIndividualMapper.selectById(member.getPigId());
                if (pig != null && (pig.getStatus() == null || pig.getStatus() == PIG_STATUS_FARMING)) {
                    pig.setStatus(PIG_STATUS_SLAUGHTERED_OUT);
                    pig.setUpdateTime(LocalDateTime.now());
                    pigIndividualMapper.updateById(pig);
                }
            }
        }
    }

    private SlaughterApplyVO convertToVO(SlaughterApply apply) {
        SlaughterApplyVO vo = new SlaughterApplyVO();
        BeanUtils.copyProperties(apply, vo);
        if (apply.getWeightKg() != null) {
            vo.setWeightKg(BigDecimal.valueOf(apply.getWeightKg()));
        }
        List<SlaughterApplyPig> members = membersOf(apply.getId());
        vo.setEarTagNos(members.stream().map(SlaughterApplyPig::getEarTagNo).toList());
        if (vo.getPigCount() == null) vo.setPigCount(members.size());
        // 兼容旧字段：单头单时把首头耳标带出来，页面对「一单一头」仍有单值可显示
        if (vo.getEarTagNo() == null && members.size() == 1) {
            vo.setEarTagNo(members.get(0).getEarTagNo());
        }
        return vo;
    }

    private List<SlaughterApplyPig> membersOf(Long applyId) {
        return applyPigMapper.selectList(Wrappers.<SlaughterApplyPig>lambdaQuery()
                .eq(SlaughterApplyPig::getApplyId, applyId));
    }

    /** 这头猪当前挂着的未完成申报（待审/已通过）；已驳回不算，允许重新申报 */
    private SlaughterApply latestOpenApplyOfPig(Long pigId) {
        List<SlaughterApplyPig> members = applyPigMapper.selectList(Wrappers.<SlaughterApplyPig>lambdaQuery()
                .eq(SlaughterApplyPig::getPigId, pigId));
        if (members.isEmpty()) return null;
        List<Long> applyIds = members.stream().map(SlaughterApplyPig::getApplyId).distinct().toList();
        List<SlaughterApply> applies = this.list(Wrappers.<SlaughterApply>lambdaQuery()
                .in(SlaughterApply::getId, applyIds)
                .in(SlaughterApply::getApprovalStatus, STATUS_PENDING, STATUS_APPROVED)
                .orderByDesc(SlaughterApply::getId)
                .last("LIMIT 1"));
        return applies.isEmpty() ? null : applies.get(0);
    }

    private List<Long> resolvePigIds(SlaughterApplyDTO dto) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (dto.getPigIds() != null) {
            dto.getPigIds().stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty() && dto.getPigId() != null) ids.add(dto.getPigId());
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "至少选择一头生猪才能申报出栏");
        }
        return new ArrayList<>(ids);
    }
}
