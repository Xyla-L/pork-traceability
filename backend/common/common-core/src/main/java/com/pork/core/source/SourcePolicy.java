package com.pork.core.source;

/**
 * 溯源环节的数据来源管控策略。
 * <p>
 * 存在的原因：区块链只能保证「上链之后改不了」，管不了「上链之前谁写的」。
 * 想要链上数据可信，必须让关键环节的数据来自不可被随手编造的通道——也就是设备采集。
 * 这个枚举就是给每个环节定「谁能写」的规矩。
 */
public enum SourcePolicy {

    /**
     * 设备独占：只接受设备自动采集写入，人工新增入口一律关闭。
     * <p>
     * 用于对结论真实性要求最高的环节（检验、盖章、瘦肉精、入场查验）。
     * 设备上报的异常数据仍然可以在接入层的「待人工处理」队列里由实名人员复核放行，
     * 复核动作带原始报文与处理人，属于留痕的补救，不是凭空补录。
     * <p>
     * 注意：设备独占只封「新增」，不封「作废」——录错的数据如果连作废都不许，只会在库里越积越脏。
     * 作废走独立路径，强制实名 + 理由 + 上链存证。
     */
    DEVICE_ONLY("设备独占", "该环节的数据只能由设备采集写入；设备上报异常的数据可在「待人工处理」队列由实名人复核入账"),

    /**
     * 设备优先：设备通道是主路径，人工入口是补录通道。
     * <p>
     * 用于「有设备、但设备覆盖不全」的环节（产地检疫出证、申请出栏、胴体批次归集）：
     * 比如并非所有屠宰场都装了分拣台、纸质检疫单会丢失需补录。人工写入强制实名，
     * 来源标注为人工并连同操作人一并上链，事后可追问是谁录的。
     */
    DEVICE_PREFERRED("设备优先", "设备自动采集为主；设备覆盖不到时可人工补录，写入强制实名并上链留痕"),

    /** 人工可录：业务上本来就以人为主（如门店收银、签收），设备只是辅助，但写入同样强制实名。 */
    MANUAL_ALLOWED("人工可录", "人工录入为正常路径，写入强制实名并可追溯");

    private final String label;
    private final String description;

    SourcePolicy(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** 该策略下人工新增入口是否被关闭 */
    public boolean manualBlocked() {
        return this == DEVICE_ONLY;
    }
}
