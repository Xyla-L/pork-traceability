package com.pork.slaughter.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class CarcassStampDTO {

    private Long id;

    /**
     * 关联猪只ID
     */
    @NotNull(message = "生猪ID不能为空")
    private Long pigId;

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    @NotBlank(message = "胴体编号不能为空")
    private String carcassNo;

    /**
     * 印章编号
     */
    @NotBlank(message = "印章编号不能为空")
    private String stampNo;

    private String stampType;

    /**
     * 盖章时间
     */
    @NotNull(message = "盖章时间不能为空")
    private LocalDateTime stampTime;

    /**
     * 盖章位置
     */
    private String stampPosition;

    /**
     * 兽医/操作人员
     */
    @NotBlank(message = "兽医不能为空")
    private String veterinary;

    /**
     * 电子签名
     */
    private String eSignature;

    private Integer status;
    private String remark;
}
