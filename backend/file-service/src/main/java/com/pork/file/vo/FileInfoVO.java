package com.pork.file.vo;

public record FileInfoVO(String fileId, String originalName, long fileSize,
                         String mimeType, String sha256, String url) { }
