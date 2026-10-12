package com.pork.slaughter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.slaughter.client.BreedingClient;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.entity.SlaughterInspection;
import com.pork.slaughter.mapper.SlaughterInspectionMapper;
import com.pork.slaughter.service.SlaughterInspectionService;
import com.pork.slaughter.support.SlaughterStatus;
import com.pork.slaughter.vo.SlaughterInspectionVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.source.ManualOperator;
import com.pork.core.source.TraceStage;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.web.source.SourceGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SlaughterInspectionServiceImpl extends ServiceImpl<SlaughterInspectionMapper, SlaughterInspection>
        implements SlaughterInspectionService {

    /** 人工录入来源标记（与设备采集 SOURCE_DEVICE 对应，便于链上审计区分） */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 允许修改的说明性字段：检验记录本身即结论载体，只有附件属于可补充的说明性内容 */
    private static final String EDITABLE_FIELDS = "检验照片/报告附件";

    private final ChainEventPublisher chainEvents;
    private final BreedingClient breedingClient;
    private final SourceGuard sourceGuard;

    /* 人工新增方法已删除：屠宰检验为设备独占环节，写入只走设备通道
       （SlaughterDeviceIngestController /slaughter/internal/ingest/inspection）。 */


    /**
     * 作废：逻辑作废 + 理由 + 实名，并单独上链一条 VOID 事件。
     * <p>
     * 「这条合格结论为什么不算数了」必须答得出来，所以理由不能省；
     * 也不做物理删除——链上已有哈希，删掉记录等于让追溯链断在这里。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidInspection(Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.INSPECTION, reason);
        String cleanReason = reason.trim();
        SlaughterInspection old = this.getById(id);
        if (old == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "屠宰检验记录不存在或已作废");
        String operator = voidBy.display();
        if (baseMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：记录可能已被他人作废");
        }
        Map<String, Object> voidPayload = new HashMap<>();
        voidPayload.put("inspectNo", old.getInspectNo());
        voidPayload.put("pigId", old.getPigId());
        voidPayload.put("action", "VOID");
        voidPayload.put("voidReason", cleanReason);
        voidPayload.put("operator", operator);
        voidPayload.put("originalSource", old.getSource());
        Map<String, Object> hashSource = new HashMap<>(voidPayload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        chainEvents.publish(ChainTxMessage.create("SLAUGHTER_INSPECT_VOID", id, old.getInspectNo() + ":VOID",
                HashUtil.sha256Json(hashSource), voidPayload));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteInspection(Long id) {
        // 检验记录已上链存证，物理删除会造成链上哈希无法溯源验证，必须走逻辑作废
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "检验记录已上链，不可删除，请使用作废功能");
    }

    @Override
    public Page<SlaughterInspectionVO> pageQuery(SlaughterInspectionDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : dto.getPageSize();
        Page<SlaughterInspection> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<SlaughterInspection> wrapper = new LambdaQueryWrapper<>();
        if (dto.getPigId() != null) {
            wrapper.eq(SlaughterInspection::getPigId, dto.getPigId());
        }
        if (dto.getInspectNo() != null && !dto.getInspectNo().isEmpty()) {
            wrapper.like(SlaughterInspection::getInspectNo, dto.getInspectNo());
        }
        if (dto.getBatchNo() != null && !dto.getBatchNo().isEmpty()) {
            wrapper.like(SlaughterInspection::getBatchNo, dto.getBatchNo());
        }
        if (dto.getEarTagNo() != null && !dto.getEarTagNo().isEmpty()) {
            wrapper.like(SlaughterInspection::getEarTagNo, dto.getEarTagNo());
        }
        // 检验类型支持中文或数字编码。宰后检验(2) 与 宰后同步检验(3) 按法规口径是同一个环节
        // （宰后检疫的标准做法就是同步检疫），所以筛「宰后检验」要把两者都带出来；
        // 筛「宰后检验（同步）」才精确到 3。
        // 原先 3 直接跳过条件，等于选「同步检验」时返回全部类型——一个静默失效的筛选项。
        Integer inspectType = SlaughterStatus.inspectType(dto.getInspectType());
        if (inspectType != null) {
            wrapper.in(SlaughterInspection::getInspectType,
                    inspectType == 2 ? List.of(2, 3) : List.of(inspectType));
        }
        // 状态支持中文("待检验/合格/不合格")或数字编码
        Integer status = SlaughterStatus.inspection(dto.getStatus());
        if (status != null) {
            wrapper.eq(SlaughterInspection::getStatus, status);
        }
        if (dto.getStartDate() != null && !dto.getStartDate().isEmpty()) {
            wrapper.ge(SlaughterInspection::getInspectTime, LocalDate.parse(dto.getStartDate()).atStartOfDay());
        }
        if (dto.getEndDate() != null && !dto.getEndDate().isEmpty()) {
            wrapper.le(SlaughterInspection::getInspectTime, LocalDate.parse(dto.getEndDate()).atTime(23, 59, 59));
        }
        wrapper.orderByDesc(SlaughterInspection::getInspectTime);

        Page<SlaughterInspection> entityPage = this.page(page, wrapper);

        Page<SlaughterInspectionVO> voPage = new Page<>();
        BeanUtil.copyProperties(entityPage, voPage, "records");
        voPage.setRecords(BeanUtil.copyToList(entityPage.getRecords(), SlaughterInspectionVO.class));

        return voPage;
    }
}
