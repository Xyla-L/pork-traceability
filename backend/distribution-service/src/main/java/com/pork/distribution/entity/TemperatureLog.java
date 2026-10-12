package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("temperature_log")
public class TemperatureLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transportId;
    private LocalDateTime recordTime;
    private BigDecimal temperature;
    private BigDecimal tempRangeMin;
    private BigDecimal tempRangeMax;
    private Integer isAbnormal;
    private String recorder;
    private String recordMethod;
    /** 数据来源 MANUAL=人工打卡 DEVICE=设备自动采集 API=第三方推送 IMPORT=批量导入 */
    private String source;
    /** 来源设备号/单据号 */
    private String sourceRef;
    /** 设备原始报文留档 */
    private String rawPayload;
    /**
     * 探头自己声明的额定上报间隔（秒）。「多久采一次」是探头的属性，
     * 系统不轮询、也不假设（车会进隧道/地库），所以随每条记录一起留档，
     * 供事后核对「这条的间隔是拿什么基准判的」。
     */
    private Integer sampleIntervalSeconds;
    /**
     * 距上一条采集记录的间隔（秒）。补传的历史数据可能为负值。
     */
    private Integer gapSeconds;
    /**
     * 是否断档：间隔 &gt; 额定间隔 × 容差倍数 即置 1，标在<b>断档之后的第一条</b>上。
     * 只判单点越界（{@link #isAbnormal}）不够 —— 15:00 报一条、17:30 才报下一条，
     * 中间两个半小时是空白，界面却显示「温度正常」。空白比超标更危险。
     */
    private Integer isGap;
    private LocalDateTime createTime;
}
