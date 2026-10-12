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
 * 分割车间监控通道：定时拍一张车间现场照片。
 * <p>
 * 与其它通道的三处不同：
 * <ol>
 *   <li><b>两段式上报</b>：图片走文件服务（{@code POST /file/upload}）换成 {@code fileId}，
 *       报文里只带 fileId —— 接入层单条报文 64KB 上限，图片本身绝不可能塞进 JSON。
 *       这里照抄免疫注射通道的做法：<b>调文件服务核对 fileId 真实存在</b>，
 *       编一个 fileId 蒙混过不了这一关。</li>
 *   <li><b>不产生业务判定</b>：不校验瘦肉精、不校验任何前置条件 ——
 *       照片只是现场影像，不回答任何业务问题。唯一的例外是<b>绑定关系</b>：
 *       报文中若声称「这张照片属于批次 X」，X 就必须真实存在（否则 = 编造绑定），
 *       由配送服务校验并拒绝。</li>
 *   <li><b>失败不入人工队列</b>：照片是分钟级周期快照，丢一张不影响追溯。
 *       凭证不存在（伪造）或绑定批次不存在（编造）判<b>已拒绝</b>；文件服务抖动判<b>已丢弃</b>。
 *       转人工会让待人工队列被照片噪声淹没，而人工对着一堆「上传失败的照片」什么也做不了。</li>
 * </ol>
 */
@Slf4j
@Component
public class SplitSnapshotChannelHandler implements ChannelHandler {

    private final BizClient bizClient;
    private final String distributionUrl;
    private final String fileUrl;

    public SplitSnapshotChannelHandler(BizClient bizClient,
                                       @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl,
                                       @Value("${services.file-url:http://localhost:8088}") String fileUrl) {
        this.bizClient = bizClient;
        this.distributionUrl = distributionUrl;
        this.fileUrl = fileUrl;
    }

    @Override
    public IngestChannel channel() {
        return IngestChannel.SPLIT_SNAPSHOT;
    }

    @Override
    public List<String> validate(IngestContext ctx) {
        List<String> errors = new ArrayList<>();
        if (fileId(ctx.data()) == null) {
            errors.add("缺少照片(fileId)：监控相机须先上传照片取得 fileId，再上报");
        }
        return errors;
    }

    @Override
    public DispatchResult dispatch(IngestContext ctx, boolean force) {
        String table = IngestChannel.SPLIT_SNAPSHOT.targetTable();
        Map<String, Object> data = ctx.data();
        String fileId = fileId(data);

        // 照片凭证：报了就必须是真的。文件不存在 = 编造凭证 → 已拒绝；
        // 文件服务不可用 = 环境问题，下次整点再拍一张就是了 → 已丢弃（不进人工队列）。
        try {
            bizClient.get(fileUrl + "/file/" + fileId + "/meta", Map.of());
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                return DispatchResult.dropped(table, "文件服务不可用，本次快照丢弃（照片为周期快照，缺失不影响追溯）：" + e.getMessage());
            }
            return DispatchResult.rejected(table, "快照照片不存在（" + fileId + "），疑似伪造凭证，禁止入库");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileId", fileId);
        // 拍摄时刻取报文级时间（设备拍的瞬间），缺省才退回接入层接收时间 ——
        // 与温度打卡同一条纪律：现场时刻不能拿入库时刻顶替。
        LocalDateTime capturedTime = Payloads.time(data, "capturedTime");
        body.put("capturedTime", Handlers.time(capturedTime == null ? ctx.reportTime() : capturedTime));
        body.put("workshop", Payloads.str(data, "workshop"));
        // 绑定：工位终端（分割线扫码称重台）会把「当前正在分割的白条属于哪个批次」一起报过来。
        // 相机自己不认识批次 —— 这个值不是相机推断的，是终端给的；不带就只是不绑定（现场影像）。
        // 带了却查不到该批次，由配送服务判「编造绑定关系」并拒绝（见 DistributionDeviceIngestService）。
        body.put("batchNo", Payloads.str(data, "batchNo"));
        // 相机本来就是机器在记录，记录「哪台相机拍的」用设备号是对的（与温度 recorder 同理）
        body.put("deviceNo", ctx.deviceNo());
        body.put("source", "DEVICE");
        body.put("sourceRef", ctx.sourceRef());
        body.put("rawPayload", ctx.rawPayload());

        try {
            Map<String, Object> result = bizClient.post(distributionUrl + "/distribution/internal/ingest/split-snapshot", body);
            return Handlers.toResult(result, table);
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                // 配送服务抖动同样只丢弃：照片丢一张不影响追溯，下次整点自然会再拍。
                log.warn("split_snapshot_downstream_unavailable fileId={} err={}", fileId, e.getMessage());
                return DispatchResult.dropped(table, "配送服务不可用，本次快照丢弃：" + e.getMessage());
            }
            throw e;
        }
    }

    /** 单张优先：{@code fileId} 单值；兼容 {@code fileIds} 数组（取第一张） */
    private static String fileId(Map<String, Object> data) {
        String single = Payloads.str(data, "fileId");
        if (single != null) return single;
        List<String> many = Payloads.strings(data, "fileIds");
        return many.isEmpty() ? null : many.get(0);
    }
}
