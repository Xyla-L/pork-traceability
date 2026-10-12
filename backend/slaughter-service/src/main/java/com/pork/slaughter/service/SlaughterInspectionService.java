package com.pork.slaughter.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.entity.SlaughterInspection;
import com.pork.slaughter.vo.SlaughterInspectionVO;

public interface SlaughterInspectionService extends IService<SlaughterInspection> {

    /* 人工新增与修改方法均已删除：屠宰检验为设备独占环节，写入只走设备通道。 */

    Page<SlaughterInspectionVO> pageQuery(SlaughterInspectionDTO dto);

    /** 作废（原「删除」）：实名 + 理由必填，上链留证 */
    void voidInspection(Long id, String reason);

    /** 已上链记录不可物理删除，调用即抛业务异常 */
    boolean deleteInspection(Long id);
}
