package com.pork.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_metadata")
public class FileMetadata {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileId;
    private String originalName;
    private String storedPath;
    private Long fileSize;
    private String mimeType;
    private String sha256;
    private String uploader;
    private LocalDateTime uploadTime;
    private String bizRef;
}
