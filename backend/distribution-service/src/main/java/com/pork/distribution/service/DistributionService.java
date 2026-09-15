package com.pork.distribution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.CarcassBatch;
import com.pork.distribution.entity.ColdChainTransport;
import com.pork.distribution.entity.SplitBatch;
import com.pork.distribution.entity.StoreReceipt;
import com.pork.distribution.entity.TemperatureLog;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DistributionService {
    CarcassBatch createBatch(DistributionRequests.BatchCreate request);
    Page<CarcassBatch> pageBatches(String batchNo, String operator, LocalDate startDate, LocalDate endDate, long pageNum, long pageSize);
    void updateBatch(Long id, DistributionRequests.BatchCreate request);
    void deleteBatch(Long id);
    /** 全量返回 pigId -> 所在胴体批次 的占用映射，供前端选猪时置灰已归批生猪 */
    List<Map<String, Object>> getPigOccupancy();
    SplitBatch createSplit(DistributionRequests.SplitCreate request);
    Page<SplitBatch> pageSplits(String keyword, long pageNum, long pageSize);
    SplitBatch getSplit(Long id);
    void updateSplit(Long id, DistributionRequests.SplitUpdate request);
    void deleteSplit(Long id);
    Map<String, Object> getSplitTree(String batchNo);
    List<Map<String, Object>> upstream(Long splitId);
    Map<String, Object> downstream(String batchNo);
    ColdChainTransport createTransport(DistributionRequests.TransportCreate request);
    void updateTransport(Long id, DistributionRequests.TransportUpdate request);
    void deleteTransport(Long id);
    Page<ColdChainTransport> pageTransports(Integer status, String keyword, long pageNum, long pageSize);
    ColdChainTransport getTransport(Long id);
    void depart(Long id);
    TemperatureLog addTemperature(Long transportId, DistributionRequests.TemperatureCreate request);
    List<TemperatureLog> temperatureLogs(Long transportId);
    void arrive(Long id);
    StoreReceipt createReceipt(DistributionRequests.ReceiptCreate request);
    StoreReceipt getReceipt(Long id);
    void deleteReceipt(Long id);
    Page<StoreReceipt> pageReceipts(Long storeId, String storeName, LocalDate startDate, LocalDate endDate, long pageNum, long pageSize);
    List<Map<String, Object>> stores();
}
