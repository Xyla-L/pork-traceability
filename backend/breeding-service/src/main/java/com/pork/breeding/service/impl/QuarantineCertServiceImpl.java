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
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuarantineCertServiceImpl extends ServiceImpl<QuarantineCertMapper, QuarantineCertificate> implements QuarantineCertService {
    private final PigIndividualMapper pigMapper;
    private final ChainEventPublisher chainEvents;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadCert(QuarantineCertDTO dto) {
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
        cert.setCreateTime(LocalDateTime.now());
        cert.setContentHash(HashUtil.sha256Json(dto));
        if (!this.save(cert)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "检疫证保存失败");
        chainEvents.publish(ChainTxMessage.create("QUARANTINE_CERT", cert.getId(), cert.getCertNo(),
                cert.getContentHash(), Map.of("certNo", cert.getCertNo(), "pigId", pig.getId(),
                        "earTagNo", pig.getEarTagNo(), "issueOrg", cert.getIssueOrg())));
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
