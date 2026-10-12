package com.pork.core.source;

/**
 * 溯源关键环节登记表 —— 全项目唯一的「环节 ↔ 设备通道 ↔ 目标表 ↔ 来源策略」对应关系。
 * <p>
 * 各服务不再各自散落地判断"这个环节能不能人工录"，一律查这张表，
 * 避免出现「管理端按钮藏了但接口还能调」或「A 服务拦了 B 服务没拦」的漏口。
 * <p>
 * 目标表名与 {@code ingest-service} 的 IngestChannel 保持一致，两边对得上才算闭环。
 */
public enum TraceStage {

    // ⚠️ 声明顺序 = 业务发生顺序，即侧边栏「养殖 → 屠宰 → 分割配送 → 销售」自上而下的口径。
    // `SourcePolicyRegistry.list()` 直接按 values() 遍历输出，因此这份顺序决定了
    // 前端「来源策略」表格的展示次序——调整前请先确认与侧边栏一致。

    /** 养殖建档：耳标读写器，佩戴即建档 */
    TAG("TAG", "养殖建档（耳标）", "pig_individual", SourcePolicy.DEVICE_ONLY,
            "耳标佩戴由读写器自动建档，人工建档入口已删除——数据只认设备"),

    /** 免疫注射：智能连续注射器 */
    VACCINE("VACCINE", "免疫注射", "vaccine_record", SourcePolicy.DEVICE_ONLY,
            "注射器自动记录批号剂量，人工录入入口已删除——数据只认设备"),

    /**
     * 产地检疫出证：检疫证扫码登记终端。
     * <p>
     * 检疫结论由官方兽医作出、证由官方签发，扫码终端只是把证面上的信息（证号/耳标/机构/有效期/官方兽医）
     * 读进系统，属于「登记采集」而不是「结论采集」。人工出证入口保留——纸质单丢失、二维码磨损、
     * 跨省格式不一、批量补录这些情况永远存在，删掉人工会让业务断在检疫申报点。
     */
    QUARANTINE("QUARANTINE", "产地检疫出证", "quarantine_certificate", SourcePolicy.DEVICE_PREFERRED,
            "检疫证由官方兽医签发，扫码登记终端可自动登记证面信息；设备覆盖不到时可人工出证，写入强制实名并上链留痕"),

    /**
     * 申请出栏：出栏通道地磅（耳标识读 + 称重）。
     * <p>
     * 设备能证明「这头猪多重、什么时候称的」，证明不了「场里要不要卖、卖给谁」——
     * 目标屠宰场是去向声明，不是测量值。所以这一环是「设备优先」而不是「设备独占」：
     * 设备自动登记为主路径，报文里带了去向（预设默认场或取自检疫证目的地）就直接入账，
     * 没带就转「待人工确认」补录。<br>
     * 注意：审批（通过 / 驳回）不在本环节——那是人的责任行为，永远人工，不设设备通道。
     */
    EXIT_APPLY("EXIT_APPLY", "申请出栏", "slaughter_apply", SourcePolicy.DEVICE_PREFERRED,
            "出栏口地磅自动识别耳标并称重登记；目标屠宰场是去向声明，设备给不出时转人工补录"),

    /**
     * 入场查验：门禁地磅 + 耳标识读 + 检疫证核验。
     * <p>
     * 除了本环节的采集方式，还有两条业务前提：
     * <ol>
     *   <li>这头猪必须<b>已取得出栏审批</b>——未经审批的猪进厂，等于把出栏审批这道责任关卡绕过去了；</li>
     *   <li>这头猪的状态必须是<b>「在养」或「已出栏」</b>——「已屠宰」「异常」是终态，
     *       一头猪只能进厂一次。</li>
     * </ol>
     * 两条都在业务侧硬校验，设备上报会被直接拒绝，人工确认同样放不过去（不受 force 影响）。
     */
    ENTRY("ENTRY", "入场查验", "entry_inspection", SourcePolicy.DEVICE_ONLY,
            "查验结论直接决定生猪能否入厂，必须由闸口设备采集；且以该猪的出栏审批已通过为前提，"
                    + "已屠宰或异常的猪不得再入场"),

    /**
     * 瘦肉精快速检测：胶体金读数仪（宰前抽检，排在屠宰检验之前）。
     * <p>
     * 检测是<b>按批抽检</b>，结论落在「批」上：检出阳性时，该猪及同批次的猪都不放行——
     * 阳性记录照常入账留证，阻断由屠宰服务的 {@code RactopamineGuard} 统一判定，
     * 屠宰检验、胴体盖章、胴体批次创建三方在写入前各查一次。
     * <p>
     * 反过来，<b>未检也不放行</b>：批批检是屠宰厂的法定动作，检测记录就是「这批猪被检过」的凭证，
     * 查不到记录（自身没有、所属批次也没有）的猪同样被同一条判定拦在屠宰检验之外。
     */
    RACTOPAMINE("RACTOPAMINE", "瘦肉精检测", "ractopamine_test", SourcePolicy.DEVICE_ONLY,
            "检测结果涉及食品安全红线，仪器读数不得人工代填；按批抽检，一批检出阳性则整批不得进入屠宰检验及后续环节；"
                    + "查不到检测记录的猪同样不得进入屠宰检验及后续环节（未检不得屠宰）"),

