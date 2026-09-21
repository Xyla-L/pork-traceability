package com.pork.ingest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备上报报文统一信封。
 * <p>
 * 所有通道共用一层外壳，通道差异全部放在 {@code data} 里：
 * 设备侧只需要一套上报代码，接入层也只做一次鉴权、一次幂等判断。
 */
@Data
public class DeviceReportRequest {

    /** 接入通道 TEMPERATURE/ENTRY/RACTOPAMINE/SALE/RECEIPT（缺省时按设备台账登记的通道推断） */
    private String channel;

    /**
     * 设备侧业务唯一号，幂等键。
     * 设备必须保证「同一条数据重发时 bizKey 不变」——重复上报只有第一条会入账。
     */
    @NotBlank(message = "bizKey(设备侧唯一号)不能为空")
    private String bizKey;

    /** 设备侧数据时间（断网补传时可能是过去的时间，不覆盖新数据） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportTime;

    /** 来源单据号/设备流水号，落库到 source_ref */
    private String sourceRef;

    /** 通道业务字段 */
    private Map<String, Object> data;
}
