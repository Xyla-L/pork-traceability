package com.pork.blockchain.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.blockchain.entity.ChainLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface ChainLedgerMapper extends BaseMapper<ChainLedger> {

    /** 账本里有、存证表里找不到对应记录的历史块（多为早期 record 重建前的遗留） */
    @Select("select count(*) from blockchain_ledger l where not exists"
            + " (select 1 from blockchain_record r where r.tx_hash = l.tx_hash)")
    long countOrphan();

    /** 某时间点之后新增的孤儿块：存量可以留着如实披露，新增就是链路真的断了 */
    @Select("select count(*) from blockchain_ledger l where l.chain_time >= #{since} and not exists"
            + " (select 1 from blockchain_record r where r.tx_hash = l.tx_hash)")
    long countOrphanSince(@Param("since") LocalDateTime since);

    /** 存证表说已上链，账本里却查不到对应块（悬空存证，验真必然失败） */
    @Select("select count(*) from blockchain_record r where r.status = 1 and (r.tx_hash is null or r.tx_hash = ''"
            + " or not exists (select 1 from blockchain_ledger l where l.tx_hash = r.tx_hash))")
    long countDanglingRecord();
}
