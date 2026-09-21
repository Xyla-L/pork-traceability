package com.pork.ingest.dto;

import lombok.Data;

/** 待确认队列的人工处理入参 */
@Data
public class StagingHandleRequest {
    /** 处理人（取网关透传的 X-User-Id，缺省时用这里填的值） */
    private String handler;
    /** 确认入账时的备注 / 拒绝原因 */
    private String remark;

    public String reason() {
        return remark;
    }
}
