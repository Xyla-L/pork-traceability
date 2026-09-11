package com.pork.trace.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SafeBuyVO {
    private String qrCode;
    private Map<String, Object> product;
    private List<Object> certChain;    // 证书链
    private List<Object> reports;      // 检测报告
    private List<Object> chainRecords; // 溯源链条记录
    private Integer recordCount;       // 记录总数
    private Map<String, Object> blockchain; // 区块链验证信息
}