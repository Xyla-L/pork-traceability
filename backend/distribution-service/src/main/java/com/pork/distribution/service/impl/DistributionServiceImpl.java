package com.pork.distribution.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.BusinessNoGenerator;
import com.pork.core.util.HashUtil;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.*;
import com.pork.distribution.mapper.*;
import com.pork.distribution.service.DistributionService;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DistributionServiceImpl implements DistributionService {
    private final CarcassBatchMapper carcassBatchMapper;
    private final SplitBatchMapper splitBatchMapper;
    private final ColdChainTransportMapper transportMapper;
    private final TemperatureLogMapper temperatureLogMapper;
    private final StoreReceiptMapper receiptMapper;
    private final ChainEventPublisher chainEvents;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarcassBatch createBatch(DistributionRequests.BatchCreate request) {
        validatePigNotOccupied(request.pigIds(), null);
        CarcassBatch entity = new CarcassBatch();
        BeanUtils.copyProperties(request, entity);
        entity.setBatchNo(StringUtils.hasText(request.batchNo()) ? request.batchNo() : BusinessNoGenerator.next("CB"));
        entity.setCreateTime(LocalDateTime.now());
        carcassBatchMapper.insert(entity);
        return entity;
    }

    @Override
    public Page<CarcassBatch> pageBatches(String batchNo, String operator, LocalDate startDate, LocalDate endDate,
                                          long pageNum, long pageSize) {
        return carcassBatchMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<CarcassBatch>lambdaQuery()
                        .like(StringUtils.hasText(batchNo), CarcassBatch::getBatchNo, batchNo)
                        .like(StringUtils.hasText(operator), CarcassBatch::getOperator, operator)
                        // 创建日期范围：[开始日期 00:00:00, 结束日期次日 00:00:00)，结束日当天包含在内
                        .ge(startDate != null, CarcassBatch::getCreateTime,
                                startDate == null ? null : startDate.atStartOfDay())
                        .lt(endDate != null, CarcassBatch::getCreateTime,
                                endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                        .orderByDesc(CarcassBatch::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBatch(Long id, DistributionRequests.BatchCreate request) {
        CarcassBatch entity = require(carcassBatchMapper.selectById(id), "胴体批次不存在");
        // 批次号为溯源标识不可修改；仅当关联生猪集合发生变化时做业务校验
        List<Long> oldPigs = entity.getPigIds() == null ? List.of() : entity.getPigIds();
        List<Long> newPigs = request.pigIds() == null ? List.of() : request.pigIds();
        if (!new HashSet<>(oldPigs).equals(new HashSet<>(newPigs))) {
            Long splitCount = splitBatchMapper.selectCount(Wrappers.<SplitBatch>lambdaQuery()
                    .eq(SplitBatch::getParentBatchId, id));
            if (splitCount != null && splitCount > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该批次已发生分割，不能变更关联生猪");
            }
            validatePigNotOccupied(newPigs, id);
        }
        entity.setPigIds(request.pigIds());
        entity.setTotalWeightKg(request.totalWeightKg());
        entity.setSlaughterhouse(request.slaughterhouse());
        entity.setOperator(request.operator());
        entity.setNote(request.note());
        carcassBatchMapper.updateById(entity);
    }

    @Override
    public List<Map<String, Object>> getPigOccupancy() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CarcassBatch b : carcassBatchMapper.selectList(null)) {
            if (b.getPigIds() == null) continue;
            for (Long pigId : b.getPigIds()) {
                Map<String, Object> m = new HashMap<>();
                m.put("pigId", pigId);
                m.put("batchId", b.getId());
                m.put("batchNo", b.getBatchNo());
                result.add(m);
            }
        }
        return result;
    }

    /** 校验待关联生猪未被其他胴体批次占用（一头猪只能归属一个批次） */
    private void validatePigNotOccupied(List<Long> pigIds, Long excludeBatchId) {
        if (pigIds == null || pigIds.isEmpty()) return;
        Map<Long, String> occupied = new HashMap<>();
        for (CarcassBatch b : carcassBatchMapper.selectList(null)) {
            if (excludeBatchId != null && excludeBatchId.equals(b.getId())) continue;
            if (b.getPigIds() == null) continue;
            for (Long pid : b.getPigIds()) occupied.putIfAbsent(pid, b.getBatchNo());
        }
        List<String> conflicts = pigIds.stream().distinct()
                .filter(occupied::containsKey)
                .map(pid -> "生猪ID" + pid + "（已属批次" + occupied.get(pid) + "）")
                .toList();
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "以下生猪已属于其他批次，一头猪只能归属一个批次：" + String.join("、", conflicts));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long id) {
        require(carcassBatchMapper.selectById(id), "胴体批次不存在");
        // 存在一级分割时禁止删除，保证批次溯源链不断裂
        Long splitCount = splitBatchMapper.selectCount(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getParentBatchId, id).eq(SplitBatch::getSplitLevel, 1));
        if (splitCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该胴体批次下存在分割批次，无法删除");
        carcassBatchMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SplitBatch createSplit(DistributionRequests.SplitCreate request) {
        Integer splitLevel = request.splitLevel() == null ? resolveSplitLevel(request.parentBatchId()) : request.splitLevel();
        validateParent(request.parentBatchId(), splitLevel);
        SplitBatch entity = new SplitBatch();
        BeanUtils.copyProperties(request, entity);
        entity.setSplitLevel(splitLevel);
        entity.setBatchNo(StringUtils.hasText(request.batchNo()) ? request.batchNo() : BusinessNoGenerator.next("SP"));
        entity.setPackageCount(request.packageCount() == null ? 1 : request.packageCount());
        entity.setSplitTime(request.splitTime() == null ? LocalDateTime.now() : request.splitTime());
        entity.setCreateTime(LocalDateTime.now());
        entity.setContentHash(HashUtil.sha256Json(request));
        splitBatchMapper.insert(entity);
        publish("SPLIT_BATCH", entity.getId(), entity.getBatchNo(), entity.getContentHash(), Map.of("batchNo", entity.getBatchNo()));
        return entity;
    }

    @Override
    public Page<SplitBatch> pageSplits(String keyword, long pageNum, long pageSize) {
        return splitBatchMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<SplitBatch>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w.like(SplitBatch::getBatchNo, keyword)
                        .or().like(SplitBatch::getProductName, keyword))
                .orderByDesc(SplitBatch::getCreateTime));
    }

    @Override
    public SplitBatch getSplit(Long id) {
        return require(splitBatchMapper.selectById(id), "分割批次不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSplit(Long id, DistributionRequests.SplitUpdate request) {
        SplitBatch entity = require(splitBatchMapper.selectById(id), "分割批次不存在");
        // DTO 仅含可编辑业务字段，BeanUtils 只覆盖同名字段，父批次/层级/批次号/哈希/时间不受影响
        BeanUtils.copyProperties(request, entity);
        splitBatchMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSplit(Long id) {
        SplitBatch split = require(splitBatchMapper.selectById(id), "分割批次不存在");
        // 存在下级分割或已生成冷链运输任务时禁止删除
        Long childCount = splitBatchMapper.selectCount(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getParentBatchId, id)
                .eq(split.getSplitLevel() != null, SplitBatch::getSplitLevel, split.getSplitLevel() == null ? null : split.getSplitLevel() + 1));
        if (childCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该分割批次下存在子分割，无法删除");
        Long transportCount = transportMapper.selectCount(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getSplitBatchId, id));
        if (transportCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该分割批次已关联冷链运输任务，无法删除");
        splitBatchMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> getSplitTree(String batchNo) {
        CarcassBatch carcass = carcassBatchMapper.selectOne(Wrappers.<CarcassBatch>lambdaQuery().eq(CarcassBatch::getBatchNo, batchNo));
        if (carcass != null) {
            return Map.of("type", "CARCASS", "data", carcass,
                    "children", children(carcass.getId(), 0, new HashSet<>()));
        }
        SplitBatch split = splitBatchMapper.selectOne(Wrappers.<SplitBatch>lambdaQuery().eq(SplitBatch::getBatchNo, batchNo));
        if (split == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "批次不存在");
        return splitNode(split, new HashSet<>());
    }

    private List<Map<String, Object>> children(Long parentId, int parentLevel, Set<String> visited) {
        String visitKey = parentLevel + ":" + parentId;
        if (!visited.add(visitKey))
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "检测到批次循环引用");
        List<SplitBatch> rows = splitBatchMapper.selectList(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getParentBatchId, parentId)
                .eq(SplitBatch::getSplitLevel, parentLevel + 1)
                .orderByAsc(SplitBatch::getId));
        List<Map<String, Object>> result = rows.stream().map(row -> splitNode(row, new HashSet<>(visited))).toList();
        visited.remove(visitKey);
        return result;
    }

    private Map<String, Object> splitNode(SplitBatch split, Set<String> visited) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "SPLIT");
        node.put("data", split);
        node.put("children", children(split.getId(), split.getSplitLevel(), visited));
        return node;
    }

    @Override
    public List<Map<String, Object>> upstream(Long splitId) {
        List<Map<String, Object>> path = new ArrayList<>();
        SplitBatch current = require(splitBatchMapper.selectById(splitId), "分割批次不存在");
        Set<Long> visited = new HashSet<>();
        while (current != null) {
            if (!visited.add(current.getId())) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "检测到批次循环引用");
            path.add(Map.of("type", "SPLIT", "data", current));
            Long parentId = current.getParentBatchId();
            if (current.getSplitLevel() > 1) {
                SplitBatch parentSplit = splitBatchMapper.selectById(parentId);
                if (parentSplit == null || parentSplit.getSplitLevel() != current.getSplitLevel() - 1)
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "分割批次父级关系无效");
                current = parentSplit;
            } else {
                CarcassBatch carcass = carcassBatchMapper.selectById(parentId);
                if (carcass == null) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "胴体父批次不存在");
                path.add(Map.of("type", "CARCASS", "data", carcass));
                break;
            }
        }
        return path;
    }

    @Override
    public Map<String, Object> downstream(String batchNo) {
        Map<String, Object> tree = getSplitTree(batchNo);
        List<Long> splitIds = new ArrayList<>();
        collectSplitIds(tree, splitIds);
        List<ColdChainTransport> transports = splitIds.isEmpty() ? List.of()
                : transportMapper.selectList(Wrappers.<ColdChainTransport>lambdaQuery()
                        .in(ColdChainTransport::getSplitBatchId, splitIds)
                        .orderByAsc(ColdChainTransport::getCreateTime));
        List<Map<String, Object>> logistics = transports.stream().map(transport -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("transport", transport);
            item.put("temperatureLogs", temperatureLogMapper.selectList(Wrappers.<TemperatureLog>lambdaQuery()
                    .eq(TemperatureLog::getTransportId, transport.getId())
                    .orderByAsc(TemperatureLog::getRecordTime)));
            item.put("receipt", receiptMapper.selectOne(Wrappers.<StoreReceipt>lambdaQuery()
                    .eq(StoreReceipt::getTransportId, transport.getId()).last("LIMIT 1")));
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tree", tree);
        result.put("splitIds", splitIds);
        result.put("logistics", logistics);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ColdChainTransport createTransport(DistributionRequests.TransportCreate request) {
        require(splitBatchMapper.selectById(request.splitBatchId()), "分割批次不存在");
        ColdChainTransport entity = new ColdChainTransport();
        BeanUtils.copyProperties(request, entity);
        entity.setTransportNo(StringUtils.hasText(request.transportNo()) ? request.transportNo() : BusinessNoGenerator.next("TR"));
        entity.setStatus(1);
        entity.setCreateTime(LocalDateTime.now());
        transportMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTransport(Long id, DistributionRequests.TransportUpdate request) {
        ColdChainTransport entity = require(transportMapper.selectById(id), "运输任务不存在");
        require(splitBatchMapper.selectById(request.splitBatchId()), "分割批次不存在");
        // 已发车后货物已在途，不允许再更换分割批次
        if (!Objects.equals(entity.getSplitBatchId(), request.splitBatchId())
                && !Objects.equals(entity.getStatus(), 1)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "运单已发车，不能变更关联分割批次");
        }
        // 仅覆盖业务字段，运单号/状态/实际发车与到达时间/创建时间保持不变
        entity.setSplitBatchId(request.splitBatchId());
        entity.setVehicleNo(request.vehicleNo());
        entity.setVehicleType(request.vehicleType());
        entity.setRefrigeration(request.refrigeration());
        entity.setDriverName(request.driverName());
        entity.setDriverPhone(request.driverPhone());
        entity.setOrigin(request.origin());
        entity.setDestination(request.destination());
        entity.setPlannedDepart(request.plannedDepart());
        entity.setPlannedArrive(request.plannedArrive());
        transportMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTransport(Long id) {
        ColdChainTransport entity = require(transportMapper.selectById(id), "运输任务不存在");
        // 已产生门店签收的运单属于溯源链下游凭证，禁止删除
        Long receiptCount = receiptMapper.selectCount(Wrappers.<StoreReceipt>lambdaQuery()
                .eq(StoreReceipt::getTransportId, id));
        if (receiptCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该运单已生成门店签收记录，无法删除");
        temperatureLogMapper.delete(Wrappers.<TemperatureLog>lambdaQuery().eq(TemperatureLog::getTransportId, id));
        transportMapper.deleteById(id);
    }

    @Override
    public Page<ColdChainTransport> pageTransports(Integer status, String keyword, long pageNum, long pageSize) {
        return transportMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(status != null, ColdChainTransport::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w.like(ColdChainTransport::getTransportNo, keyword)
                        .or().like(ColdChainTransport::getVehicleNo, keyword)
                        .or().like(ColdChainTransport::getDriverName, keyword))
                .orderByDesc(ColdChainTransport::getCreateTime));
    }

    @Override
    public ColdChainTransport getTransport(Long id) {
        return require(transportMapper.selectById(id), "运输任务不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void depart(Long id) {
        int changed = transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                .eq(ColdChainTransport::getId, id).eq(ColdChainTransport::getStatus, 1)
                .set(ColdChainTransport::getStatus, 2).set(ColdChainTransport::getDepartTime, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅待发车任务可以发车");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemperatureLog addTemperature(Long transportId, DistributionRequests.TemperatureCreate request) {
        ColdChainTransport transport = getTransport(transportId);
        if (!Objects.equals(transport.getStatus(), 2)) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "运输中任务才能记录温度");
        BigDecimal min = request.tempRangeMin() == null ? new BigDecimal("-18.0") : request.tempRangeMin();
        BigDecimal max = request.tempRangeMax() == null ? BigDecimal.ZERO : request.tempRangeMax();
        if (min.compareTo(max) > 0) throw new BusinessException(ErrorCode.PARAM_ERROR, "温度下限不能高于上限");
        TemperatureLog log = new TemperatureLog();
        log.setTransportId(transportId);
        log.setRecordTime(LocalDateTime.now());
        log.setTemperature(request.temperature());
        log.setTempRangeMin(min);
        log.setTempRangeMax(max);
        log.setIsAbnormal(request.temperature().compareTo(min) < 0 || request.temperature().compareTo(max) > 0 ? 1 : 0);
        log.setRecorder(request.recorder());
        log.setRecordMethod(StringUtils.hasText(request.recordMethod()) ? request.recordMethod() : "MANUAL");
        log.setCreateTime(LocalDateTime.now());
        temperatureLogMapper.insert(log);
        return log;
    }

    @Override
    public List<TemperatureLog> temperatureLogs(Long transportId) {
        getTransport(transportId);
        return temperatureLogMapper.selectList(Wrappers.<TemperatureLog>lambdaQuery()
                .eq(TemperatureLog::getTransportId, transportId).orderByAsc(TemperatureLog::getRecordTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void arrive(Long id) {
        int changed = transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                .eq(ColdChainTransport::getId, id).eq(ColdChainTransport::getStatus, 2)
                .set(ColdChainTransport::getStatus, 3).set(ColdChainTransport::getArriveTime, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅运输中任务可以到达");
    }

    @Override
    public StoreReceipt getReceipt(Long id) {
        return require(receiptMapper.selectById(id), "签收记录不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreReceipt createReceipt(DistributionRequests.ReceiptCreate request) {
        ColdChainTransport transport = getTransport(request.transportId());
        if (!Objects.equals(transport.getStatus(), 3)) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "运输任务尚未到达门店，无法签收");
        Long existing = receiptMapper.selectCount(Wrappers.<StoreReceipt>lambdaQuery().eq(StoreReceipt::getTransportId, request.transportId()));
        if (existing > 0) throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "该运单已签收");
        StoreReceipt entity = new StoreReceipt();
        BeanUtils.copyProperties(request, entity);
        entity.setReceiptTime(LocalDateTime.now());
        entity.setQtyCheck(request.qtyCheck() == null ? 1 : request.qtyCheck());
        entity.setTempCheck(request.tempCheck() == null ? 1 : request.tempCheck());
        entity.setPackageIntact(request.packageIntact() == null ? 1 : request.packageIntact());
        entity.setCreateTime(LocalDateTime.now());
        entity.setContentHash(HashUtil.sha256Json(request));
        receiptMapper.insert(entity);
        transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                .eq(ColdChainTransport::getId, request.transportId())
                .eq(ColdChainTransport::getStatus, 3)
                .set(ColdChainTransport::getStatus, 4));
        publish("STORE_RECEIPT", entity.getId(), transport.getTransportNo(), entity.getContentHash(), Map.of("transportNo", transport.getTransportNo()));
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReceipt(Long id) {
        require(receiptMapper.selectById(id), "签收记录不存在");
        receiptMapper.deleteById(id);
    }

    @Override
    public Page<StoreReceipt> pageReceipts(Long storeId, String storeName, LocalDate startDate, LocalDate endDate,
                                           long pageNum, long pageSize) {
        return receiptMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<StoreReceipt>lambdaQuery()
                .eq(storeId != null, StoreReceipt::getStoreId, storeId)
                .like(StringUtils.hasText(storeName), StoreReceipt::getStoreName, storeName)
                // 签收日期范围：[开始日期 00:00:00, 结束日期次日 00:00:00)，结束日当天包含在内
                .ge(startDate != null, StoreReceipt::getReceiptTime,
                        startDate == null ? null : startDate.atStartOfDay())
                .lt(endDate != null, StoreReceipt::getReceiptTime,
                        endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                .orderByDesc(StoreReceipt::getReceiptTime));
    }

    @Override
    public List<Map<String, Object>> stores() {
        return receiptMapper.selectMaps(Wrappers.<StoreReceipt>query()
                .select("store_id", "MAX(store_name) AS store_name").groupBy("store_id").orderByAsc("store_id"));
    }

    private <T> T require(T value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, message);
        return value;
    }

    private Integer resolveSplitLevel(Long parentId) {
        if (carcassBatchMapper.selectById(parentId) != null) return 1;
        SplitBatch parent = splitBatchMapper.selectById(parentId);
        if (parent == null || parent.getSplitLevel() == null)
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "父批次不存在");
        int level = parent.getSplitLevel() + 1;
        if (level > 4) throw new BusinessException(ErrorCode.PARAM_ERROR, "分割层级不能超过4级");
        return level;
    }

    private void validateParent(Long parentId, Integer splitLevel) {
        if (splitLevel == 1) {
            require(carcassBatchMapper.selectById(parentId), "一级分割的胴体父批次不存在");
            return;
        }
        SplitBatch parent = require(splitBatchMapper.selectById(parentId), "上级分割批次不存在");
        if (parent.getSplitLevel() == null || parent.getSplitLevel() + 1 != splitLevel)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分割层级必须与父批次连续");
    }

    @SuppressWarnings("unchecked")
    private void collectSplitIds(Map<String, Object> node, List<Long> result) {
        if ("SPLIT".equals(node.get("type")) && node.get("data") instanceof SplitBatch split) {
            result.add(split.getId());
        }
        Object rawChildren = node.get("children");
        if (!(rawChildren instanceof Collection<?> children)) return;
        for (Object child : children) {
            if (child instanceof Map<?, ?> map) collectSplitIds((Map<String, Object>) map, result);
        }
    }

    private void publish(String type, Long id, String key, String hash, Map<String, Object> payload) {
        chainEvents.publish(ChainTxMessage.create(type, id, key, hash, payload));
    }
}
