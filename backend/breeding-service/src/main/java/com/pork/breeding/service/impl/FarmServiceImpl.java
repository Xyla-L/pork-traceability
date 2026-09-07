package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.FarmDTO;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.service.FarmService;
import com.pork.breeding.vo.FarmVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FarmServiceImpl extends ServiceImpl<FarmMapper, Farm> implements FarmService {

    @Override
    public Page<FarmVO> pageQuery(Long current, Long size, String farmName) {
        Page<Farm> page = this.page(
                new Page<>(current, size),
                Wrappers.<Farm>lambdaQuery().like(farmName != null, Farm::getFarmName, farmName)
        );
        Page<FarmVO> voPage = new Page<>(current, size);
        BeanUtils.copyProperties(page, voPage);
        voPage.setRecords(page.getRecords().stream().map(farm -> {
            FarmVO vo = new FarmVO();
            BeanUtils.copyProperties(farm, vo);
            return vo;
        }).toList());
        return voPage;
    }

    @Override
    @Transactional
    public void addFarm(FarmDTO dto) {
        // 检查许可证编号是否重复
        long count = this.count(Wrappers.<Farm>lambdaQuery().eq(Farm::getLicenseNo, dto.getLicenseNo()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "养殖许可证编号已存在");
        }
        Farm farm = new Farm();
        BeanUtils.copyProperties(dto, farm);
        this.save(farm);
    }

    @Override
    @Transactional
    public void updateFarm(FarmDTO dto) {
        Farm farm = this.getById(dto.getId());
        if (farm == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "养殖场不存在");
        }
        // 检查许可证编号是否被其他养殖场使用
        long count = this.count(Wrappers.<Farm>lambdaQuery().eq(Farm::getLicenseNo, dto.getLicenseNo()).ne(Farm::getId, dto.getId()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "养殖许可证编号已存在");
        }
        BeanUtils.copyProperties(dto, farm);
        this.updateById(farm);
    }

    @Override
    public FarmVO getDetail(Long id) {
        Farm farm = this.getById(id);
        if (farm == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "养殖场不存在");
        }
        FarmVO vo = new FarmVO();
        BeanUtils.copyProperties(farm, vo);
        return vo;
    }

    @Override
    @Transactional
    public void removeFarm(Long id) {
        // 检查养殖场下是否还有生猪
        // 此处省略对 pig_individual 表的查询，实际开发中需要注入 PigIndividualMapper
        // long pigCount = pigIndividualMapper.selectCount(Wrappers.<PigIndividual>lambdaQuery().eq(PigIndividual::getFarmId, id));
        // if (pigCount > 0) {
        //     throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该养殖场下存在生猪档案，无法删除");
        // }
        this.removeById(id);
    }
}