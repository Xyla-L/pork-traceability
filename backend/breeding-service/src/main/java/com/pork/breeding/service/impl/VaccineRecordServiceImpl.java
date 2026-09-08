package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.VaccineRecordDTO;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.VaccineRecordMapper;
import com.pork.breeding.service.VaccineRecordService;
import com.pork.breeding.vo.VaccineRecordVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VaccineRecordServiceImpl extends ServiceImpl<VaccineRecordMapper, VaccineRecord> implements VaccineRecordService {
    private final PigIndividualMapper pigMapper;

    @Override
    public Page<VaccineRecordVO> pageQuery(Long current, Long size, Long pigId) {
        Page<VaccineRecord> page = this.page(
                new Page<>(current, size),
                Wrappers.<VaccineRecord>lambdaQuery().eq(pigId != null, VaccineRecord::getPigId, pigId)
        );
        Page<VaccineRecordVO> voPage = new Page<>(current, size);
        BeanUtils.copyProperties(page, voPage);
        voPage.setRecords(convertToVOList(page.getRecords()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRecord(VaccineRecordDTO dto) {
        requirePig(dto.getPigId());
        VaccineRecord record = new VaccineRecord();
        BeanUtils.copyProperties(dto, record);
        record.setFileIds(JSON.toJSONString(dto.getFileIds() == null ? List.of() : dto.getFileIds()));
        record.setCreateTime(java.time.LocalDateTime.now());
        if (!this.save(record)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "疫苗记录保存失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRecord(VaccineRecordDTO dto) {
        if (dto.getId() == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "疫苗记录ID不能为空");
        requirePig(dto.getPigId());
        VaccineRecord record = this.getById(dto.getId());
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "疫苗记录不存在");
        }
        if (!record.getPigId().equals(dto.getPigId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "疫苗记录不属于指定生猪");
        }
        BeanUtils.copyProperties(dto, record);
        record.setFileIds(JSON.toJSONString(dto.getFileIds() == null ? List.of() : dto.getFileIds()));
        if (!this.updateById(record)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "疫苗记录更新失败");
    }

    @Override
    public VaccineRecordVO getDetail(Long id) {
        VaccineRecord record = this.getById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "疫苗记录不存在");
        }
        VaccineRecordVO vo = new VaccineRecordVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRecord(Long id) {
        if (!this.removeById(id)) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "疫苗记录不存在");
    }

    @Override
    public List<VaccineRecordVO> listByPigId(Long pigId) {
        requirePig(pigId);
        List<VaccineRecord> records = this.list(Wrappers.<VaccineRecord>lambdaQuery().eq(VaccineRecord::getPigId, pigId));
        return convertToVOList(records);
    }

    private void requirePig(Long pigId) {
        if (pigMapper.selectById(pigId) == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在");
        }
    }

    private List<VaccineRecordVO> convertToVOList(List<VaccineRecord> records) {
        return records.stream().map(record -> {
            VaccineRecordVO vo = new VaccineRecordVO();
            BeanUtils.copyProperties(record, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
