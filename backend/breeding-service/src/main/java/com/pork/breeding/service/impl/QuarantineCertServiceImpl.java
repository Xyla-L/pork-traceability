package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.QuarantineCertDTO;
import com.pork.breeding.entity.QuarantineCertificate;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.QuarantineCertMapper;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuarantineCertServiceImpl extends ServiceImpl<QuarantineCertMapper, QuarantineCertificate> implements QuarantineCertService {

    /** 生猪状态：3 已屠宰（已屠宰的猪不存在「调运」这件事，再出证就是凭空造凭据） */
    private static final int PIG_STATUS_SLAUGHTERED = 3;

    private final PigIndividualMapper pigMapper;
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
        PigIndividual pig = pigMapper.selectById(dto.getPigId());
        if (pig == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在");

        // 前置一：证面时间必须自洽 —— 有效期早于签发日的证是自相矛盾的，
        // 下游入场查验会拿它当有效凭据放行，必须在落库前挡住
        if (dto.getValidUntil() != null && dto.getIssueTime() != null
                && dto.getValidUntil().isBefore(dto.getIssueTime().toLocalDate())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "检疫证有效期 " + dto.getValidUntil() + " 早于签发时间 " + dto.getIssueTime().toLocalDate()
                            + "，证面数据不成立");
        }
        // 前置二：已屠宰的猪不再有「调运」这件事，给它出产地检疫证等于凭空造凭据
        if (pig.getStatus() != null && pig.getStatus() == PIG_STATUS_SLAUGHTERED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "该生猪已屠宰，不能再签发产地检疫证（产地检疫是调运前置，猪已不在场）");
        }
        // 前置三：产地检疫须查验强制免疫档案 —— 判定口径见 ImmunityGate
        String immunityBlock = immunityGate.blockReason(pig.getId(), dto.getIssueTime());
        if (immunityBlock != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, immunityBlock);
        }

        long count = this.count(Wrappers.<QuarantineCertificate>lambdaQuery()
                .eq(QuarantineCertificate::getPigId, dto.getPigId())
                .or().eq(QuarantineCertificate::getCertNo, dto.getCertNo()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "生猪或检疫证编号已存在");
        }
        QuarantineCertificate cert = new QuarantineCertificate();
        BeanUtils.copyProperties(dto, cert);
        cert.setCertType("产地检疫");
        if (!StringUtils.hasText(cert.getInspector())) {
            cert.setInspector(operator.display());
        }
        cert.setCreateTime(LocalDateTime.now());
        cert.setContentHash(HashUtil.sha256Json(dto));
        // 来源标记：本路径是官方兽医在系统内人工出证（扫码登记走的是内部设备通道，那边记 DEVICE）
        cert.setSource("MANUAL");
        if (!this.save(cert)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "检疫证保存失败");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certNo", cert.getCertNo());
        payload.put("pigId", pig.getId());
        payload.put("earTagNo", pig.getEarTagNo());
        payload.put("issueOrg", cert.getIssueOrg());
        payload.put("inspector", cert.getInspector());
        payload.put("operator", operator.display());
        chainEvents.publish(ChainTxMessage.create("QUARANTINE_CERT", cert.getId(), cert.getCertNo(),
                cert.getContentHash(), payload));
    }

    /**
     * 作废（原「删除」）：检疫证是生猪调运的通行依据，删掉一张证书，下游的入场查验就失去了核对对象，
     * 所以这里只允许作废，理由与操作人必须留档并上链。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidCert(Long pigId, Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.QUARANTINE, reason);
        String cleanReason = reason.trim();
        QuarantineCertificate cert = this.getById(id);
        if (cert == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "检疫证明不存在或已作废");
        if (pigId != null && !pigId.equals(cert.getPigId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "检疫证明不属于指定生猪");
        }
        String operator = voidBy.display();
        if (baseMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：证书可能已被他人作废");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certNo", cert.getCertNo());
        payload.put("pigId", cert.getPigId());
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
        QuarantineCertificate cert = this.getOne(Wrappers.<QuarantineCertificate>lambdaQuery().eq(QuarantineCertificate::getPigId, pigId));
        if (cert == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "未找到该生猪的检疫证明");
        }
        QuarantineCertVO vo = new QuarantineCertVO();
        BeanUtils.copyProperties(cert, vo);
        return vo;
    }
}
