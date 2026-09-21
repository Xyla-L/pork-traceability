package com.pork.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备通道（温度采集 / 门店签收）接入请求。
 * <p>
 * 独立于人工录入的 DTO：设备侧多带来源三件套（source/sourceRef/rawPayload），
 * 且签收必须由人签（receiver/eSignature）——设备可以自动读温度、自动读数量，
 * 但不能替人承担签收责任。
 */
public final class DeviceIngestRequests {

    private DeviceIngestRequests() {
    }

    /** 冷链温度采集：车载温控/冷库探头 */
    @Data
    public static class DeviceTemperature {
        /** 运输单号（设备侧只有单号，接入层透传后由本服务换成主键） */
        private String transportNo;

        private Long transportId;

        @NotNull(message = "温度值不能为空")
        @DecimalMin(value = "-60.0", message = "温度值超出物理合理区间")
        private BigDecimal temperature;

        /** 允许区间下限，缺省用服务默认值 */
        private BigDecimal tempRangeMin;
        private BigDecimal tempRangeMax;

        /** 设备侧采集时间（断网补传时可能是过去的时间） */
        private LocalDateTime recordTime;

        /** 采集设备标识，落库到 recorder */
        private String recorder;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /** 门店签收：PDA 扫码 + 门店冷柜测温 */
    @Data
    public static class DeviceReceipt {
        private String transportNo;
        private Long transportId;

        private Long storeId;

        @NotBlank(message = "门店名称不能为空")
        private String storeName;

        /** 签收人（责任转移必须落具体人，缺失时转人工处理） */
        private String receiver;
        private String receiverPhone;

        private Integer qtyCheck;
        private String qtyDiffNote;
        private Integer tempCheck;
        private BigDecimal tempValue;
        private Integer packageIntact;
        /** 电子签名（图片地址或 base64），保留手写签收的法律意义 */
        private String signature;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }
}
