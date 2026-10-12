package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.PigIndividualDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.vo.PigIndividualVO;

import java.util.List;

public interface PigIndividualService extends IService<PigIndividual> {

    Page<PigIndividualVO> pageQuery(Long current, Long size, String earTagNo, Long farmId, String breed,
                                    String status, String birthDateStart, String birthDateEnd);

    /**
     * 品种字典：前端下拉的数据源。
     * <p>
     * 不用前端硬编码枚举——设备通道上报的品种不受前端约束，硬编码会造成
     * 「列表里看得到、搜索下拉里选不到」的错位（如设备上报的「三元杂」）。
     */
    List<String> breedOptions();

    /* 人工建档与修改方法均已删除：养殖建档为设备独占环节，写入只走设备通道。 */

    PigIndividualVO getDetail(Long id);

    /** 追溯用：返回完整档案（含内部字段），供 trace-service 内部调用 */
    PigIndividualVO getTraceDetail(Long id);

    /** 作废（原「删除」）：实名 + 理由必填，上链留证，不做物理删除 */
    void voidIndividual(Long id, String reason);
}
