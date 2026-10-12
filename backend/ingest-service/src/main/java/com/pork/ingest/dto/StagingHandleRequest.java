package com.pork.ingest.dto;

import lombok.Data;

import java.util.Map;

/** 待确认队列的人工处理入参 */
@Data
public class StagingHandleRequest {
    /** 处理人（取网关透传的 X-User-Id，缺省时用这里填的值） */
    private String handler;
    /** 确认入账时的备注 / 拒绝原因 */
    private String remark;
    /**
     * 人工补齐的字段（仅确认入账时使用）。
     * <p>
     * 设备拿不到、但记录必须有的内容由人工在这里补：现场人员姓名（接种人 / 查验员 / 签收人）、
     * 接种凭证照片 fileIds 等。补齐内容只并入本次入账的数据，设备原始报文保持不可变，
     * 补了什么会写进处理备注留档。
     */
    private Map<String, Object> payload;

    public String reason() {
        return remark;
    }
}
