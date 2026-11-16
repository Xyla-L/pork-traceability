package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.QuarantineCertDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.QuarantineCertPig;
import com.pork.breeding.entity.QuarantineCertificate;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.QuarantineCertMapper;
import com.pork.breeding.mapper.QuarantineCertPigMapper;
import com.pork.breeding.service.QuarantineCertService;
import com.pork.breeding.support.ImmunityGate;
import com.pork.breeding.vo.QuarantineCertVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.source.ManualOperator;
import com.pork.core.source.TraceStage;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.web.source.SourceGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 产地检疫出证。
 * <p>
 * 2026-11-15 起一张证覆盖多头（一证多头）：证是单据，成员在 {@code quarantine_cert_pig}。
 * 现实里产地检疫证按批出证、证面有「数量」栏，逐头一证会让同一车猪出现 N 个证号，
 * 装车核对时反而对不上。逐头的前提校验一个都没少：每头都要过「已屠宰 / 免疫档案」这一关。
 */
@Service
@RequiredArgsConstructor
public class QuarantineCertServiceImpl extends ServiceImpl<QuarantineCertMapper, QuarantineCertificate> implements QuarantineCertService {

    /** 生猪状态：3 已屠宰（已屠宰的猪不存在「调运」这件事，再出证就是凭空造凭据） */
    private static final int PIG_STATUS_SLAUGHTERED = 3;

