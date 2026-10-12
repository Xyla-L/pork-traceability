package com.pork.breeding.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "疫苗记录VO")
public class VaccineRecordVO {
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "生猪ID")
    private Long pigId;

    @Schema(description = "疫苗名称")
    private String vaccineName;

    @Schema(description = "疫苗生产批次号")
    private String batchNo;

    @Schema(description = "疫苗生产厂家")
    private String manufacturer;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "注射时间")
    private LocalDateTime injectTime;

    @Schema(description = "剂量")
    private String dosage;

    @Schema(description = "注射部位")
    private String injectSite;

    @Schema(description = "操作人姓名")
    private String operator;

    @Schema(description = "数据来源：MANUAL=人工录入 DEVICE=设备自动采集")
    private String source;

    @Schema(description = "来源设备号/单据号")
    private String sourceRef;

    @Schema(description = "文件ID列表")
    private List<String> fileIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "是否已作废：0=有效 1=已作废（作废记录仍在库中可追溯，列表按此打标）")
    private Integer voidFlag;

    @Schema(description = "作废理由")
    private String voidReason;

    @Schema(description = "作废操作人（实名）")
    private String voidOperator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "作废时间")
    private LocalDateTime voidTime;
}