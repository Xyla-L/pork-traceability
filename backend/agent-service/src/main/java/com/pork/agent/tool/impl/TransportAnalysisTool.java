package com.pork.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pork.agent.support.AgentContext;
import com.pork.agent.support.AnalyticsSupport;
import com.pork.agent.support.ReadOnlyRemote;
import com.pork.agent.tool.AgentTool;
import com.pork.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 冷链运单分析：状态分布、实际运输时长、拒收站点、车辆运单排名。
 * <p>
 * 口径跟 distribution 的 {@code /transports} 列表走；状态文案（1 待发车 / 2 运输中 /
 * 3 待签收 / 4 已签收）与运输监控页一致。只统计，不判断这趟车「该不该到」——
 * 到达由车载终端推进、签收由门店确认，本工具不替任何一方说话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransportAnalysisTool implements AgentTool {

    private static final Map<Integer, String> STATUS = Map.of(1, "待发车", 2, "运输中", 3, "待签收", 4, "已签收");
    private static final List<String> HINTS = List.of(
            "运单", "运输", "冷链", "签收", "在途", "发车", "配送", "拒收率", "车辆",
            "车号", "车牌", "车到哪", "这车", "司机");

    /**
     * 生猪指代：出现说明问的是<b>猪</b>而不是车（「这车上的猪被拒收了吗」问的还是这头猪），
     * 让位给全链路追溯。刻意不收单字「猪」——「猪肉冷链运单」问的确实是运单。
     */
    private static final List<String> PIG_HINTS = List.of(
            "耳标", "的猪", "这头猪", "这批猪", "生猪", "胴体", "白条", "猪只");

    private final ReadOnlyRemote remote;

    @Value("${services.distribution-url:http://localhost:8084}")
    private String distributionUrl;

    @Override
    public String name() {
        return "transport-analysis";
    }

    @Override
    public String label() {
        return "冷链运单分析";
    }

    @Override
    public String description() {
        return "冷链运单状态分布、在途时长、拒收站点、车辆运单排名；问「车到哪了」「有没有被拒收」「跑了几趟」时用。温度不归这里，归 temperature";
    }

    @Override
    public int priority() {
        return 15;
    }

    @Override
    public List<String> roles() {
        return List.of("ADMIN", "SUPERVISOR", "DISTRIBUTOR", "RETAILER");
    }

    @Override
    public String hint() {
        return "冷链运单分析：状态分布、在途时长、拒收站点";
    }

    @Override
    public boolean supports(String question) {
        if (question == null || question.isBlank()) return false;
        // 「这车上的猪」主语是猪，不能被运单分析抢走（否则会拿车号去解释拒收）
        if (PIG_HINTS.stream().anyMatch(question::contains)) return false;
        return HINTS.stream().anyMatch(question::contains);
    }

    @Override
    public Map<String, String> arguments(String question) {
        return Map.of();
    }

    @Override
    public ToolResult execute(Map<String, String> args, AgentContext ctx) {
        // 配送侧运单列表：控制器带 /distribution 前缀（区别于 breeding 的无前缀），分页参数 pageNum/pageSize
        JsonNode data = remote.getData(distributionUrl + "/distribution/transports?pageNum=1&pageSize=200", ctx);
        JsonNode records = data == null ? null : data.get("records");
        long total = data == null ? 0 : data.path("total").asLong(0);
        if (records == null || !records.isArray() || records.isEmpty()) {
            return ToolResult.empty(name(), label(), "近期没有冷链运单，无从分析。");
        }

        Map<Integer, Long> statusCounts = new LinkedHashMap<>();
        for (int s = 1; s <= 4; s++) statusCounts.put(s, 0L);
        List<Duration> durations = new ArrayList<>();
        long rejectedStops = 0;
        long receivedStops = 0;
        Map<String, Long> byVehicle = new LinkedHashMap<>();

        for (JsonNode row : records) {
            int status = row.path("status").asInt(0);
            statusCounts.merge(status, 1L, Long::sum);
            rejectedStops += row.path("rejectedStopCount").asLong(0);
            receivedStops += row.path("receivedStopCount").asLong(0);
            LocalDateTime depart = AnalyticsSupport.parseTime(row.path("departTime").asText(null));
            LocalDateTime arrive = AnalyticsSupport.parseTime(row.path("arriveTime").asText(null));
            if (depart != null && arrive != null) {
                durations.add(Duration.between(depart, arrive));
            }
            String vehicle = row.path("vehicleNo").asText("");
            if (!vehicle.isBlank()) byVehicle.merge(vehicle, 1L, Long::sum);
        }

        long finished = statusCounts.get(4);
        StringBuilder note = new StringBuilder();
        note.append("最近 ").append(records.size()).append(" 张运单（共 ").append(total).append(" 张）：");
        note.append(statusCounts.entrySet().stream()
                .map(e -> STATUS.getOrDefault(e.getKey(), "未知") + " " + e.getValue())
                .reduce((a, b) -> a + "、" + b).orElse("无"));
        if (!durations.isEmpty()) {
            Duration avg = durations.stream().reduce(Duration.ZERO, Duration::plus).dividedBy(durations.size());
            Duration max = durations.stream().max(Duration::compareTo).orElse(Duration.ZERO);
            note.append("。已完成的平均在途 ").append(AnalyticsSupport.durationText(avg))
                    .append("，最长一单 ").append(AnalyticsSupport.durationText(max)).append("。");
        }
        if (rejectedStops > 0) {
            note.append("被拒收的站点共 ").append(rejectedStops).append(" 个，占已到站点的 ")
                    .append(AnalyticsSupport.percent(rejectedStops, rejectedStops + receivedStops)).append("。");
        } else {
            note.append("暂无拒收站点。");
        }

        List<String> facts = new ArrayList<>();
        facts.add("统计范围: 最近 " + records.size() + " 张 / 全部 " + total + " 张");
        statusCounts.forEach((s, c) -> facts.add("状态[" + STATUS.get(s) + "]: " + c + " 张"));
        facts.add("拒收站点: " + rejectedStops + " 个（已签收站点 " + receivedStops + " 个）");
        if (!durations.isEmpty()) {
            facts.add("平均在途时长: " + AnalyticsSupport.durationText(
                    durations.stream().reduce(Duration.ZERO, Duration::plus).dividedBy(durations.size())));
            facts.add("最长在途: " + AnalyticsSupport.durationText(
                    durations.stream().max(Duration::compareTo).orElse(Duration.ZERO)));
        }
        if (byVehicle.size() > 1) {
            facts.add("承运车辆 " + byVehicle.size() + " 台，按单数前 5：");
            byVehicle.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> facts.add("  车辆 " + e.getKey() + ": " + e.getValue() + " 单"));
        }
        // 温度不在这份统计里。温度已由 TemperatureTool 专管，这里只交代落点，
        // 不让模型拿总量去替温度下结论（「都没有异常」这种话在这份数据里没有依据）。
        facts.add("说明: 本统计不含温度记录。温度越界与断档由「冷链温度监控」回答（给运单号可精确查一张）；"
                + "本统计不要提温度是否正常。");
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("rejectedStops", String.valueOf(rejectedStops));
        metrics.put("inTransit", String.valueOf(statusCounts.get(2)));
        return new ToolResult(name(), label(), note.toString(), facts, metrics);
    }
}
