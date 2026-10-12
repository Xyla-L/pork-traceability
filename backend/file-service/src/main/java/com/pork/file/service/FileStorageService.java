package com.pork.file.service;

import com.pork.file.entity.FileMetadata;
import com.pork.file.vo.FileInfoVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    FileInfoVO store(MultipartFile file, String bizRef, String uploader);
    /**
     * 只查元信息、不读文件内容：供上游（如接入层）核对凭证 fileId 是否真实存在。
     * 文件被删或从未上传过都返回「不存在」，从而让「随手编一个 fileId 当凭证」无法通过。
     */
    FileInfoVO meta(String fileId);
    StoredResource load(String fileId);
    StoredResource thumbnail(String fileId);
    void delete(String fileId);

    record StoredResource(FileMetadata metadata, Resource resource, String contentType) { }
}
