package com.pork.breeding.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "养殖场VO")
public class FarmVO {
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "养殖场名称")
    private String farmName;

    @Schema(description = "养殖许可证编号")
    private String licenseNo;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "养殖规模")
    private Integer scale;

    @Schema(description = "状态：1-正常, 0-停业")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}