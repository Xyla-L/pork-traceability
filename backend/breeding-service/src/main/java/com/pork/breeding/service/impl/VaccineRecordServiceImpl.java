package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.VaccineRecordDTO;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.mapper.VaccineRecordMapper;
import com.pork.breeding.service.VaccineRecordService;
import com.pork.breeding.vo.VaccineRecordVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VaccineRecordServiceImpl extends ServiceImpl<VaccineRecordMapper, VaccineRecord> implements VaccineRecordService {

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
    @Transactional
    public void addRecord(VaccineRecordDTO dto) {
        VaccineRecord record = new VaccineRecord();
        BeanUtils.copyProperties(dto, record);
        this.save(record);
    }

    @Override
    @Transactional
    public void updateRecord(VaccineRecordDTO dto) {
        VaccineRecord record = this.getById(dto.getId());
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "疫苗记录不存在");
        }
        BeanUtils.copyProperties(dto, record);
        this.updateById(record);
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
    @Transactional
    public void removeRecord(Long id) {
        this.removeById(id);
    }

    @Override
    public List<VaccineRecordVO> listByPigId(Long pigId) {
        List<VaccineRecord> records = this.list(Wrappers.<VaccineRecord>lambdaQuery().eq(VaccineRecord::getPigId, pigId));
        return convertToVOList(records);
    }

    private List<VaccineRecordVO> convertToVOList(List<VaccineRecord> records) {
        return records.stream().map(record -> {
            VaccineRecordVO vo = new VaccineRecordVO();
            BeanUtils.copyProperties(record, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}