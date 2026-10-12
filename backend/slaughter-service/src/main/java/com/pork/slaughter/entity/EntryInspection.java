package com.pork.slaughter.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
    /**
     * 作废标记：0=有效 1=已作废。
     * 同时作为 MyBatis-Plus 的逻辑删除标记——查询默认只取有效记录，作废数据留在库里不物理删除。
     */
    @TableLogic(value = "0", delval = "1")
    private Integer voidFlag;
    /** 作废理由（必填），随作废事件一并上链 */
    private String voidReason;
    /** 作废操作人（实名） */
    private String voidOperator;
    /** 作废时间 */
    private LocalDateTime voidTime;
}
