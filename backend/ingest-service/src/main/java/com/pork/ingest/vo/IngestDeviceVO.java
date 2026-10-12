package com.pork.ingest.vo;

import com.pork.ingest.entity.IngestDevice;
import com.pork.ingest.support.IngestChannel;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备台账展示对象。
 * <p>
 * <b>故意不含 secretKey</b>：设备密钥是设备身份凭据，管理端只需要知道"这台设备是什么、
 * 在不在线"，不需要也不应该看到密钥。直接返回实体曾把密钥泄给任何已登录用户，
 * 那等于把设备凭据公开发放。密钥只在设备侧保管，服务端只做反查。
 */
@Data
public class IngestDeviceVO {
    private Long id;
    private String deviceNo;
    private String deviceName;
    private String channel;
    private String channelLabel;
    /**
     * 这台设备能上报的全部通道（枚举名）。
     * <p>
     * 全系统只有车载终端不是「一设备一通道」：驾驶室里那台终端既确认发车也确认到站刷卡
     * （同一个读卡头、同一把密钥），所以台账里一台车只有一行，而它承担两个通道。
     */
    private List<String> channels;
    /** 上面那组通道的中文名，按业务顺序用「·」连接，直接展示 */
    private String channelLabels;
    private String location;
    /** 车载设备绑定的车牌（固定点位设备为空）；发车设备化要靠它把「设备 ↔ 车」对上 */
    private String vehicleNo;
    /** 额定上报间隔（秒），温度探头用；展示成「5 分钟一条」这种说法 */
    private Integer sampleIntervalSeconds;
    /** 1启用 0停用 */
    private Integer status;
    private String statusLabel;
    private LocalDateTime lastSeenTime;
    private String remark;
    private LocalDateTime createTime;

    public static IngestDeviceVO from(IngestDevice entity) {
        IngestDeviceVO vo = new IngestDeviceVO();
        vo.setId(entity.getId());
        vo.setDeviceNo(entity.getDeviceNo());
        vo.setDeviceName(entity.getDeviceName());
        vo.setChannel(entity.getChannel());
        vo.setChannelLabel(IngestChannel.labelOf(entity.getChannel()));
        // 一台车载终端 = 发车确认 + 到站刷卡确认（同一台设备、同一个读卡头）
        List<String> group = IngestChannel.deviceChannelGroup(entity.getChannel());
        vo.setChannels(group);
        vo.setChannelLabels(group.stream().map(IngestChannel::labelOf).collect(Collectors.joining(" · ")));
        vo.setLocation(entity.getLocation());
        vo.setVehicleNo(entity.getVehicleNo());
        vo.setSampleIntervalSeconds(entity.getSampleIntervalSeconds());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(Integer.valueOf(1).equals(entity.getStatus()) ? "启用" : "停用");
        vo.setLastSeenTime(entity.getLastSeenTime());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
