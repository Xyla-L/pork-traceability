package com.pork.breeding.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

        /** 接种凭证照片的文件ID列表（能拍照的终端上报时带上；接入层已核验过真实性） */
        private List<String> fileIds;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /** 产地检疫出证：检疫证扫码登记终端（扫纸质证二维码带出证面信息） */
    @Data
    public static class DeviceQuarantineCert {
        private Long pigId;
        private String earTagNo;

        @NotBlank(message = "检疫证编号不能为空")
        private String certNo;

        @NotBlank(message = "签发机构不能为空")
        private String issueOrg;

        private LocalDateTime issueTime;
        private LocalDate validUntil;
        /** 证面签发的官方兽医（不是扫码的人，也不是设备名） */
        private String inspector;

        /**
         * 检疫证扫描件文件ID列表（接入层已核验真实性）。
         * 检疫证表是单列 file_id，入库时只取第一张；要多张须先改列类型。
         */
        private List<String> fileIds;

        private String caSignature;
        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 申请出栏：出栏口地磅（耳标识读 + 称重）。
     * <p>
     * 注意这里<b>没有操作人字段</b>：称重是机器动作，本环节也不需要人签名——
     * 出栏的「谁批准的」落在审批环节（审批人实名），不在称重登记这里。
     * 硬凑一个操作人字段只会让设备名或空值混进人名位置，反而破坏「设备 ≠ 人」的口径。
     */
    @Data
    public static class DeviceExitApply {
        private Long pigId;
        private String earTagNo;

        /** 出栏体重（kg），地磅实测值 */
        @NotNull(message = "出栏体重不能为空")
        private BigDecimal weightKg;

        /** 称重时刻；缺失时由调用方用上报时刻补齐 */
        private LocalDateTime applyTime;

        /** 目标屠宰场：去向声明，设备给不出时为空，由养殖服务转为待人工确认 */
        private String targetSlaughterhouse;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }
}
