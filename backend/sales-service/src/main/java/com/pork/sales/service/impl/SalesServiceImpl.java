package com.pork.sales.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.PageResult;
import com.pork.core.util.BusinessNoGenerator;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.sales.dto.SalesRequests;
import com.pork.sales.client.DistributionClient;
import com.pork.sales.entity.ExpireWarning;
import com.pork.sales.entity.RecallOrder;
import com.pork.sales.entity.RetailSale;
import com.pork.sales.mapper.ExpireWarningMapper;
import com.pork.sales.mapper.RecallOrderMapper;
import com.pork.sales.mapper.RetailSaleMapper;
import com.pork.sales.service.SalesService;
import com.pork.sales.vo.ExpireWarningVO;
import com.pork.sales.vo.QrCodeVO;
import com.pork.sales.vo.SaleRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesServiceImpl implements SalesService {
    private final RetailSaleMapper saleMapper;
    private final ExpireWarningMapper warningMapper;
    private final RecallOrderMapper recallMapper;
    private final ChainEventPublisher chainEvents;
    private final DistributionClient distributionClient;
    private final com.pork.sales.client.AuthClient authClient;
    private final com.pork.sales.client.NotificationClient notificationClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> generateQrs(SalesRequests.QrBatch request, Long storeId) {
        Map<String, Object> receipt = distributionClient.requireReceipt(request.receiptId());
        Long transportId = Long.valueOf(Objects.toString(receipt.get("transportId")));
        Map<String, Object> transport = distributionClient.requireTransport(transportId);
        if (!Integer.valueOf(4).equals(transport.get("status"))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该批次门店尚未签收，无法生成二维码");
        }
        Long splitBatchId = Long.valueOf(Objects.toString(transport.get("splitBatchId")));
        Long receiptStoreId = receipt.get("storeId") == null ? 0L : Long.valueOf(Objects.toString(receipt.get("storeId")));
        // 门店边界：零售商只能给「自己门店的签收单」建码，拿别家的签收单号也不行
        requireStore(storeId, receiptStoreId, "该签收单");
        String storeName = Objects.toString(receipt.get("storeName"), null);
        List<String> codes = new ArrayList<>(request.count());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < request.count(); i++) {
            String code = "QR-PORK-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
            RetailSale row = new RetailSale();
            row.setSplitBatchId(splitBatchId);
            row.setTransportId(transportId);
            row.setReceiptId(request.receiptId());
            row.setProductQrCode(code);
            row.setStoreId(receiptStoreId);
            row.setStoreName(storeName);
            row.setIsActivated(0);
            row.setStatus(1);
            row.setExpireDate(request.expireDate() == null ? LocalDate.now().plusDays(7) : request.expireDate());
            row.setCreateTime(now);
            saleMapper.insert(row);
            codes.add(code);
        }
        return codes;
    }

    @Override
    public PageResult<QrCodeVO> pageQrs(Integer status, String qrCode, String batchNo, Long storeId,
                                        long pageNum, long pageSize) {
        var wrapper = Wrappers.<RetailSale>lambdaQuery()
                // 门店是数据边界，不是筛选项：零售商永远只看得到自己这一家
                .eq(storeId != null, RetailSale::getStoreId, storeId)
                .eq(status != null, RetailSale::getStatus, status)
                .like(StringUtils.hasText(qrCode), RetailSale::getProductQrCode, qrCode)
                .orderByDesc(RetailSale::getCreateTime);
        Map<Long, Map<String, Object>> splitCache = new HashMap<>();
        Map<Long, Map<String, Object>> transportCache = new HashMap<>();
        if (StringUtils.hasText(batchNo)) {
            List<RetailSale> matched = saleMapper.selectList(wrapper).stream()
                    .filter(row -> matchesSplit(row, null, batchNo, splitCache)).toList();
            long total = matched.size();
            int from = (int) Math.min((pageNum - 1) * pageSize, total);
            int to = (int) Math.min(from + pageSize, total);
            List<QrCodeVO> records = matched.subList(from, to).stream()
                    .map(row -> toQrCodeVO(row, splitCache, transportCache)).toList();
            return PageResult.of(pageNum, pageSize, total, records);
        }
        Page<RetailSale> page = saleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<QrCodeVO> records = page.getRecords().stream()
                .map(row -> toQrCodeVO(row, splitCache, transportCache)).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    private QrCodeVO toQrCodeVO(RetailSale row, Map<Long, Map<String, Object>> splitCache,
                                 Map<Long, Map<String, Object>> transportCache) {
        Map<String, Object> split = splitInfo(row.getSplitBatchId(), splitCache);
        String batchNo = split == null ? null : Objects.toString(split.get("batchNo"), null);
        String transportNo = null;
        if (row.getTransportId() != null && transportCache != null) {
            Map<String, Object> transport = transportInfo(row.getTransportId(), transportCache);
            if (transport != null) transportNo = Objects.toString(transport.get("transportNo"), null);
        }
        return new QrCodeVO(row.getId(), row.getProductQrCode(), row.getSplitBatchId(), batchNo,
                row.getTransportId(), transportNo, row.getReceiptId(), row.getStoreId(), row.getStoreName(),
                row.getIsActivated() == 0 ? 0 : row.getStatus(), row.getExpireDate(), row.getCreateTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetailSale activate(Long id, Long storeId) {
        RetailSale existing = require(saleMapper.selectById(id), "产品不存在");
        requireStore(storeId, existing.getStoreId(), "该商品");
        if (existing.getIsActivated() == 1) return existing;
        if (existing.getStatus() != 1) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前产品状态不可激活");
        LocalDateTime now = LocalDateTime.now();
        int changed = saleMapper.update(null, Wrappers.<RetailSale>lambdaUpdate()
                .eq(RetailSale::getId, id).eq(RetailSale::getIsActivated, 0)
                .set(RetailSale::getIsActivated, 1).set(RetailSale::getActivateTime, now).set(RetailSale::getShelfTime, now));
        RetailSale activated = require(saleMapper.selectById(id), "产品不存在");
        if (changed > 0) publish("RETAIL_SALE", id, activated.getProductQrCode(), HashUtil.sha256Json(activated));
        return activated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetailSale sell(SalesRequests.SaleCreate request, Long storeId) {
        RetailSale row = saleMapper.selectOne(Wrappers.<RetailSale>lambdaQuery().eq(RetailSale::getProductQrCode, request.qrCode()));
        require(row, "二维码对应产品不存在");
        requireStore(storeId, row.getStoreId(), "该商品");
        if (row.getStatus() != 1 || row.getIsActivated() != 1) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "产品未激活或已不可售");
        if (row.getExpireDate().isBefore(LocalDate.now())) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "产品已过期");
        int changed = saleMapper.update(null, Wrappers.<RetailSale>lambdaUpdate()
                .eq(RetailSale::getId, row.getId()).eq(RetailSale::getStatus, 1)
                .set(RetailSale::getStatus, 2).set(RetailSale::getSellTime, LocalDateTime.now())
                .set(RetailSale::getSellPrice, request.sellPrice()).set(RetailSale::getSellWeightKg, request.sellWeightKg()));
        if (changed == 0) throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "产品已被销售");
        RetailSale sold = saleMapper.selectById(row.getId());
        publish("RETAIL_SALE", sold.getId(), sold.getProductQrCode(), HashUtil.sha256Json(sold));
        return sold;
    }

    @Override
    public PageResult<SaleRecordVO> pageSales(Long storeId, String status, String productName, String batchNo,
                                              String storeName, String startDate, String endDate, long pageNum, long pageSize) {
        Integer statusCode = parseStatus(status);
        LocalDateTime start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate).atTime(23, 59, 59) : null;
        var wrapper = Wrappers.<RetailSale>lambdaQuery()
                .eq(storeId != null, RetailSale::getStoreId, storeId)
                .eq(statusCode != null, RetailSale::getStatus, statusCode)
                .like(StringUtils.hasText(storeName), RetailSale::getStoreName, storeName)
                .ge(start != null, RetailSale::getSellTime, start)
                .le(end != null, RetailSale::getSellTime, end)
                .orderByDesc(RetailSale::getCreateTime);
        Map<Long, Map<String, Object>> splitCache = new HashMap<>();
        if (StringUtils.hasText(productName) || StringUtils.hasText(batchNo)) {
            List<RetailSale> matched = saleMapper.selectList(wrapper).stream()
                    .filter(row -> matchesSplit(row, productName, batchNo, splitCache)).toList();
            long total = matched.size();
            int from = (int) Math.min((pageNum - 1) * pageSize, total);
            int to = (int) Math.min(from + pageSize, total);
            List<SaleRecordVO> records = matched.subList(from, to).stream().map(row -> toSaleRecordVO(row, splitCache)).toList();
            return PageResult.of(pageNum, pageSize, total, records);
        }
        Page<RetailSale> page = saleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<SaleRecordVO> records = page.getRecords().stream().map(row -> toSaleRecordVO(row, splitCache)).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    private Integer parseStatus(String status) {
        if (!StringUtils.hasText(status)) return null;
        return switch (status.trim()) {
            case "在售" -> 1;
            case "已售" -> 2;
            case "已过期" -> 3;
            case "已召回" -> 4;
            default -> status.trim().matches("\\d+") ? Integer.valueOf(status.trim()) : null;
        };
    }

    private boolean matchesSplit(RetailSale row, String productName, String batchNo, Map<Long, Map<String, Object>> cache) {
        Map<String, Object> split = splitInfo(row.getSplitBatchId(), cache);
        if (split == null) return true;
        if (StringUtils.hasText(batchNo) && !String.valueOf(split.get("batchNo")).contains(batchNo)) return false;
        return !StringUtils.hasText(productName) || String.valueOf(split.get("productName")).contains(productName);
    }

    private SaleRecordVO toSaleRecordVO(RetailSale row, Map<Long, Map<String, Object>> cache) {
        Map<String, Object> split = splitInfo(row.getSplitBatchId(), cache);
        String productName = split == null ? null : Objects.toString(split.get("productName"), null);
        String batchNo = split == null ? null : Objects.toString(split.get("batchNo"), null);
        return new SaleRecordVO(row.getId(), row.getSplitBatchId(), row.getProductQrCode(), row.getStoreId(), row.getStoreName(),
                row.getShelfTime(), row.getSellTime(), row.getSellPrice(), row.getSellWeightKg(),
                row.getIsActivated(), row.getActivateTime(), row.getStatus(), row.getExpireDate(), row.getCreateTime(),
                productName, batchNo, row.getBlockHash(), row.getSource(), row.getSourceRef());
    }

    private Map<String, Object> splitInfo(Long splitBatchId, Map<Long, Map<String, Object>> cache) {
        if (splitBatchId == null) return null;
        if (cache.containsKey(splitBatchId)) return cache.get(splitBatchId);
        Map<String, Object> split;
        try {
            split = distributionClient.requireSplit(splitBatchId);
        } catch (BusinessException e) {
            split = null;
        }
        cache.put(splitBatchId, split);
        return split;
    }

    private Map<String, Object> transportInfo(Long transportId, Map<Long, Map<String, Object>> cache) {
        if (transportId == null) return null;
        if (cache.containsKey(transportId)) return cache.get(transportId);
        Map<String, Object> transport;
        try {
            transport = distributionClient.requireTransport(transportId);
        } catch (BusinessException e) {
            transport = null;
        }
        cache.put(transportId, transport);
        return transport;
    }

    @Override
    public PageResult<ExpireWarningVO> pageWarnings(Integer warningLevel, Integer handled, Long storeId,
                                                    long pageNum, long pageSize) {
        var wrapper = Wrappers.<ExpireWarning>lambdaQuery()
                .eq(warningLevel != null, ExpireWarning::getWarningLevel, warningLevel)
                .eq(handled != null, ExpireWarning::getHandled, handled)
                // 预警表本身没有门店列（它只记「哪个商品快过期了」），门店归属在 retail_sale 上。
                // 用子查询把门店条件下推到 SQL，分页总数才是对的；先查全部再在内存里筛，
                // 会出「第 1 页只有 2 条、总数却写着 13」这种对不上的账。
                .inSql(storeId != null, ExpireWarning::getSaleId,
                        storeId == null ? null : "SELECT id FROM retail_sale WHERE store_id = " + storeId)
                .orderByDesc(ExpireWarning::getWarningTime);
        Page<ExpireWarning> page = warningMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Long> saleIds = page.getRecords().stream().map(ExpireWarning::getSaleId).filter(Objects::nonNull).distinct().toList();
        Map<Long, RetailSale> saleMap = saleIds.isEmpty() ? Map.of() :
                saleMapper.selectBatchIds(saleIds).stream().collect(java.util.stream.Collectors.toMap(RetailSale::getId, s -> s));
        Map<Long, Map<String, Object>> splitCache = new HashMap<>();
        List<ExpireWarningVO> records = page.getRecords().stream().map(w -> {
            RetailSale sale = saleMap.get(w.getSaleId());
            String productQrCode = sale == null ? null : sale.getProductQrCode();
            String storeName = sale == null ? null : sale.getStoreName();
            LocalDate expireDate = sale == null ? null : sale.getExpireDate();
            String productName = null;
            if (sale != null && sale.getSplitBatchId() != null) {
                Map<String, Object> split = splitInfo(sale.getSplitBatchId(), splitCache);
                if (split != null) productName = Objects.toString(split.get("productName"), null);
            }
            return new ExpireWarningVO(w.getId(), w.getSaleId(), w.getWarningLevel(), w.getWarningTime(),
                    w.getNotifyChannel(), w.getNotified(), w.getHandled(), w.getHandleTime(), w.getHandler(),
                    w.getCreateTime(), productQrCode, productName, storeName, expireDate);
        }).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWarning(Long id, SalesRequests.WarningHandle request, Long storeId) {
        ExpireWarning warning = require(warningMapper.selectById(id), "预警不存在");
        if (storeId != null) {
            RetailSale sale = warning.getSaleId() == null ? null : saleMapper.selectById(warning.getSaleId());
            requireStore(storeId, sale == null ? null : sale.getStoreId(), "该预警");
        }
        warning.setHandled(request.handled() ? 1 : 0);
        warning.setHandler(request.handler());
        warning.setHandleTime(request.handled() ? LocalDateTime.now() : null);
        warningMapper.updateById(warning);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecallOrder createRecall(SalesRequests.RecallCreate request) {
        RecallOrder row = new RecallOrder();
        row.setRecallNo(BusinessNoGenerator.next("RC"));
        row.setReason(request.reason());
        row.setRiskLevel(request.riskLevel());
        row.setScope(request.scope());
        row.setInitiator(request.initiator());
        row.setInitiateTime(LocalDateTime.now());
        row.setStatus(1);
        row.setRecalledCount(0);
        row.setCreateTime(LocalDateTime.now());
        List<Long> batchIds = longList(request.scope().get("batchIds"));
        List<Long> storeIds = longList(request.scope().get("storeIds"));
        // 受影响范围包含在售(1)与已售出(2)的销售记录——已售出的商品才是召回的主要对象
        var query = Wrappers.<RetailSale>lambdaQuery().in(!batchIds.isEmpty(), RetailSale::getSplitBatchId, batchIds)
                .in(!storeIds.isEmpty(), RetailSale::getStoreId, storeIds).in(RetailSale::getStatus, 1, 2);
        List<RetailSale> affected = batchIds.isEmpty() && storeIds.isEmpty() ? List.of() : saleMapper.selectList(query);
        row.setAffectedCount(affected.size());
        recallMapper.insert(row);
        if (!affected.isEmpty()) {
            saleMapper.update(null, Wrappers.<RetailSale>lambdaUpdate().in(RetailSale::getId, affected.stream().map(RetailSale::getId).toList())
                    .set(RetailSale::getStatus, 4));
        }
        publish("RECALL_ORDER", row.getId(), row.getRecallNo(), HashUtil.sha256Json(request));
        // 召回一旦发起，货就得有人去收。两件配套动作，都发生在召回单落库之后：
        //   ① 写进配送的「待收运池」—— 处理运输页面的处理计划里当场出现这些货，冷链中心按门店排车；
        //   ② 通知涉事门店（备好货、等上门）与冷链中心（安排车）。
        // 两步都是尽力而为：召回本身已上链（既成事实），配套失败只留痕，绝不回滚召回单 ——
        // 否则「因为通知发不出去，所以召回没发起」这种事会真的发生。
        registerRecallCollect(row, affected);
        notifyRecall(row, affected);
        return row;
    }

    /**
     * 把召回的货写进配送的待收运池（召回 × 门店 × 批次 一行）。
     * <p>
     * 按「门店 + 批次」聚合：司机到店扫的是批次标签，一件一行会让现场扫码对不上清单。
     * 货重按销售记录汇总，未知则留空 —— 不拿 0 冒充「没重量」。
     */
    private void registerRecallCollect(RecallOrder recall, List<RetailSale> affected) {
        if (affected == null || affected.isEmpty()) return;
        Map<String, Map<String, Object>> items = new LinkedHashMap<>();
        for (RetailSale sale : affected) {
            if (sale.getStoreId() == null || sale.getSplitBatchId() == null) continue;
            String key = sale.getStoreId() + "#" + sale.getSplitBatchId();
            Map<String, Object> item = items.computeIfAbsent(key, k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("storeId", sale.getStoreId());
                row.put("storeName", sale.getStoreName());
                row.put("splitBatchId", sale.getSplitBatchId());
                row.put("weightKg", java.math.BigDecimal.ZERO);
                row.put("saleCount", 0);
                row.put("weightKnown", false);
                return row;
            });
            item.put("saleCount", (Integer) item.get("saleCount") + 1);
            if (sale.getSellWeightKg() != null) {
                item.put("weightKg", ((java.math.BigDecimal) item.get("weightKg")).add(sale.getSellWeightKg()));
                item.put("weightKnown", true);
            }
        }
        if (items.isEmpty()) return;
        List<Map<String, Object>> payloadItems = new ArrayList<>(items.values());
        // weightKnown 只是本方法内部的聚合标记，不发出去（配送侧只认货重本身）
        payloadItems.forEach(m -> m.remove("weightKnown"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recallId", recall.getId());
        body.put("recallNo", recall.getRecallNo());
        body.put("reason", recall.getReason());
        body.put("items", payloadItems);
        try {
            Map<String, Object> result = distributionClient.registerRecallCollect(body);
            log.info("recall_pool_registered recallNo={} added={} skipped={}",
                    recall.getRecallNo(), result.get("added"), result.get("skipped"));
        } catch (RuntimeException e) {
            // 池子没登记上，收运就得人工补录 —— 这条日志是唯一线索，必须带够上下文
            log.error("recall_pool_register_failed recallNo={} items={} err={}",
                    recall.getRecallNo(), payloadItems.size(), e.toString());
        }
    }

    /**
     * 通知涉事门店与冷链中心。
     * <p>
     * 两类收件人、两种落点（bizType 决定点开去哪）：门店看「召回通知」准备货，
     * 冷链中心看「召回待收运」去排车。标题里带召回编号 —— 通知中心不按中文猜业务，
     * 但人看列表时要第一眼认出是哪一单。
     */
    private void notifyRecall(RecallOrder recall, List<RetailSale> affected) {
        if (affected == null || affected.isEmpty()) return;
        List<Long> storeIds = affected.stream().map(RetailSale::getStoreId)
                .filter(Objects::nonNull).distinct().toList();
        Set<Long> batchIds = affected.stream().map(RetailSale::getSplitBatchId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String reason = StringUtils.hasText(recall.getReason()) ? recall.getReason() : "未填写原因";
        // ① 门店：谁家的货谁备着
        List<Long> storeUsers = authClient.recipients("RETAILER", storeIds).stream()
                .map(m -> m.get("userId")).filter(Objects::nonNull)
                .map(v -> Long.valueOf(String.valueOf(v))).distinct().toList();
        if (!storeUsers.isEmpty()) {
            int written = notificationClient.notifyUsers(storeUsers,
                    "召回通知：" + recall.getRecallNo(),
                    "召回单 " + recall.getRecallNo() + "（" + reason + "）涉及本店 " + batchIds.size()
                            + " 个批次、共 " + affected.size() + " 件销售记录。请把这些货留在店内待回收，"
                            + "冷链中心会派车上门收取；司机到店扫码收货后交接完成。",
                    "recall_store", recall.getId());
            log.info("recall_notify_store recallNo={} users={} written={}",
                    recall.getRecallNo(), storeUsers.size(), written);
        }
        // ② 冷链中心：这批货要有人去收
        List<Long> centerUsers = authClient.recipients("DISTRIBUTOR", List.of()).stream()
                .map(m -> m.get("userId")).filter(Objects::nonNull)
                .map(v -> Long.valueOf(String.valueOf(v))).distinct().toList();
        if (!centerUsers.isEmpty()) {
            int written = notificationClient.notifyUsers(centerUsers,
                    "召回待收运：" + recall.getRecallNo(),
                    "召回单 " + recall.getRecallNo() + "（" + reason + "）涉及 " + storeIds.size()
                            + " 家门店、" + batchIds.size() + " 个批次，货已进入处理运输的「处理计划」，"
                            + "请安排收运车辆上门收取，收齐后送无害化处理中心。",
                    "recall_center", recall.getId());
            log.info("recall_notify_center recallNo={} users={} written={}",
                    recall.getRecallNo(), centerUsers.size(), written);
        }
    }

    @Override
    public PageResult<RecallOrder> pageRecalls(Integer status, long pageNum, long pageSize) {
        return PageResult.of(recallMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<RecallOrder>lambdaQuery()
                .eq(status != null, RecallOrder::getStatus, status).orderByDesc(RecallOrder::getInitiateTime)));
    }

    @Override
    public RecallOrder getRecall(Long id) { return require(recallMapper.selectById(id), "召回指令不存在"); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRecall(Long id, SalesRequests.RecallStatus request) {
        RecallOrder row = getRecall(id);
        if (row.getStatus() == 3 || row.getStatus() == 4) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "召回已结束，不能再次修改");
        if (request.status() < row.getStatus() && request.status() != 4) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "召回状态不能回退");
        row.setStatus(request.status());
        if (request.recalledCount() != null) {
            if (request.recalledCount() < 0 || request.recalledCount() > row.getAffectedCount())
                throw new BusinessException(ErrorCode.PARAM_ERROR, "召回数量超出影响范围");
            row.setRecalledCount(request.recalledCount());
            // 已召回数量达到受影响数量（且有受影响商品）时，自动完成召回
            if (row.getAffectedCount() != null && row.getAffectedCount() > 0
                    && row.getAffectedCount().equals(row.getRecalledCount())) {
                row.setStatus(3);
            }
        }
        if (row.getStatus() == 3 && row.getCompletedTime() == null) row.setCompletedTime(LocalDateTime.now());
        recallMapper.updateById(row);
        // 进度更新属于关键状态变更，单独上链存证（独立 bizKey，不影响原召回指令的验真）
        publish("RECALL_ORDER_PROGRESS", row.getId(), row.getRecallNo() + ":PROGRESS",
                HashUtil.sha256Json(Map.of("recallNo", row.getRecallNo(), "status", row.getStatus(),
                        "recalledCount", row.getRecalledCount() == null ? 0 : row.getRecalledCount(),
                        "time", LocalDateTime.now().toString())));
    }

    @Override
    public Map<String, Object> previewRecallScope(List<Long> batchIds) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (batchIds == null || batchIds.isEmpty()) {
            view.put("affectedCount", 0);
            view.put("stores", List.of());
            return view;
        }
        // 口径与 createRecall 完全一致：在售(1)+已售(2) 才是「还在市场上的货」。
        // 两处若各写一份，弹窗会带出 A 门店，召回落库后 affected 却多出 B 门店。
        List<RetailSale> affected = saleMapper.selectList(Wrappers.<RetailSale>lambdaQuery()
                .in(RetailSale::getSplitBatchId, batchIds).in(RetailSale::getStatus, 1, 2));
        Map<Long, Map<String, Object>> byStore = new LinkedHashMap<>();
        for (RetailSale sale : affected) {
            if (sale.getStoreId() == null) continue;
            Map<String, Object> item = byStore.computeIfAbsent(sale.getStoreId(), k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("storeId", sale.getStoreId());
                row.put("storeName", sale.getStoreName());
                row.put("saleCount", 0);
                return row;
            });
            item.put("saleCount", (Integer) item.get("saleCount") + 1);
            if (StringUtils.hasText(sale.getStoreName())) item.put("storeName", sale.getStoreName());
        }
        view.put("affectedCount", affected.size());
        view.put("stores", new ArrayList<>(byStore.values()));
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeRecall(Long id, String transportNo) {
        RecallOrder row = getRecall(id);
        // 幂等：签收可能重试 / 回执重放，收口只发生一次
        if (row.getStatus() != null && row.getStatus() == 3) return;
        if (row.getStatus() != null && row.getStatus() == 4) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "召回单已撤销，不能标记完成");
        }
        int affected = row.getAffectedCount() == null ? 0 : row.getAffectedCount();
        row.setStatus(3);
        // 车把货送到无害化并签收，就是「受影响的全收回了」这个事实本身：
        // 数量由受影响件数落定，不是让人估一个数填进来。
        row.setRecalledCount(affected);
        if (row.getCompletedTime() == null) row.setCompletedTime(LocalDateTime.now());
        recallMapper.updateById(row);
        publish("RECALL_COMPLETED", row.getId(), row.getRecallNo() + ":COMPLETED",
                HashUtil.sha256Json(Map.of("recallNo", row.getRecallNo(), "status", 3,
                        "recalledCount", affected,
                        "transportNo", transportNo == null ? "" : transportNo,
                        "time", LocalDateTime.now().toString())));
        notifyRecallCompleted(row, transportNo);
    }

    /**
     * 召回收口后通知管理员。
     * <p>
     * 只发给 ADMIN：召回是监管动作，门店和冷链中心在流程里已经各自收到过通知，
     * 再给他们推一条「已完成」只会变成噪音。通知失败不影响收口（事务已提交、已上链）。
     */
    private void notifyRecallCompleted(RecallOrder recall, String transportNo) {
        List<Long> admins = authClient.recipients("ADMIN", List.of()).stream()
                .map(m -> m.get("userId")).filter(Objects::nonNull)
                .map(v -> Long.valueOf(String.valueOf(v))).distinct().toList();
        if (admins.isEmpty()) {
            log.warn("recall_complete_notify_no_admin recallNo={}", recall.getRecallNo());
            return;
        }
        String reason = StringUtils.hasText(recall.getReason()) ? recall.getReason() : "未填写原因";
        int written = notificationClient.notifyUsers(admins,
                "召回已完成：" + recall.getRecallNo(),
                "召回单 " + recall.getRecallNo() + "（" + reason + "）受影响 "
                        + (recall.getAffectedCount() == null ? 0 : recall.getAffectedCount())
                        + " 件，运输单 " + (StringUtils.hasText(transportNo) ? transportNo : "（未知）")
                        + " 已将收回的货全部送达无害化处理中心并完成签收，执行状态已改为「已完成」。",
                "recall_done", recall.getId());
        log.info("recall_complete_notified recallNo={} admins={} written={}",
                recall.getRecallNo(), admins.size(), written);
    }

    @Override
    @Scheduled(cron = "${sales.expire-scan-cron:0 0 2 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void scanExpiringProducts() {
        LocalDate today = LocalDate.now();
        List<RetailSale> products = saleMapper.selectList(Wrappers.<RetailSale>lambdaQuery()
                .eq(RetailSale::getStatus, 1).le(RetailSale::getExpireDate, today.plusDays(3)));
        for (RetailSale product : products) {
            long days = ChronoUnit.DAYS.between(today, product.getExpireDate());
            int level = days <= 0 ? 3 : days == 1 ? 2 : 1;
            Long exists = warningMapper.selectCount(Wrappers.<ExpireWarning>lambdaQuery()
                    .eq(ExpireWarning::getSaleId, product.getId()).eq(ExpireWarning::getWarningLevel, level));
            if (exists == 0) {
                ExpireWarning warning = new ExpireWarning();
                warning.setSaleId(product.getId());
                warning.setWarningLevel(level);
                warning.setWarningTime(LocalDateTime.now());
                warning.setNotifyChannel("WEB_NOTICE");
                warning.setNotified(0);
                warning.setHandled(0);
                warning.setCreateTime(LocalDateTime.now());
                warningMapper.insert(warning);
            }
            if (level == 3) saleMapper.update(null, Wrappers.<RetailSale>lambdaUpdate()
                    .eq(RetailSale::getId, product.getId()).eq(RetailSale::getStatus, 1).set(RetailSale::getStatus, 3));
        }
    }

    /**
     * 零售经营统计（工作台用）。
     * <p>
     * 只认「已售出」这一个口径：{@code status=2} 且未作废。货架期、二维码生成都不算销量，
     * 否则工作台上的数字会比实际卖出去的虚高，店长看了会误判。
     */
    @Override
    public Map<String, Object> statsSummary(Integer days, Long storeId) {
        int span = days == null || days <= 0 ? 30 : Math.min(days, 90);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(span - 1L);

        // 上限 5000 条：工作台只做概览，不承担全量报表。
        List<RetailSale> rows = saleMapper.selectList(Wrappers.<RetailSale>lambdaQuery()
                .eq(RetailSale::getStatus, 2)
                .isNotNull(RetailSale::getSellTime)
                .ge(RetailSale::getSellTime, from.atStartOfDay())
                .eq(storeId != null, RetailSale::getStoreId, storeId)
                .orderByDesc(RetailSale::getSellTime)
                .last("LIMIT 5000"));

        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal todayAmount = BigDecimal.ZERO;
        long soldCount = 0;
        long todayCount = 0;
        // 每天的 [销售额, 销量kg, 件数]
        Map<LocalDate, BigDecimal[]> buckets = new HashMap<>();
        // 部位 -> [销量kg, 件数]
        Map<String, BigDecimal[]> parts = new HashMap<>();
        Map<Long, Map<String, Object>> splitCache = new HashMap<>();
        int partLookups = 0;

        for (RetailSale row : rows) {
            LocalDate day = row.getSellTime().toLocalDate();
            BigDecimal price = row.getSellPrice() == null ? BigDecimal.ZERO : row.getSellPrice();
            BigDecimal kg = row.getSellWeightKg() == null ? BigDecimal.ZERO : row.getSellWeightKg();

            amount = amount.add(price);
            weight = weight.add(kg);
            soldCount++;
            if (day.equals(today)) {
                todayCount++;
                todayAmount = todayAmount.add(price);
            }

            BigDecimal[] bucket = buckets.computeIfAbsent(day,
                    key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            bucket[0] = bucket[0].add(price);
            bucket[1] = bucket[1].add(kg);
            bucket[2] = bucket[2].add(BigDecimal.ONE);

            // 部位名来自分割批次；远程调用有缓存，另设 500 次上限防止数据量大时拖垮工作台。
            if (partLookups < 500 && row.getSplitBatchId() != null) {
                Map<String, Object> split = splitInfo(row.getSplitBatchId(), splitCache);
                String name = split == null ? null : Objects.toString(split.get("productName"), null);
                if (StringUtils.hasText(name)) {
                    partLookups++;
                    BigDecimal[] part = parts.computeIfAbsent(name,
                            key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    part[0] = part[0].add(kg);
                    part[1] = part[1].add(BigDecimal.ONE);
                }
            }
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter label = DateTimeFormatter.ofPattern("M/d");
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            BigDecimal[] bucket = buckets.get(day);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", label.format(day));
            point.put("amount", bucket == null ? BigDecimal.ZERO : scale(bucket[0]));
            point.put("weight", bucket == null ? BigDecimal.ZERO : scale(bucket[1]));
            point.put("count", bucket == null ? 0L : bucket[2].longValue());
            trend.add(point);
        }

        List<Map<String, Object>> topParts = parts.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]))
                .limit(5)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.getKey());
                    item.put("weight", scale(entry.getValue()[0]));
                    item.put("count", entry.getValue()[1].longValue());
                    return item;
                }).toList();

        long onSale = saleMapper.selectCount(Wrappers.<RetailSale>lambdaQuery()
                .eq(storeId != null, RetailSale::getStoreId, storeId)
                .eq(RetailSale::getStatus, 1));
        long expiring = warningMapper.selectCount(Wrappers.<ExpireWarning>lambdaQuery()
                .eq(ExpireWarning::getHandled, 0)
                // 与列表同一口径：零售商的「临期」必须只数自己门店的，否则卡片点进去对不上
                .inSql(storeId != null, ExpireWarning::getSaleId,
                        storeId == null ? null : "SELECT id FROM retail_sale WHERE store_id = " + storeId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", span);
        result.put("from", from.toString());
        result.put("to", today.toString());
        result.put("soldCount", soldCount);
        result.put("amount", scale(amount));
        result.put("weight", scale(weight));
        result.put("todayCount", todayCount);
        result.put("todayAmount", scale(todayAmount));
        result.put("onSale", onSale);
        result.put("expiring", expiring);
        result.put("trend", trend);
        result.put("topParts", topParts);
        return result;
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().filter(Objects::nonNull).map(v -> v instanceof Number n ? n.longValue() : Long.valueOf(v.toString())).toList();
    }

    private <T> T require(T value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, message);
        return value;
    }

    /**
     * 门店边界校验：{@code scopedStoreId} 非空＝本次请求被限定在这家门店。
     * <p>
     * 列表靠 SQL 条件收敛就够，但「拿一个别家的记录ID 直接调接口」是另一条路——
     * 激活、售出、建码、处理预警这些写操作必须在记录级再校一次，
     * 否则前端看不见的东西，改个请求体就能改。
     */
    private void requireStore(Long scopedStoreId, Long actualStoreId, String subject) {
        if (scopedStoreId == null) return;
        if (!Objects.equals(scopedStoreId, actualStoreId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "【数据隔离】" + subject + "不属于当前登录门店，无权查看或操作");
        }
    }

    private void publish(String type, Long id, String key, String hash) {
        chainEvents.publish(ChainTxMessage.create(type, id, key, hash, Map.of("bizKey", key)));
    }
}
