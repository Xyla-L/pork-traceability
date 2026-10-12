package com.pork.ingest.support;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/** 接入通道：一条通道对应一类设备、一张业务表 */
public enum IngestChannel {
    /** 冷链温度采集 → temperature_log */
    TEMPERATURE("temperature_log", "冷链温度采集"),
    /** 入场查验（门禁地磅 + 耳标识读 + 检疫证核验） → entry_inspection */
    ENTRY("entry_inspection", "入场查验"),
    /** 瘦肉精快速检测（胶体金读数仪） → ractopamine_test */
    RACTOPAMINE("ractopamine_test", "瘦肉精检测"),
    /** 门店收银 POS：扫码激活 + 售出 → retail_sale */
    SALE("retail_sale", "门店收银"),
    /** 门店签收 PDA → store_receipt */
    RECEIPT("store_receipt", "门店签收"),
    /** 养殖建档：耳标读写器，佩戴即建档 → pig_individual */
    TAG("pig_individual", "养殖建档（耳标）"),
    /** 免疫注射：智能连续注射器（记录剂量/时间/批号） → vaccine_record */
    VACCINE("vaccine_record", "免疫注射"),
    /** 产地检疫出证：检疫证扫码登记终端（扫纸质证二维码带出证号/耳标/机构/有效期） → quarantine_certificate */
    QUARANTINE("quarantine_certificate", "产地检疫出证"),
    /** 出栏称重登记：出栏通道地磅（耳标识读 + 称重，猪走出栏口即登记） → slaughter_apply */
    EXIT_APPLY("slaughter_apply", "出栏称重登记"),
    /** 屠宰检验工位终端：兽医判定 + 终端录入（判定权在人，设备只当"笔"） → slaughter_inspection */
    INSPECTION("slaughter_inspection", "屠宰检验（工位终端）"),
    /** 胴体自动盖章机：检验合格后自动执行盖章动作 → carcass_stamp */
    STAMP("carcass_stamp", "胴体自动盖章"),
    /** 屠宰线胴体称重分拣台：白条下线识读挂钩标签并过磅，系统按「屠宰场 + 屠宰日」自动归集 → carcass_batch */
    BATCH("carcass_batch", "胴体批次（分拣台）"),
    /** 分割线扫码称重台：扫白条钩标签自动建分割批次 → split_batch */
    SPLIT("split_batch", "分割批次（扫码称重）"),
    /**
     * 订单转入（商家选购端 / 外部 ERP、OMS）：只收「要什么货、发去哪家店、什么时候要」，
     * 转成一张<b>待排车的</b>发货计划。
     * <p>
     * <b>与其它通道的本质差别：它们落的都是「已经发生的事实」，这条落的是「决策」</b>
     * —— 排车还没做，车、司机、发车时间都还没有。所以这条通道<b>不做任何业务判定</b>：
     * 货发去哪、怎么凑批次由配送服务按可发运库存算，凑不齐转人工。
     * <p>
     * 三条不能破的界线：订单 ≠ 计划（它只是计划的<b>来源之一</b>，运单上游永远是计划）；
     * 不复制订单（权威副本在外部系统，本系统只留 {@code order_no}）；
     * <b>字段层面就不收金额</b>（不是事后不接支付）。
     * 同一 {@code bizKey}（订单号）重复推送幂等 —— 外部系统重推是常态。
     */
    ORDER("delivery_plan", "订单转入（商家选购）"),
    /**
     * 装车口识别终端（车牌识别相机 + 扫码枪）：
     * 只提供「车牌 / 装了哪几批」两个现场事实，系统据此倒查待发货计划并自动生成运单。
     * <p>
     * 这是唯一一条<b>不产生业务判定</b>的通道：目的地门店由计划决定，设备无权也无需知道。
     * 匹配不上（货不属于任何计划、车牌与计划安排不符）一律转人工，
     * 绝不替人猜目的地 —— 猜错就是货发错店。写入目标是运单。
     * <p>
     * <b>这条通道上没有读卡器</b>（2026-10-10 起）：现场只在上车那一刻刷一次卡，
     * 司机身份由 {@link #DEPART} 确认。所以这里的司机<b>允许留空</b>（装车时司机还没上车，
     * 也没人能替他证明），报文里<b>不该出现卡号</b> —— 出现即说明设备接错点位，按结构性拒绝挡下。
     * 「货要走了责任必须落到具体人」这条红线挪到了发车那端，没有破。
     */
    LOAD("cold_chain_transport", "装车发运（装车口）"),
    /**
     * 发车确认（车载终端）：司机上车刷一下卡即确认发车。
     * <p>
     * 装车口已经能自动建单，但建完停在 {@code status=1 待发车}，发车仍要人去点按钮 ——
     * 自动化断在最后一步。这条通道补的就是这一步：车内终端读卡 → 系统按<b>车牌</b>倒查
     * 该车名下「待发车」的运单 → 推进为「运输中」并写现场发车时刻。
     * <p>
     * 与 LOAD 同源的三条口径：
     * <ul>
     *   <li><b>设备只报「我（某车）发车了」</b>，运单号由系统倒查 —— 扫码/刷卡是「确认」不是「选择」；</li>
     *   <li><b>发车不产生业务判定</b>：哪台车该发、发给谁，早就在运单里定死了；</li>
     *   <li><b>车牌只是找车的凭据、不是责任人</b>：司机必须由卡号换出具体的人，
     *       换不出人一律转人工（「设备 ≠ 人」）。</li>
     * </ul>
     * <b>全系统产生「司机本人确认」（{@code driver_source=CARD}）的只有两处</b>：这里（上车刷卡），
     * 以及到站刷卡确认（{@link #ARRIVE}）。两处是<b>同一台车载终端</b>的两个动作
     * （见 {@link #deviceGroup()}）—— 现场只有一台设备、一个读卡头，所以设备台账里
     * 一台车也只登记一条设备，不为第二个角色再配一把密钥。
     * 装车口没有读卡器，现场只在上车这一刻刷一次卡 —— 于是这条通道同时兼任
     * 「把责任落到具体人」的那颗闸门：没有司机的运单发不出去（人工「立即发车」也走同一条闸门）。
     * 该车牌名下没有待发车运单、或同时有<b>多张</b>待发车运单（不敢猜是哪一趟）都转人工。
     * 写入目标是运单（推进状态，不新增事实）。
     */
    DEPART("cold_chain_transport", "发车确认（车载终端）"),
    /**
     * 到达确认（车载终端，司机到站刷卡）。
     * <p>
     * 发车已经设备化，到达却还要人点按钮 —— 不对称，且「到了没」本来就是<b>位置事实</b>
     * （车在不在这个卸货点，人反而会忘了点）。所以照 DEPART 的写法补一条：设备报<b>现场事实</b>
     * （哪台车、司机刷的是哪张卡、什么时刻），运单与站点由系统按车牌<b>倒查与推导</b> ——
     * 设备无权挑站，更无权决定货卸在哪。
     * <p>
     * <b>报文不报「卸货门店」（2026-10-11 去掉）</b>：车载终端不是门店收货区的固定设备，
     * 读得到车牌/卡号/时刻/GPS，<b>读不到门店编码</b>（那是 ERP/OMS 的主数据）。
     * 而这件事本来也不该由设备回答：车牌把运单锁到唯一一张「未结束」的单子，
     * 站点清单由装车扫码顺序决定，所以「当前第一个未到达的站」在服务端唯一确定 ——
     * 报文报不了站，也就报不错站。
     * <p>
     * <b>到达的粒度是「卸货门店」，不是「批次」</b>：一车装了 12 批货送 3 家店就是 3 次到达，
     * 同店多批只到一次。判据是卸货门店数，与车厢里装了几批货无关。
     * <p>
     * <b>到达必须由司机本人刷卡确认</b>（2026-10-11 起）：与发车是<b>同一台车载终端</b>，
     * 卡在司机手上、由机器读出，所以「谁把货送到这一站」第一次有了责任人。
     * <p>
     * <b>红线分两档判（2026-10-11 收紧）</b> —— 分档的依据是「这条该不该由人来收拾」：
     * <ul>
     *   <li><b>一律转人工</b>（条件没到位 / 无处可记，人得去补或去核对）：该车牌名下没有
     *       <b>还没结束</b>的运单（运输中 2 / 待签收 3），或同时有多张（不敢猜是哪一趟）；
     *       站点台账为空，或<b>所有站点都已到达</b>（重复上报）；<b>没有卡号、卡换不出人</b>
     *       （没备案 / 司机已停用 —— 配置待补，司机重刷也过不去）。</li>
     *   <li><b>直接已拒绝</b>：<b>刷出来的人 ≠ 运单安排的司机</b>。现场看就是「刷错卡 / 上错车」，
     *       <b>司机重刷即可自愈</b>，不需要调度决策；回执 {@code retryable=false} 正好告诉
     *       车载终端「这条别重推、请重新刷卡」（发车侧同类比对仍判转人工 —— 那里比对的是
     *       装车时人工预填的司机，可信度低，需人来核对）。</li>
     * </ul>
     * 写入目标是<b>站点</b>，不是运单 —— 这是唯一一条写 {@code transport_stop} 的通道。
     */
    ARRIVE("transport_stop", "到达确认（车载终端刷卡）"),
    /**
     * 分割车间监控（摄像头）：定时拍一张车间现场照片。
     * <p>
     * <b>与其它所有通道的本质差别：它不产生溯源链上的事实，只是一份现场影像快照。</b>
     * 照片回答的是「这个时刻车间是什么样」，不回答「这头猪切出了什么部位」——
     * 所以它<b>不参与任何业务判定</b>（不校验批次、不校验瘦肉精），只落一张独立的时间戳快照表；
     * 也<b>不进 {@link com.pork.core.source.TraceStage}</b>：它不是溯源环节，不参与来源管控，
     * 加进去只会让「来源策略」表多一行无意义记录。
     * <p>
     * 上报是<b>两段式</b>：设备先把图片 POST 给文件服务换回 {@code fileId}，
     * 再把 {@code {fileId, capturedTime, workshop}} 报进来 —— 图片本身不进报文
     * （接入层单条报文 64KB 上限，塞图片必然超限）。接入层会调文件服务核对 fileId 真实存在，
     * 与免疫注射（接种凭证）、产地检疫（检疫证照片）用的是同一套防伪。
     * <p>
     * <b>失败不入人工队列</b>：照片是周期性快照，丢一张不影响任何追溯结论。
     * 凭证不存在（编造 fileId）判「已拒绝」；文件服务抖动判「已丢弃」——
     * 照片按分钟级产生，转人工只会让待人工队列瞬间被噪声淹没。
     * <p>
     * <b>保留策略收在业务侧</b>：每台相机只留最近若干张，新的一张进来就淘汰最老的
     * （连同文件一起删），否则本地盘会被定时照片撑爆。
     */
    SPLIT_SNAPSHOT("workshop_snapshot", "分割车间监控");

