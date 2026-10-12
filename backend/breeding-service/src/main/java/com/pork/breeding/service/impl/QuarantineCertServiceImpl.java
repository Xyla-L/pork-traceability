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
    private final PigIndividualMapper pigMapper;
    private final ChainEventPublisher chainEvents;
    private final SourceGuard sourceGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadCert(QuarantineCertDTO dto) {
        // 来源管控：产地检疫以扫码登记终端为主路径；设备覆盖不到时的人工出证须实名并上链留痕；
        // 出证人必须落到具体人（未填则回填当前登录人），保证链上能追到人，
        // 且这个人只能是证面签发的官方兽医——扫码终端是工具，不得代签
        ManualOperator operator = sourceGuard.requireManualAllowed(TraceStage.QUARANTINE);
        PigIndividual pig = pigMapper.selectById(dto.getPigId());
        if (pig == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在");
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
