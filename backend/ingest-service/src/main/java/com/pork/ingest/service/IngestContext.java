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

    /**
     * 采集设备编号。
     * <p>
     * 只用于「本来就是机器在记录」的字段（如冷链温度的温度记录仪）。
     * 操作人 / 查验员 / 检测员 / 签收人这类字段**一律不得**用它兜底 ——
     * 设备不是人，把设备名写进人名字段会让追溯链上查不到具体责任人。
     * 这类字段缺失时由各通道转人工补录。
     */
    public String deviceNo() {
        return device.getDeviceNo();
    }
}
