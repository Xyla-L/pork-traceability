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

    @Schema(description = "文件ID列表")
    private List<String> fileIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}