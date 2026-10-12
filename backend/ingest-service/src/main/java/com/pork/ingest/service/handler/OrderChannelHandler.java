package com.pork.ingest.service.handler;

import com.pork.core.exception.BusinessException;
import com.pork.ingest.client.BizClient;
import com.pork.ingest.service.ChannelHandler;
import com.pork.ingest.service.IngestContext;
import com.pork.ingest.support.DispatchResult;
import com.pork.ingest.support.IngestChannel;
import com.pork.ingest.support.Payloads;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单转入通道（商家选购端 / 外部 ERP、OMS）。
 * <p>
 * <b>与其它 15 条通道有一处本质差别：它们落的都是「已经发生的事实」，这条落的是「决策」。</b>
 * 温度、重量、读数、装了哪几批 —— 那些是现场已经发生、只是没记下来的东西；
 * 而订单转出来的是<b>待排车的发货计划</b>：车还没定、司机还没定、什么时候走也还没定。
 * 所以这条通道<b>不做任何业务判定</b>，也不写运单 —— 排车是人的决定（人工配车，
 * 或到装车口扫批次自动倒查）。把「待排车」当成「已排车」是这条通道最危险的写法。
 * <p>
 * 三条不能破的界线（与 {@code PlanSource} 口径一致）：
 * <ul>
 *   <li><b>订单 ≠ 计划</b>：订单只是计划的<b>来源之一</b>（{@code plan_source=ORDER}），
 *       运单的上游永远是计划。不能因为接了订单就退回「下单即运单」；</li>
 *   <li><b>不复制订单</b>：权威副本在外部系统，本系统只留 {@code order_no}。
 *       原始报文已全量落在 {@code ingest_staging.payload}（不可变），不必也不该再存一份；</li>
 *   <li><b>字段层面就不收金额</b>：不是「事后不接支付」——金额不参与上链、也不影响召回圈范围，
 *       所以报文里根本没有价格的容身之地。本通道只取「要什么货、发去哪家店、什么时候要」。</li>
 * </ul>
 * 只报名称没报编码时，部位由配送服务按 {@code meat_part} 字典反查（与分割上报共用一份口径），
 * 反查不到 / 备不齐货 → 转人工。装车口不猜目的地，这里同样不猜货物。
 */
@Slf4j
@Component
public class OrderChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;

    public OrderChannelHandler(BizClient bizClient,
                               @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.ORDER;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "orderNo") == null) {
            errors.add("缺少订单号(orderNo)：没有订单号就无法幂等，外部系统重推会重复建计划");
        }
        if (Payloads.integer(data, "storeId") == null) {
            errors.add("缺少目的地门店(storeId)：订单必须说清发去哪家店");
        }
        List<Map<String, Object>> items = readItems(data);
        if (items.isEmpty()) {
            errors.add("缺少货物明细(items)：订单至少要有「部位 + 重量」一项");
            return errors;
        }
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            if (Payloads.str(item, "partCode") == null && Payloads.str(item, "partName") == null) {
                errors.add("第 " + (i + 1) + " 条货物明细缺少部位(partCode / partName)");
            }
            java.math.BigDecimal weight = Payloads.decimal(item, "weightKg");
            if (weight == null) {
                errors.add("第 " + (i + 1) + " 条货物明细缺少重量(weightKg)");
            } else if (weight.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                // 负重量在配货时会「立刻凑够」，最后生成一张 0 明细的空计划，属于报文本身不成立
                errors.add("第 " + (i + 1) + " 条货物明细的重量必须大于 0");
            }
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.ORDER.targetTable();
        Map<String, Object> data = ctx.data();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNo", Payloads.str(data, "orderNo"));
        body.put("storeId", Payloads.integer(data, "storeId"));
        // 门店名只是参考：目的地名称以机构树为准（外部系统的叫法可能与机构树不一致）
        body.put("storeName", Payloads.str(data, "storeName"));
        LocalDateTime planDate = Payloads.time(data, "planDate");
        if (planDate != null) {
            body.put("planDate", planDate.toLocalDate().toString());
        }
        body.put("expectArrive", Handlers.time(Payloads.time(data, "expectArrive")));
        body.put("items", itemsBody(readItems(data)));
        body.put("remark", Payloads.str(data, "remark"));
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/order", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            // 走到这里说明报文结构是完整的（validate 已经挡过一轮），失败出在业务条件或下游可用性上：
            // 机构服务查不到门店、备不齐货、配送服务不可达……一律转人工。
            // <b>绝不能判成「拒绝」</b>：拒绝的语义是"数据本身不成立、不要重推"，
            // 外部系统据此会把订单删掉，那这张单就永久丢了。订单重推是常态，宁可多收一条待人工，
            // 也不能让一张真实订单在接入层消失。
            return DispatchResult.manual(table, "订单转入未完成：" + e.getMessage());
        }
    }

    /** 取货物明细数组；设备/外部系统可能给数组，别的一律当空处理 */
    private List<Map<String, Object>> readItems(Map<String, Object> data) {
        Object raw = data == null ? null : data.get("items");
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (element instanceof Map<?, ?> map) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    map.forEach((key, value) -> item.put(String.valueOf(key), value));
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** 明细只透传「部位 + 重量 + 包数」三样，金额类字段即使报文里带了也不会往下走 */
    private List<Map<String, Object>> itemsBody(List<Map<String, Object>> items) {
        List<Map<String, Object>> result = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("partCode", Payloads.str(item, "partCode"));
            one.put("partName", Payloads.str(item, "partName"));
            one.put("weightKg", Payloads.decimal(item, "weightKg"));
            result.add(one);
        }
        return result;
    }
}
