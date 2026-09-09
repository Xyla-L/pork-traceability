package com.pork.trace.service;

import java.util.Map;

public interface TraceQueryService {
    Map<String, Object> search(String keyword);
    Object upstream(String batchNo);
    Object downstream(String batchNo);
    Map<String, Object> full(String batchNo);
    Map<String, Object> verify(String qrCode);
    Map<String, Object> scan(String qrCode);
    Map<String, Object> safeBuy(String qrCode);
}
