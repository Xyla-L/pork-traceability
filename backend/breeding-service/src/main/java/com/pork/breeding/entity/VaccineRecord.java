package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("vaccine_record")
public class VaccineRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pigId;
    private String vaccineName;
    private String batchNo;
    private String manufacturer;
    private LocalDateTime injectTime;
    private String dosage;
    private String injectSite;
    private String operator;
    private String fileIds; // JSON字段

    /** 数据来源：MANUAL=人工录入 DEVICE=设备自动采集 */
    private String source;
    /** 来源设备号/单据号 */
    private String sourceRef;
    /** 设备原始报文留档 */
    private String rawPayload;

    private LocalDateTime createTime;

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