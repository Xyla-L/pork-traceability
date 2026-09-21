package com.pork.ingest.service;

import com.pork.ingest.dto.DeviceReportRequest;
import com.pork.ingest.entity.IngestDevice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 一次上报的上下文。
 *
 * @param device     鉴权通过的设备
 * @param report     上报信封
 * @param rawPayload 原始报文（JSON 字符串，直接落库留档）
 */
public record IngestContext(IngestDevice device, DeviceReportRequest report, String rawPayload) {

    public Map<String, Object> data() {
        return report.getData() == null ? Map.of() : report.getData();
    }

    /** 设备侧数据时间；设备没给就用接收时间，保证业务表时间字段非空 */
    public LocalDateTime reportTime() {
        return report.getReportTime() == null ? LocalDateTime.now() : report.getReportTime();
    }

    public String sourceRef() {
        return report.getSourceRef() == null ? device.getDeviceNo() : report.getSourceRef();
    }

    /** 落库到业务表"采集人/查验人/操作员"字段的默认值 */
    public String deviceLabel() {
        return device.getDeviceNo() + " 自动采集";
    }
}
