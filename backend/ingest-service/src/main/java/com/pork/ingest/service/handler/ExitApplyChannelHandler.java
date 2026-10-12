package com.pork.ingest.service.handler;

import com.pork.core.enums.ErrorCode;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出栏称重登记通道：出栏口地磅（耳标识读 + 称重）。
 * <p>
 * 这一环与其它设备通道的差别在于<b>设备只负责一半</b>：
 * <ul>
 *   <li><b>设备给得出</b>：哪头猪（耳标）、多重、什么时候称的、哪台设备——称重是机器的本职，
 *       让人凭估重抄数反而是这环最不可靠的地方。</li>
 *   <li><b>设备给不出</b>：目标屠宰场。这是<b>去向声明</b>，不是测量值。设备不知道场里要卖去哪，
 *       所以缺去向一律转「待人工确认」补录，绝不拿设备号或默认值顶——那等于让机器替场里决定卖猪。</li>
 * </ul>
 * 因此策略是「设备优先」：设备自动登记为主路径，设备覆盖不到时允许人工补录（强制实名并上链留痕）。
 * <p>
 * 审批（通过 / 驳回）<b>不在这里</b>：那是人的责任行为，系统里永远由实名的人作出。
 */
@Slf4j
@Component
public class ExitApplyChannelHandler implements ChannelHandler {

    /** 出栏体重的合理区间：低于下限多是没称到（猪只过磅一半），高于上限多是设备读数异常 */
    private static final BigDecimal MIN_WEIGHT = new BigDecimal("20");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("400");

    /** 生猪状态：1 在养 */
    private static final int PIG_STATUS_FARMING = 1;

    private final BizClient bizClient;
    private final String breedingUrl;

    public ExitApplyChannelHandler(BizClient bizClient,
                                   @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.EXIT_APPLY;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) errors.add("缺少耳标号(earTagNo)");

        BigDecimal weight = Payloads.decimal(data, "weightKg");
        if (weight == null) {
            errors.add("缺少出栏体重(weightKg)");
        } else if (weight.compareTo(MIN_WEIGHT) < 0 || weight.compareTo(MAX_WEIGHT) > 0) {
            // 越界值属于硬错误：宁可拒绝让现场重称，也不把一条明显不对的体重写进溯源链
            errors.add("出栏体重 " + weight.toPlainString() + " kg 超出合理范围（" + MIN_WEIGHT.toPlainString()
                    + "–" + MAX_WEIGHT.toPlainString() + " kg），疑似称重设备读数异常");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.EXIT_APPLY.targetTable();
        Map<String, Object> data = ctx.data();
        String earTagNo = Payloads.str(data, "earTagNo");

        // 1) 耳标必须在档：地磅读到的耳标查不到档案，说明猪与档案对不上或档案缺失，不丢数据、交人工核对
        Map<String, Object> pig;
        try {
            pig = bizClient.get(breedingUrl + "/breeding/internal/pigs/by-ear-tag", Map.of("earTagNo", earTagNo));
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用，暂时无法核验耳标：" + e.getMessage());
            }
            return DispatchResult.manual(table, "称重读到的耳标在生猪档案中不存在（" + earTagNo + "），请人工核对");
        }
        Long pigId = Handlers.longValue(pig.get("id"));
        if (pigId == null) {
            return DispatchResult.manual(table, "耳标查到的生猪档案缺少主键，无法自动入账");
        }

        // 2) 只有「在养」的猪才能申报出栏：已出栏/已屠宰说明这头猪早已走完这一步，
        //    地磅重复读数或猪只折返都会撞上这一条，属于确定性错误，直接拒绝而不是留待人工
        Integer status = Handlers.intValue(pig.get("status"));
        if (status != null && status != PIG_STATUS_FARMING) {
            return DispatchResult.rejected(table, "该生猪当前状态为「" + pigStatusLabel(status) + "」，不能再申报出栏");
        }

        // 3) 去向：设备给不出，必须有人说了算。缺了就转人工补录，绝不用设备号或默认值顶替
        String slaughterhouse = Payloads.str(data, "targetSlaughterhouse");
        if (slaughterhouse == null && !force) {
            return DispatchResult.manual(table,
                    "称重已完成，但缺少目标屠宰场（去向），请人工确认这批猪发往哪个屠宰场后入账");
        }

        BigDecimal weight = Payloads.decimal(data, "weightKg");
        LocalDateTime weighTime = Payloads.time(data, "weighTime");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pigId", pigId);
        body.put("earTagNo", earTagNo);
        body.put("weightKg", weight == null ? null : weight.stripTrailingZeros().toPlainString());
        // 称重时刻取报文值，缺失时用上报时刻兜底（猪出栏口即登记，两者等价）
        body.put("applyTime", Handlers.time(weighTime == null ? ctx.reportTime() : weighTime));
        body.put("targetSlaughterhouse", slaughterhouse);
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(breedingUrl + "/breeding/internal/ingest/exit-apply", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }

    private String pigStatusLabel(Integer status) {
        return switch (status) {
            case 2 -> "已出栏";
            case 3 -> "已屠宰";
            case 4 -> "异常/淘汰";
            default -> "状态未知";
        };
    }
}
