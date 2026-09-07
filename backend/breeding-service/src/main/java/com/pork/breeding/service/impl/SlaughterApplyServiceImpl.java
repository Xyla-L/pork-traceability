package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.SlaughterApply;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.SlaughterApplyMapper;
import com.pork.breeding.service.SlaughterApplyService;
import com.pork.breeding.vo.SlaughterApplyVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlaughterApplyServiceImpl extends ServiceImpl<SlaughterApplyMapper, SlaughterApply> implements SlaughterApplyService {

    private final PigIndividualMapper pigIndividualMapper;

    @Override
    public Page<SlaughterApplyVO> pageQuery(Long current, Long size, Integer approvalStatus) {
        Page<SlaughterApply> page = this.page(
                new Page<>(current, size),
                Wrappers.<SlaughterApply>lambdaQuery().eq(approvalStatus != null, SlaughterApply::getApprovalStatus, approvalStatus)
        );
        Page<SlaughterApplyVO> voPage = new Page<>(current, size);
        BeanUtils.copyProperties(page, voPage);
        voPage.setRecords(page.getRecords().stream().map(apply -> {
            SlaughterApplyVO vo = new SlaughterApplyVO();
            BeanUtils.copyProperties(apply, vo);
            // 关联查询耳标号
            PigIndividual pig = pigIndividualMapper.selectById(apply.getPigId());
            if (pig != null) {
                vo.setEarTagNo(pig.getEarTagNo());
            }
            return vo;
        }).toList());
        return voPage;
    }

    @Override
    public SlaughterApplyVO getDetail(Long id) {
        SlaughterApply apply = this.getById(id);
        if (apply == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "出栏申报记录不存在");
        }
        SlaughterApplyVO vo = new SlaughterApplyVO();
        BeanUtils.copyProperties(apply, vo);
        // 关联查询耳标号
        PigIndividual pig = pigIndividualMapper.selectById(apply.getPigId());
        if (pig != null) {
            vo.setEarTagNo(pig.getEarTagNo());
        }
        return vo;
    }
}