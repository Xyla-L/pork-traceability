package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.VaccineRecordDTO;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.vo.VaccineRecordVO;

import java.util.List;

public interface VaccineRecordService extends IService<VaccineRecord> {
    Page<VaccineRecordVO> pageQuery(Long current, Long size, Long pigId);
    void addRecord(VaccineRecordDTO dto);
    void updateRecord(VaccineRecordDTO dto);
    VaccineRecordVO getDetail(Long pigId, Long id);
    void removeRecord(Long pigId, Long id);
    List<VaccineRecordVO> listByPigId(Long pigId);
}