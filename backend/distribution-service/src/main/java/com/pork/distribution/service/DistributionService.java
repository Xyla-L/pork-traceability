package com.pork.distribution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.distribution.dto.DeviceIngestRequests;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.CarcassBatch;
import com.pork.distribution.entity.ColdChainTransport;
import com.pork.distribution.entity.DeliveryPlan;
import com.pork.distribution.entity.MeatPart;
import com.pork.distribution.entity.SplitBatch;
import com.pork.distribution.entity.SplitJob;
import com.pork.distribution.entity.StoreReceipt;
import com.pork.distribution.entity.TemperatureLog;
import com.pork.distribution.entity.TransportStop;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DistributionService {
    CarcassBatch createBatch(DistributionRequests.BatchCreate request);
    Page<CarcassBatch> pageBatches(String batchNo, String operator, LocalDate startDate, LocalDate endDate, long pageNum, long pageSize);
    void updateBatch(Long id, DistributionRequests.BatchCreate request);
    void deleteBatch(Long id);
    /** 封批：此后分拣台不再把当日白条并入本批次（换班、换圈时人工切批用） */
    void sealBatch(Long id);
    /** 全量返回 pigId -> 所在胴体批次 的占用映射，供前端选猪时置灰已归批生猪 */
    List<Map<String, Object>> getPigOccupancy();
    /* 人工创建分割批次方法已删除：分割批次为设备独占环节，写入只走设备通道。 */
    Page<SplitBatch> pageSplits(String keyword, long pageNum, long pageSize);
    SplitBatch getSplit(Long id);

    /**
     * 修改分割批次：仅允许修改备注、附件等说明性字段。
     * 批次号、父批次、层级、品名、重量属结论字段，入账后不可改，需作废后重建。
     */
    void updateSplit(Long id, DistributionRequests.SplitUpdate request);

    /**
     * 作废分割批次（原「删除」）：实名 + 理由必填，作废事件上链留证，不做物理删除。
     */
    void voidSplit(Long id, String reason);
    Map<String, Object> getSplitTree(String batchNo);
    /**
     * 分割车间监控快照：取某台相机最近的若干张（按拍摄/写入倒序）。
     * <p>
     * {@code deviceNo} 为空时取全部相机的最新若干张 —— 页面上没指定设备也能看到最近影像。
     * 只读，不触发任何业务动作。
     */
    /**
     * 分割车间监控快照。
     * @param deviceNo 只取某台相机的（可空 = 不限相机）
     * @param batchNo  只取绑定到该胴体批次的（可空 = 不限批次，含未绑定的历史照片）
     * @param limit    取最近若干张
     */
    List<Map<String, Object>> recentSnapshots(String deviceNo, String batchNo, int limit);
    List<Map<String, Object>> upstream(Long splitId);
    Map<String, Object> downstream(String batchNo);
    /** 分割作业分页：作业 = 一头白条的整份产出清单（部位是它的明细） */
    Page<SplitJob> pageSplitJobs(String keyword, long pageNum, long pageSize);
    /** 部位字典（启用中的）：设备上报只认这里的编码，前端下拉也取自这里 */
    List<MeatPart> listMeatParts();
    ColdChainTransport createTransport(DistributionRequests.TransportCreate request);
    void updateTransport(Long id, DistributionRequests.TransportUpdate request);
    void deleteTransport(Long id);
    /**
     * 运单分页。
     *
     * @param storeId 门店数据边界；非空时只返回「该门店有卸货站点」的运单（零售商视角），
     *                为空表示不限（管理员/监管/配送商）
     * @param rejected 非空且为 true 时只返回「有已拒收站点」的运单（门店「拒收/处置」页签用）
     */
    Page<ColdChainTransport> pageTransports(Integer status, String keyword, Long storeId, Boolean rejected,
                                            String kind, long pageNum, long pageSize);
    ColdChainTransport getTransport(Long id);
    void depart(Long id);
    /* 人工补录温度方法已删除：冷链温度为设备独占环节，写入只走设备通道。 */
    List<TemperatureLog> temperatureLogs(Long transportId);
    /**
     * 人工确认到达：推进<b>当前第一个未到达的站点</b>。
     * <p>
     * 单站运单点一次 = 全部到达（运单转「待签收」，与改造前行为一致）；
     * 多站运单一次推进一站。返回本次到达的是哪一站（站序 + 门店名）与运单最新状态。
     * 人工按钮天然不跳站；跳站只可能来自设备上报，那条路上单独拦。
     */
    Map<String, Object> arrive(Long id);
    /** 设备到达倒查用：该运单的站点清单，按站点序号升序 */
    List<TransportStop> stopsOfTransport(Long transportId);
    /**
     * 标记某站到达，并按站点汇总回写运单状态（设备通道与人工共用这一处写入）。
     *
     * @return 运单最新状态；0 表示运单不存在
     */
    int markStopArrived(Long stopId, java.time.LocalDateTime arriveTime, String source,
                        String sourceRef, String rawPayload, String driverName, String driverSource);
    /**
     * 门店签收落库。
     *
     * @param storeId 调用方的门店边界；非空时请求体里的门店必须是这一家，
     *                否则按越权拒绝（签收是货权转移，不能替别家店签）
     */
    StoreReceipt createReceipt(DistributionRequests.ReceiptCreate request, Long storeId);
    /**
     * 批量签收：逐单按「已到达且未签收」的站点签收，**单张失败不影响其余**。
     * <p>
     * 返回逐单结果（成功/跳过 + 原因），让界面能如实说清「签了几单、哪几单没签、为什么」——
     * 批量操作最怕的是「提交了、返回成功、但实际上少签了一张」。
     * <p>
     * 多站运单若同时有多个站点待签收，不替人猜签哪一站，直接跳过并写明原因，
     * 让操作员回到单行「确认签收」逐站处理。
     *
     * @param storeId 调用方的门店边界；非空时只签本店那一站（零售商只能签自己的店）
     */
    List<Map<String, Object>> batchReceipts(DistributionRequests.ReceiptBatch request, Long storeId);
    StoreReceipt getReceipt(Long id);
    void deleteReceipt(Long id);
    Page<StoreReceipt> pageReceipts(Long storeId, String storeName, LocalDate startDate, LocalDate endDate, long pageNum, long pageSize);
    List<Map<String, Object>> stores();

    /* ==================== 门店拒收：冷链异常货物的正式出口 ==================== */

    /**
     * 门店拒收：站点转入「已拒收」终态（理由必填、实名、上链）。
     * <p>
     * 只能由门店人工发起——拒收是收货人的商业判断，设备与司机无权代劳。
     * 拒收站点对运单推导视同「已终结」：单店拒收不卡别的店签收，
     * 全部站点终结运单即闭环（车辆放行口径随之放行）。
     *
     * @param storeId 调用方的门店边界；非空时只能拒本店那一站
     * @return 拒收结果（站序/门店/运单最新状态），供界面提示
     */
    Map<String, Object> rejectStop(Long transportId, DistributionRequests.StopReject request, Long storeId);

    /**
     * 拒收后的处置登记：退回供应商 / 就地销毁 / 折价接收。
     * <p>
     * 系统只记事实、不替人决策；一经登记不可更改（结论字段保护），
     * 且已登记处置的站点不能再「改签收」——货都销毁了，签收无从谈起。
     */
    Map<String, Object> registerDisposal(Long transportId, DistributionRequests.StopDisposal request, Long storeId);

    /**
     * 拒收改签收：协商成功后，把「已拒收」站点按正常签收录入（带签收凭证），
     * 站点标记「曾拒收后改签」。已登记处置的站点拒绝改签。
     */
    StoreReceipt convertRejectToReceipt(Long transportId, DistributionRequests.ReceiptCreate request, Long storeId);

    /* ==================== 拒收回运链（2026-11-02；2026-11-03 收敛为单段）==================== */

    /** 运单段落：正常配送 / 回运段（取回段 RETURN 已取消，仅存历史数据） */
    String KIND_NORMAL = "NORMAL";
    String KIND_RETURN = "RETURN";
    String KIND_DISPOSAL = "DISPOSAL";

    /**
     * 拒收货链条池：已拒收（且未改签收）站点里还没排进回运段的货。
     *
     * @param stage 只支持 DISPOSAL（待回运池）；RETURN（取回池）已取消，传了会明确报错
     */
    java.util.List<java.util.Map<String, Object>> returnPool(String stage);

    /** 取回计划：2026-11-03 起取消（门店 → 冷链中心不再由系统排车），调用返回明确说明 */
    java.util.List<java.util.Map<String, Object>> generateReturnLegs(DistributionRequests.LegPlanGenerate request);

    /** 冷链中心一键生成回运计划（拒收回运）：把「待回运」的拒收货送往无害化处理中心，按核定载重拼车 */
    java.util.List<java.util.Map<String, Object>> generateDisposalLegs(DistributionRequests.LegPlanGenerate request);

    /**
     * 装车扫码即出单：RETURN_LOAD 扫码登记成功后，把扫到的拒收货立刻生成（或并入）
     * 该车牌名下的处理运单（待发车），司机随后即可在车载终端刷卡发车。
     *
     * @return created=是否新开运单 / appended=是否并入已有运单 / transportNo / reason=未能出单的原因
     */
    java.util.Map<String, Object> autoPlanDisposalForScan(String plateNo, java.util.List<Long> stopIds,
                                                          java.time.LocalDateTime scanTime, String deviceNo,
                                                          Long deviceOrgId);

    /** 取回入库：2026-11-03 起取消（拒收即视为已在冷链中心，无需入库确认） */
    java.util.Map<String, Object> returnIntake(Long transportId);

    /** 无害化处理中心确认签收：回运段终点（只有确认，没有拒收；签收即链条终结） */
    java.util.Map<String, Object> disposalConfirm(Long transportId);

    /**
     * 回运单装载明细：这张车装的是哪几家门店、哪批被拒的货，以及「为什么拒」。
     * <p>
     * 回运单的站点只有一站（无害化处理中心），所以「途经站点」那一套在这里没有信息量；
     * 真正要能回答的是「这车倒的是什么、从哪来、凭什么判它不能进市场」——
     * 拒收理由/拒收人/拒收时间回到原站点取，不由回运单另存一份（避免两处不一致）。
     */
    java.util.List<java.util.Map<String, Object>> legItems(Long transportId);

    /* ==================== 召回收运（2026-11-04）==================== */

    /** 召回收运运单：路线是「冷链中心 → 门店1 … 门店N → 无害化处理中心」一趟跑完 */
    String KIND_RECALL = "RECALL";

    /**
     * 召回登记（销售服务调用）：把召回单涉及的「门店 × 批次」写进待收运池。
     * <p>
     * 幂等口径 {@code recall_no + store_id + split_batch_id}：同一次召回重复上报不产生重复待收运行。
     * 返回本次新增条数与池中总量，供回执说明。
     */
    java.util.Map<String, Object> registerRecallCollect(DistributionRequests.RecallCollectRegister request);

    /** 生成收运运单：把待收运池里的召回货按门店分站排成一张多站运单（一车一趟照常生效） */
    java.util.Map<String, Object> generateRecallTransport(DistributionRequests.RecallTransportGenerate request);

    /**
     * 司机到店扫码收货：车牌锁定这一趟，货码锁定这一站上的召回货。
     * 收完该店最后一件 → 站点标记「已收货」，司机才能往下一站走。
     */
    java.util.Map<String, Object> ingestRecallCollect(DeviceIngestRequests.DeviceRecallCollect request);

    /** 收运运单的装载明细：这家店收的是哪几批召回货、收没收、什么时候收的 */
    java.util.List<java.util.Map<String, Object>> recallItems(Long transportId);

    /**
     * 待收运池（页面「处理计划」）：还没排上任何收运运单的召回货，按门店聚合展示。
     * <p>
     * 与拒收回运池的区别只在货的位置：那一边货已在冷链中心等装车，这一边货还在门店货架上等上门收。
     */
    java.util.List<java.util.Map<String, Object>> recallCollectPool();

    /** 收运车辆候选：启用中的备案车（页面生成收运运单时选车，不给空白手填） */
    java.util.List<java.util.Map<String, Object>> vehicleFleet();

    /**
     * 召回收运的「到下一站」前置闸：前面的门店只要还有没扫码收完的货，就不许往下一站走。
     *
     * @return null=可以到达下一站；否则是给现场看的原因
     */
    String recallArriveBlocker(Long transportId, int nextStopNo);

    /* ==================== 发货计划：运单的上游决策单 ==================== */

    /**
     * 新建发货计划：勾选待发运的分割批次 + 目的地门店 + 车次信息。
     * <p>
     * 计划环节只把「决策」记下来，不产生运单 —— 车和司机还没定是常态，
     * 允许先建计划、后配车。运单由 {@link #generateTransportFromPlan} 生成。
     */
    DeliveryPlan createPlan(DistributionRequests.PlanCreate request);

    /**
     * 修改计划：仅「待发货」可改全部（含货物明细）；「部分排车」只允许改剩下的货物与车/人信息。
     * 已排车的明细与事实已绑定，不再改写。
     */
    void updatePlan(Long id, DistributionRequests.PlanUpdate request);

    /** 取消计划：置状态为已取消，不做物理删除 —— 「发过什么计划」本身是管理轨迹 */
    void cancelPlan(Long id, String reason);

    Page<DeliveryPlan> pagePlans(Integer status, String keyword, LocalDate startDate, LocalDate endDate,
                                 long pageNum, long pageSize);

    /** 计划详情：含明细（这一车要装的批次清单），供生成运单前复核 */
    Map<String, Object> getPlan(Long id);

    /**
     * 一键生成运单：目的地/计划时间/车辆司机信息全部从计划带出，货物明细写入运单装载明细。
     * <p>
     * 车牌与司机在<b>这一步</b>强制校验 —— 计划阶段可以空着，货真要走了责任必须落到具体车、具体人。
     *
     * @param splitBatchIds 本次要发的批次；<b>为空表示把剩余未排车的全部发走</b>。
     *                      给子集即「分车」——一车装不完，剩下的留在计划里等下一车，
     *                      计划转为「部分排车」而不是整单作废重开。
     */
    ColdChainTransport generateTransportFromPlan(Long id, List<Long> splitBatchIds);

    /**
     * 装车口自动生成运单：与人工一键生成同一条路径，只多一个「现场事实」入口。
     * <p>
     * {@code site} 是车牌识别相机与司机实名终端读到的值，用来<b>补齐</b>计划里空着的车与人；
     * 若计划已经写了车/人而现场读到的不一致，一律拒绝生成（可能装错车），交人工核对 ——
     * 设备只提供事实，无权改写计划里的安排。
     */
    ColdChainTransport generateTransportFromPlan(Long id, List<Long> splitBatchIds, DistributionRequests.LoadSite site);

    /**
     * 一次发运<b>多张计划</b> —— 一车多目的地（2026-10-10）的主入口。
     * <p>
     * 现实里一辆冷藏车装满后顺路送三四家门店，所以运单粒度是「一个车次」而不是「一次送货」：
     * 传进来的几张计划各自对应一家门店，合起来就是这趟车的站点清单。
     * 单计划版本 {@link #generateTransportFromPlan} 只是它的特例（口径只写一份）。
     * <p>
     * <b>站点序号按 {@code splitBatchIds} 的顺序分配</b>：那正是装车口扫码 / 人工勾选的先后，
     * 「按站装车」时就是卸货顺序；不用另传站点参数 —— 门店归属由每批货所属的计划定死，
     * 系统一次都不猜目的地。
     * <p>
     * 三处不能破的界线：
     * ① 每批货的归属唯一（同时挂在两张计划的未排车明细上 ⇒ 台账冲突，转人工）；
     * ② 一趟车只能有一个车牌、一个责任人（几张计划安排的互不相同 ⇒ 拒绝，很可能是装错车）；
     * ③ 计划状态逐张推进（这趟只装了其中一张的货，其余计划不动）。
     *
     * @param splitBatchIds 本次要发的批次；<b>为空表示把这些计划剩余未排车的全部发走</b>。
     */
    ColdChainTransport generateTransportFromPlans(List<Long> planIds, List<Long> splitBatchIds,
                                                  DistributionRequests.LoadSite site);

    /**
     * 运单的站点清单（一车多目的地的展示口径）。
     * <p>
     * 站点不另建表：按 {@code transport_item.store_id} 聚合即得，同一站的货合起来算量；
     * 到达与签收状态从 {@code store_receipt} 按 (运单, 门店) 取。
     * 单站直送的运单（含存量 34 条）也返回一条，前端不必为两种形态写两套渲染。
     */
    List<Map<String, Object>> transportStops(Long transportId);

    /**
     * 待发运的分割批次：既没被运单装载过、也没被「待发货/部分排车」计划的<b>未排车明细</b>占用，
     * 供建计划时勾选。已取消计划占用、或计划里已排车的批次会自动回到这个列表
     * （判断依据是实时引用关系，不是标记位）。
     */
    Page<SplitBatch> pagePendingSplits(String keyword, long pageNum, long pageSize);

    /**
     * 订单转入（接入通道 {@code ORDER} 的落点）：外部系统的选购/订单事实 → <b>待排车的</b>发货计划。
     * <p>
     * 三条不能破的界线：
     * <ul>
     *   <li><b>订单 ≠ 计划</b>：订单只是计划的一种来源（{@code plan_source=ORDER}），
     *       运单的上游永远是计划。这批货哪天走、哪台车、谁开，仍由排车决定；</li>
     *   <li><b>不建订单表</b>：订单的权威副本在外部系统，本系统只留 {@code order_no} 以便回溯，
     *       原始报文留在 {@code ingest_staging.payload}，不复制第二个真相；</li>
     *   <li><b>只收「要什么、去哪、什么时候要」</b>，不含任何金额 ——
     *       金额不参与上链、也不影响召回圈范围，字段层面就不接收。</li>
     * </ul>
     * 归并与分配：同一 {@code order_no} 重复推送幂等；订单给的「部位 + 重量」由本方法按
     * 可发运的实物批次凑（先进先出），<b>凑不齐一律转人工</b> —— 不猜、不拆零兜底。
     *
     * @return {@code {written, id, reason, retryable}}，与设备内部接口同一套回执语义
     */
    Map<String, Object> createPlanFromOrder(DistributionRequests.OrderIntake request);
}
