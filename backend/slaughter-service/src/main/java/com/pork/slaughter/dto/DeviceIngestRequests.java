package com.pork.slaughter.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备通道（入场查验 / 瘦肉精检测）接入请求。
 * <p>
 * 与人工录入的 DTO 分开：设备侧字段更少（仪器/闸口能采到什么就报什么），
 * 且强制携带来源标记，避免设备数据与人工数据在库里混成一团、审计分不清。
 */
public final class DeviceIngestRequests {

    private DeviceIngestRequests() {
    }

    /**
     * 入场查验：门禁地磅（车牌+重量）+ 耳标识读器（耳标）+ 检疫证核验终端（证号）合成的报文。
     * 耳标是主键，生猪档案、来源养殖场都由耳标反查，设备不填。
     */
    @Data
    public static class DeviceEntry {
        /** 接入层按耳标反查到生猪档案后回填的主键（设备侧不填） */
        private Long pigId;

        @NotBlank(message = "耳标号不能为空")
        private String earTagNo;

        @NotBlank(message = "批次号不能为空")
        private String batchNo;

        @NotNull(message = "到厂时间不能为空")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime arriveTime;

        /** 车牌识别结果 */
        private String vehicleNo;

        /** 地磅称重（kg） */
        private BigDecimal weight;

        /** 检疫证核验终端读到的检疫证号 */
        private String quarantineCert;

        /** 来源养殖场（接入层按耳标反查后回填，设备侧不填） */
        private String sourceFarm;

        /** 临床健康检查：闸口设备默认 1（无异常体征）；1通过 0异常 */
        private Integer healthCheck;

        /** 检疫证在线核验结果：接入层核验后回填，1通过 0未通过（缺证即 0） */
        private Integer certVerified;

        private String abnormalNote;
        private String remark;

        /** 采集设备名，落库到查验人字段，例如「门禁-001 自动采集」 */
        private String inspector;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 瘦肉精检测：胶体金读数仪输出。
     * result 为 null 表示仪器尚未出结果——这种情况必须拒绝入库，绝不能默认成"阴性"。
     */
    @Data
    public static class DeviceRactopamine {
        /** 接入层按耳标反查到生猪档案后回填的主键（设备侧不填）；为空时接入层转人工处理 */
        private Long pigId;

        @NotBlank(message = "耳标号不能为空")
        private String earTagNo;

        @NotBlank(message = "样本编号不能为空")
        private String sampleNo;

        @NotNull(message = "检测时间不能为空")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime testTime;

        @NotBlank(message = "检测方法不能为空")
        private String testMethod;

        /** 1阴性 0阳性；null=仪器未出结果 */
        private Integer result;

        private String detectionLimit;
        private String testType;
        private String testTarget;
        private String samplePart;
        private String batchNo;
        private String reportUrl;
        private String remark;

        /** 仪器编号/操作员，落库到检测人员字段 */
        private String operator;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 屠宰检验工位终端：兽医在工位判定，终端一键回传。
     * 判定权在人——result 与 veterinary 是硬性必填，接入层与业务侧双重校验。
     */
    @Data
    public static class DeviceInspection {
        /** 接入层按耳标反查到生猪档案后回填的主键（设备侧不填） */
        private Long pigId;

        @NotBlank(message = "耳标号不能为空")
        private String earTagNo;

        @NotBlank(message = "批次号不能为空")
        private String batchNo;

        /** 1宰前检验 2宰后检验 3同步检验；缺省按宰后检验 */
        private Integer inspectType;

        /** 1合格 0不合格；null=终端未给出结论，禁止入库 */
        private Integer result;

        /** 体温（宰前检验用） */
        private BigDecimal temperature;

        private String organCheck;
        private String conclusion;
        private String issueDesc;
        private String disposal;

        @NotBlank(message = "检验人不能为空：检验结论必须落具体兽医")
        private String veterinary;

        private String licenseNo;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 胴体自动盖章机：检验合格后自动执行盖章动作。
     * 授权兽医必填——盖章的授权是兽医的法定行为，机器只是执行机构。
     */
    @Data
    public static class DeviceStamp {
        /** 接入层按耳标反查到生猪档案后回填的主键（设备侧不填） */
        private Long pigId;

        @NotBlank(message = "耳标号不能为空")
        private String earTagNo;

        @NotBlank(message = "批次号不能为空")
        private String batchNo;

        @NotBlank(message = "胴体编号不能为空")
        private String carcassNo;

        /** 缺省按「检疫合格章」 */
        private String stampType;
        private String stampPosition;

        @NotBlank(message = "授权兽医不能为空：盖章授权必须落具体人")
        private String veterinary;

        private String remark;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }
}
