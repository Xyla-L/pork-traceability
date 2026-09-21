package com.pork.sales.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.sales.entity.RetailSale;
import com.pork.sales.mapper.RetailSaleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 门店收银 POS 通道写入：扫码即「激活 + 售出」。
 * <p>
 * 幂等是这条通道的命门。收银台手抖重复扫码、POS 断网补传都会把同一条码推多次，
 * 所以这里用「状态条件更新」而不是先查后写：
 * {@code UPDATE ... WHERE status=1} 影响行数为 0 就说明已经被别的请求卖掉了，
 * 直接返回既有记录而不是报错——对收银台来说"已经卖掉了"就是成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesDeviceIngestService {

    private static final String SOURCE_DEVICE = "DEVICE";
    /** 产品状态：1在库可售 2已售出 */
    private static final int STATUS_ON_SHELF = 1;
    private static final int STATUS_SOLD = 2;

    private final RetailSaleMapper saleMapper;
    private final ChainEventPublisher chainEvents;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestRetailSale(String qrCode, BigDecimal sellPrice, BigDecimal sellWeightKg,
                                                String sourceRef, String rawPayload) {
        if (!StringUtils.hasText(qrCode)) {
            return rejected("缺少产品二维码，无法定位产品");
        }
        RetailSale row = saleMapper.selectOne(Wrappers.<RetailSale>lambdaQuery()
                .eq(RetailSale::getProductQrCode, qrCode).last("LIMIT 1"));
        if (row == null) {
            return manual("二维码在销售库中不存在（" + qrCode + "），可能是非本店产品或未激活的码，请人工核对");
        }
        if (row.getStatus() != null && row.getStatus() == STATUS_SOLD) {
            // 重复扫码：不报错，返回既有销售记录，收银台按成功处理
            log.info("ingest_sale_duplicate qrCode={} id={} sellTime={}", qrCode, row.getId(), row.getSellTime());
            return written(row.getId(), "该产品已售出，返回既有销售记录（重复扫码不重复入账）");
        }
        if (row.getExpireDate() != null && row.getExpireDate().isBefore(LocalDate.now())) {
            return manual("产品已过期（" + row.getExpireDate() + "），请人工确认是否允许售出");
        }

        // 扫码即激活：POS 场景下收银员不会先去后台点激活
        if (row.getIsActivated() == null || row.getIsActivated() == 0) {
            LocalDateTime now = LocalDateTime.now();
            saleMapper.update(null, Wrappers.<RetailSale>lambdaUpdate()
                    .eq(RetailSale::getId, row.getId()).eq(RetailSale::getIsActivated, 0)
                    .set(RetailSale::getIsActivated, 1).set(RetailSale::getActivateTime, now)
                    .set(RetailSale::getShelfTime, now));
        }

        RetailSale fresh = saleMapper.selectById(row.getId());
        if (fresh == null || fresh.getStatus() == null || fresh.getStatus() != STATUS_ON_SHELF) {
            return manual("产品当前状态不可售（" + (fresh == null ? "记录已变化" : fresh.getStatus()) + "），请人工核对");
        }

        int changed = saleMapper.update(null, Wrappers.<RetailSale>lambdaUpdate()
                .eq(RetailSale::getId, fresh.getId()).eq(RetailSale::getStatus, STATUS_ON_SHELF)
                .set(RetailSale::getStatus, STATUS_SOLD)
                .set(RetailSale::getSellTime, LocalDateTime.now())
                .set(sellPrice != null, RetailSale::getSellPrice, sellPrice)
                .set(sellWeightKg != null, RetailSale::getSellWeightKg, sellWeightKg)
                .set(RetailSale::getSource, SOURCE_DEVICE)
                .set(StringUtils.hasText(sourceRef), RetailSale::getSourceRef, sourceRef)
                .set(RetailSale::getRawPayload, rawPayload));
        if (changed == 0) {
            // 并发扫码：另一个请求先卖掉了，同样按成功返回
            RetailSale sold = saleMapper.selectById(fresh.getId());
            return written(fresh.getId(), "该产品已被并发售出，返回既有销售记录");
        }

        RetailSale sold = saleMapper.selectById(fresh.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qrCode", qrCode);
        payload.put("source", SOURCE_DEVICE);
        if (StringUtils.hasText(sourceRef)) payload.put("sourceRef", sourceRef);
        chainEvents.publish(ChainTxMessage.create("RETAIL_SALE", sold.getId(), sold.getProductQrCode(),
                HashUtil.sha256Json(sold), payload));
        return written(sold.getId(), "已激活并售出，销售记录已上链");
    }

    private Map<String, Object> written(Long id, String reason) {
        return result(true, id, reason, false);
    }

    private Map<String, Object> manual(String reason) {
        return result(false, null, reason, true);
    }

    private Map<String, Object> rejected(String reason) {
        return result(false, null, reason, false);
    }

    private Map<String, Object> result(boolean written, Long id, String reason, boolean retryable) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("written", written);
        body.put("id", id);
        body.put("reason", reason);
        body.put("retryable", retryable);
        return body;
    }
}
