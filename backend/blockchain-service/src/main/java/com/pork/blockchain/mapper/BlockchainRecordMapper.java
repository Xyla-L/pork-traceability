package com.pork.blockchain.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.blockchain.entity.BlockchainRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface BlockchainRecordMapper extends BaseMapper<BlockchainRecord> {

    /** 最老一条未完成存证的时间：超过容忍窗口还没上链，就是积压而不是「正在重试」 */
    @Select("select min(update_time) from blockchain_record where status in (0, 2)")
    LocalDateTime oldestUnfinishedTime();
}