    /**
     * 同一台物理终端承担多个通道时，它们归入同一个组 —— 全系统只有一种情形：
     * <b>车载终端</b>（驾驶室里的读卡头 + 显示屏 + T-Box）既确认<b>发车</b>也确认<b>到站</b>。
     * <p>
     * 除这一组外，其余通道都是「一台设备一条通道」：温度探头只报温度、地磅只报过磅，
     * 谁也不能替谁报。所以这里用枚举写死而不是做成配置 —— 这是设备能力，不是运营参数。
     */
    private static final String GROUP_ONBOARD = "ONBOARD";

    private final String targetTable;
    private final String label;

    IngestChannel(String targetTable, String label) {
        this.targetTable = targetTable;
        this.label = label;
    }

    /**
     * 本通道归属的「同一台终端」分组；{@code null} 表示这台设备只干这一件事。
     * <p>
     * 台账里仍然是<b>一台设备一行</b>：{@code DEPART-001} 那行既是发车终端也是到站终端，
     * 不为第二个角色再登记一行 —— 现场只有一台设备、一把密钥，登记两行只会离现实更远，
     * 而且「这台车上有几个终端」也就看不清了。
     */
    public String deviceGroup() {
        return switch (this) {
            case DEPART, ARRIVE -> GROUP_ONBOARD;
            default -> null;
        };
    }

