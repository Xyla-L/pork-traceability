package com.pork.trace.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.trace.client.TraceRemoteClient;
import com.pork.trace.entity.ComplaintReport;
import com.pork.trace.mapper.ComplaintReportMapper;
import com.pork.trace.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * 工作台聚合。
 * <p>
 * <b>一个角色一份视图</b>：养殖户看存栏与出栏、屠宰岗看入场与盖章、配送看运单、零售看销量、
 * 监管看异常、管理员看全局。角色由网关从 JWT 解出后透传到本服务，前端不再自行筛选 ——
 * 「接口把全平台数据都发下去、页面只是不显示」属于假隔离。
 * <p>
 * 每个角色返回同一套结构（{@code statCards} / {@code blocks} / {@code todoList}），
 * 前端用一套渲染器渲染，新增角色只需在这里加一个视图方法。
 * <p>
 * <b>下游不可用不等于 0</b>：某个服务取数失败时，指标保持 0 但会在 {@code degraded} 里列出该服务，
 * 由页面明确提示「某项数据暂不可用」，而不是让用户把「取不到」看成「没有」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private final TraceRemoteClient clients;
    private final ComplaintReportMapper complaintMapper;

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 未识别角色（含未带角色头的直连调用）按监管视角处理：只给合规相关，不含经营明细。 */
    @Override
    public Map<String, Object> overview(String role, String org) {
        String code = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "ADMIN" -> adminView();
            case "FARMER" -> farmerView();
            case "SLAUGHTER_OP" -> slaughterView();
            case "DISTRIBUTOR" -> distributorView();
            case "RETAILER" -> retailerView(parseId(org));
            default -> supervisorView();
        };
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("dashboard_bad_org value={}", value);
            return null;
        }
    }

    // ==================================================================
    // 管理员：平台总览
    // ==================================================================
    private Map<String, Object> adminView() {
        Map<String, Object> view = shell("ADMIN", "系统管理员", "全平台");
        List<String> degraded = degraded(view);
        LocalDate today = LocalDate.now();

        long pigCount = total(map(safe(degraded, "养殖服务", () -> clients.breedingPigs(1))));
        long slaughterCount = total(map(safe(degraded, "屠宰服务", () -> clients.slaughterStamps(today.toString()))));
        long transportCount = total(map(safe(degraded, "配送服务", () -> clients.transports(2))));
        Map<String, Object> batchPage = map(safe(degraded, "配送服务", () -> clients.batches()));
        Map<String, Object> salePage = map(safe(degraded, "销售服务", () -> clients.salesQrs()));
        Map<String, Object> warningPage = map(safe(degraded, "销售服务", () -> clients.warnings()));
        Map<String, Object> chainPage = map(safe(degraded, "存证服务", () -> clients.chainRecords()));

        List<Map<String, Object>> warningRows = records(warningPage);
        List<Map<String, Object>> chainRows = records(chainPage);
        long complaintCount = countPendingComplaints();
        long chainCount = chainRows.stream()
                .filter(row -> inCurrentMonth(row.get("chainTime"), row.get("createTime")))
                .count();

        cards(view, List.of(
                card("pigCount", "在养生猪数", pigCount, "头"),
                card("slaughterCount", "今日屠宰量", slaughterCount, "头"),
                card("transportCount", "在途批次", transportCount, "单"),
                card("expireCount", "临期产品数", total(warningPage), "件"),
                card("complaintCount", "待处理举报", complaintCount, "条"),
                card("chainCount", "本月上链数", chainCount, "笔")));

        blocks(view, List.of(
                sankeyBlock(pigCount, total(batchPage), total(salePage)),
                pieBlock("expirePie", "临期/过期产品分布", warningDistribution(warningRows, true), 1),
                barBlock("chainBar", "区块链每日上链量", chainTrend(chainRows), 1, "笔"),
                pieBlock("warningPie", "预警级别分布", warningDistribution(warningRows, false), 1),
                warningTable(warningRows, 1)));

        view.put("todoList", complaintTodos());
        return view;
    }

    // ==================================================================
    // 监管人员：合规与异常
    // ==================================================================
    private Map<String, Object> supervisorView() {
        Map<String, Object> view = shell("SUPERVISOR", "监管人员", "全平台 · 合规视角");
        List<String> degraded = degraded(view);
        LocalDate today = LocalDate.now();

        long pigCount = total(map(safe(degraded, "养殖服务", () -> clients.breedingPigs(1))));
        long slaughterCount = total(map(safe(degraded, "屠宰服务", () -> clients.slaughterStamps(today.toString()))));
        long transportCount = total(map(safe(degraded, "配送服务", () -> clients.transports(2))));
        Map<String, Object> batchPage = map(safe(degraded, "配送服务", () -> clients.batches()));
        Map<String, Object> salePage = map(safe(degraded, "销售服务", () -> clients.salesQrs()));
        Map<String, Object> warningPage = map(safe(degraded, "销售服务", () -> clients.warnings()));
        Map<String, Object> chainPage = map(safe(degraded, "存证服务", () -> clients.chainRecords()));

        List<Map<String, Object>> warningRows = records(warningPage);
        List<Map<String, Object>> chainRows = records(chainPage);
        long complaintCount = countPendingComplaints();
        long expired = warningRows.stream().filter(row -> number(row.get("warningLevel")) == 3).count();
        long chainCount = chainRows.stream()
                .filter(row -> inCurrentMonth(row.get("chainTime"), row.get("createTime")))
                .count();

        cards(view, List.of(
                card("complaintCount", "待处理举报", complaintCount, "条"),
                card("expireCount", "临期产品数", total(warningPage), "件"),
                card("expiredCount", "已过期未处置", expired, "件"),
                card("transportCount", "在途批次", transportCount, "单"),
                card("slaughterCount", "今日屠宰量", slaughterCount, "头"),
                card("chainCount", "本月上链数", chainCount, "笔")));

        blocks(view, List.of(
                sankeyBlock(pigCount, total(batchPage), total(salePage)),
                pieBlock("warningPie", "预警级别分布", warningDistribution(warningRows, false), 1),
                barBlock("chainBar", "区块链每日上链量", chainTrend(chainRows), 1, "笔"),
                warningTable(warningRows, 1)));

        view.put("todoList", complaintTodos());
        return view;
    }

    // ==================================================================
    // 养殖户：存栏与出栏
    // ==================================================================
    private Map<String, Object> farmerView() {
        Map<String, Object> view = shell("FARMER", "养殖户", "本场养殖环节");
        List<String> degraded = degraded(view);

        long raising = total(map(safe(degraded, "养殖服务", () -> clients.pigPage(1, 1))));
        long exited = total(map(safe(degraded, "养殖服务", () -> clients.pigPage(2, 1))));
        long slaughtered = total(map(safe(degraded, "养殖服务", () -> clients.pigPage(3, 1))));
        long abnormal = total(map(safe(degraded, "养殖服务", () -> clients.pigPage(4, 1))));
        Map<String, Object> latestPage = map(safe(degraded, "养殖服务", () -> clients.pigPage(null, 6)));
        Map<String, Object> applyCount = map(safe(degraded, "养殖服务", () -> clients.applyCounts()));
        Map<String, Object> pendingPage = map(safe(degraded, "养殖服务", () -> clients.applyPage(0, 6)));
        Map<String, Object> rejectedPage = map(safe(degraded, "养殖服务", () -> clients.applyPage(2, 6)));

        long pending = number(applyCount.get("0"));
        long approved = number(applyCount.get("1"));
        long rejected = number(applyCount.get("2"));

        cards(view, List.of(
                card("raisingCount", "在养生猪", raising, "头"),
                card("pendingApply", "待审批出栏", pending, "单"),
                card("approvedApply", "已通过待出栏", approved, "单"),
                card("exitedCount", "已出栏", exited, "头")));

        List<Map<String, Object>> statusData = List.of(
                node("在养", raising), node("已出栏", exited),
                node("已屠宰", slaughtered), node("异常", abnormal));

        blocks(view, List.of(
                pieBlock("pigStatusPie", "生猪状态分布", statusData, 1),
                barBlock("rejectBar", "出栏申报状态", List.of(
                        node("待审批", pending), node("已通过", approved), node("已驳回", rejected)), 1, "单"),
                applyTable(pendingPage, "待审批出栏申报", "/admin/farm/apply", 1),
                pigTable(latestPage, "最近建档生猪", "/admin/farm/pigs", 1)));

        // 养殖户的待办：被驳回的申报要改，而不是「待审批」（那是监管该做的）
        view.put("todoList", applyTodos(records(rejectedPage), "出栏申报被驳回", "待处理"));
        return view;
    }

    // ==================================================================
    // 屠宰场操作员：入场、检验、盖章
    // ==================================================================
    private Map<String, Object> slaughterView() {
        Map<String, Object> view = shell("SLAUGHTER_OP", "屠宰场操作员", "今日 · 屠宰环节");
        List<String> degraded = degraded(view);
        String today = LocalDate.now().toString();

        Map<String, Object> entryPage = map(safe(degraded, "屠宰服务", () -> clients.slaughterEntries(today, 6)));
        Map<String, Object> inspectPage = map(safe(degraded, "屠宰服务", () -> clients.slaughterInspections(today, 200)));
        Map<String, Object> stampPage = map(safe(degraded, "屠宰服务", () -> clients.slaughterStampPage(today, 6)));
        Map<String, Object> batchPage = map(safe(degraded, "配送服务", () -> clients.batches()));

        List<Map<String, Object>> inspectRows = records(inspectPage);
        long passed = inspectRows.stream().filter(row -> number(row.get("result")) == 1).count();
        long failed = inspectRows.stream().filter(row -> number(row.get("result")) == 0).count();
        long undecided = inspectRows.size() - passed - failed;

        cards(view, List.of(
                card("entryToday", "今日入场", total(entryPage), "头"),
                card("inspectToday", "今日检验", total(inspectPage), "头"),
                card("stampToday", "今日盖章", total(stampPage), "头"),
                card("batchTotal", "胴体批次总数", total(batchPage), "批")));

        blocks(view, List.of(
                pieBlock("inspectResultPie", "今日检验结论", List.of(
                        node("合格", passed), node("不合格", failed), node("未出结论", undecided)), 1),
                barBlock("entryBar", "今日入场批次分布", entryBatchData(records(entryPage)), 1, "头"),
                entryTable(entryPage, "今日入场查验", "/admin/slaughter/entry", 1),
                stampTable(stampPage, "今日盖章记录", "/admin/slaughter/stamp", 1)));

        view.put("todoList", stampTodos(records(stampPage)));
        return view;
    }

    // ==================================================================
    // 配送：运单流转
    // ==================================================================
    private Map<String, Object> distributorView() {
        Map<String, Object> view = shell("DISTRIBUTOR", "分销商", "全平台运单");
        List<String> degraded = degraded(view);

        Map<String, Object> waiting = map(safe(degraded, "配送服务", () -> clients.transportPage(1, 6)));
        Map<String, Object> moving = map(safe(degraded, "配送服务", () -> clients.transportPage(2, 6)));
        Map<String, Object> arriving = map(safe(degraded, "配送服务", () -> clients.transportPage(3, 6)));
        Map<String, Object> received = map(safe(degraded, "配送服务", () -> clients.transportPage(4, 1)));

        long waitingCount = total(waiting);
        long movingCount = total(moving);
        long arrivingCount = total(arriving);
        long receivedCount = total(received);

        cards(view, List.of(
                card("waitingTransport", "待发车运单", waitingCount, "单"),
                card("movingTransport", "运输中", movingCount, "单"),
                card("arrivingTransport", "待签收", arrivingCount, "单"),
                card("receivedTransport", "已签收", receivedCount, "单")));

        blocks(view, List.of(
                pieBlock("transportPie", "运单状态分布", List.of(
                        node("待发车", waitingCount), node("运输中", movingCount),
                        node("待签收", arrivingCount), node("已签收", receivedCount)), 1),
                barBlock("transportMixBar", "待办运单构成", List.of(
                        node("待发车", waitingCount), node("运输中", movingCount),
                        node("待签收", arrivingCount)), 1, "单"),
                transportTable("waitingTable", waiting, "待发车运单", "/admin/distribution/transport", 1),
                transportTable("movingTable", moving, "在途运单", "/admin/distribution/transport", 1)));

        view.put("todoList", transportTodos(records(waiting), "待发车运单", "待发车"));
        return view;
    }

    // ==================================================================
    // 零售商：本门店的销量与临期
    // ==================================================================
    /**
     * 零售商视角。
     * <p>
     * <b>口径就是「这一家店」</b>：{@code storeId} 来自令牌里的机构声明，页面上的
     * 「近 30 天销售额」只能等于这家店柜台收的钱。做全平台汇总再让页面标一句「近 30 天」，
     * 等于给店长看别人的营业额 —— 数字越大越误导。
     * <p>
     * 门店识别不出来时<b>宁可什么都不给</b>：卡片归零 + degraded 明确写出「所属门店未识别」。
     * 退回全平台汇总看起来「有数据」，但那是最坏的一种错——把别人的生意当成自己的。
     */
    private Map<String, Object> retailerView(Long storeId) {
        Map<String, Object> view = shell("RETAILER", "零售商",
                storeId == null ? "未识别门店" : storeLabel(storeId) + " · 近 30 天");
        List<String> degraded = degraded(view);

        if (storeId == null) {
            degraded.add("所属门店");
            cards(view, List.of(
                    card("salesAmount", "近 30 天销售额", 0, "元"),
                    card("salesWeight", "近 30 天销量", 0, "kg"),
                    card("soldCount", "近 30 天售出", 0, "件"),
                    card("expiringCount", "临期商品", 0, "件")));
            return view;
        }

        Map<String, Object> stats = map(safe(degraded, "销售服务", () -> clients.salesStats(30, storeId)));
        Map<String, Object> warningPage = map(safe(degraded, "销售服务", () -> clients.warningsForStore(storeId)));
        List<Map<String, Object>> warningRows = records(warningPage);

        cards(view, List.of(
                card("salesAmount", "近 30 天销售额", stats.getOrDefault("amount", 0), "元"),
                card("salesWeight", "近 30 天销量", stats.getOrDefault("weight", 0), "kg"),
                card("soldCount", "近 30 天售出", stats.getOrDefault("soldCount", 0), "件"),
                card("expiringCount", "临期商品", stats.getOrDefault("expiring", 0), "件")));

        blocks(view, List.of(
                barBlock("salesTrendBar", "近 30 天每日销量", salesTrendData(stats), 2, "kg"),
                barBlock("partRankBar", "部位销量排行", partRankData(stats), 1, "kg"),
                pieBlock("expirePie", "临期/过期产品分布", warningDistribution(warningRows, true), 1),
                warningTable(warningRows, 2)));

        List<Map<String, Object>> todos = new ArrayList<>();
        for (Map<String, Object> row : warningRows.stream().limit(6).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", text(row.get("productName")) + "（到期 " + text(row.get("expireDate")) + "）");
            item.put("type", "临期处理");
            item.put("time", text(row.get("warningTime")));
            item.put("status", "待处理");
            todos.add(item);
        }
        view.put("todoList", todos);
        return view;
    }

    // ==================================================================
    // 结构组装
    // ==================================================================
    private Map<String, Object> shell(String role, String roleLabel, String scopeLabel) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("role", role);
        view.put("roleLabel", roleLabel);
        view.put("scopeLabel", scopeLabel);
        view.put("statCards", new ArrayList<Map<String, Object>>());
        view.put("blocks", new ArrayList<Map<String, Object>>());
        view.put("todoList", new ArrayList<Map<String, Object>>());
        view.put("degraded", new ArrayList<String>());
        return view;
    }

    @SuppressWarnings("unchecked")
    private List<String> degraded(Map<String, Object> view) {
        return (List<String>) view.get("degraded");
    }

    /**
     * 单点取数失败不拖垮整页：返回 null 并把服务名记进 degraded，页面据此提示「该项暂不可用」。
     * 直接抛出会让一个下游抖动变成整张工作台白屏。
     */
    private Object safe(List<String> degraded, String source, Supplier<Object> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            log.warn("dashboard_source_unavailable source={} err={}", source, e.toString());
            if (!degraded.contains(source)) degraded.add(source);
            return null;
        }
    }

    private void cards(Map<String, Object> view, List<Map<String, Object>> items) {
        listOf(view, "statCards").addAll(items);
    }

    private void blocks(Map<String, Object> view, List<Map<String, Object>> items) {
        listOf(view, "blocks").addAll(items);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Map<String, Object> view, String key) {
        return (List<Map<String, Object>>) view.get(key);
    }

    private Map<String, Object> card(String key, String label, Object value, String unit) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("value", value);
        item.put("unit", unit);
        return item;
    }

    private Map<String, Object> block(String key, String type, String title, int span) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("type", type);
        item.put("title", title);
        item.put("span", span);
        return item;
    }

    private Map<String, Object> sankeyBlock(long pigs, long batchCount, long saleCount) {
        Map<String, Object> item = block("flowSankey", "sankey", "全链路数据流通统计", 2);
        item.put("data", sankey(pigs, batchCount, saleCount));
        return item;
    }

    private Map<String, Object> pieBlock(String key, String title, List<Map<String, Object>> data, int span) {
        Map<String, Object> item = block(key, "pie", title, span);
        item.put("data", data);
        return item;
    }

    private Map<String, Object> barBlock(String key, String title, List<Map<String, Object>> data,
                                         int span, String unit) {
        Map<String, Object> item = block(key, "bar", title, span);
        item.put("unit", unit);
        item.put("data", data);
        return item;
    }

    private Map<String, Object> warningTable(List<Map<String, Object>> rows, int span) {
        List<Map<String, Object>> columns = List.of(
                column("productName", "产品名称", 150),
                column("batchNo", "产品二维码", 140),
                column("warningLevel", "预警级别", 100),
                column("expireDate", "过期日期", 110),
                column("storeName", "门店", 120));
        Map<String, Object> item = tableBlock("warningTable", "临期/过期产品", columns, rows, span);
        item.put("link", linkItem("查看全部预警", "/admin/sales/warnings"));
        return item;
    }

    private Map<String, Object> entryTable(Map<String, Object> page, String title, String path, int span) {
        List<Map<String, Object>> columns = List.of(
                column("earTagNo", "耳标号", 160),
                column("batchNo", "批次号", 130),
                column("sourceFarm", "来源养殖场", 150),
                column("vehicleNo", "车牌", 100),
                column("healthCheckLabel", "健康检查", 100));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : records(page)) {
            rows.add(row(row, "earTagNo", "batchNo", "sourceFarm", "vehicleNo", "healthCheckLabel"));
        }
        Map<String, Object> item = tableBlock("entryTable", title, columns, rows, span);
        item.put("emptyText", "今日暂无入场记录");
        item.put("link", linkItem("查看全部", path));
        return item;
    }

    private Map<String, Object> stampTable(Map<String, Object> page, String title, String path, int span) {
        List<Map<String, Object>> columns = List.of(
                column("carcassNo", "胴体编号", 150),
                column("earTagNo", "耳标号", 160),
                column("stampNo", "印章编号", 150),
                column("veterinary", "盖章兽医", 110),
                column("stampTime", "盖章时间", 140));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : records(page)) {
            Map<String, Object> mapped = row(row, "carcassNo", "earTagNo", "stampNo", "veterinary");
            mapped.put("stampTime", timeText(row.get("stampTime")));
            rows.add(mapped);
        }
        Map<String, Object> item = tableBlock("stampTable", title, columns, rows, span);
        item.put("emptyText", "今日暂无盖章记录");
        item.put("link", linkItem("查看全部", path));
        return item;
    }

    private Map<String, Object> transportTable(String key, Map<String, Object> page, String title,
                                               String path, int span) {
        List<Map<String, Object>> columns = List.of(
                column("transportNo", "运单号", 170),
                column("vehicleNo", "车牌", 110),
                column("driverName", "司机", 100),
                column("destination", "目的地", 150),
                column("statusLabel", "状态", 100));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : records(page)) {
            Map<String, Object> mapped = row(row, "transportNo", "vehicleNo", "driverName", "destination");
            mapped.put("statusLabel", transportStatusLabel(number(row.get("status"))));
            rows.add(mapped);
        }
        Map<String, Object> item = tableBlock(key, title, columns, rows, span);
        item.put("link", linkItem("查看全部", path));
        return item;
    }

    private Map<String, Object> applyTable(Map<String, Object> page, String title, String path, int span) {
        List<Map<String, Object>> columns = List.of(
                column("applyNo", "申报单号", 170),
                column("earTagNo", "耳标号", 160),
                column("weightKg", "出栏重量(kg)", 120),
                column("applyTime", "申报时间", 140),
                column("approvalLabel", "审批状态", 100));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : records(page)) {
            Map<String, Object> mapped = row(row, "applyNo", "earTagNo", "weightKg");
            mapped.put("applyTime", timeText(row.get("applyTime")));
            mapped.put("approvalLabel", applyStatusLabel(number(row.get("approvalStatus"))));
            rows.add(mapped);
        }
        Map<String, Object> item = tableBlock("applyTable", title, columns, rows, span);
        item.put("link", linkItem("查看全部", path));
        return item;
    }

    private Map<String, Object> pigTable(Map<String, Object> page, String title, String path, int span) {
        List<Map<String, Object>> columns = List.of(
                column("earTagNo", "耳标号", 170),
                column("farmName", "养殖场", 160),
                column("breed", "品种", 100),
                column("statusLabel", "状态", 100),
                column("createTime", "建档时间", 140));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : records(page)) {
            Map<String, Object> mapped = row(row, "earTagNo", "farmName", "breed", "statusLabel");
            mapped.put("createTime", timeText(row.get("createTime")));
            rows.add(mapped);
        }
        Map<String, Object> item = tableBlock("pigTable", title, columns, rows, span);
        item.put("link", linkItem("查看全部", path));
        return item;
    }

    private Map<String, Object> tableBlock(String key, String title, List<Map<String, Object>> columns,
                                           List<Map<String, Object>> rows, int span) {
        Map<String, Object> item = block(key, "table", title, span);
        item.put("columns", columns);
        item.put("rows", rows);
        return item;
    }

    private Map<String, Object> column(String prop, String label, int width) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("prop", prop);
        item.put("label", label);
        item.put("width", width);
        return item;
    }

    private Map<String, Object> linkItem(String text, String path) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("text", text);
        item.put("path", path);
        return item;
    }

    private Map<String, Object> row(Map<String, Object> source, String... props) {
        Map<String, Object> item = new LinkedHashMap<>();
        for (String prop : props) item.put(prop, text(source.get(prop)));
        return item;
    }

    // ==================================================================
    // 数据加工
    // ==================================================================
    private Map<String, Object> sankey(long pigs, long batchCount, long saleCount) {
        long slaughter = Math.min(pigs, batchCount == 0 ? pigs : batchCount);
        long distribution = batchCount;
        long sales = saleCount;
        long consumer = sales;
        List<Map<String, Object>> nodes = List.of(node("养殖免疫", pigs), node("屠宰检疫", slaughter),
                node("分割配送", distribution), node("市场销售", sales), node("消费者", consumer));
        List<Map<String, Object>> links = List.of(link("养殖免疫", "屠宰检疫", slaughter),
                link("屠宰检疫", "分割配送", distribution), link("分割配送", "市场销售", sales),
                link("市场销售", "消费者", consumer));
        return Map.of("nodes", nodes, "links", links);
    }

    private Map<String, Object> node(String name, long value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    private Map<String, Object> link(String source, String target, long value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("source", source);
        item.put("target", target);
        item.put("value", value);
        return item;
    }

    private List<Map<String, Object>> warningDistribution(List<Map<String, Object>> rows, boolean includeSafe) {
        long level1 = countLevel(rows, 1);
        long level2 = countLevel(rows, 2);
        long level3 = countLevel(rows, 3);
        List<Map<String, Object>> result = new ArrayList<>();
        if (includeSafe) result.add(node("安全（>7天）", 0));
        result.add(node("临期3天", level1));
        result.add(node("临期1天", level2));
        result.add(node("已过期", level3));
        return result;
    }

    private long countLevel(List<Map<String, Object>> rows, int level) {
        return rows.stream().filter(row -> number(row.get("warningLevel")) == level).count();
    }

    private List<Map<String, Object>> chainTrend(List<Map<String, Object>> rows) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDate date = localDate(row.get("chainTime"));
            if (date == null) date = localDate(row.get("createTime"));
            if (date != null) counts.merge(date, 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            result.add(node(DAY_LABEL.format(day), counts.getOrDefault(day, 0L)));
        }
        return result;
    }

    /** 销售趋势：只取销量（kg），金额波动大，放进卡片看更直观。 */
    private List<Map<String, Object>> salesTrendData(Map<String, Object> stats) {
        Object trend = stats.get("trend");
        if (!(trend instanceof Collection<?> collection)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("name", Objects.toString(raw.get("date"), ""));
            point.put("value", raw.get("weight") == null ? 0 : raw.get("weight"));
            result.add(point);
        }
        return result;
    }

    private List<Map<String, Object>> partRankData(Map<String, Object> stats) {
        Object parts = stats.get("topParts");
        if (!(parts instanceof Collection<?> collection)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("name", Objects.toString(raw.get("name"), "未命名"));
            point.put("value", raw.get("weight") == null ? 0 : raw.get("weight"));
            result.add(point);
        }
        return result;
    }

    /** 入场按批次归并：台账里一眼看不出「今天来了几批」，按批次数更容易发现异常集中。 */
    private List<Map<String, Object>> entryBatchData(List<Map<String, Object>> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String batchNo = text(row.get("batchNo"));
            if ("-".equals(batchNo)) continue;
            counts.merge(batchNo, 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        counts.forEach((batchNo, count) -> result.add(node(batchNo, count)));
        return result.size() > 6 ? result.subList(0, 6) : result;
    }

    private List<Map<String, Object>> complaintTodos() {
        return complaintMapper.selectList(Wrappers.<ComplaintReport>lambdaQuery()
                .eq(ComplaintReport::getStatus, 0)
                .orderByAsc(ComplaintReport::getCreateTime)
                .last("LIMIT 10")).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", "处理举报 #" + row.getReportNo());
            item.put("type", "举报处理");
            item.put("time", row.getCreateTime());
            item.put("status", "待受理");
            return item;
        }).toList();
    }

    private List<Map<String, Object>> applyTodos(List<Map<String, Object>> rows, String type, String status) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", "出栏申报 " + text(row.get("applyNo")) + "（耳标 " + text(row.get("earTagNo")) + "）");
            item.put("type", type);
            item.put("time", timeText(row.get("applyTime")));
            item.put("status", status);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> stampTodos(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows.stream().limit(6).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", "胴体 " + text(row.get("carcassNo")) + " 已盖章（耳标 " + text(row.get("earTagNo")) + "）");
            item.put("type", "可建批");
            item.put("time", timeText(row.get("stampTime")));
            item.put("status", "待归批");
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> transportTodos(List<Map<String, Object>> rows, String type, String status) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", "运单 " + text(row.get("transportNo")) + " · 车牌 " + text(row.get("vehicleNo")));
            item.put("type", type);
            item.put("time", timeText(row.get("createTime")));
            item.put("status", status);
            result.add(item);
        }
        return result;
    }

    /**
     * 门店展示名：向 auth-service 取机构名（{@code sys_org.name}）。
     * <p>
     * 取不到就退回「门店 #ID」而不是猜一个名字 —— 标题写着别家店名，比写着编号更难发现是错的。
     * 这里不抛异常：取不到名字不该让整张工作台降级，数据本身还是能看的。
     */
    private String storeLabel(Long storeId) {
        try {
            Map<String, Object> org = map(clients.org(storeId));
            String name = Objects.toString(org.get("name"), "").trim();
            if (!name.isEmpty()) return name;
        } catch (RuntimeException e) {
            log.warn("dashboard_store_label_unavailable storeId={} err={}", storeId, e.toString());
        }
        return "门店 #" + storeId;
    }

    private long countPendingComplaints() {
        return complaintMapper.selectCount(Wrappers.<ComplaintReport>lambdaQuery()
                .eq(ComplaintReport::getStatus, 0));
    }

    private String transportStatusLabel(long status) {
        return switch ((int) status) {
            case 1 -> "待发车";
            case 2 -> "运输中";
            case 3 -> "待签收";
            case 4 -> "已签收";
            default -> "未知";
        };
    }

    private String applyStatusLabel(long status) {
        return switch ((int) status) {
            case 0 -> "待审批";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "未知";
        };
    }

    private String timeText(Object value) {
        if (value == null) return "-";
        String raw = value.toString();
        int length = Math.min(16, raw.length());
        return length <= 10 ? raw : raw.substring(0, length).replace('T', ' ');
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? "-" : value.toString();
    }

    private boolean inCurrentMonth(Object primary, Object fallback) {
        LocalDate value = localDate(primary);
        if (value == null) value = localDate(fallback);
        return value != null && YearMonth.from(value).equals(YearMonth.now());
    }

    private LocalDate localDate(Object value) {
        if (value == null) return null;
        String text = value.toString();
        try {
            return LocalDate.parse(text.substring(0, Math.min(10, text.length())));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private long total(Map<String, Object> page) {
        return number(page.get("total"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> page) {
        Object value = page.get("records");
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? 0 : Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
