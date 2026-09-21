package com.pork.sales.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店收银 POS 接入请求。
 * <p>
 * 设备只报「扫了哪个码、卖了多少钱、多少重」，产品与批次信息由二维码反查，
 * 不允许 POS 自行指定批次——否则收银端可以伪造任意批次的产品售出。
 */
public final class DeviceIngestRequests {

    private DeviceIngestRequests() {
    }

    @Data
    public static class DeviceSale {
        @NotBlank(message = "产品二维码不能为空")
        private String qrCode;

        /** 收银小票金额 */
        private BigDecimal sellPrice;

        /** 电子秤重量 */
        private BigDecimal sellWeightKg;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }
}
