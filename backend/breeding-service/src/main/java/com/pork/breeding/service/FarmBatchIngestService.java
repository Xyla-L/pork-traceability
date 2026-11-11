package com.pork.breeding.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.dto.FarmBatchIngestRequest;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.entity.PigBatch;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.mapper.PigBatchMapper;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.support.PigTagRange;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 外部养殖系统（ERP / 猪场生产管理系统）批次档案同步。
 *
 * <p><b>为什么要有这条路径</b>：现实中的溯源系统不自己造养殖端数据。批次号、品种、
 * 耳标区间、进场与出生日期本来就躺在养殖场的 ERP 里，让人再手抄一遍既费人又会抄错，
 * 抄错了还没人知道——因为世上没有第二份账可以对。接过来才是正解。
 *
 * <p><b>但「接过来」不等于「相信它」</b>。三条纪律：
 * <ol>
 *   <li><b>外部身份优先</b>：幂等键是 {@code external_system + external_id}，不是批次号。
 *       两家 ERP 都叫 PB-001 是常态，按批次号认一定串场。</li>
 *   <li><b>冲突一律转人工，绝不覆盖</b>：场名对不上、批次号与本系统人工登记的重了、
 *       耳标已被别的批次占用——这些都得人来判。外部系统的数据不等于正确的数据。</li>
 *   <li><b>撤销是「源撤销」不是「删除」</b>：ERP 撤批走作废并上链，带
 *       {@code void_source=EXTERNAL} 与撤销理由；成员已出栏/屠宰时拒绝撤销，
 *       因为那些事实已经发生，外部系统撤不掉。</li>
 * </ol>
 *
 * <p>回执口径与设备通道一致：{@code {written, id, reason, retryable}}。
 * 只有 {@code written=true} 才表示真的写进了档案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmBatchIngestService {

    /** 外部系统写入的档案来源标记（沿用 14 期定义的 MANUAL/DEVICE/API/IMPORT 枚举） */
    private static final String DATA_SOURCE_API = "API";
    /** 报文里没给系统代号时的默认值 */
    private static final String DEFAULT_SYSTEM = "FARM_ERP";
    private static final String ACTION_REVOKE = "REVOKE";
    /** 冲突检查分批大小：一条 IN 里塞 2000 个耳标对 MySQL 的解析器不友好 */
    private static final int CONFLICT_CHUNK = 300;

    private final FarmMapper farmMapper;
    private final PigBatchMapper pigBatchMapper;
    private final PigIndividualMapper pigIndividualMapper;
    private final ChainEventPublisher chainEvents;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingest(FarmBatchIngestRequest req) {
        String system = StringUtils.hasText(req.getSystemCode()) ? req.getSystemCode().trim().toUpperCase() : DEFAULT_SYSTEM;
        String batchNo = trim(req.getBatchNo());
        String externalId = StringUtils.hasText(req.getExternalId()) ? req.getExternalId().trim() : batchNo;
        if (batchNo == null) return retryable("批次号(batchNo)为空，无法同步");

        String farmName = trim(req.getFarmName());
        // 场名匹配是本版的简化：真实对接应有一张对码表（外部场编码 ↔ farm_id）。
        // 宁可让人补一次映射，也不要猜「这个场大概是哪个场」——场猜错，整批猪的产地就错了。
        List<Farm> farms = farmName == null ? List.of()
                : farmMapper.selectList(new LambdaQueryWrapper<Farm>().eq(Farm::getFarmName, farmName));
        if (farms.isEmpty()) {
            return retryable("养殖场「" + farmName + "」在本系统没有登记：请先建场，或核对场名是否与 ERP 一致");
        }
        if (farms.size() > 1) {
            return retryable("养殖场「" + farmName + "」在本系统有 " + farms.size()
                    + " 个同名记录，无法确定归属，请人工核对（真实对接应改用场编码对码表）");
        }
        Farm farm = farms.get(0);

        return ACTION_REVOKE.equalsIgnoreCase(req.getAction())
                ? revoke(system, externalId, batchNo, farm, req)
                : upsert(system, externalId, batchNo, farm, req);
    }

    // ------------------------------------------------------------------ 新建 / 更新

    private Map<String, Object> upsert(String system, String externalId, String batchNo, Farm farm,
                                       FarmBatchIngestRequest req) {
        List<String> tags;
        try {
            tags = resolveTags(req);
        } catch (BusinessException e) {
            // 区间解析不成立（前缀不一致、止号小于起号、超上限）是确定性错误，让外部系统改数据而不是进人工队列
            return rejected(e.getMessage());
        }
        if (tags.isEmpty()) return retryable("报文既没有耳标区间也没有耳标清单，无法确定批次成员");
        if (tags.size() > PigTagRange.MAX_BATCH_SIZE) {
            return rejected("单批次最多 " + PigTagRange.MAX_BATCH_SIZE + " 头，本次推送 " + tags.size() + " 头，请核对后重发");
        }
        LocalDate entryDate;
        LocalDate birthDate;
        try {
            entryDate = parseDate(req.getEntryDate(), "进场日期");
            birthDate = parseDate(req.getBirthDate(), "出生日期");
        } catch (BusinessException e) {
            return rejected(e.getMessage());
        }

        PigBatch existing = pigBatchMapper.selectByExternal(system, externalId);
        if (existing != null && Integer.valueOf(1).equals(existing.getVoidFlag())) {
            return retryable("该批次已在本系统被撤销（" + nullToDash(existing.getVoidReason())
                    + "），源系统再次推送不会自动恢复，请人工核对后再决定是否重新登记");
        }

        return existing == null
                ? create(system, externalId, batchNo, farm, req, tags, entryDate, birthDate)
                : update(system, externalId, existing, batchNo, farm, req, tags, entryDate, birthDate);
    }

    private Map<String, Object> create(String system, String externalId, String batchNo, Farm farm,
                                       FarmBatchIngestRequest req, List<String> tags,
                                       LocalDate entryDate, LocalDate birthDate) {
        // 批次号撞车：唯一键 uk_batch 是「场 + 批次号」且连作废行一起算，
        // 硬插只会给外部系统回一个 Duplicate entry。这里提前查出来，转人工说人话。
        PigBatch sameNo = pigBatchMapper.selectAnyByBatchNo(farm.getId(), batchNo);
        if (sameNo != null) {
            String who = sameNo.getExternalSystem() == null
                    ? "本系统人工登记的批次"
                    : "外部系统 " + sameNo.getExternalSystem() + " 的批次";
            return retryable("养殖场「" + farm.getFarmName() + "」已有同名批次号 " + batchNo + "（"
                    + who + (Integer.valueOf(1).equals(sameNo.getVoidFlag()) ? "，已作废" : "")
                    + "）：两个批次不能共用一个号，请人工核对后再同步");
        }
        List<String> conflicts = conflicts(tags, null);
        if (!conflicts.isEmpty()) {
            return retryable("耳标 " + sample(conflicts) + " 已被其他批次或历史档案占用（一头猪只能属于一个批次），请人工核对");
        }

        LocalDateTime now = LocalDateTime.now();
        PigBatch batch = new PigBatch();
        batch.setBatchNo(batchNo);
        batch.setFarmId(farm.getId());
        batch.setBreed(trim(req.getBreed()));
        batch.setEntryDate(entryDate);
        batch.setBirthDate(birthDate);
        batch.setEarTagStart(tags.get(0));
        batch.setEarTagEnd(tags.get(tags.size() - 1));
        batch.setQuantity(tags.size());
        batch.setSource(trim(req.getSource()));
        batch.setExternalSystem(system);
        batch.setExternalId(externalId);
        batch.setDataSource(DATA_SOURCE_API);
        batch.setSourceRef(truncate("养殖系统 " + system + "：" + externalId, 128));
        batch.setRawPayload(truncate(req.getRawPayload(), 60000));
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        pigBatchMapper.insert(batch);

        int inserted = insertMembers(batch, batch.getBreed(), batch.getBirthDate(), tags, now);
        log.info("farm_batch_created system={} externalId={} batchNo={} members={} farm={}",
                system, externalId, batchNo, inserted, farm.getId());
        return written(batch.getId(), "已新建批次 " + batchNo + "（" + inserted + " 头）");
    }

    private Map<String, Object> update(String system, String externalId, PigBatch existing, String batchNo,
                                       Farm farm, FarmBatchIngestRequest req, List<String> tags,
                                       LocalDate entryDate, LocalDate birthDate) {
        // 批次号被外部改过：新号如果已经被同场别的批次占了，转人工 —— 改号是一次人工决策，
        // 不是接口能替人做的（下游单据可能已经印了旧号）。
        if (!existing.getBatchNo().equals(batchNo)) {
            PigBatch sameNo = pigBatchMapper.selectAnyByBatchNo(farm.getId(), batchNo);
            if (sameNo != null && !sameNo.getId().equals(existing.getId())) {
                return retryable("外部系统把批次号改为 " + batchNo + "，但该号已被本场另一批次占用，请人工核对");
            }
        }
        Long batchId = existing.getId();
        List<String> conflicts = conflicts(tags, batchId);
        if (!conflicts.isEmpty()) {
            return retryable("耳标 " + sample(conflicts) + " 已被其他批次占用，本次推送的成员无法全部归入 "
                    + batchNo + "，请人工核对");
        }

        LocalDateTime now = LocalDateTime.now();
        PigBatch patch = new PigBatch();
        patch.setId(batchId);
        patch.setBatchNo(batchNo);
        patch.setBreed(trim(req.getBreed()));
        patch.setEntryDate(entryDate);
        patch.setBirthDate(birthDate);
        patch.setSource(trim(req.getSource()));
        patch.setRawPayload(truncate(req.getRawPayload(), 60000));
        patch.setUpdateTime(now);
        // 空字段跳过（MyBatis-Plus 默认不更新 null）—— ERP 少给一个字段不该把已有值抹掉。
        // 注意这里刻意不动 ear_tag_start/end：成员才是权威，起止号在下面按实际成员重算。
        pigBatchMapper.updateById(patch);

        int inserted = insertMembers(existing,
                patch.getBreed() != null ? patch.getBreed() : existing.getBreed(),
                birthDate != null ? birthDate : existing.getBirthDate(), tags, now);
        // 数量与耳标范围以成员行为准重算：只在创建时写一次的话，列表页会一直显示登记那天的数字
        Map<String, Object> summary = pigIndividualMapper.batchMemberSummary(batchId);
        long members = summary == null || summary.get("cnt") == null ? 0
                : ((Number) summary.get("cnt")).longValue();
        pigBatchMapper.update(null, Wrappers.<PigBatch>lambdaUpdate()
                .eq(PigBatch::getId, batchId)
                .set(PigBatch::getQuantity, (int) members)
                .set(PigBatch::getEarTagStart, summary == null ? null : summary.get("minTag"))
                .set(PigBatch::getEarTagEnd, summary == null ? null : summary.get("maxTag"))
                .set(PigBatch::getUpdateTime, now));
        log.info("farm_batch_updated system={} externalId={} batchNo={} added={} members={}",
                system, externalId, batchNo, inserted, members);
        return written(batchId, inserted > 0
                ? "已更新批次 " + batchNo + "（新增 " + inserted + " 头，共 " + members + " 头）"
                : "已更新批次 " + batchNo + "（成员无变化，共 " + members + " 头）");
    }

    /** 补齐成员：只增不删 —— 少了的那几头是出栏/淘汰，由个体状态体现，不是「从批次里消失」 */
    private int insertMembers(PigBatch batch, String breed, LocalDate birthDate, List<String> tags,
                              LocalDateTime now) {
        List<String> have = pigIndividualMapper.selectList(new LambdaQueryWrapper<PigIndividual>()
                        .select(PigIndividual::getEarTagNo)
                        .eq(PigIndividual::getBatchId, batch.getId()))
                .stream().map(PigIndividual::getEarTagNo).collect(Collectors.toList());
        List<String> missing = tags.stream().filter(tag -> !have.contains(tag)).toList();
        for (String tag : missing) {
            PigIndividual member = new PigIndividual();
            member.setEarTagNo(tag);
            member.setBatchId(batch.getId());
            member.setFarmId(batch.getFarmId());
            member.setBreed(breed);
            member.setBirthDate(birthDate);
            member.setSource(batch.getSource());
            member.setStatus(1);
            member.setDataSource(DATA_SOURCE_API);
            member.setSourceRef(truncate("养殖系统同步：" + batch.getBatchNo(), 64));
            member.setCreateTime(now);
            member.setUpdateTime(now);
            member.setVoidFlag(0);
            member.setDeleted(0);
            pigIndividualMapper.insert(member);
        }
        return missing.size();
    }

    // ------------------------------------------------------------------ 源撤销

    private Map<String, Object> revoke(String system, String externalId, String batchNo, Farm farm,
                                       FarmBatchIngestRequest req) {
        String reason = trim(req.getVoidReason());
        if (reason == null) return rejected("撤销批次必须写明理由");
        PigBatch batch = pigBatchMapper.selectByExternal(system, externalId);
        if (batch == null) {
            return retryable("外部系统要求撤销的批次在本系统不存在（" + batchNo
                    + "）：可能从未同步成功，请先补推一次批次档案");
        }
        if (Integer.valueOf(1).equals(batch.getVoidFlag())) {
            // 重复撤销按幂等处理：外部系统重推撤销是常态，不该报错也不该再写一条链上事件
            return written(batch.getId(), "批次 " + batchNo + " 已是作废状态（幂等返回）");
        }
        long downstream = pigIndividualMapper.selectCount(new LambdaQueryWrapper<PigIndividual>()
                .eq(PigIndividual::getBatchId, batch.getId())
                .ge(PigIndividual::getStatus, 2));
        if (downstream > 0) {
            return retryable("批次 " + batchNo + " 内已有 " + downstream
                    + " 头出栏或屠宰：这些事实已经发生，不能按外部撤销处理，请人工核对后逐头处置");
        }
        String operator = truncate("养殖系统(" + system + ")", 32);
        if (pigBatchMapper.voidByExternal(batch.getId(), reason, operator) == 0) {
            return retryable("批次 " + batchNo + " 作废失败：可能刚被他人改成作废状态，请重试");
        }
        int members = pigIndividualMapper.voidByBatchId(batch.getId(), "源系统 " + system + " 撤销批次：" + reason, operator);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", batch.getBatchNo());
        payload.put("farmId", batch.getFarmId());
        payload.put("quantity", batch.getQuantity());
        payload.put("voidedMembers", members);
        payload.put("action", "REVOKE");
        payload.put("voidSource", "EXTERNAL");
        payload.put("externalSystem", system);
        payload.put("externalId", externalId);
        payload.put("voidReason", reason);
        payload.put("operator", operator);
        Map<String, Object> hashSource = new HashMap<>(payload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("PIG_BATCH_VOID", batch.getId(), batch.getBatchNo() + ":REVOKE",
                HashUtil.sha256Json(hashSource), payload));
        log.info("farm_batch_revoked system={} externalId={} batchNo={} members={} reason={}",
                system, externalId, batchNo, members, reason);
        return written(batch.getId(), "已按源系统撤销批次 " + batchNo + "（连带作废 " + members + " 头成员）");
    }

    // ------------------------------------------------------------------ 内部工具

    /** 成员清单：明细优先，其次区间展开 */
    private List<String> resolveTags(FarmBatchIngestRequest req) {
        List<String> list = new ArrayList<>(new LinkedHashSet<>(req.getEarTags() == null ? List.of()
                : req.getEarTags().stream().filter(StringUtils::hasText).map(String::trim).toList()));
        if (!list.isEmpty()) return list;
        String start = trim(req.getEarTagStart());
        String end = trim(req.getEarTagEnd());
        if (start == null || end == null) return List.of();
        try {
            return PigTagRange.of(start, end).members();
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, e.getMessage());
        }
    }

    /**
     * 找出「已被别人占用」的耳标。{@code excludeBatchId} 是当前批次（更新时要排除自己的成员）。
     * 查的是含作废、含逻辑删除的全表——唯一键 uk_ear_tag 就是全表算的。
     */
    private List<String> conflicts(List<String> tags, Long excludeBatchId) {
        List<String> out = new ArrayList<>();
        for (int from = 0; from < tags.size(); from += CONFLICT_CHUNK) {
            List<String> chunk = tags.subList(from, Math.min(from + CONFLICT_CHUNK, tags.size()));
            for (PigIndividual row : pigIndividualMapper.selectAnyByEarTags(chunk, 20)) {
                boolean mine = excludeBatchId != null && excludeBatchId.equals(row.getBatchId())
                        && !Integer.valueOf(1).equals(row.getVoidFlag());
                if (!mine) out.add(row.getEarTagNo());
            }
            if (out.size() >= 5) break;
        }
        return out;
    }

    private String sample(List<String> tags) {
        return tags.stream().limit(3).collect(Collectors.joining("、")) + (tags.size() > 3 ? " 等 " + tags.size() + " 个" : "");
    }

    private LocalDate parseDate(String raw, String label) {
        String text = trim(raw);
        if (text == null) return null;
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    label + "格式不成立：" + text + "（只接受 yyyy-MM-dd）");
        }
    }

    private Map<String, Object> written(Long id, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("written", true);
        result.put("id", id);
        result.put("reason", reason);
        result.put("retryable", false);
        return result;
    }

    private Map<String, Object> retryable(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("written", false);
        result.put("reason", reason);
        result.put("retryable", true);
        return result;
    }

    private Map<String, Object> rejected(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("written", false);
        result.put("reason", reason);
        result.put("retryable", false);
        return result;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String nullToDash(String value) {
        return value == null ? "-" : value;
    }
}
