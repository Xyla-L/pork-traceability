package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.QuarantineCertDTO;
import com.pork.breeding.entity.QuarantineCertificate;
import com.pork.breeding.mapper.QuarantineCertMapper;
import com.pork.breeding.service.QuarantineCertService;
import com.pork.breeding.vo.QuarantineCertVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuarantineCertServiceImpl extends ServiceImpl<QuarantineCertMapper, QuarantineCertificate> implements QuarantineCertService {

    @Override
    @Transactional
    public void uploadCert(QuarantineCertDTO dto) {
        // 检查是否已存在检疫证明
        long count = this.count(Wrappers.<QuarantineCertificate>lambdaQuery().eq(QuarantineCertificate::getPigId, dto.getPigId()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该生猪已存在检疫证明，不可重复上传");
        }
        QuarantineCertificate cert = new QuarantineCertificate();
        BeanUtils.copyProperties(dto, cert);
        this.save(cert);
        // 此处应添加发送MQ消息，触发异步上链的逻辑
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