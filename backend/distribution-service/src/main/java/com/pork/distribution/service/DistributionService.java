package com.pork.distribution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.CarcassBatch;
import com.pork.distribution.entity.ColdChainTransport;
import com.pork.distribution.entity.SplitBatch;
import com.pork.distribution.entity.StoreReceipt;
import com.pork.distribution.entity.TemperatureLog;

import java.util.List;
import java.util.Map;

public interface DistributionService {
    CarcassBatch createBatch(DistributionRequests.BatchCreate request);
    Page<CarcassBatch> pageBatches(String batchNo, long pageNum, long pageSize);
    SplitBatch createSplit(DistributionRequests.SplitCreate request);
    SplitBatch getSplit(Long id);
    Map<String, Object> getSplitTree(String batchNo);
    List<Map<String, Object>> upstream(Long splitId);
    Map<String, Object> downstream(String batchNo);
    ColdChainTransport createTransport(DistributionRequests.TransportCreate request);
    Page<ColdChainTransport> pageTransports(Integer status, long pageNum, long pageSize);
    ColdChainTransport getTransport(Long id);
    void depart(Long id);
    TemperatureLog addTemperature(Long transportId, DistributionRequests.TemperatureCreate request);
    List<TemperatureLog> temperatureLogs(Long transportId);
    void arrive(Long id);
    StoreReceipt createReceipt(DistributionRequests.ReceiptCreate request);
    Page<StoreReceipt> pageReceipts(Long storeId, long pageNum, long pageSize);
    List<Map<String, Object>> stores();
}
