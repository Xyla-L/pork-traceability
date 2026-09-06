// 1. 必须有的包声明（否则Java会找不到它属于哪个包）
package com.pork.breeding.service;

// 2. 补全这些导入
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.PigCreateDTO;
import com.pork.breeding.dto.PigQueryDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.vo.PigDetailVO;

public interface PigIndividualService extends IService<PigIndividual> {

    Long createPig(PigCreateDTO dto);

    Page<PigDetailVO> pagePigs(Integer page, Integer size, PigQueryDTO queryDTO);

    PigDetailVO getPigDetailById(Long id);

    void updatePig(Long id, PigQueryDTO updateDTO);

    void deletePig(Long id);
}