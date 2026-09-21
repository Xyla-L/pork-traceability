package com.pork.ingest.vo;

import com.pork.ingest.entity.IngestStaging;
import com.pork.ingest.support.IngestStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 待确认队列展示对象：补上状态/通道中文标签与设备名称，前端不再做编码翻译 */
@Data
public class IngestStagingVO {
    private Long id;
    private String channel;
    private String channelLabel;
    private String deviceNo;
    private String deviceName;
    private String bizKey;
    private LocalDateTime reportTime;
    private LocalDateTime receiveTime;
    private String payload;
    private String sourceRef;
    private Integer status;
    private String statusLabel;
    private String targetTable;
    private Long targetId;
    private String errorMsg;
    private LocalDateTime handleTime;
    private String handler;
    private String remark;
    private LocalDateTime createTime;

    public static IngestStagingVO from(IngestStaging entity, String deviceName) {
        IngestStagingVO vo = new IngestStagingVO();
        vo.setId(entity.getId());
        vo.setChannel(entity.getChannel());
        vo.setChannelLabel(channelLabel(entity.getChannel()));
        vo.setDeviceNo(entity.getDeviceNo());
        vo.setDeviceName(deviceName);
        vo.setBizKey(entity.getBizKey());
        vo.setReportTime(entity.getReportTime());
        vo.setReceiveTime(entity.getReceiveTime());
        vo.setPayload(entity.getPayload());
        vo.setSourceRef(entity.getSourceRef());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(IngestStatus.label(entity.getStatus()));
        vo.setTargetTable(entity.getTargetTable());
        vo.setTargetId(entity.getTargetId());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setHandleTime(entity.getHandleTime());
        vo.setHandler(entity.getHandler());
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
