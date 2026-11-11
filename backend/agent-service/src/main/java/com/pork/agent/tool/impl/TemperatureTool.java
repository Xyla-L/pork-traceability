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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 冷链温度监控：越界、断档、温度区间与采样来源。
 * <p>
 * 口径完全跟 {@code temperature_log} 走，<b>不自己判合规</b>：越界与否是写入时就打好的
 * {@code is_abnormal}，断档是 {@code TemperatureRecorder} 按额定间隔判出来的 {@code is_gap}。
 * 本工具只做汇总，不重算阈值——重算等于造第二份口径。
 * <p>
 * 为什么单独成工具而不是塞进运单分析：温度问题原本只能回「统计里没有温度数据」，
 * 而温度恰恰是冷链最要紧的一项，且<b>断档比超标更危险</b>——15:00 报一条、17:30 才报下一条，
 * 中间两个半小时空白，界面却显示「正常」。空白必须能说出口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemperatureTool implements AgentTool {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final List<String> HINTS = List.of(
            "温度", "超标", "越界", "断档", "探头", "温控", "冷藏温度", "冷库温度", "几度", "零下");

    /** 运单号 / 车牌：给了就精确查那一张，不给就体检在途运单 */
    private static final Pattern TRANSPORT_NO = Pattern.compile("TR[-A-Z0-9]{6,}");
    private static final Pattern PLATE = Pattern.compile("[\\u4e00-\\u9fa5][A-Z][·\\s]?[A-Z0-9]{4,6}");

    private final ReadOnlyRemote remote;

    @Value("${services.distribution-url:http://localhost:8084}")
    private String distributionUrl;

    @Override
    public String name() {
        return "temperature";
    }

    @Override
    public String label() {
        return "冷链温度监控";
    }

    @Override
    public String description() {
        return "运单温度记录：有没有越界、有没有断档、温度区间与采样来源；问「温度有没有超标」「中途有没有断档」时用";
    }

    /**
     * 排在运单分析（15）之前：温度词出现时由本工具答，
     * 否则会被运单统计认领后回一句「本统计不含温度」——那是答非所问。
     */
    @Override
    public int priority() {
        return 13;
    }

    /** 温度记录跟着运单/门店走：养殖与屠宰环节没有冷链运单，问这个没有落点 */
    @Override
    public List<String> roles() {
        return List.of("ADMIN", "SUPERVISOR", "DISTRIBUTOR", "RETAILER");
    }

    @Override
    public String hint() {
        return "冷链温度：越界几条、中途有没有断档、采样来自设备还是模拟探头";
    }

    @Override
    public boolean supports(String question) {
        if (question == null || question.isBlank()) return false;
        return HINTS.stream().anyMatch(question::contains);
    }

    @Override
    public Map<String, String> arguments(String question) {
        if (question == null) return Map.of();
        Matcher no = TRANSPORT_NO.matcher(question.toUpperCase());
        if (no.find()) return Map.of("keyword", no.group());
        Matcher plate = PLATE.matcher(question);
        if (plate.find()) return Map.of("keyword", plate.group().replace(" ", ""));
        return Map.of();
    }

    @Override
    public ToolResult execute(Map<String, String> args, AgentContext ctx) {
        JsonNode data = remote.getData(distributionUrl + "/distribution/transports?pageNum=1&pageSize=200", ctx);
        JsonNode records = data == null ? null : data.get("records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            return ToolResult.empty(name(), label(), "近期没有冷链运单，没有温度记录可看。");
        }

        String key = args == null ? "" : args.getOrDefault("keyword", "").trim();
        List<JsonNode> targets = new ArrayList<>();
        if (!key.isBlank()) {
            JsonNode hit = match(records, key);
            if (hit == null) {
                return ToolResult.empty(name(), label(),
                        "没有找到运单「" + key + "」。请给运单号（TR…）或车牌，也可以在冷链配送-运输监控里核对。");
            }
            targets.add(hit);
        } else {
            targets.addAll(inTransit(records));
            if (targets.isEmpty()) {
                // 没有在途车时退一步：体检最近完成的几张。问「今天温度有没有超标」的人，
                // 想知道的就是刚跑完这几趟有没有出事，回一句「没有在途运单」等于没答。
                targets.addAll(recentFinished(records));
            }
        }

        if (targets.isEmpty()) {
            return ToolResult.empty(name(), label(),
                    "当前没有在途或已完成的运单，没有温度记录可看。");
        }

        List<String> facts = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> abnormalLines = new ArrayList<>();
        List<String> emptyLines = new ArrayList<>();
        Map<String, String> metrics = new LinkedHashMap<>();

        int totalRecords = 0;
        int abnormalTransports = 0;
        int abnormalPoints = 0;
        int gapPoints = 0;
        Set<String> sources = new LinkedHashSet<>();

        for (JsonNode t : targets) {
            long id = t.path("id").asLong();
            String no = t.path("transportNo").asText("—");
            String vehicle = t.path("vehicleNo").asText("");
            TempStat st = stat(remote.getData(distributionUrl + "/distribution/transports/" + id + "/temperature", ctx));
            totalRecords += st.count();
            sources.addAll(st.sources());

            if (st.count() == 0) {
                emptyLines.add("运单 " + no + (vehicle.isBlank() ? "" : "（" + vehicle + "）") + ": 0 条温度记录");
                continue;
            }
            // 断档-only 的运单也算异常：只数越界会把「17 处空白」的车报成正常，
            // 而空白恰恰是这里最该被看见的（实测就这样少算过一张）
            if (st.abnormal() > 0 || st.gap() > 0) abnormalTransports++;
            abnormalPoints += st.abnormal();
            gapPoints += st.gap();

            if (st.abnormal() > 0 || st.gap() > 0) {
                StringBuilder line = new StringBuilder("运单 " + no + (vehicle.isBlank() ? "" : "（" + vehicle + "）"));
                line.append(": 越界 ").append(st.abnormal()).append(" 条、断档 ").append(st.gap()).append(" 处");
                if (!st.abnormalTimes().isEmpty()) {
                    line.append("；越界时刻 ").append(String.join("、", st.abnormalTimes()));
                }
                if (!st.gapTimes().isEmpty()) {
                    line.append("；断档后首条 ").append(String.join("、", st.gapTimes()));
                }
                abnormalLines.add(line.toString());
            }

            if (targets.size() == 1) {
                // 单张运单：给到能背下来的粒度
                facts.add("运单: " + no + (vehicle.isBlank() ? "" : "（" + vehicle + "）")
                        + "，状态=" + statusText(t.path("status").asInt(0)));
                facts.add("温度记录: " + st.count() + " 条，覆盖 "
                        + (st.from() == null ? "—" : st.from().format(HM)) + " ~ "
                        + (st.to() == null ? "—" : st.to().format(HM)));
                facts.add("温度区间: " + st.minText() + " ~ " + st.maxText() + "℃"
                        + (st.rangeText().isBlank() ? "（未取到额定区间）" : "，额定 " + st.rangeText()));
                facts.add("越界条数: " + st.abnormal()
                        + (st.abnormalTimes().isEmpty() ? "" : "（" + String.join("、", st.abnormalTimes()) + "）"));
                facts.add("断档处数: " + st.gap()
                        + (st.gapTimes().isEmpty() ? "" : "（断档后首条出现在 " + String.join("、", st.gapTimes()) + "）"));
                facts.add("采样节奏: 额定 " + (st.interval() <= 0 ? "未记录" : st.interval() + " 秒")
                        + "，最长实际间隔 " + (st.maxGap() <= 0 ? "—" : st.maxGap() + " 秒"));
                notes.add("运单 " + no + " 共 " + st.count() + " 条温度记录，" + st.minText() + " ~ " + st.maxText()
                        + "℃；越界 " + st.abnormal() + " 条、断档 " + st.gap() + " 处。");
            }
        }

        if (targets.size() > 1) {
            facts.add("体检范围: 在途/待签收 " + targets.size() + " 张运单");
            facts.add("有温度异常的运单: " + abnormalTransports + " 张（越界记录共 " + abnormalPoints
                    + " 条、断档共 " + gapPoints + " 处）");
            if (!abnormalLines.isEmpty()) {
                facts.add("异常明细（按运单）"
                        + (abnormalLines.size() > 5 ? "，仅列前 5 张，共 " + abnormalLines.size() + " 张" : "") + ":");
                abnormalLines.stream().limit(5).forEach(l -> facts.add("  " + l));
            }
            if (!emptyLines.isEmpty()) {
                facts.add("没有温度记录的运单"
                        + (emptyLines.size() > 5 ? "（仅列前 5 张，共 " + emptyLines.size() + " 张）" : "") + ":");
                emptyLines.stream().limit(5).forEach(l -> facts.add("  " + l));
            }
            facts.add("温度记录合计: " + totalRecords + " 条");
            notes.add(targets.size() + " 张在途运单里，有温度异常的 " + abnormalTransports + " 张；越界 "
                    + abnormalPoints + " 条、断档 " + gapPoints + " 处。");
        }

        if (!emptyLines.isEmpty() && targets.size() == 1) {
            facts.add("说明: 该运单没有任何温度记录。空白不等于正常——可能是探头未接入或未上报，应到运输监控核对。");
        }
        String sourceText = sourceText(sources);
        if (!sourceText.isBlank()) facts.add("记录来源: " + sourceText);
        facts.add("说明: 温度只覆盖冷链运输段（发车到到站），养殖与屠宰环节不在其中。是否合规由系统判定，本工具只报事实。");

        metrics.put("abnormalPoints", String.valueOf(abnormalPoints));
        metrics.put("gapPoints", String.valueOf(gapPoints));
        metrics.put("sampledTransports", String.valueOf(targets.size()));

        String note = notes.isEmpty() ? "已读取温度记录。" : String.join(" ", notes);
        return new ToolResult(name(), label(), note, facts, metrics);
    }

    /* ---------------- 取数与汇总 ---------------- */

    private JsonNode match(JsonNode records, String key) {
        String plate = key.replace("·", "").replace(" ", "").toUpperCase();
        for (JsonNode row : records) {
            String no = row.path("transportNo").asText("");
            String vehicle = row.path("vehicleNo").asText("").replace("·", "").replace(" ", "").toUpperCase();
            if (no.equalsIgnoreCase(key) || vehicle.equals(plate)
                    || String.valueOf(row.path("id").asLong()).equals(key)) {
                return row;
            }
        }
        return null;
    }

    /** 未指定运单时：在途/待签收的，最多看 8 张（再多多打十几次请求，收益还不如先处理在途的） */
    private List<JsonNode> inTransit(JsonNode records) {
        List<JsonNode> list = new ArrayList<>();
        for (JsonNode row : records) {
            int status = row.path("status").asInt(0);
            if (status == 2 || status == 3) list.add(row);
            if (list.size() >= 8) break;
        }
        return list;
    }

    /** 全签收时补位：最近发车的 5 张，按发车时间倒序（没有发车时间的排后面） */
    private List<JsonNode> recentFinished(JsonNode records) {
        List<JsonNode> finished = new ArrayList<>();
        for (JsonNode row : records) {
            if (row.path("status").asInt(0) == 4) finished.add(row);
        }
        finished.sort((a, b) -> {
            LocalDateTime ta = AnalyticsSupport.parseTime(a.path("departTime").asText(null));
            LocalDateTime tb = AnalyticsSupport.parseTime(b.path("departTime").asText(null));
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return finished.stream().limit(5).toList();
    }

    private TempStat stat(JsonNode logs) {
        if (logs == null || !logs.isArray() || logs.isEmpty()) return TempStat.EMPTY;
        int count = 0, abnormal = 0, gap = 0, interval = 0, maxGap = 0;
        BigDecimal min = null, max = null;
        LocalDateTime from = null, to = null;
        String rangeMin = null, rangeMax = null;
        Set<String> sources = new LinkedHashSet<>();
        List<String> abnormalTimes = new ArrayList<>();
        List<String> gapTimes = new ArrayList<>();

        for (JsonNode log : logs) {
            count++;
            // 缺读数就不进区间统计：当成 0℃ 会把「额定 -18~0」的区间算歪，比不报还糟
            JsonNode tv = log.path("temperature");
            if (tv.isNumber()) {
                BigDecimal temp = tv.decimalValue();
                if (min == null || temp.compareTo(min) < 0) min = temp;
                if (max == null || temp.compareTo(max) > 0) max = temp;
            }
            LocalDateTime time = AnalyticsSupport.parseTime(log.path("recordTime").asText(null));
            if (time != null) {
                if (from == null || time.isBefore(from)) from = time;
                if (to == null || time.isAfter(to)) to = time;
            }
            if (log.path("isAbnormal").asInt(0) == 1) {
                abnormal++;
                if (time != null && abnormalTimes.size() < 5) abnormalTimes.add(time.format(HM));
            }
            if (log.path("isGap").asInt(0) == 1) {
                gap++;
                if (time != null && gapTimes.size() < 5) gapTimes.add(time.format(HM));
            }
            int iv = log.path("sampleIntervalSeconds").asInt(0);
            if (iv > 0 && interval == 0) interval = iv;
            int g = log.path("gapSeconds").asInt(0);
            if (g > maxGap) maxGap = g;
            String src = log.path("source").asText("");
            if (!src.isBlank()) sources.add(src);
            if (rangeMin == null) rangeMin = log.path("tempRangeMin").asText("");
            if (rangeMax == null) rangeMax = log.path("tempRangeMax").asText("");
        }
        String range = (rangeMin == null || rangeMin.isBlank() || rangeMax == null || rangeMax.isBlank())
                ? "" : rangeMin + " ~ " + rangeMax + "℃";
        return new TempStat(count, min, max, abnormal, gap, from, to, interval, maxGap,
                range, sources, abnormalTimes, gapTimes);
    }

    private String sourceText(Set<String> sources) {
        if (sources.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (String s : sources) {
            parts.add(switch (s) {
                case "SIMULATED" -> "模拟探头（系统定时采集的演示数据，不是真实车载设备读数）";
                case "DEVICE" -> "车载/冷库设备自动采集";
                case "API" -> "第三方推送";
                case "MANUAL" -> "人工记录";
                case "IMPORT" -> "批量导入";
                default -> s;
            });
        }
        return String.join("、", parts);
    }

    private String statusText(int status) {
        return switch (status) {
            case 1 -> "待发车";
            case 2 -> "运输中";
            case 3 -> "待签收";
            case 4 -> "已签收";
            default -> "未知";
        };
    }

    /** 一张运单的温度体检结果；来源集合与时刻列表只用于组装事实，不参与判定 */
    private record TempStat(int count, BigDecimal min, BigDecimal max, int abnormal, int gap,
                            LocalDateTime from, LocalDateTime to, int interval, int maxGap,
                            String range, Set<String> sources,
                            List<String> abnormalTimes, List<String> gapTimes) {

        static final TempStat EMPTY = new TempStat(0, null, null, 0, 0, null, null, 0, 0,
                "", Set.of(), List.of(), List.of());

        String minText() {
            return min == null ? "—" : plain(min);
        }

        String maxText() {
            return max == null ? "—" : plain(max);
        }

        String rangeText() {
            return range;
        }

        private static String plain(BigDecimal v) {
            return v.stripTrailingZeros().toPlainString();
        }
    }
}
