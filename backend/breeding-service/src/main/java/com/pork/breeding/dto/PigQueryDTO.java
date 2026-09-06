package com.pork.breeding.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * 猪只个体查询条件 DTO
 */
@Data
public class PigQueryDTO {

    /**
     * 耳标号（支持模糊查询）
     */
    private String earTagNo;

    /**
     * 品种 ID
     */
    private Long breedId;

    /**
     * 状态（例如：1-正常，2-患病，3-已出栏，4-死亡）
     */
    private Integer status;

    /**
     * 入栏开始日期
     */
    private LocalDate entryDateStart;

    /**
     * 入栏结束日期
     */
    private LocalDate entryDateEnd;

}