package com.pork.file.controller;

import com.pork.core.result.Result;
import com.pork.file.service.FileStorageService;
import com.pork.file.vo.FileInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService storage;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileInfoVO> upload(@RequestPart("file") MultipartFile file,
                                     @RequestParam(required = false) String bizRef,
                                     @RequestHeader(value = "X-User-Id", required = false) String uploader) {
        return Result.success(storage.store(file, bizRef, uploader));
    }

    /**
     * 只读元信息，不返回文件内容。
     * <p>
     * 用途：上游（接入层 / 业务服务）在把 fileId 记入单据前，先核对这个文件是不是真的传过。
     * 已删除或编造的 fileId 会得到「文件不存在」，因此拿假凭证蒙混过关走不通。
     */
    @GetMapping("/{fileId}/meta")
    public Result<FileInfoVO> meta(@PathVariable String fileId) {
        return Result.success(storage.meta(fileId));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<org.springframework.core.io.Resource> preview(@PathVariable String fileId) {
        return response(storage.load(fileId), true);
    }

    @GetMapping("/{fileId}/thumbnail")
    public ResponseEntity<org.springframework.core.io.Resource> thumbnail(@PathVariable String fileId) {
        return response(storage.thumbnail(fileId), false);
    }

    @DeleteMapping("/{fileId}")
    public Result<Void> delete(@PathVariable String fileId) { storage.delete(fileId); return Result.success(); }

    private ResponseEntity<org.springframework.core.io.Resource> response(FileStorageService.StoredResource value, boolean originalName) {
        String filename = originalName ? value.metadata().getOriginalName() : value.metadata().getFileId() + ".jpg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(value.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .eTag('"' + value.metadata().getSha256() + '"')
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(value.resource());
    }
}
