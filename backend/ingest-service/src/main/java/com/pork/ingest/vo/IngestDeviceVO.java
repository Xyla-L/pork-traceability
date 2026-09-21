package com.pork.ingest.vo;

import com.pork.ingest.entity.IngestDevice;
import lombok.Data;

import java.time.LocalDateTime;

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
    private String location;
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
        vo.setChannelLabel(channelLabel(entity.getChannel()));
        vo.setLocation(entity.getLocation());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(Integer.valueOf(1).equals(entity.getStatus()) ? "启用" : "停用");
        vo.setLastSeenTime(entity.getLastSeenTime());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private static String channelLabel(String channel) {
        if (channel == null) return null;
        return switch (channel) {
            case "TEMPERATURE" -> "冷链温度采集";
            case "ENTRY" -> "入场查验";
            case "RACTOPAMINE" -> "瘦肉精检测";
            case "SALE" -> "门店收银";
            case "RECEIPT" -> "门店签收";
            default -> channel;
        };
    }
}
