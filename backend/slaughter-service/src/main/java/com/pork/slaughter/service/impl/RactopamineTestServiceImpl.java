package com.pork.slaughter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.entity.RactopamineTest;
import com.pork.slaughter.mapper.RactopamineTestMapper;
import com.pork.slaughter.service.RactopamineTestService;
import com.pork.slaughter.vo.RactopamineTestVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RactopamineTestServiceImpl extends ServiceImpl<RactopamineTestMapper, RactopamineTest>
        implements RactopamineTestService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addTest(RactopamineTestDTO dto) {
        RactopamineTest entity = new RactopamineTest();
        BeanUtil.copyProperties(dto, entity);
        entity.setCreateTime(LocalDateTime.now());
        if (!this.save(entity)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "瘦肉精检测保存失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTest(Long id, RactopamineTestDTO dto) {
        if (this.getById(id) == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "瘦肉精检测记录不存在");
        RactopamineTest entity = new RactopamineTest();
        BeanUtil.copyProperties(dto, entity);
        entity.setId(id);
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
        if (dto.getResult() != null) {
            wrapper.eq(RactopamineTest::getResult, dto.getResult());
        }
        wrapper.orderByDesc(RactopamineTest::getTestTime);

        Page<RactopamineTest> entityPage = this.page(page, wrapper);

        Page<RactopamineTestVO> voPage = new Page<>();
        BeanUtil.copyProperties(entityPage, voPage, "records");
        voPage.setRecords(BeanUtil.copyToList(entityPage.getRecords(), RactopamineTestVO.class));

        return voPage;
    }
}
