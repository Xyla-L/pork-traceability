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
