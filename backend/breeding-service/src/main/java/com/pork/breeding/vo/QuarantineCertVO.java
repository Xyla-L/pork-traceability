package com.pork.breeding.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "产地检疫证明VO")
public class QuarantineCertVO {
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "生猪ID")
    private Long pigId;

    @Schema(description = "检疫证明编号")
    private String certNo;

    @Schema(description = "签发机构全称")
    private String issueOrg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "签发时间")
    private LocalDateTime issueTime;

    @Schema(description = "有效期至")
    private java.time.LocalDate validUntil;

    @Schema(description = "官方兽医姓名")
    private String inspector;

    @Schema(description = "证明类型")
    private String certType;

    @Schema(description = "检疫证原件扫描件文件ID")
    private String fileId;

    @Schema(description = "签发机构CA数字签名")
    private String caSignature;

    @Schema(description = "证照内容+签名的SHA-256哈希")
    private String contentHash;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}