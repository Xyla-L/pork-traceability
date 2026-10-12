package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.QuarantineCertDTO;
import com.pork.breeding.entity.QuarantineCertificate;
import com.pork.breeding.vo.QuarantineCertVO;

public interface QuarantineCertService extends IService<QuarantineCertificate> {
    void uploadCert(QuarantineCertDTO dto);
    QuarantineCertVO getCertByPigId(Long pigId);

    /**
     * 作废产地检疫证明：实名 + 理由必填，作废事件上链留证，不做物理删除。
     * 检疫证作废直接影响生猪能否调运，属于最需要留痕的一类动作。
     */
    void voidCert(Long pigId, Long id, String reason);
}