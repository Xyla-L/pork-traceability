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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产地检疫通道：检疫证扫码登记终端（扫码枪 / 手持机）。
 * <p>
 * 它与其它设备通道有一个本质区别，值得写在最前面：<b>扫码不产生结论</b>。
 * 检疫结论（合格不合格、准不准调运）由官方兽医作出，证也是官方签发的；
 * 扫码终端做的事只是把证面上的信息——证号、耳标、签发机构、签发时间、有效期、官方兽医——读进系统，
 * 替代手抄。所以这一环的策略是「设备优先」而不是「设备独占」：
 * 设备覆盖不了的情况（纸质单丢失、二维码磨损、跨省格式不一、批量补录）保留人工出证入口，
 * 设备覆盖不到时允许人工登记，但必须实名并上链留痕。
 * <p>
 * 三条校验底线：
 * <ol>
 *   <li><b>耳标必须在档</b>：读到的耳标档案里没有，说明证与猪对不上或档案缺失，交人工核对。</li>
 *   <li><b>官方兽医必须落到人</b>：姓名取自证面，是这张证的责任人。扫码终端是工具，
 *       拿不到名字就转人工，绝不能用设备号顶替——否则追溯链上「谁签发的这张证」查到的是一台机器。</li>
 *   <li><b>扫描件报了就必须是真的</b>：伪造 fileId 直接拒绝；但只有扫码枪的终端拿不到照片，
 *       这种情况不拦——检疫证的核心凭据是证号本身，扫描件是佐证。</li>
 * </ol>
 */
@Slf4j
@Component
public class QuarantineChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String breedingUrl;
    private final String fileUrl;

    public QuarantineChannelHandler(BizClient bizClient,
                                    @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl,
                                    @Value("${services.file-url:http://localhost:8088}") String fileUrl) {
        this.bizClient = bizClient;
        this.breedingUrl = breedingUrl;
        this.fileUrl = fileUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.QUARANTINE;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = ctx.data();
        if (Payloads.str(data, "earTagNo") == null) errors.add("缺少耳标号(earTagNo)");
        if (Payloads.str(data, "certNo") == null) errors.add("缺少检疫证编号(certNo)");
        if (Payloads.str(data, "issueOrg") == null) errors.add("缺少签发机构(issueOrg)");
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.QUARANTINE.targetTable();
        Map<String, Object> data = ctx.data();
        String earTagNo = Payloads.str(data, "earTagNo");

        // 1) 耳标必须在档：证上登记的耳标在档案里查不到，就说明证与猪对不上或档案缺失，
        //    这种情况不丢数据，交人工核对纸质材料后放行
        Map<String, Object> pig;
        try {
            pig = bizClient.get(breedingUrl + "/breeding/internal/pigs/by-ear-tag", Map.of("earTagNo", earTagNo));
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用，暂时无法核验耳标：" + e.getMessage());
            }
            return DispatchResult.manual(table, "检疫证登记的耳标在生猪档案中不存在（" + earTagNo + "），请人工核对");
        }
        Long pigId = Handlers.longValue(pig.get("id"));
        if (pigId == null) {
            return DispatchResult.manual(table, "耳标查到的生猪档案缺少主键，无法自动入账");
        }

        // 2) 官方兽医（证面签发人）：扫码终端只是读码工具，不能把它写进这个人名字段
        String inspector = Payloads.str(data, "inspector");
        if (inspector == null && !force) {
            return DispatchResult.manual(table, "缺少官方兽医（inspector），检疫证必须落到签发人，请补录后入账");
        }

        // 3) 扫描件：报了就得是真的，编一个 fileId 过不了这一关；没报（只有扫码枪的形态）不拦
        List<String> fileIds = Payloads.strings(data, "fileIds");
        for (String fileId : fileIds) {
            try {
                bizClient.get(fileUrl + "/file/" + fileId + "/meta", Map.of());
            } catch (BusinessException e) {
                if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                    return DispatchResult.manual(table, "文件服务不可用，暂时无法核验检疫证扫描件：" + e.getMessage());
                }
                return DispatchResult.rejected(table, "检疫证扫描件不存在（" + fileId + "），疑似伪造凭证，禁止入库");
            }
        }

        // 4) 落业务表：签发时间取证面值，缺失时用上报时刻兜底（证是当场扫的，两者等价）
        LocalDateTime issueTime = Payloads.time(data, "issueTime");
        LocalDateTime validUntil = Payloads.time(data, "validUntil");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pigId", pigId);
        body.put("earTagNo", earTagNo);
        body.put("certNo", Payloads.str(data, "certNo"));
        body.put("issueOrg", Payloads.str(data, "issueOrg"));
        body.put("issueTime", Handlers.time(issueTime == null ? ctx.reportTime() : issueTime));
        body.put("validUntil", validUntil == null ? null : validUntil.toLocalDate().toString());
        body.put("inspector", inspector == null ? "未记录" : inspector);
        body.put("fileIds", fileIds);
        body.put("caSignature", Payloads.str(data, "caSignature"));
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(breedingUrl + "/breeding/internal/ingest/quarantine-cert", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.manual(table, "养殖服务不可用：" + e.getMessage());
            }
            throw e;
        }
    }
}
