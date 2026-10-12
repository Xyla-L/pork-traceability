package com.pork.ingest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 接入设备台账（db_ingest.ingest_device） */
@Data
@TableName("ingest_device")
public class IngestDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 设备编号（设备侧唯一标识） */
    private String deviceNo;
    private String deviceName;
    /** 接入通道 TEMPERATURE/ENTRY/RACTOPAMINE/SALE/RECEIPT */
    private String channel;
    private String location;
    /**
     * 车载设备绑定的车牌（发车终端 / 车载温度探头用；固定点位设备为 NULL）。
     * <p>
     * 存在的意义是让「这个设备是哪台车」<b>可查询</b>：原来只有 {@link #location} 里
     * 一句「一号冷藏车车厢」这种自然语言，发车设备化要拿车牌当唯一凭据，靠字符串猜是不行的。
     */
    private String vehicleNo;
    /**
     * 额定上报间隔（秒），温度探头用；NULL 表示按服务默认值。
     * <p>
     * 「多久采一次」是探头的属性，不该由系统假设。系统拿它当基准判断档
     * （相邻两条间隔 &gt; 本值 × 容差 ⇒ 标 {@code is_gap}）。
     */
    private Integer sampleIntervalSeconds;
    /** 设备密钥（请求头 X-Device-Key） */
    private String secretKey;
    /** 1启用 0停用 */
    private Integer status;
    private LocalDateTime lastSeenTime;
    private String remark;
    private LocalDateTime createTime;
}
