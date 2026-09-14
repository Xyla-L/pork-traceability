package com.pork.trace.service.impl;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.trace.client.TraceRemoteClient;
import com.pork.trace.service.TraceQueryService;
import com.pork.trace.vo.SafeBuyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TraceQueryServiceImpl implements TraceQueryService {
    private final TraceRemoteClient clients;

    @Override
    public Map<String, Object> search(String keyword) {
        if (keyword == null || keyword.isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "检索关键词不能为空");
        return keyword.toUpperCase(Locale.ROOT).startsWith("QR-") ? scan(keyword) : full(keyword);
    }

    @Override
    public Object upstream(String batchNo) {
        Map<String, Object> tree = map(clients.tree(batchNo));
        if ("CARCASS".equals(tree.get("type"))) return List.of(Map.of("type", "CARCASS", "data", nodeData(tree)));
        Map<String, Object> split = map(nodeData(tree));
        return clients.upstream(number(split.get("id")).longValue());
    }

    @Override
    public Object downstream(String batchNo) {
        Map<String, Object> result = new LinkedHashMap<>(map(clients.downstream(batchNo)));
        List<Long> splitIds = longList(result.get("splitIds"));
        result.put("sales", splitIds.isEmpty() ? List.of() : clients.productsBySplits(splitIds));
        return result;
    }

    @Override
    public Map<String, Object> full(String batchNo) {
        Object upstream = upstream(batchNo);
        List<Long> pigIds = pigIds(upstream);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("upstream", upstream);
        result.put("downstream", downstream(batchNo));
        result.put("breeding", pigIds.isEmpty() ? List.of() : clients.breedingByPigs(pigIds));
        result.put("slaughter", pigIds.isEmpty() ? List.of() : clients.slaughterByPigs(pigIds));
        result.put("blockchain", clients.chainStatus(batchNo));
        return result;
    }

    @Override
    public Map<String, Object> verify(String qrCode) {
        Map<String, Object> product = map(clients.productByQr(qrCode));
        Map<String, Object> split = map(clients.split(number(product.get("splitBatchId")).longValue()));
        String batchNo = Objects.toString(split.get("batchNo"), "");
        List<Object> details = new ArrayList<>();
        addRecords(details, clients.chainStatus(qrCode));
        addRecords(details, clients.chainStatus(batchNo));
        List<Map<String, Object>> comparisons = details.stream().map(this::verifyRecord).toList();
        boolean verified = !comparisons.isEmpty() && comparisons.stream()
                .allMatch(row -> Boolean.TRUE.equals(row.get("match")));
        return Map.of("allVerified", verified, "verified", verified, "chainStatus",
                verified ? "区块链已验证" : "区块链验证未通过", "details", comparisons,
                "detail", verified ? "全部存证记录与链上哈希一致" : "存在未确认或缺失的存证记录");
    }

    @Override
    public Map<String, Object> scan(String qrCode) {
        Map<String, Object> product = map(clients.productByQr(qrCode));
        Map<String, Object> split = map(clients.split(number(product.get("splitBatchId")).longValue()));
        String batchNo = Objects.toString(split.get("batchNo"), "");
        Map<String, Object> productView = new LinkedHashMap<>(product);
        productView.put("name", split.get("productName"));
        productView.put("batchNo", batchNo);
        productView.put("packageDate", split.get("splitTime"));
        productView.put("batchWeightKg", split.get("weightKg"));
        productView.put("packageCount", split.get("packageCount"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", productView);
        result.put("traceChain", full(batchNo));
        result.put("verification", verify(qrCode));
        return result;
    }

    @Override
    public SafeBuyVO safeBuy(String qrCode) {
        Map<String, Object> scanResult = scan(qrCode);
        Map<String, Object> product = map(scanResult.get("product"));
        Map<String, Object> verification = map(scanResult.get("verification"));
        Map<String, Object> traceChain = map(scanResult.get("traceChain"));

        List<Object> chainRecords = chainRecords(traceChain.get("blockchain"));
        List<Object> certChain = certChain(traceChain.get("slaughter"));
        List<Object> reports = reports(traceChain.get("slaughter"));

        Map<String, Object> blockchain = new LinkedHashMap<>();
        blockchain.put("verified", Boolean.TRUE.equals(verification.get("verified")));
        blockchain.put("recordCount", chainRecords.size());
        blockchain.put("records", verification.getOrDefault("details", List.of()));

        SafeBuyVO vo = new SafeBuyVO();
        vo.setQrCode(qrCode);
        vo.setProduct(product);
        vo.setCertChain(certChain);
        vo.setReports(reports);
        vo.setChainRecords(chainRecords);
        vo.setRecordCount(chainRecords.size());
        vo.setBlockchain(blockchain);
        return vo;
    }

    private List<Object> chainRecords(Object blockchain) {
        if (!(blockchain instanceof Collection<?> records)) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : records) {
            Map<String, Object> record = map(value);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", formatTime(record.get("chainTime")));
            row.put("desc", bizTypeLabel(Objects.toString(record.get("bizType"), "")) + "信息上链");
            row.put("txHash", record.get("txHash"));
            row.put("blockNumber", record.get("blockNumber"));
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> Objects.toString(row.get("time"), "")));
        return new ArrayList<>(rows);
    }

    private String bizTypeLabel(String bizType) {
        return switch (bizType) {
            case "QUARANTINE_CERT" -> "产地检疫证明";
            case "SLAUGHTER_INSPECT" -> "屠宰检验";
            case "SPLIT_BATCH" -> "批次分割";
            case "STORE_RECEIPT" -> "门店签收";
            case "RETAIL_SALE" -> "销售激活";
            case "RECALL_ORDER" -> "产品召回";
            default -> bizType;
        };
    }

    private List<Object> certChain(Object slaughter) {
        if (!(slaughter instanceof Collection<?> pigs)) return List.of();
        List<Object> certs = new ArrayList<>();
        boolean originCertAdded = false;
        boolean ractopamineAdded = false;
        Set<String> stampTypes = new HashSet<>();
        for (Object pig : pigs) {
            Map<String, Object> row = map(pig);
            if (!originCertAdded) {
                Map<String, Object> entry = first(row.get("entries"));
                if (entry != null && hasText(entry.get("quarantineCert"))) {
                    Object issueOrg = hasText(entry.get("inspector")) ? entry.get("inspector") : entry.get("sourceFarm");
                    certs.add(cert("产地检疫合格证明", entry.get("quarantineCert"), issueOrg, entry.get("arriveTime")));
                    originCertAdded = true;
                }
            }
            if (row.get("stamps") instanceof Collection<?> stamps) {
                for (Object value : stamps) {
                    Map<String, Object> stamp = map(value);
                    String type = hasText(stamp.get("stampType")) ? stamp.get("stampType").toString() : "肉品品质检验合格证";
                    if (!stampTypes.add(type)) continue;
                    certs.add(cert(type, stamp.get("stampNo"), stamp.get("veterinary"), stamp.get("stampTime")));
                }
            }
            if (!ractopamineAdded) {
                Map<String, Object> test = first(row.get("ractopamineTests"));
                if (test != null && hasText(test.get("testNo"))) {
                    certs.add(cert("瘦肉精检测报告", test.get("testNo"), test.get("operator"), test.get("testTime")));
                    ractopamineAdded = true;
                }
            }
        }
        return certs;
    }

    private Map<String, Object> cert(String type, Object certNo, Object issueOrg, Object issueTime) {
        Map<String, Object> cert = new LinkedHashMap<>();
        cert.put("type", type);
        cert.put("certNo", certNo == null ? "" : certNo);
        cert.put("issueOrg", issueOrg == null ? "" : issueOrg);
        cert.put("issueTime", formatTime(issueTime));
        cert.put("photo", "");
        return cert;
    }

    private List<Object> reports(Object slaughter) {
        if (!(slaughter instanceof Collection<?> pigs)) return List.of();
        List<Map<String, Object>> entries = new ArrayList<>();
        List<Map<String, Object>> preInspections = new ArrayList<>();
        List<Map<String, Object>> postInspections = new ArrayList<>();
        List<Map<String, Object>> tests = new ArrayList<>();
        for (Object pig : pigs) {
            Map<String, Object> row = map(pig);
            addMapped(entries, row.get("entries"));
            if (row.get("inspections") instanceof Collection<?> inspections) {
                for (Object value : inspections) {
                    Map<String, Object> inspection = map(value);
                    if (intEquals(inspection.get("inspectType"), 1)) preInspections.add(inspection);
                    else if (intEquals(inspection.get("inspectType"), 2)) postInspections.add(inspection);
                }
            }
            addMapped(tests, row.get("ractopamineTests"));
        }
        List<Object> reports = new ArrayList<>();
        if (!entries.isEmpty()) {
            boolean pass = entries.stream().allMatch(e -> intEquals(e.get("healthCheck"), 1) && intEquals(e.get("certVerified"), 1));
            String certNos = entries.stream().map(e -> Objects.toString(e.get("quarantineCert"), ""))
                    .filter(s -> !s.isBlank()).distinct().collect(Collectors.joining("、"));
            reports.add(report("产地检疫",
                    pass ? "该批次生猪经官方兽医现场检疫，临床检查健康，具备产地检疫合格条件。" : "该批次生猪入场查验存在异常项。",
                    "共查验" + entries.size() + "头生猪；检疫证明编号：" + (certNos.isBlank() ? "无" : certNos), pass));
        }
        if (!preInspections.isEmpty()) {
            boolean pass = preInspections.stream().allMatch(i -> intEquals(i.get("result"), 1));
            reports.add(report("宰前检验",
                    pass ? "宰前静养观察与群体检查，健康状况良好，无异常临床表现。" : "宰前检验存在不合格项。",
                    inspectionDetail(preInspections), pass));
        }
        if (!postInspections.isEmpty()) {
            boolean pass = postInspections.stream().allMatch(i -> intEquals(i.get("result"), 1));
            reports.add(report("宰后检验",
                    pass ? "胴体及内脏同步检疫，淋巴结、脏器检查未见异常。" : "宰后检验存在不合格项。",
                    inspectionDetail(postInspections), pass));
        }
        if (!tests.isEmpty()) {
            boolean pass = tests.stream().allMatch(t -> intEquals(t.get("result"), 1));
            Map<String, Object> first = tests.get(0);
            reports.add(report("瘦肉精检测",
                    pass ? "盐酸克伦特罗、莱克多巴胺等检测均为阴性。" : "瘦肉精检测存在阳性样本。",
                    "检测方法：" + Objects.toString(first.get("testMethod"), "未记录")
                            + "；采样部位：" + Objects.toString(first.get("samplePart"), "未记录")
                            + "；共检测" + tests.size() + "批次样本", pass));
        }
        return reports;
    }

    private String inspectionDetail(List<Map<String, Object>> inspections) {
        Map<String, Object> first = inspections.get(0);
        String conclusion = Objects.toString(first.get("conclusion"), "");
        String veterinary = Objects.toString(first.get("veterinary"), "");
        StringBuilder detail = new StringBuilder("共检验").append(inspections.size()).append("头");
        if (!conclusion.isBlank()) detail.append("；检验结论：").append(conclusion);
        if (!veterinary.isBlank()) detail.append("；官方兽医：").append(veterinary);
        return detail.toString();
    }

    private Map<String, Object> report(String title, String summary, String detail, boolean pass) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("title", title);
        report.put("summary", summary);
        report.put("detail", detail);
        report.put("pass", pass);
        return report;
    }

    private Map<String, Object> first(Object value) {
        if (value instanceof Collection<?> rows && !rows.isEmpty()) return map(rows.iterator().next());
        return null;
    }

    private void addMapped(List<Map<String, Object>> target, Object value) {
        if (value instanceof Collection<?> rows) rows.forEach(row -> target.add(map(row)));
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private boolean intEquals(Object value, int expected) {
        return value instanceof Number n && n.intValue() == expected;
    }

    private String formatTime(Object value) {
        if (value == null) return "";
        String text = value.toString();
        try {
            return LocalDateTime.parse(text).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (DateTimeParseException e) {
            return text;
        }
    }

    private Object nodeData(Object tree) {
        Object data = map(tree).get("data");
        if (data == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "批次数据不存在");
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "下游数据格式异常");
        return (Map<String, Object>) raw;
    }

    private Number number(Object value) {
        if (value instanceof Number n) return n;
        if (value != null) return Long.valueOf(value.toString());
        throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "下游数据缺少关联ID");
    }

    private void addRecords(List<Object> result, Object value) {
        if (value instanceof Collection<?> rows) result.addAll(rows);
    }

    private List<Long> pigIds(Object upstream) {
        if (!(upstream instanceof Collection<?> nodes)) return List.of();
        for (Object value : nodes) {
            Map<String, Object> node = map(value);
            if (!"CARCASS".equals(node.get("type"))) continue;
            Object rawIds = map(node.get("data")).get("pigIds");
            if (rawIds instanceof Collection<?> values) {
                return values.stream().map(this::number).map(Number::longValue).distinct().toList();
            }
        }
        return List.of();
    }

    private Map<String, Object> verifyRecord(Object value) {
        Map<String, Object> record = map(value);
        String bizType = Objects.toString(record.get("bizType"), "");
        Long bizId = number(record.get("bizId")).longValue();
        Map<String, Object> verification = map(clients.verify(bizType, bizId));
        boolean match = number(record.get("status")).intValue() == 1
                && Boolean.TRUE.equals(verification.get("matched"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", bizType);
        detail.put("bizId", bizId);
        detail.put("localHash", verification.get("localHash"));
        detail.put("chainHash", verification.get("onChainHash"));
        detail.put("match", match);
        detail.put("txHash", record.get("txHash"));
        return detail;
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().map(this::number).map(Number::longValue).distinct().toList();
    }
}