    /** 两个通道是否由同一台终端承担（报文说清这次是哪个动作，设备身份仍是同一台） */
    public boolean sameDeviceAs(IngestChannel other) {
        return other != null && deviceGroup() != null && deviceGroup().equals(other.deviceGroup());
    }

    /**
     * 某个通道名对应「同一台终端能上报的全部通道名」（含自身，按枚举声明顺序 = 业务顺序）。
     * <p>
     * 设备台账按通道过滤时用它：问「能确认到站的设备有哪些」，答案里必须有那些
     * <b>登记为发车终端、但同时也承担到站刷卡</b>的车载终端 —— 漏掉同组，页面上就只剩
     * 一台设备可选，「合并」也就白合了。认不出的通道名原样返回，不抛异常（展示层口径）。
     */
    public static List<String> deviceChannelGroup(String name) {
        if (name == null || name.isBlank()) return List.of();
        IngestChannel self;
        try {
            self = valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return List.of(name);
        }
        String group = self.deviceGroup();
        if (group == null) return List.of(self.name());
        List<String> names = new ArrayList<>();
        for (IngestChannel c : values()) {
            if (group.equals(c.deviceGroup())) names.add(c.name());
        }
        return names;
    }

    public String targetTable() {
        return targetTable;
    }

    /** 通道中文名。展示层只认这一个来源，避免各 VO 各写一份映射、漏掉通道就露出英文枚举名 */
    public String label() {
        return label;
    }

    /** 通道名与设备台账、业务表来源标记一一对应，未知通道直接拒绝 */
    public static IngestChannel of(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "通道(channel)不能为空");
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的接入通道: " + name);
        }
    }

    /**
     * 通道中文名的宽松取值，供展示层（VO）使用：认不出就原样返回，不抛异常。
     * 历史数据里出现未知通道时，界面宁可显示原值也不要因为翻译失败打不开列表。
     */
    public static String labelOf(String name) {
        if (name == null || name.isBlank()) return name;
        try {
            return valueOf(name.trim().toUpperCase()).label;
        } catch (IllegalArgumentException e) {
            return name;
        }
    }
}
