package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.PigBatchDTO;
import com.pork.breeding.entity.PigBatch;
import com.pork.breeding.vo.PigBatchVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 生猪批次档案服务。
 *
 * <p>批次是养殖端档案的登记单位：人工登记一张批次表单（场 + 批次号 + 品种 +
 * 进场日期 + 耳标起止号），登记时按耳标区间自动展开成员行（pig_individual）。
 * 区间内的耳标在设备侧被读到时直接命中成员行，不再需要「先建档再入账」。
 */
public interface PigBatchService extends IService<PigBatch> {

    /** 批次分页查询 */
    Page<PigBatchVO> pageQuery(long current, long size, String batchNo, Long farmId, String breed);

    /** 人工登记批次：校验通过后落批次档 + 按耳标区间展开成员行 */
    Long createBatch(PigBatchDTO dto);

    /** 批次详情（含成员状态汇总） */
    PigBatchVO getDetail(Long id);

    /** 作废批次：实名 + 理由 + 上链；成员行一并作废 */
    void voidBatch(Long id, String reason);
}
