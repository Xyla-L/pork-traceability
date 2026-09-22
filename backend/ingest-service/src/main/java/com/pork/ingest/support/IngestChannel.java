package com.pork.ingest.support;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;

/** 接入通道：一条通道对应一类设备、一张业务表 */
public enum IngestChannel {
    /** 冷链温度采集 → temperature_log */
    TEMPERATURE("temperature_log"),
    /** 入场查验（门禁地磅 + 耳标识读 + 检疫证核验） → entry_inspection */
    ENTRY("entry_inspection"),
    /** 瘦肉精快速检测（胶体金读数仪） → ractopamine_test */
    RACTOPAMINE("ractopamine_test"),
    /** 门店收银 POS：扫码激活 + 售出 → retail_sale */
    SALE("retail_sale"),
    /** 门店签收 PDA → store_receipt */
    RECEIPT("store_receipt"),
    /** 养殖建档：耳标读写器，佩戴即建档 → pig_individual */
    TAG("pig_individual"),
    /** 免疫注射：智能连续注射器（记录剂量/时间/批号） → vaccine_record */
    VACCINE("vaccine_record"),
    /** 屠宰检验工位终端：兽医判定 + 终端录入（判定权在人，设备只当"笔"） → slaughter_inspection */
    INSPECTION("slaughter_inspection"),
    /** 胴体自动盖章机：检验合格后自动执行盖章动作 → carcass_stamp */
    STAMP("carcass_stamp"),
    /** 分割线扫码称重台：扫白条钩标签自动建分割批次 → split_batch */
    SPLIT("split_batch");

    private final String targetTable;

    IngestChannel(String targetTable) {
        this.targetTable = targetTable;
    }

    public String targetTable() {
        return targetTable;
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
}
