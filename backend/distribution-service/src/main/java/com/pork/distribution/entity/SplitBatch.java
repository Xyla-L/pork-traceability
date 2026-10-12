package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("split_batch")
public class SplitBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private Long parentBatchId;

    /** 所属分割作业 split_job.id；为空表示「未经作业归集」的历史记录（字典建立前写入的） */
    private Long jobId;

    private Integer splitLevel;

    /** 标准部位编码（见 meat_part 字典）；为空表示字典建立前的历史记录 */
    private String partCode;

    private String productName;
    private BigDecimal weightKg;
    private Integer packageCount;
    private String packageType;
    private LocalDateTime splitTime;
    private String workshop;
    private BigDecimal workshopTemp;
    private String operator;
    private String fileIds;
    private String note;
    private String contentHash;

    /** 数据来源：MANUAL=人工录入 DEVICE=设备自动采集（分割线扫码称重台） */
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