    private final PigIndividualMapper pigMapper;
    private final QuarantineCertPigMapper certPigMapper;
    private final ChainEventPublisher chainEvents;
    private final SourceGuard sourceGuard;
    private final ImmunityGate immunityGate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadCert(QuarantineCertDTO dto) {
        // 来源管控：产地检疫以扫码登记终端为主路径；设备覆盖不到时的人工出证须实名并上链留痕；
        // 出证人必须落到具体人（未填则回填当前登录人），保证链上能追到人，
        // 且这个人只能是证面签发的官方兽医——扫码终端是工具，不得代签
        ManualOperator operator = sourceGuard.requireManualAllowed(TraceStage.QUARANTINE);

        List<Long> pigIds = resolvePigIds(dto);
        List<PigIndividual> pigs = new ArrayList<>();
        for (Long pigId : pigIds) {
            PigIndividual pig = pigMapper.selectById(pigId);
            if (pig == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在：" + pigId);
            pigs.add(pig);
        }
        // 一张证只能覆盖同一个场的猪：跨场出证意味着两个场的生猪混在一张凭据上，调运与追责都说不清
        Long farmId = pigs.get(0).getFarmId();
        for (PigIndividual pig : pigs) {
            if (farmId != null && pig.getFarmId() != null && !farmId.equals(pig.getFarmId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "一张检疫证只能覆盖同一个养殖场的生猪（" + pig.getEarTagNo() + " 不属于本场）");
            }
        }

        // 前置一：证面时间必须自洽 —— 有效期早于签发日的证是自相矛盾的，
        // 下游入场查验会拿它当有效凭据放行，必须在落库前挡住
        if (dto.getValidUntil() != null && dto.getIssueTime() != null
                && dto.getValidUntil().isBefore(dto.getIssueTime().toLocalDate())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "检疫证有效期 " + dto.getValidUntil() + " 早于签发时间 " + dto.getIssueTime().toLocalDate()
                            + "，证面数据不成立");
        }

        for (PigIndividual pig : pigs) {
            // 前置二：已屠宰的猪不再有「调运」这件事，给它出产地检疫证等于凭空造凭据
            if (pig.getStatus() != null && pig.getStatus() == PIG_STATUS_SLAUGHTERED) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "生猪 " + pig.getEarTagNo() + " 已屠宰，不能再签发产地检疫证（产地检疫是调运前置，猪已不在场）");
            }
            // 前置三：产地检疫须查验强制免疫档案 —— 判定口径见 ImmunityGate，逐头校验一头都不能少
            String immunityBlock = immunityGate.blockReason(pig.getId(), dto.getIssueTime());
            if (immunityBlock != null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "生猪 " + pig.getEarTagNo() + "：" + immunityBlock);
            }
        }

        long dupCert = this.count(Wrappers.<QuarantineCertificate>lambdaQuery()
                .eq(QuarantineCertificate::getCertNo, dto.getCertNo()));
        if (dupCert > 0) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "检疫证编号已存在：" + dto.getCertNo());
        }
        // 一头猪同时只能持有一张有效证：放开 uk_pig 之后，这条唯一性改由成员表保证
        List<String> held = new ArrayList<>();
        for (PigIndividual pig : pigs) {
            QuarantineCertificate active = activeCertOfPig(pig.getId());
            if (active != null) held.add(pig.getEarTagNo() + "（" + active.getCertNo() + "）");
        }
        if (!held.isEmpty()) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS,
                    "以下生猪已有有效检疫证，需先作废原证再重新出证：" + String.join("、", held));
        }

        QuarantineCertificate cert = new QuarantineCertificate();
        BeanUtils.copyProperties(dto, cert);
        cert.setFarmId(farmId);
        cert.setPigCount(pigs.size());
        cert.setCertType("产地检疫");
        if (!StringUtils.hasText(cert.getInspector())) {
            cert.setInspector(operator.display());
        }
        cert.setCreateTime(LocalDateTime.now());
        cert.setContentHash(HashUtil.sha256Json(dto));
        // 来源标记：本路径是官方兽医在系统内人工出证（扫码登记走的是内部设备通道，那边记 DEVICE）
        cert.setSource("MANUAL");
        if (!this.save(cert)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "检疫证保存失败");

        List<String> earTags = new ArrayList<>();
        for (PigIndividual pig : pigs) {
            QuarantineCertPig member = new QuarantineCertPig();
            member.setCertId(cert.getId());
            member.setPigId(pig.getId());
            member.setEarTagNo(pig.getEarTagNo());
            member.setCreateTime(LocalDateTime.now());
            certPigMapper.insert(member);
            earTags.add(pig.getEarTagNo());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certNo", cert.getCertNo());
        payload.put("pigIds", pigIds);
        payload.put("earTagNos", earTags);
        payload.put("pigCount", pigs.size());
        payload.put("issueOrg", cert.getIssueOrg());
        payload.put("inspector", cert.getInspector());
        payload.put("operator", operator.display());
        chainEvents.publish(ChainTxMessage.create("QUARANTINE_CERT", cert.getId(), cert.getCertNo(),
                cert.getContentHash(), payload));
    }

    /**
     * 作废（原「删除」）：检疫证是生猪调运的通行依据，删掉一张证书，下游的入场查验就失去了核对对象，
     * 所以这里只允许作废，理由与操作人必须留档并上链。
     * <p>
     * 作废是<b>整证</b>的：证是单据，单据作废即整张证失效，成员不再单独作废
     * （只去掉其中一头属于「证面与实物不符」，应整证作废后重新出证）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidCert(Long pigId, Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.QUARANTINE, reason);
        String cleanReason = reason.trim();
        QuarantineCertificate cert = this.getById(id);
        if (cert == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "检疫证明不存在或已作废");
        // pigId 参数保留以兼容旧入口：若传了，必须确实是这张证的成员，避免「用 A 猪的路径作废 B 猪的证」
        if (pigId != null && !isMember(id, pigId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "检疫证明未覆盖指定生猪");
        }
        String operator = voidBy.display();
        if (baseMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：证书可能已被他人作废");
        }
        List<QuarantineCertPig> members = membersOf(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certNo", cert.getCertNo());
        payload.put("pigIds", members.stream().map(QuarantineCertPig::getPigId).toList());
        payload.put("earTagNos", members.stream().map(QuarantineCertPig::getEarTagNo).toList());
        payload.put("issueOrg", cert.getIssueOrg());
        payload.put("action", "VOID");
        payload.put("voidReason", cleanReason);
        payload.put("operator", operator);
        Map<String, Object> hashSource = new LinkedHashMap<>(payload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("QUARANTINE_CERT_VOID", id, cert.getCertNo() + ":VOID",
                HashUtil.sha256Json(hashSource), payload));
    }

    @Override
    public QuarantineCertVO getCertByPigId(Long pigId) {
        QuarantineCertificate cert = activeCertOfPig(pigId);
        if (cert == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "未找到该生猪的检疫证明");
        }
        QuarantineCertVO vo = new QuarantineCertVO();
        BeanUtils.copyProperties(cert, vo);
        List<QuarantineCertPig> members = membersOf(cert.getId());
        vo.setPigIds(members.stream().map(QuarantineCertPig::getPigId).toList());
        vo.setEarTagNos(members.stream().map(QuarantineCertPig::getEarTagNo).toList());
        return vo;
    }

    /** 这头猪当前持有的有效检疫证（未作废）；没有返回 null */
    private QuarantineCertificate activeCertOfPig(Long pigId) {
        List<QuarantineCertPig> members = certPigMapper.selectList(Wrappers.<QuarantineCertPig>lambdaQuery()
                .eq(QuarantineCertPig::getPigId, pigId));
        if (members.isEmpty()) return null;
        List<Long> certIds = members.stream().map(QuarantineCertPig::getCertId).distinct().toList();
        List<QuarantineCertificate> certs = this.list(Wrappers.<QuarantineCertificate>lambdaQuery()
                .in(QuarantineCertificate::getId, certIds)
                .orderByDesc(QuarantineCertificate::getId));
        return certs.isEmpty() ? null : certs.get(0);
    }

    private List<QuarantineCertPig> membersOf(Long certId) {
        return certPigMapper.selectList(Wrappers.<QuarantineCertPig>lambdaQuery()
                .eq(QuarantineCertPig::getCertId, certId));
    }

    private boolean isMember(Long certId, Long pigId) {
        return certPigMapper.selectCount(Wrappers.<QuarantineCertPig>lambdaQuery()
                .eq(QuarantineCertPig::getCertId, certId)
                .eq(QuarantineCertPig::getPigId, pigId)) > 0;
    }

    /** 要覆盖的生猪：优先 pigIds（一证多头），否则用路径变量注入的单头 */
    private List<Long> resolvePigIds(QuarantineCertDTO dto) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (dto.getPigIds() != null) {
            dto.getPigIds().stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty() && dto.getPigId() != null) ids.add(dto.getPigId());
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "至少选择一头生猪才能出证");
        }
        return new ArrayList<>(ids);
    }
}
