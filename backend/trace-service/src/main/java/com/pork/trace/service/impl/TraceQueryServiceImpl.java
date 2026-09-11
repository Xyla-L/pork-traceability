package com.pork.trace.service.impl;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.trace.client.TraceRemoteClient;
import com.pork.trace.service.TraceQueryService;
import com.pork.trace.vo.SafeBuyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

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
        // 1. 获取基础扫码结果
        Map<String, Object> scanResult = scan(qrCode);
        if (scanResult == null) {
            throw new RuntimeException("未查询到溯源信息");
        }

        // 2. 提取各部分数据并安全转换类型 (解决 List<?> 转 List<Object> 报错)
        Map<String, Object> product = map(scanResult.get("product"));
        Map<String, Object> verification = map(scanResult.get("verification"));

        // 提取溯源记录 (即 chainRecords)
        List<Object> chainRecords = new ArrayList<>();
        if (scanResult.get("traceChain") instanceof List) {
            chainRecords = new ArrayList<>((List<?>) scanResult.get("traceChain"));
        }

        // 提取证书链 (尝试从 scanResult 中获取，如果没有则给空列表兜底)
        List<Object> certChain = new ArrayList<>();
        if (scanResult.get("certChain") instanceof List) {
            certChain = new ArrayList<>((List<?>) scanResult.get("certChain"));
        }

        // 提取检测报告 (尝试从 scanResult 中获取，如果没有则给空列表兜底)
        List<Object> reports = new ArrayList<>();
        if (scanResult.get("reports") instanceof List) {
            reports = new ArrayList<>((List<?>) scanResult.get("reports"));
        }

        // 3. 组装区块链验证信息
        Map<String, Object> blockchain = Map.of(
                "verified", verification != null ? verification.get("verified") : false,
                "records", verification != null ? verification.get("details") : List.of()
        );

        // 4. 组装最终的 SafeBuyVO 返回给前端
        SafeBuyVO vo = new SafeBuyVO();
        vo.setQrCode(qrCode);
        vo.setProduct(product != null ? product : Map.of());
        vo.setCertChain(certChain);
        vo.setReports(reports);
        vo.setChainRecords(chainRecords);
        vo.setRecordCount(chainRecords.size()); // 记录总数即为溯源链条的条数
        vo.setBlockchain(blockchain);

        return vo;
    }

// --- 以下是你需要补充的伪代码方法 ---

    private List<Object> getCertChainByProduct(Map<String, Object> product) {
        // TODO: 根据 product 中的信息（如 batchNo）去数据库查询证书链数据
        // 例如：certificateMapper.selectByBatchNo(...)
        return List.of();
    }

    private List<Object> getReportsByProduct(Map<String, Object> product) {
        // TODO: 根据 product 中的信息去数据库查询检测报告数据
        // 例如：inspectionMapper.selectByBatchNo(...)
        return List.of();
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
