package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.PigCreateDTO;
import com.pork.breeding.dto.PigQueryDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.vo.PigDetailVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PigIndividualServiceImpl extends ServiceImpl<PigIndividualMapper, PigIndividual> implements PigIndividualService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPig(PigCreateDTO dto) {
        // 1. 校验耳标号是否重复
        long count = this.count(new QueryWrapper<PigIndividual>().eq("ear_tag_no", dto.getEarTagNo()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "耳标号已存在，请勿重复录入");
        }

        // 2. DTO 转 Entity
        PigIndividual pig = new PigIndividual();
        BeanUtils.copyProperties(dto, pig);

        // 3. 保存入库
        this.save(pig);
        return pig.getId();
    }

    @Override
    public Page<PigDetailVO> pagePigs(Integer page, Integer size, PigQueryDTO queryDTO) {
        // 1. 构建分页对象
        Page<PigIndividual> pageParam = new Page<>(page, size);

        // 2. 构建动态查询条件
        QueryWrapper<PigIndividual> wrapper = new QueryWrapper<>();
        if (queryDTO != null) {
            wrapper.like(queryDTO.getEarTagNo() != null, "ear_tag_no", queryDTO.getEarTagNo())
                    .eq(queryDTO.getBreedId() != null, "breed_id", queryDTO.getBreedId())
                    .eq(queryDTO.getStatus() != null, "status", queryDTO.getStatus())
                    .ge(queryDTO.getEntryDateStart() != null, "entry_date", queryDTO.getEntryDateStart())
                    .le(queryDTO.getEntryDateEnd() != null, "entry_date", queryDTO.getEntryDateEnd());
        }
        // 默认按入栏时间倒序排列
        wrapper.orderByDesc("entry_date");

        // 3. 执行分页查询
        Page<PigIndividual> pigPage = this.page(pageParam, wrapper);

        // 4. Entity 转 VO
        Page<PigDetailVO> voPage = new Page<>(pigPage.getCurrent(), pigPage.getSize(), pigPage.getTotal());
        List<PigDetailVO> voList = pigPage.getRecords().stream().map(pig -> {
            PigDetailVO vo = new PigDetailVO();
            BeanUtils.copyProperties(pig, vo);
            return vo;
        }).collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public PigDetailVO getPigDetailById(Long id) {
        // 1. 根据 ID 查询
        PigIndividual pig = this.getById(id);
        if (pig == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "未找到该生猪档案");
        }

        // 2. Entity 转 VO
        PigDetailVO vo = new PigDetailVO();
        BeanUtils.copyProperties(pig, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePig(Long id, PigQueryDTO updateDTO) {
        // 1. 检查是否存在
        PigIndividual pig = this.getById(id);
        if (pig == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "未找到该生猪档案");
        }

        // 2. 将更新 DTO 的属性拷贝到实体中
        BeanUtils.copyProperties(updateDTO, pig);

        // 3. 执行更新
        this.updateById(pig);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePig(Long id) {
        // 1. 检查是否存在
        PigIndividual pig = this.getById(id);
        if (pig == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "未找到该生猪档案");
        }

        // 2. 执行删除
        this.removeById(id);
    }
}