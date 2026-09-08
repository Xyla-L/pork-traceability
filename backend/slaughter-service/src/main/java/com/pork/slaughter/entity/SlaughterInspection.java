package com.pork.slaughter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
     * 检验类型：1-宰前检验, 2-宰后检验
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
     * 创建时间
     */
    private LocalDateTime createTime;
}
