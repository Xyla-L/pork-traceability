package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.QuarantineCertPig;
import com.pork.breeding.entity.QuarantineCertificate;
import com.pork.breeding.entity.SlaughterApply;
import com.pork.breeding.entity.SlaughterApplyPig;
import com.pork.breeding.entity.VaccineBatch;
import com.pork.breeding.entity.VaccineBatchPig;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.QuarantineCertMapper;
import com.pork.breeding.mapper.QuarantineCertPigMapper;
import com.pork.breeding.mapper.SlaughterApplyMapper;
import com.pork.breeding.mapper.SlaughterApplyPigMapper;
import com.pork.breeding.mapper.VaccineBatchMapper;
import com.pork.breeding.mapper.VaccineBatchPigMapper;
import com.pork.breeding.mapper.VaccineRecordMapper;
import com.pork.breeding.service.RecordGroupService;
import com.pork.breeding.support.FarmScope;
import com.pork.breeding.vo.RecordGroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 记录列表（单据视图）。
 * <p>
 * 2026-11-15 之前这里做的是「纯展示层聚合」：不建单据表，查询时按
 * 「养殖批次 + 动作签名 + 日期」把逐头记录归成一组，批次号是算出来的
 * （当时注释里写着「数据一多就该换真单据表」）。
 * 现在三个模块都落了真单据（免疫批次 / 检疫证 / 申报单），主行就是单据本身：
 * 一次免疫、一张证、一份申报单，各自有自己的批次号，成员名单来自成员表。
 * <p>
 * 分页也从「内存归组 + 截断」改成数据库分页——单据有主键，翻页是准的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordGroupServiceImpl implements RecordGroupService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final VaccineBatchMapper vaccineBatchMapper;
    private final VaccineBatchPigMapper vaccineBatchPigMapper;
    private final VaccineRecordMapper vaccineRecordMapper;
    private final QuarantineCertMapper quarantineCertMapper;
    private final QuarantineCertPigMapper certPigMapper;
    private final SlaughterApplyMapper slaughterApplyMapper;
    private final SlaughterApplyPigMapper applyPigMapper;
    private final PigIndividualMapper pigMapper;
    private final FarmMapper farmMapper;
    private final FarmScope farmScope;

    // ==================== 疫苗 ====================

    @Override
    public Page<RecordGroupVO> pageVaccine(Long current, Long size, String earTagNo,
                                           String vaccineName, boolean includeVoid) {
        Long farmId = farmScope.scopedFarmId();
        // 按耳标过滤：耳标在成员表上，先反查批次 id 再下推，避免整表扫
        List<Long> narrowed = null;
        if (StringUtils.hasText(earTagNo)) {
            narrowed = vaccineBatchPigMapper.selectList(Wrappers.<VaccineBatchPig>lambdaQuery()
                            .eq(VaccineBatchPig::getEarTagNo, earTagNo.trim()))
                    .stream().map(VaccineBatchPig::getBatchId).distinct().toList();
            if (narrowed.isEmpty()) return new Page<>(current, size);
        }
        Page<VaccineBatch> page = vaccineBatchMapper.selectPage(new Page<>(current, size),
                Wrappers.<VaccineBatch>lambdaQuery()
                        .eq(farmId != null, VaccineBatch::getFarmId, farmId)
                        .like(StringUtils.hasText(vaccineName), VaccineBatch::getVaccineName,
                                vaccineName == null ? null : vaccineName.trim())
                        .eq(!includeVoid, VaccineBatch::getVoidFlag, 0)
                        .in(narrowed != null, VaccineBatch::getId, narrowed)
                        .orderByDesc(VaccineBatch::getInjectTime)
                        .orderByDesc(VaccineBatch::getId));

        List<VaccineBatch> batches = page.getRecords();
        Page<RecordGroupVO> out = new Page<>(current, size);
        out.setTotal(page.getTotal());
        if (batches.isEmpty()) return out;

        List<Long> batchIds = batches.stream().map(VaccineBatch::getId).toList();
        Map<Long, List<VaccineBatchPig>> memberMap = vaccineBatchPigMapper.selectList(
                        Wrappers.<VaccineBatchPig>lambdaQuery().in(VaccineBatchPig::getBatchId, batchIds))
                .stream().collect(Collectors.groupingBy(VaccineBatchPig::getBatchId));
        // 含已作废：批次名单要如实显示「这头作废了」，不能被逻辑删除悄悄过滤掉
        Map<Long, List<VaccineRecord>> recordMap = vaccineRecordMapper.selectByBatchIdsIncludeVoid(batchIds)
                .stream().collect(Collectors.groupingBy(VaccineRecord::getVbatchId));
        Map<Long, PigIndividual> pigMap = loadPigs(memberMap.values().stream()
                .flatMap(List::stream).map(VaccineBatchPig::getPigId).toList());
        Map<Long, String> farmNames = loadFarmNames(batches.stream().map(VaccineBatch::getFarmId).toList());

        List<RecordGroupVO> rows = new ArrayList<>();
        for (VaccineBatch b : batches) {
            List<VaccineBatchPig> members = memberMap.getOrDefault(b.getId(), List.of());
            List<VaccineRecord> records = recordMap.getOrDefault(b.getId(), List.of());
            Map<Long, VaccineRecord> recordByPig = records.stream()
                    .collect(Collectors.toMap(VaccineRecord::getPigId, Function.identity(), (a, c) -> a));

            List<Map<String, Object>> memberRows = new ArrayList<>();
            int voided = 0;
            for (VaccineBatchPig m : members) {
                VaccineRecord r = recordByPig.get(m.getPigId());
                boolean isVoid = (r != null && r.getVoidFlag() != null && r.getVoidFlag() == 1)
                        || (r == null && m.getVoidFlag() != null && m.getVoidFlag() == 1);
                if (isVoid) voided++;
                Map<String, Object> row = new LinkedHashMap<>();
                // id/pigId 是前端作废与查看凭证的参数：作废走的是逐头免疫记录（事实表），不是批次
                row.put("id", r == null ? null : r.getId());
                row.put("pigId", m.getPigId());
                row.put("earTagNo", m.getEarTagNo());
                row.put("vaccineName", b.getVaccineName());
                row.put("batchNo", b.getProdBatchNo());
                row.put("injectTime", r == null ? null : r.getInjectTime());
                row.put("dosage", r == null ? null : r.getDosage());
                row.put("operator", r == null ? null : r.getOperator());
                row.put("source", r == null ? null : r.getSource());
                row.put("sourceRef", r == null ? null : r.getSourceRef());
                row.put("fileIds", r == null ? null : r.getFileIds());
                row.put("voidFlag", isVoid ? 1 : 0);
                row.put("voidReason", r == null ? null : r.getVoidReason());
                row.put("voidOperator", r == null ? null : r.getVoidOperator());
                row.put("voidTime", r == null ? null : r.getVoidTime());
                memberRows.add(row);
            }

            RecordGroupVO g = baseGroup("VACCINE|" + b.getId(), "VACCINE", b.getFarmId(), farmNames, pigMap, memberRows);
            g.setDocNo(b.getBatchNo());
            g.setTitle(b.getVaccineName());
            g.setSubTitle(StringUtils.hasText(b.getProdBatchNo()) ? "疫苗批号 " + b.getProdBatchNo() : b.getManufacturer());
            g.setHappenedOn(dayText(b.getInjectTime()));
            g.setOperator(b.getOperator());
            g.setVoidCount(voided);
            if (voided == 0) {
                g.setStatusSummary("全部有效");
                g.setStatusDetail(members.size() + " 头全部有效");
            } else if (voided >= members.size()) {
                g.setStatusSummary("全部作废");
                g.setStatusDetail("已作废 " + voided + " 头");
            } else {
                g.setStatusSummary("部分作废");
                g.setStatusDetail("有效 " + (members.size() - voided) + " / 已作废 " + voided);
            }
            rows.add(g);
        }
        out.setRecords(rows);
        return out;
    }

    // ==================== 产地检疫 ====================

    @Override
    public Page<RecordGroupVO> pageCert(Long current, Long size, String earTagNo, String certState) {
        Long farmId = farmScope.scopedFarmId();
        Page<QuarantineCertificate> page = quarantineCertMapper.selectDocPage(new Page<>(current, size),
                farmId, StringUtils.hasText(earTagNo) ? earTagNo.trim() : null);
        List<QuarantineCertificate> certs = page.getRecords();
        Page<RecordGroupVO> out = new Page<>(current, size);
        out.setTotal(page.getTotal());
        if (certs.isEmpty()) return out;

        List<Long> certIds = certs.stream().map(QuarantineCertificate::getId).toList();
        Map<Long, List<QuarantineCertPig>> memberMap = certPigMapper.selectList(
                        Wrappers.<QuarantineCertPig>lambdaQuery().in(QuarantineCertPig::getCertId, certIds))
                .stream().collect(Collectors.groupingBy(QuarantineCertPig::getCertId));
        Map<Long, PigIndividual> pigMap = loadPigs(memberMap.values().stream()
                .flatMap(List::stream).map(QuarantineCertPig::getPigId).toList());
        Map<Long, String> farmNames = loadFarmNames(certs.stream().map(QuarantineCertificate::getFarmId).toList());

        List<RecordGroupVO> rows = new ArrayList<>();
        for (QuarantineCertificate c : certs) {
            List<QuarantineCertPig> members = memberMap.getOrDefault(c.getId(), List.of());
            List<Map<String, Object>> memberRows = new ArrayList<>();
            for (QuarantineCertPig m : members) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", c.getId());
                row.put("pigId", m.getPigId());
                row.put("earTagNo", m.getEarTagNo());
                row.put("certNo", c.getCertNo());
                row.put("issueTime", c.getIssueTime());
                row.put("validUntil", c.getValidUntil());
                row.put("inspector", c.getInspector());
                row.put("source", c.getSource());
                row.put("sourceRef", c.getSourceRef());
                row.put("voidFlag", c.getVoidFlag() == null ? 0 : c.getVoidFlag());
                row.put("voidReason", c.getVoidReason());
                row.put("voidOperator", c.getVoidOperator());
                row.put("voidTime", c.getVoidTime());
                memberRows.add(row);
            }

            RecordGroupVO g = baseGroup("CERT|" + c.getId(), "CERT", c.getFarmId(), farmNames, pigMap, memberRows);
            g.setDocNo(c.getCertNo());
            g.setTitle(c.getCertNo());
            g.setSubTitle(c.getIssueOrg());
            g.setHappenedOn(dayText(c.getIssueTime()));
            g.setOperator(c.getInspector());
            boolean isVoid = c.getVoidFlag() != null && c.getVoidFlag() == 1;
            g.setVoidCount(isVoid ? 1 : 0);
            if (isVoid) {
                g.setStatusSummary("已作废");
                g.setStatusDetail("作废理由：" + fallback(c.getVoidReason()));
            } else if (isExpired(c.getValidUntil())) {
                g.setStatusSummary("已过期");
                g.setStatusDetail("有效期至 " + c.getValidUntil());
            } else {
                g.setStatusSummary("有效");
                g.setStatusDetail(c.getValidUntil() == null ? "未登记有效期" : "有效期至 " + c.getValidUntil());
            }
            rows.add(g);
        }

        // 状态过滤（与本页口径一致；精确按状态翻页由 SQL 承担，这里只收窄展示）
        if ("valid".equalsIgnoreCase(certState)) {
            rows.removeIf(g -> !"有效".equals(g.getStatusSummary()));
        } else if ("expired".equalsIgnoreCase(certState)) {
            rows.removeIf(g -> !"已过期".equals(g.getStatusSummary()));
        }
        out.setRecords(rows);
        return out;
    }

    // ==================== 出栏申报 ====================

    @Override
    public Page<RecordGroupVO> pageApply(Long current, Long size, Integer approvalStatus) {
        Long farmId = farmScope.scopedFarmId();
        Page<SlaughterApply> page = slaughterApplyMapper.selectDocPage(new Page<>(current, size), farmId, approvalStatus);
        List<SlaughterApply> applies = page.getRecords();
        Page<RecordGroupVO> out = new Page<>(current, size);
        out.setTotal(page.getTotal());
        if (applies.isEmpty()) return out;

        List<Long> applyIds = applies.stream().map(SlaughterApply::getId).toList();
        Map<Long, List<SlaughterApplyPig>> memberMap = applyPigMapper.selectList(
                        Wrappers.<SlaughterApplyPig>lambdaQuery().in(SlaughterApplyPig::getApplyId, applyIds))
                .stream().collect(Collectors.groupingBy(SlaughterApplyPig::getApplyId));
        Map<Long, PigIndividual> pigMap = loadPigs(memberMap.values().stream()
                .flatMap(List::stream).map(SlaughterApplyPig::getPigId).toList());
        Map<Long, String> farmNames = loadFarmNames(applies.stream().map(SlaughterApply::getFarmId).toList());

        List<RecordGroupVO> rows = new ArrayList<>();
        for (SlaughterApply a : applies) {
            List<SlaughterApplyPig> members = memberMap.getOrDefault(a.getId(), List.of());
            List<Map<String, Object>> memberRows = new ArrayList<>();
            for (SlaughterApplyPig m : members) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", a.getId());
                row.put("pigId", m.getPigId());
                row.put("earTagNo", m.getEarTagNo());
                row.put("applyNo", a.getApplyNo());
                row.put("applyTime", a.getApplyTime());
                row.put("weightKg", m.getWeightKg());
                row.put("approvalStatus", a.getApprovalStatus());
                row.put("approver", a.getApprover());
                row.put("rejectReason", a.getRejectReason());
                row.put("source", a.getSource());
                row.put("sourceRef", a.getSourceRef());
                memberRows.add(row);
            }

            RecordGroupVO g = baseGroup("APPLY|" + a.getId(), "APPLY", a.getFarmId(), farmNames, pigMap, memberRows);
            g.setDocNo(a.getApplyNo());
            g.setTitle(a.getApplyNo());
            g.setSubTitle(a.getTargetSlaughterhouse());
            g.setHappenedOn(dayText(a.getApplyTime()));
            g.setOperator(a.getApprover());
            Integer st = a.getApprovalStatus();
            if (st != null && st == 1) {
                g.setStatusSummary("已通过");
                g.setStatusDetail("审批人 " + fallback(a.getApprover()));
            } else if (st != null && st == 2) {
                g.setStatusSummary("已驳回");
                g.setStatusDetail("驳回原因 " + fallback(a.getRejectReason()));
            } else {
                g.setStatusSummary("待审批");
                g.setStatusDetail("共 " + members.size() + " 头待审批");
            }
            rows.add(g);
        }
        out.setRecords(rows);
        return out;
    }

    // ==================== 单据骨架 ====================

    /**
     * 主行公共字段：本部分批次号由各方法设置，这里只填「这单覆盖了哪几头、属于哪个场、什么品种」。
     * 分页与排序一律由数据库完成——单据有主键，翻页不再靠内存截断。
     */
    private RecordGroupVO baseGroup(String key, String kind, Long farmId,
                                    Map<Long, String> farmNames,
                                    Map<Long, PigIndividual> pigMap,
                                    List<Map<String, Object>> memberRows) {
        RecordGroupVO g = new RecordGroupVO();
        g.setGroupKey(key);
        g.setKind(kind);
        g.setFarmId(farmId);
        g.setFarmName(farmId == null ? null : farmNames.get(farmId));
        g.setHeadCount(memberRows.size());
        g.setMembers(memberRows);
        // 品种取首头（同批同品种是常态）；不强求一致，成员表里逐头可查
        g.setBreed(memberRows.stream()
                .map(m -> pigMap.get((Long) m.get("pigId")))
                .filter(Objects::nonNull)
                .map(PigIndividual::getBreed)
                .filter(StringUtils::hasText)
                .findFirst().orElse(null));
        return g;
    }

    private Map<Long, PigIndividual> loadPigs(List<Long> pigIds) {
        List<Long> ids = pigIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return pigMapper.selectList(Wrappers.<PigIndividual>lambdaQuery().in(PigIndividual::getId, ids))
                .stream().collect(Collectors.toMap(PigIndividual::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> loadFarmNames(List<Long> farmIds) {
        List<Long> ids = farmIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return new HashMap<>();
        return farmMapper.selectList(Wrappers.<Farm>lambdaQuery().in(Farm::getId, ids))
                .stream().collect(Collectors.toMap(Farm::getId, Farm::getFarmName, (a, b) -> a));
    }

    private boolean isExpired(LocalDate validUntil) {
        return validUntil != null && validUntil.isBefore(LocalDate.now());
    }

    /** 列表只显示到日：同一天多次免疫靠批次号区分，不需要秒级时间挤在同一列 */
    private String dayText(LocalDateTime time) {
        return time == null ? null : time.toLocalDate().format(DAY);
    }

    private String fallback(String value) {
        return StringUtils.hasText(value) ? value : "未记录";
    }
}
