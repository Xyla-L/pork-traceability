package com.pork.slaughter.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.entity.EntryInspection;
import com.pork.slaughter.vo.EntryInspectionVO;

public interface EntryInspectionService extends IService<EntryInspection> {

    /* 人工新增与修改方法均已删除：入场查验为设备独占环节，写入只走设备通道。 */

    Page<EntryInspectionVO> pageQuery(EntryInspectionDTO dto, int pageNum, int pageSize);

    /** 作废（原「删除」）：实名 + 理由必填，上链留证 */
    boolean voidEntry(Long id, String reason);
}
