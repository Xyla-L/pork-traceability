package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.SlaughterApplyApproveDTO;
import com.pork.breeding.dto.SlaughterApplyDTO;
import com.pork.breeding.entity.SlaughterApply;
import com.pork.breeding.vo.SlaughterApplyVO;

public interface SlaughterApplyService extends IService<SlaughterApply> {
    Page<SlaughterApplyVO> pageQuery(Long current, Long size, Integer approvalStatus);
    SlaughterApplyVO getDetail(Long id);

    /**
     * 创建出栏申报（同一头生猪仅允许一条申报记录）
     *
     * @return 新建申报的ID
     */
    Long createApply(SlaughterApplyDTO dto);

    /**
     * 审批出栏申报：通过后生猪状态置为已出栏
     *
     * @param id         申报ID
     * @param dto        审批结果
     * @param approverId 审批人ID（来自网关下发的 X-User-Id）
     */
    void approve(Long id, SlaughterApplyApproveDTO dto, Long approverId);
}