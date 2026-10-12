package com.pork.ingest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 演示/联调用的模拟上报请求（管理端发起，需要登录）。
 * <p>
 * 与 {@link DeviceReportRequest} 的区别只有一点：设备侧报的是「密钥」，
 * 这里报的是「设备编号」——密钥由服务端按编号反查，不下发给浏览器。
 */
@Data
public class DemoReportRequest {

    /** 设备编号，必须在 ingest_device 中已登记 */
    @NotBlank(message = "deviceNo(设备编号)不能为空")
    private String deviceNo;

    /**
     * 这次上报走哪个通道；留空取设备台账登记的那个通道。
     * <p>
     * 一台车载终端同时承担「发车确认」与「到站刷卡确认」两个通道（同一台设备、同一个读卡头，
     * 见 {@code IngestChannel.deviceGroup()}），所以代设备上报时得说清<b>这次是哪个动作</b> ——
     * 真机也一样：报文自己带 channel，服务端只校验它是否落在该设备的能力范围内。
     */
    private String channel;

    /** 设备侧唯一号；留空则自动生成，便于反复触发。填同一个值可验证幂等 */
    private String bizKey;

    /** 设备侧数据时间，用于演示补传/乱序场景；留空取当前时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportTime;

    /** 来源单据号/设备流水号，留空取设备编号 */
    private String sourceRef;

    /** 通道业务字段，与真机上报的 data 结构完全一致 */
    private Map<String, Object> data;
}
