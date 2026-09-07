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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RactopamineTestServiceImpl extends ServiceImpl<RactopamineTestMapper, RactopamineTest>
        implements RactopamineTestService {

    @Override
    public boolean addTest(RactopamineTestDTO dto) {
        RactopamineTest entity = new RactopamineTest();
        BeanUtil.copyProperties(dto, entity);
        return this.save(entity);
    }

    @Override
    public boolean updateTest(Long id, RactopamineTestDTO dto) {
        RactopamineTest entity = new RactopamineTest();
        BeanUtil.copyProperties(dto, entity);
        entity.setId(id);
        return this.updateById(entity);
    }

    @Override
    public boolean deleteTest(Long id) {
        return this.removeById(id);
    }

    @Override
    public Page<RactopamineTestVO> pageQuery(RactopamineTestDTO dto) {
        Page<RactopamineTest> page = new Page<>(dto.getPageNum(), dto.getPageSize());

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