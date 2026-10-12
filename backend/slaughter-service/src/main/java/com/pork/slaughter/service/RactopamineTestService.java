package com.pork.slaughter.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.entity.RactopamineTest;
import com.pork.slaughter.vo.RactopamineTestVO;

public interface RactopamineTestService extends IService<RactopamineTest> {

    /* 人工新增与修改方法均已删除：瘦肉精检测为设备独占环节，写入只走设备通道。 */

    Page<RactopamineTestVO> pageQuery(RactopamineTestDTO dto);

    /** 作废（原「删除」）：实名 + 理由必填，上链留证 */
    boolean voidTest(Long id, String reason);
}
