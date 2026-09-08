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
import com.pork.sales.vo.QrCodeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SalesServiceImpl implements SalesService {
    private final RetailSaleMapper saleMapper;
    private final ExpireWarningMapper warningMapper;
    private final RecallOrderMapper recallMapper;
    private final ChainEventPublisher chainEvents;
    private final DistributionClient distributionClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> generateQrs(SalesRequests.QrBatch request) {
        distributionClient.requireSplit(request.splitBatchId());
        List<String> codes = new ArrayList<>(request.count());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < request.count(); i++) {
            String code = "QR-PORK-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
            RetailSale row = new RetailSale();
            row.setSplitBatchId(request.splitBatchId());
            row.setProductQrCode(code);
            row.setStoreId(request.storeId() == null ? 0L : request.storeId());
            row.setStoreName(request.storeName());
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
    public PageResult<QrCodeVO> pageQrs(Integer status, String qrCode, long pageNum, long pageSize) {
        Page<RetailSale> page = saleMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<RetailSale>lambdaQuery()
                .eq(status != null, RetailSale::getStatus, status)
                .like(StringUtils.hasText(qrCode), RetailSale::getProductQrCode, qrCode)
                .orderByDesc(RetailSale::getCreateTime));
        List<QrCodeVO> records = page.getRecords().stream().map(row -> new QrCodeVO(row.getId(), row.getProductQrCode(),
                row.getSplitBatchId(), row.getIsActivated() == 0 ? 0 : row.getStatus(), row.getExpireDate(), row.getCreateTime())).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetailSale activate(Long id) {
        RetailSale existing = require(saleMapper.selectById(id), "产品不存在");
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
    public RetailSale sell(SalesRequests.SaleCreate request) {
        RetailSale row = saleMapper.selectOne(Wrappers.<RetailSale>lambdaQuery().eq(RetailSale::getProductQrCode, request.qrCode()));
        require(row, "二维码对应产品不存在");
        if (row.getStatus() != 1 || row.getIsActivated() != 1) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "产品未激活或已不可售");
        if (row.getExpireDate().isBefore(LocalDate.now())) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "产品已过期");
        int changed = saleMapper.update(null, Wrappers.<RetailSale>lambdaUpdate()
                .eq(RetailSale::getId, row.getId()).eq(RetailSale::getStatus, 1)
                .set(RetailSale::getStatus, 2).set(RetailSale::getSellTime, LocalDateTime.now())
                .set(RetailSale::getSellPrice, request.sellPrice()).set(RetailSale::getSellWeightKg, request.sellWeightKg()));
        if (changed == 0) throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "产品已被销售");
        return saleMapper.selectById(row.getId());
    }

    @Override
    public PageResult<RetailSale> pageSales(Long storeId, Integer status, long pageNum, long pageSize) {
        Page<RetailSale> page = saleMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<RetailSale>lambdaQuery()
                .eq(storeId != null, RetailSale::getStoreId, storeId).eq(status != null, RetailSale::getStatus, status)
                .orderByDesc(RetailSale::getCreateTime));
        return PageResult.of(page);
    }

    @Override
    public PageResult<ExpireWarning> pageWarnings(Integer warningLevel, Integer handled, long pageNum, long pageSize) {
        Page<ExpireWarning> page = warningMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<ExpireWarning>lambdaQuery()
                .eq(warningLevel != null, ExpireWarning::getWarningLevel, warningLevel)
                .eq(handled != null, ExpireWarning::getHandled, handled).orderByDesc(ExpireWarning::getWarningTime));
        return PageResult.of(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWarning(Long id, SalesRequests.WarningHandle request) {
        ExpireWarning warning = require(warningMapper.selectById(id), "预警不存在");
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
        var query = Wrappers.<RetailSale>lambdaQuery().in(!batchIds.isEmpty(), RetailSale::getSplitBatchId, batchIds)
                .in(!storeIds.isEmpty(), RetailSale::getStoreId, storeIds).eq(RetailSale::getStatus, 1);
        List<RetailSale> affected = batchIds.isEmpty() && storeIds.isEmpty() ? List.of() : saleMapper.selectList(query);
        row.setAffectedCount(affected.size());
        recallMapper.insert(row);
        if (!affected.isEmpty()) {
            saleMapper.update(null, Wrappers.<RetailSale>lambdaUpdate().in(RetailSale::getId, affected.stream().map(RetailSale::getId).toList())
                    .set(RetailSale::getStatus, 4));
        }
        publish("RECALL_ORDER", row.getId(), row.getRecallNo(), HashUtil.sha256Json(request));
        return row;
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
        }
        if (request.status() == 3) row.setCompletedTime(LocalDateTime.now());
        recallMapper.updateById(row);
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

    private List<Long> longList(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().filter(Objects::nonNull).map(v -> v instanceof Number n ? n.longValue() : Long.valueOf(v.toString())).toList();
    }

    private <T> T require(T value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, message);
        return value;
    }

    private void publish(String type, Long id, String key, String hash) {
        chainEvents.publish(ChainTxMessage.create(type, id, key, hash, Map.of("bizKey", key)));
    }
}
