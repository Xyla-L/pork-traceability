package com.pork.slaughter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("slaughter_inspection")
public class SlaughterInspection {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联猪只ID
     */
    private Long pigId;

    private String inspectNo;

    private String batchNo;

    private String earTagNo;

    /**
     * 检验类型：1=宰前检验，2=宰后检验，3=宰后同步检验。
     * <p>
     * 2 和 3 按法规口径是同一个环节——《动物检疫管理办法》第二十条「动物屠宰过程实行<b>全流程同步检疫</b>」，
     * 宰后检疫的标准做法就是同步检疫，3 只是在记录上标明「这次是按同步方式做的」。
     * 因此凡涉及「有没有过宰后检疫」的判断（盖章前置、列表筛选）一律按 {@code IN (2,3)} 处理。
     */
    private Integer inspectType;

    /**
     * 检验时间
     */
    private LocalDateTime inspectTime;

    private java.math.BigDecimal temperature;

    /**
     * 脏器检查明细 (JSON格式)
     */
    private String organCheck;

    /**
     * 检验结果：1-合格, 0-不合格
     */
    private Integer result;

    private Integer status;

    private String conclusion;

    /**
     * 不合格项描述
     */
    private String issueDesc;

    /**
     * 不合格品处理方式
     */
    private String disposal;

    /**
     * 官方兽医姓名
     */
    private String veterinary;

    /**
     * 官方兽医执业证号
     */
    private String licenseNo;

    /**
     * 官方兽医电子签章 (SM2签名)
     */
    private String eSignature;

    /**
     * 检验照片/报告文件ID列表 (JSON格式)
     */
    private String fileIds;

    /**
     * 检验报告内容哈希 (用于上链)
     */
    private String contentHash;

    /**
     * 数据来源：MANUAL=人工录入 DEVICE=设备自动采集（工位终端）
     */
    private String source;

    /**
     * 来源设备号/单据号
     */
    private String sourceRef;

    /**
     * 设备原始报文留档
     */
    private String rawPayload;

    /**
     * 创建时间
     */
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
