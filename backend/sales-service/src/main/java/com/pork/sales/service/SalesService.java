package com.pork.sales.service;

import com.pork.core.result.PageResult;
import com.pork.sales.dto.SalesRequests;
import com.pork.sales.entity.RecallOrder;
import com.pork.sales.entity.RetailSale;
import com.pork.sales.vo.ExpireWarningVO;
import com.pork.sales.vo.QrCodeVO;
import com.pork.sales.vo.SaleRecordVO;

import java.util.List;
import java.util.Map;

/**
 * 零售业务。
 * <p>
 * 所有带 {@code storeId} 的方法都是「数据边界」而非「筛选项」：控制器从网关透传的
 * {@code X-User-Org} 推导出调用方所能看的门店（零售商＝自己门店，其余角色＝null 不限），
 * 再传进来。服务层拿到非空值就当成硬条件，列表、详情、写操作一律照此收敛。
 */
public interface SalesService {
    List<String> generateQrs(SalesRequests.QrBatch request, Long storeId);
    PageResult<QrCodeVO> pageQrs(Integer status, String qrCode, String batchNo, Long storeId,
                                 long pageNum, long pageSize);
    RetailSale activate(Long id, Long storeId);
    RetailSale sell(SalesRequests.SaleCreate request, Long storeId);
    PageResult<SaleRecordVO> pageSales(Long storeId, String status, String productName, String batchNo,
                                       String storeName, String startDate, String endDate, long pageNum, long pageSize);
    PageResult<ExpireWarningVO> pageWarnings(Integer warningLevel, Integer handled, Long storeId,
                                             long pageNum, long pageSize);
    void handleWarning(Long id, SalesRequests.WarningHandle request, Long storeId);
    RecallOrder createRecall(SalesRequests.RecallCreate request);
    PageResult<RecallOrder> pageRecalls(Integer status, long pageNum, long pageSize);
    RecallOrder getRecall(Long id);
    void updateRecall(Long id, SalesRequests.RecallStatus request);

    /**
     * 按召回范围（分割批次）预先算出售到过这批货的门店与受影响件数。
     * <p>
     * 存在的理由只有一个：发起召回弹窗里的「目标门店」必须由范围自动带出，
     * 而「哪些门店有这批货」的权威口径是销售记录（{@code retail_sale}）。
     * 前端若自己拼运单+签收单，就会出现「弹窗写 3 家店、召回后实际 4 家」的分裂。
     */
    Map<String, Object> previewRecallScope(List<Long> batchIds);

    /**
     * 召回收运完成：货已送进无害化处理中心并签收，召回执行状态收口为「已完成」。
     * <p>
     * 由配送服务在无害化签收成功后调用（内部接口）。同一召回单重复回调是无害的 ——
     * 签收可能重试，收口只能发生一次。
     */
    void completeRecall(Long id, String transportNo);
    void scanExpiringProducts();

    /**
     * 零售经营统计：近 N 日的销售额 / 销量 / 件数、按日趋势、部位销量 TOP、在售与临期数量。
     * <p>
     * 只统计已售出（status=2）且未作废的记录；{@code storeId} 为空表示全部门店汇总。
     * 「在售」「临期」两个计数同样受 {@code storeId} 约束 —— 卡片和一列表必须同一个口径，
     * 否则零售商会看到「临期 13 件」，点进去只有 3 件。
     */
    Map<String, Object> statsSummary(Integer days, Long storeId);
}
