package com.pork.sales.service;

import com.pork.core.result.PageResult;
import com.pork.sales.dto.SalesRequests;
import com.pork.sales.entity.ExpireWarning;
import com.pork.sales.entity.RecallOrder;
import com.pork.sales.entity.RetailSale;
import com.pork.sales.vo.QrCodeVO;

import java.util.List;

public interface SalesService {
    List<String> generateQrs(SalesRequests.QrBatch request);
    PageResult<QrCodeVO> pageQrs(Integer status, String qrCode, long pageNum, long pageSize);
    RetailSale activate(Long id);
    RetailSale sell(SalesRequests.SaleCreate request);
    PageResult<RetailSale> pageSales(Long storeId, Integer status, long pageNum, long pageSize);
    PageResult<ExpireWarning> pageWarnings(Integer warningLevel, Integer handled, long pageNum, long pageSize);
    void handleWarning(Long id, SalesRequests.WarningHandle request);
    RecallOrder createRecall(SalesRequests.RecallCreate request);
    PageResult<RecallOrder> pageRecalls(Integer status, long pageNum, long pageSize);
    RecallOrder getRecall(Long id);
    void updateRecall(Long id, SalesRequests.RecallStatus request);
    void scanExpiringProducts();
}
