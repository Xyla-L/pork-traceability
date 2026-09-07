package com.pork.slaughter.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class EntryInspectionDTO {
    @NotNull(message = "生猪ID不能为空")
    private Long pigId;
    
    @NotNull(message = "到厂时间不能为空")
    private LocalDateTime arriveTime;
    
    private String vehicleNo;
    
    @NotNull(message = "临床健康检查结果不能为空")
    private Integer healthCheck; // 1通过 0异常
    
    @NotNull(message = "检疫证核验结果不能为空")
    private Integer certVerified; // 1通过 0异常
    
    private String abnormalNote;
    
    @NotNull(message = "查验人不能为空")
    private String inspector;
    
    // 前端传来的文件ID列表，后端转为JSON字符串存储
    private String fileIds; 
}