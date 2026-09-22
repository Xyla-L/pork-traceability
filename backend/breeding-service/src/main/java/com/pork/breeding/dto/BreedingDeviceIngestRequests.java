package com.pork.breeding.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备通道（养殖建档 / 免疫注射）接入请求。
 * <p>
 * 独立于人工录入的 DTO：设备侧多带来源三件套（source/sourceRef/rawPayload）。
 * 注意 pig_individual 的业务字段 source（自繁/外购）在 DeviceTag 里叫 origin，避免撞名。
 */
public final class BreedingDeviceIngestRequests {

    private BreedingDeviceIngestRequests() {
    }

    /** 养殖建档：耳标读写器，佩戴即建档 */
    @Data
    public static class DeviceTag {
        @NotBlank(message = "耳标号不能为空")
        private String earTagNo;

        /** 养殖场名称（设备侧只有名称，由本服务按名称反查 farm） */
        @NotBlank(message = "养殖场名称不能为空")
        private String farmName;

        private String breed;
        private LocalDate birthDate;
        /** 1公 2母 */
        private Integer gender;
        private String penNo;
        /** 业务来源：自繁/外购-供应商名 */
        private String origin;

        private String operator;
        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /** 免疫注射：智能连续注射器 */
    @Data
    public static class DeviceVaccine {
        private Long pigId;
        private String earTagNo;

        @NotBlank(message = "疫苗名称不能为空")
        private String vaccineName;

        @NotBlank(message = "疫苗批号不能为空：无批号的免疫记录无法溯源疫苗来源")
        private String vaccineBatchNo;

        private String manufacturer;
        private LocalDateTime injectTime;
        private String dosage;
        private String injectSite;
        private String operator;
        private String source;
        private String sourceRef;
        private String rawPayload;
    }
}
