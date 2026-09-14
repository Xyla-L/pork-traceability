package com.pork.slaughter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.entity.RactopamineTest;
import com.pork.slaughter.mapper.RactopamineTestMapper;
import com.pork.slaughter.service.RactopamineTestService;
import com.pork.slaughter.support.SlaughterStatus;
import com.pork.slaughter.vo.RactopamineTestVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class RactopamineTestServiceImpl extends ServiceImpl<RactopamineTestMapper, RactopamineTest>
        implements RactopamineTestService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addTest(RactopamineTestDTO dto) {
        RactopamineTest entity = new RactopamineTest();
        BeanUtil.copyProperties(dto, entity, "result");
        entity.setResult(SlaughterStatus.testResult(dto.getResult()));
        entity.setCreateTime(LocalDateTime.now());
        if (!this.save(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "瘦肉精检测保存失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTest(Long id, RactopamineTestDTO dto) {
        if (this.getById(id) == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "瘦肉精检测记录不存在");
        RactopamineTest entity = new RactopamineTest();
        BeanUtil.copyProperties(dto, entity, "result");
        entity.setId(id);
        entity.setResult(SlaughterStatus.testResult(dto.getResult()));
        if (!this.updateById(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "瘦肉精检测更新失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTest(Long id) {
        if (!this.removeById(id)) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "瘦肉精检测记录不存在");
        return true;
    }

    @Override
    public Page<RactopamineTestVO> pageQuery(RactopamineTestDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : dto.getPageSize();
        Page<RactopamineTest> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<RactopamineTest> wrapper = new LambdaQueryWrapper<>();
        if (dto.getPigId() != null) {
            wrapper.eq(RactopamineTest::getPigId, dto.getPigId());
        }
        if (dto.getTestNo() != null && !dto.getTestNo().isEmpty()) {
            wrapper.like(RactopamineTest::getTestNo, dto.getTestNo());
        }
        if (dto.getBatchNo() != null && !dto.getBatchNo().isEmpty()) {
            wrapper.like(RactopamineTest::getBatchNo, dto.getBatchNo());
        }
        if (dto.getSampleNo() != null && !dto.getSampleNo().isEmpty()) {
            wrapper.like(RactopamineTest::getSampleNo, dto.getSampleNo());
        }
        if (dto.getTestType() != null && !dto.getTestType().isEmpty()) {
            wrapper.eq(RactopamineTest::getTestType, dto.getTestType());
        }
        if (dto.getEarTagNo() != null && !dto.getEarTagNo().isEmpty()) {
            wrapper.like(RactopamineTest::getEarTagNo, dto.getEarTagNo());
        }
        // 结果支持中文："阴性"→result=1，"阳性"→result=0，"待检测"→status=0
        if (dto.getResult() != null && !dto.getResult().isEmpty()) {
            if ("待检测".equals(dto.getResult())) {
                wrapper.eq(RactopamineTest::getStatus, 0);
            } else {
                wrapper.eq(RactopamineTest::getResult, SlaughterStatus.testResult(dto.getResult()));
            }
        }
        if (dto.getStatus() != null) {
            wrapper.eq(RactopamineTest::getStatus, dto.getStatus());
        }
        if (dto.getStartDate() != null && !dto.getStartDate().isEmpty()) {
            wrapper.ge(RactopamineTest::getTestTime, LocalDate.parse(dto.getStartDate()).atStartOfDay());
        }
        if (dto.getEndDate() != null && !dto.getEndDate().isEmpty()) {
            wrapper.le(RactopamineTest::getTestTime, LocalDate.parse(dto.getEndDate()).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(RactopamineTest::getTestTime);

        Page<RactopamineTest> entityPage = this.page(page, wrapper);

        Page<RactopamineTestVO> voPage = new Page<>();
        BeanUtil.copyProperties(entityPage, voPage, "records");
        voPage.setRecords(BeanUtil.copyToList(entityPage.getRecords(), RactopamineTestVO.class));

        return voPage;
    }
}
