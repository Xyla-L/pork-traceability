package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.vo.RecordGroupVO;

/**
 * 「按批次分组」的记录视图。
 * <p>
 * 免疫、产地检疫、出栏申报的现实形态都是整批一次做完的动作，但底层事实表逐头一条。
 * 这里只在<b>读取侧</b>归组，不改任何写入路径、判重与状态机——目的是让列表
 * 「一次操作一行」，而不是同一件事刷 N 行耳标。
 * <p>
 * 明细逐头保留在 {@link RecordGroupVO#getMembers()}，前端展开即见：
 * 聚合是为了少刷屏，不是为了把单头追溯抹掉。
 */
public interface RecordGroupService {

    /** 疫苗记录按「批次 + 疫苗名称 + 疫苗批号 + 注射日期」归组 */
    Page<RecordGroupVO> pageVaccine(Long current, Long size, String earTagNo,
                                    String vaccineName, boolean includeVoid);

    /** 产地检疫按「批次 + 出证机构 + 出证日期」归组；certState 支持 valid / expired */
    Page<RecordGroupVO> pageCert(Long current, Long size, String earTagNo, String certState);

    /** 出栏申报按「批次 + 申报日期」归组 */
    Page<RecordGroupVO> pageApply(Long current, Long size, Integer approvalStatus);
}
