package com.pork.blockchain.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.blockchain.entity.BlockchainRecord;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface BlockchainRecordMapper extends BaseMapper<BlockchainRecord> { }
