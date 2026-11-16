package com.pork.breeding.service;

import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.VaccineBatch;

import java.time.LocalDateTime;

/**
 * 免疫批次：把逐头免疫记录归到「一次免疫作业」这张单据上。
 * <p>
 * 归批规则固定为「同场 + 同疫苗名称 + 同生产批号 + 同一天」——这与改造前页面分组的口径逐字一致，
 * 区别只是从「查询时算」变成「写入时存」，于是批次号可以被引用、可以上链、可以在下钻里点开。
 */
public interface VaccineBatchService {

    /**
     * 找或建免疫批次并挂上这头猪（幂等：同一头猪重复上报不会产生第二个成员）。
     * 设备逐头上报因此不需要人工先建单。
     */
    VaccineBatch attach(Long farmId, String vaccineName, String prodBatchNo, String manufacturer,
                        LocalDateTime injectTime, String operator, String source, String sourceRef,
                        PigIndividual pig);

    /** 作废一头猪在这次免疫里的记录时，同步成员的作废标记（成员仍保留，便于追溯「它本来在名单里」） */
    void markMemberVoid(Long batchId, Long pigId);

    /** 重算并回写成员数（成员只增不删，作废也计入，作废数另由查询统计） */
    void refreshCount(Long batchId);
}
