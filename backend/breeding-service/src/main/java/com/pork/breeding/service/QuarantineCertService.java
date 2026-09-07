package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.QuarantineCertDTO;
import com.pork.breeding.entity.QuarantineCertificate;
import com.pork.breeding.vo.QuarantineCertVO;

public interface QuarantineCertService extends IService<QuarantineCertificate> {
    void uploadCert(QuarantineCertDTO dto);
    QuarantineCertVO getCertByPigId(Long pigId);
}