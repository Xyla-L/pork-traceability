package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 免疫批次：一次免疫作业。
 * <p>
 * 为什么要有它：免疫在现实里是「一批猪同一天打同一种疫苗」的一次作业，
 * 而 {@link VaccineRecord} 是逐头事实（一条挂一头猪）。没有单据表时，
 * 页面上的「一批」只能靠查询时按「同场+同疫苗+同生产批号+同一天」临时算，
 * 批次号是编出来的，既不能引用也不能追溯。
 * <p>
 * 归批规则（{@code VaccineBatchServiceImpl#attach}）：同场 + 同疫苗名称 + 同生产批号 + 同一天
 * → 复用同一批次；设备逐头上报因此会自动聚到一张单上，不需要人工先建单。
 */
@Data
@TableName("vaccine_batch")
public class VaccineBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long farmId;
    /** 免疫批次号（这一批猪自己的批次号，与养殖批次 pig_batch.batch_no 无关） */
    private String batchNo;
    private String vaccineName;
    /** 疫苗生产批号（厂家瓶批号），与免疫批次号是两件事，不能相互替代 */
    private String prodBatchNo;
    private String manufacturer;
    private LocalDateTime injectTime;
    private String operator;
    /** 覆盖头数（成员行数，作废的也计入，作废数另见 voidCount） */
    private Integer pigCount;

    private String dataSource;
    private String sourceRef;

    private Integer voidFlag;
    private String voidReason;
    private String voidOperator;
    private LocalDateTime voidTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
