package com.pork.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.file.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> { }
