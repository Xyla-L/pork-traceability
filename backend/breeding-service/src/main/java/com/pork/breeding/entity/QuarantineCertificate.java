package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("quarantine_certificate")
public class QuarantineCertificate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pigId;
    private String certNo;
    private String issueOrg;
    private LocalDateTime issueTime;
    private LocalDate validUntil;
    private String inspector;
    private String certType;
    private String fileId;
    private String caSignature;
    private String contentHash;
    private LocalDateTime createTime;

    /** 数据来源 MANUAL=人工出证 DEVICE=扫码登记终端采集 */
    private String source;
    /** 来源设备号（设备登记时记扫码终端编号，如 QSCAN-001） */
    private String sourceRef;
    /** 设备原始报文（设备登记时留档，便于逐字段核对） */
    private String rawPayload;

    /** 作废标记：0=有效 1=已作废（同时是 MyBatis-Plus 逻辑删除标记，查询默认只取有效记录） */
    @TableLogic(value = "0", delval = "1")
    private Integer voidFlag;
    /** 作废理由（必填），随作废事件一并上链 */
    private String voidReason;
    /** 作废操作人（实名） */
    private String voidOperator;
    /** 作废时间 */
    private LocalDateTime voidTime;
}