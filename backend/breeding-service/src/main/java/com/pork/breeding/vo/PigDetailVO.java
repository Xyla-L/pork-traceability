package com.pork.breeding.vo;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PigDetailVO {
    
    /**
     * 猪只ID
     */
    private Long id;

    /**
     * 耳标号
     */
    private String earTagNo;

    /**
     * 品种名称（例如：三元猪，前端不需要看品种ID，直接看名称）
     */
    private String breedName;

    /**
     * 猪舍名称
     */
    private String styName;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 耳号
     */
    private String penNo;

    /**
     * 来源（例如：自繁、外购）
     */
    private String source;

    /**
     * 状态描述（例如：1-在栏，2-已出栏，3-死亡，前端展示用）
     */
    private String statusDesc;

    /**
     * 创建时间
     */
    private LocalDate createTime;
}