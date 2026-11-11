package com.pork.slaughter.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.vo.SlaughterGroupVO;

/**
 * 屠宰四张列表的「按批次折叠」读法。
 * <p>
 * 只换读法不改事实：底层仍走各业务 service 的分页查询，这里只把同批的记录收成一行，
 * 明细逐头留在 {@code members}。写操作（作废）仍走各自的原接口。
 */
public interface SlaughterRecordGroupService {

    /** 入场查验：按批次折叠 */
    Page<SlaughterGroupVO> pageEntries(Long current, Long size, EntryInspectionDTO dto);

    /** 屠宰检验：按批次折叠 */
    Page<SlaughterGroupVO> pageInspections(Long current, Long size, SlaughterInspectionDTO dto);

    /** 检疫盖章：按批次折叠 */
    Page<SlaughterGroupVO> pageStamps(Long current, Long size, CarcassStampDTO dto,
                                      String status, String startDate, String endDate);

    /** 瘦肉精检测：按批次折叠 */
    Page<SlaughterGroupVO> pageRactopamine(Long current, Long size, RactopamineTestDTO dto);
}
