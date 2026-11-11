package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 外部养殖系统推来的批次档案报文（内部接口用，不对外开放）。
 *
 * <p>字段与接入层 {@code FarmErpChannelHandler} 一一对应。日期一律用字符串接：
 * 外部系统的格式五花八门，接入层已做归一，养殖服务只认 {@code yyyy-MM-dd}，
 * 认不出的直接判「数据不成立」——宁可让外部系统改格式，也不要猜出一个日期。
 *
 * <p>成员两种给法：连续区间（自繁常见）或明细清单（外购拼车、跨号段领标）。
 * 两个都给时以清单为准（更精确），区间只当展示用的头尾。
 */
@Data
public class FarmBatchIngestRequest {

    @Schema(description = "外部系统代号，如 FARM_ERP；同一通道可接多家")
    private String systemCode;

    @Schema(description = "动作 UPSERT=新建/更新 REVOKE=源撤销")
    private String action;

    @Schema(description = "外部系统内的批次主键，不传则取批次号")
    private String externalId;

    @Schema(description = "养殖场名称（与本系统 farm.farm_name 对齐）")
    private String farmName;

    @Schema(description = "批次号（同一养殖场内唯一）")
    private String batchNo;

    private String breed;

    @Schema(description = "进场日期 yyyy-MM-dd")
    private String entryDate;

    @Schema(description = "批次出生日期 yyyy-MM-dd（日龄由它现算）")
    private String birthDate;

    private String source;

    private String earTagStart;

    private String earTagEnd;

    @Schema(description = "耳标明细清单（与区间二选一，优先）")
    private List<String> earTags;

    private Integer quantity;

    @Schema(description = "撤销理由（REVOKE 必填）")
    private String voidReason;

    @Schema(description = "来源凭据（设备号/外部单据号）")
    private String sourceRef;

    @Schema(description = "原始报文，留档用")
    private String rawPayload;
}
