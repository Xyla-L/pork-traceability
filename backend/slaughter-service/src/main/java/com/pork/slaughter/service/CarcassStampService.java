package com.pork.slaughter.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.vo.CarcassStampVO;

public interface CarcassStampService extends IService<CarcassStamp> {

    /**
     * 分页查询盖章记录
     */
    IPage<CarcassStampVO> pageQuery(Page<CarcassStamp> page, CarcassStampDTO query,
                                    String status, String startDate, String endDate);

    /* 人工新增与修改方法均已删除：胴体盖章为设备独占环节，写入只走设备通道。 */

    /** 作废（原「删除」）：实名 + 理由必填，上链留证 */
    void voidStamp(Long id, String reason);

    /** 已上链记录不可物理删除，调用即抛业务异常 */
    boolean deleteStamp(Long id);
}
