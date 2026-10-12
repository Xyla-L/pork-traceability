package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分割车间监控快照：一条记录 = 一张照片。
 *
 * <h3>为什么不挂在 split_batch 上</h3>
 * {@code split_batch} 一行 = <b>一个部位</b>（猪后腿、五花…），而监控照片是
 * <b>车间工位在某个时刻的样子</b>，与「哪个部位」没有对应关系 —— 相机不认识部位。
 * 硬挂上去就会出现「这张照片属于哪个部位」这种没有答案的问题。
 * 而且分割批次是设备整份上报时一次性建出来的，事后往里塞照片还会污染已有记录。
 *
 * <h3>为什么单独建表而不是复用 file_metadata</h3>
 * {@code file_metadata} 是文件服务的通用台账（只有 upload_time、biz_ref），
 * 装不下「<b>谁在什么时刻拍的</b>」这件事：拍摄时刻（设备侧时间）与上传时刻不是一回事，
 * 车间 / 设备号也得有专门的列，否则查询只能靠 biz_ref 字符串拆解。
 *
 * <h3>保留策略</h3>
 * 相机是<b>周期上报</b>的，本地盘经不起无限增长 —— 每台相机只保留最近若干张，
 * 新的一张进来就把最老的连同文件一起淘汰（见 Service 的 prune 逻辑）。
 * 所以这张表<b>不是历史档案，是滚动窗口</b>。
 *
 * <h3>与「哪一次分割」的绑定（2026-10-12）</h3>
 * {@link #batchNo} / {@link #jobId} 把照片挂到具体一次分割上 —— 页面才可能
 * 「选中哪个批次就看哪一次分割的现场照片」。绑定信息来自工位终端（见字段注释），
 * 不是相机推断；未绑定的历史数据保持为 NULL。
 */
@Data
@TableName("workshop_snapshot")
public class WorkshopSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 拍摄设备（监控相机）编号；本来就是机器在记录，这里用设备号是对的 */
    private String deviceNo;

    /** 车间（相机安装位置，如「分割一号线」） */
    private String workshop;

    /**
     * 所属胴体批次号 —— 照片绑定到「哪一次分割」。
     * <p>
     * 相机自己不认识批次：这个值是<b>工位终端</b>（分割线扫码称重台）连同报文一起给的 ——
     * 一头白条刚上称，终端知道它在哪个批次里，顺手告诉相机。所以这里存批次号不是
     * 「让相机猜批次」，而是把终端已知的事实落到照片上。
     * <p>
     * 为空 = 未绑定（历史数据，或相机没有工位信息）—— 可空列不给业务含义型默认值。
     */
    private String batchNo;

    /**
     * 所属分割作业 id —— 精确到「哪一头白条」的绑定。
     * <p>
     * 由服务端按 {@link #batchNo} 反查该批次<b>最近一次</b>作业得到，所以相机报文里
     * 只需要批次号，不必知道作业号（作业号是系统自己生成的，设备本就不该编）。
     */
    private Long jobId;

    /**
     * 照片在文件服务里的 id。
     * <p>
     * 只存 id 不存 URL：文件服务的访问路径由它自己决定，存 URL 等于把它的路由规则
     * 复制到业务表里，将来换存储方式（本地盘 → 对象存储）就要改数据。
     */
    private String fileId;

    /** 拍摄时刻（设备侧时间，报文级）—— 不是入库时刻 */
    private LocalDateTime capturedTime;

    /** 数据来源：DEVICE=设备自动上传 */
    private String source;
    /** 来源设备号/单据号 */
    private String sourceRef;
    /** 设备原始报文留档 */
    private String rawPayload;

    private LocalDateTime createTime;
}
