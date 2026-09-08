package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.FarmDTO;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.mapper.FarmMapper;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.service.FarmService;
import com.pork.breeding.vo.FarmVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FarmServiceImpl extends ServiceImpl<FarmMapper, Farm> implements FarmService {
    private final PigIndividualMapper pigIndividualMapper;

    @Override
    public Page<FarmVO> pageQuery(Long current, Long size, String farmName) {
        Page<Farm> page = this.page(
                new Page<>(current, size),
                Wrappers.<Farm>lambdaQuery()
                        .like(StringUtils.hasText(farmName), Farm::getFarmName, farmName)
                        .orderByDesc(Farm::getCreateTime)
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
    @Transactional(rollbackFor = Exception.class)
    public void addFarm(FarmDTO dto) {
        // 检查许可证编号是否重复
        long count = this.count(Wrappers.<Farm>lambdaQuery().eq(Farm::getLicenseNo, dto.getLicenseNo()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "养殖许可证编号已存在");
        }
        Farm farm = new Farm();
        BeanUtils.copyProperties(dto, farm);
        farm.setStatus(1);
        farm.setCreateTime(java.time.LocalDateTime.now());
        if (!this.save(farm)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "养殖场创建失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFarm(FarmDTO dto) {
        if (dto.getId() == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "养殖场ID不能为空");
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
        if (!this.updateById(farm)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "养殖场更新失败");
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
    @Transactional(rollbackFor = Exception.class)
    public void removeFarm(Long id) {
        if (this.getById(id) == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "养殖场不存在");
        long pigCount = pigIndividualMapper.selectCount(Wrappers.<com.pork.breeding.entity.PigIndividual>lambdaQuery()
                .eq(com.pork.breeding.entity.PigIndividual::getFarmId, id));
        if (pigCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该养殖场下存在生猪档案，无法删除");
        if (!this.removeById(id)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "养殖场删除失败");
    }
}