    /**
     * 屠宰检验：工位终端，兽医判定。
     * <p>
     * 按《生猪屠宰检疫规程》分两段：<b>宰前检查</b>（屠宰前 2 小时内逐头临床检查，合格才准予屠宰）
     * 与<b>宰后同步检疫</b>（与屠宰操作同步，查体表头蹄、内脏、胴体、旋毛虫）——
     * 法规里的「宰后检疫」标准做法就是同步检疫，所以 {@code inspect_type} 的 2=宰后检验、
     * 3=宰后同步检验 是同一个环节的两种记录方式。
     * <p>
     * 宰后段以宰前检查合格为前提：查不到合格的宰前检查记录（没检过，或检了判不合格）一律不得进入，
     * 判定见屠宰服务的 {@code PreSlaughterGate}。
     */
    INSPECTION("INSPECTION", "屠宰检验", "slaughter_inspection", SourcePolicy.DEVICE_ONLY,
            "合格/不合格结论决定肉品去向，是整条链上最不能人工补录的一环；"
                    + "宰后检验以宰前检查合格为前提（未做宰前检查不得屠宰）"),

    /** 胴体盖章：自动盖章机 */
    STAMP("STAMP", "胴体盖章", "carcass_stamp", SourcePolicy.DEVICE_ONLY,
            "盖章是法定授权行为，设备执行、系统留痕，人工不得直接建章"),

    /**
     * 胴体批次归集：屠宰线胴体称重分拣台。
     * <p>
     * 「哪几头白条算一批」在现实中不是人挑出来的：白条从分拣线下来，识读挂钩标签、过一下磅，
     * 同一屠宰场同一天的白条自然就是一批。所以设备只报它测得到的<b>单头事实</b>
     * （耳标 / 胴体重 / 哪条线 / 什么时候），<b>批次边界由系统按「屠宰场 + 屠宰日」自动归集</b>，
     * 批次号依旧由系统生成、设备报文里不带——批次号是人手编不出也编不对的东西，
     * 让设备报文带批次边界只会造出跨场混批或一猪两批。
     * <p>
     * 写入前有三道硬校验：该猪<b>已完成胴体盖章</b>（章的放行凭证意义见屠宰服务的 {@code StampGuard}）、
     * 瘦肉精<b>无阳性且已检</b>、<b>未被其他胴体批次占用</b>（一头猪只能归一批）。
     * 没有分拣台的屠宰场仍可人工补录：人工建批强制实名并上链留痕。
     */
    BATCH("BATCH", "胴体批次归集", "carcass_batch", SourcePolicy.DEVICE_PREFERRED,
            "白条下线由分拣台识读挂钩标签并过磅，系统按「屠宰场 + 屠宰日」自动归集出批次，批次号由系统生成；"
                    + "无分拣台的场可人工建批次补录，写入强制实名并上链留痕"),

    /** 分割批次创建：分割线扫码称重台 */
    SPLIT("SPLIT", "分割批次创建", "split_batch", SourcePolicy.DEVICE_ONLY,
            "扫码称重台自动建批次，人工建批次入口已删除——数据只认设备"),

    /** 冷链温度采集 */
    TEMPERATURE("TEMPERATURE", "冷链温度采集", "temperature_log", SourcePolicy.DEVICE_ONLY,
            "温度由温湿度传感器自动采集，人工补测入口已删除——数据只认设备"),

    /** 门店签收 */
    RECEIPT("RECEIPT", "门店签收", "store_receipt", SourcePolicy.MANUAL_ALLOWED,
            "签收责任在门店人员，人工签收是正常路径"),

    /** 零售售出 */
    SALE("SALE", "零售售出", "retail_sale", SourcePolicy.MANUAL_ALLOWED,
            "收银环节天然以人为主，设备只做扫码辅助");

    /** 对应 IngestChannel 名称；为 null 表示该环节暂无设备通道 */
    private final String channel;
    private final String label;
    private final String targetTable;
    private final SourcePolicy policy;
    private final String note;

    TraceStage(String channel, String label, String targetTable, SourcePolicy policy, String note) {
        this.channel = channel;
        this.label = label;
        this.targetTable = targetTable;
        this.policy = policy;
        this.note = note;
    }

    public String channel() {
        return channel;
    }

    public String label() {
        return label;
    }

    public String targetTable() {
        return targetTable;
    }

    /** 代码内置的默认策略（可被配置项应急覆盖） */
    public SourcePolicy defaultPolicy() {
        return policy;
    }

    public String note() {
        return note;
    }

    /** 是否已有设备通道——没有设备通道的环节不能真的禁人工，否则业务直接断掉 */
    public boolean deviceCapable() {
        return channel != null;
    }

    /** 实际生效的策略：无设备通道的环节不允许 DEVICE_ONLY（否则人工也进不来，等于锁死业务） */
    public SourcePolicy effectivePolicy() {
        return deviceCapable() ? policy : SourcePolicy.MANUAL_ALLOWED;
    }

    public static TraceStage of(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 按目标业务表反查环节（供接入层、审计侧使用） */
    public static TraceStage ofTable(String table) {
        if (table == null) return null;
        for (TraceStage stage : values()) {
            if (stage.targetTable.equalsIgnoreCase(table.trim())) return stage;
        }
        return null;
    }

    /** 按设备通道反查环节（供接入层使用） */
    public static TraceStage ofChannel(String channel) {
        if (channel == null) return null;
        for (TraceStage stage : values()) {
            if (stage.channel != null && stage.channel.equalsIgnoreCase(channel.trim())) return stage;
        }
        return null;
    }
}
