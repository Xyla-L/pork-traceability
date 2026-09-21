package com.pork.ingest.service;

import com.pork.ingest.support.DispatchResult;
import com.pork.ingest.support.IngestChannel;

import java.util.List;

/**
 * 单条通道处理器。
 * <p>
 * 两层校验分工：
 * <ul>
 *   <li>{@link #validate} —— 结构性校验。不通过直接拒绝，不入业务表（如仪器未出结果、温度越界、缺关键字段）。</li>
 *   <li>{@link #dispatch} —— 业务性校验。需要外部数据配合（耳标是否在档、检疫证是否匹配），
 *       不通过时按「待人工处理」入队，而不是丢数据。</li>
 * </ul>
 */
public interface ChannelHandler {

    IngestChannel channel();

    /** 结构性校验，返回错误描述列表；空列表表示通过 */
    List<String> validate(IngestContext ctx);

    /**
     * 写入业务服务。
     *
     * @param force 人工确认后强制入账（跳过依赖外部数据的软校验，硬校验仍然生效）
     * @return 处理结论；不要把可人工兜底的情况抛异常
     */
    DispatchResult dispatch(IngestContext ctx, boolean force);
}
