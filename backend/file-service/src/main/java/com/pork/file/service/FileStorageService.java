package com.pork.file.service;

import com.pork.file.entity.FileMetadata;
import com.pork.file.vo.FileInfoVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    FileInfoVO store(MultipartFile file, String bizRef, String uploader);
    StoredResource load(String fileId);
    StoredResource thumbnail(String fileId);
    void delete(String fileId);

    record StoredResource(FileMetadata metadata, Resource resource, String contentType) { }
}
