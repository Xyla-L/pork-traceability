package com.pork.slaughter.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("entry_inspection")
public class EntryInspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pigId;
    private String batchNo;
    private String earTagNo;
    private String sourceFarm;
    private LocalDateTime arriveTime;
    private java.math.BigDecimal weight;
    private String quarantineCert;
    private String vehicleNo;
    private Integer healthCheck; // 1通过 0异常
    private Integer certVerified; // 1通过 0异常
    private String abnormalNote;
    private String inspector;
    /** 数据来源 MANUAL=人工录入 DEVICE=设备自动采集 API=第三方推送 IMPORT=批量导入 */
    private String source;
    /** 来源设备号/第三方单据号，用于回溯数据是哪台机器写的 */
    private String sourceRef;
    /** 设备原始报文留档（不可变），事后可逐字段核对 */
    private String rawPayload;
    private Integer status;
    private String remark;
    private String fileIds; // JSON字符串存储
    private LocalDateTime createTime;
}
