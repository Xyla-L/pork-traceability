package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.VaccineRecordDTO;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.vo.VaccineRecordVO;

import java.util.List;

public interface VaccineRecordService extends IService<VaccineRecord> {
    Page<VaccineRecordVO> pageQuery(Long current, Long size, Long pigId);

    /* 人工录入与修改方法均已删除：免疫注射为设备独占环节，写入只走设备通道。 */

    VaccineRecordVO getDetail(Long pigId, Long id);

    /** 作废（原「删除」）：实名 + 理由必填，上链留证，不做物理删除 */
    void voidRecord(Long pigId, Long id, String reason);

    List<VaccineRecordVO> listByPigId(Long pigId);

    List<VaccineRecordVO> listByPigId(Long pigId, boolean includeVoid);
}
