package com.pork.file.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.HashUtil;
import com.pork.file.entity.FileMetadata;
import com.pork.file.mapper.FileMetadataMapper;
import com.pork.file.service.FileStorageService;
import com.pork.file.vo.FileInfoVO;
import lombok.RequiredArgsConstructor;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf", "mp4");
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "pdf", "application/pdf", "mp4", "video/mp4");

    private final FileMetadataMapper metadataMapper;

    @Value("${file.storage-root:./data/trace-files}")
    private String storageRoot;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoVO store(MultipartFile file, String bizRef, String uploader) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.PARAM_MISSING, "上传文件不能为空");
        String original = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        String extension = extension(original);
        if (!EXTENSIONS.contains(extension)) throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的文件类型");
        long max = extension.equals("mp4") ? 50L * 1024 * 1024 : 10L * 1024 * 1024;
        if (file.getSize() > max) throw new BusinessException(ErrorCode.PARAM_ERROR, "文件大小超过限制");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取上传文件失败");
        }
        validateSignature(bytes, extension);
        String hash = HashUtil.sha256(bytes);
        FileMetadata duplicate = metadataMapper.selectOne(Wrappers.<FileMetadata>lambdaQuery()
                .eq(FileMetadata::getSha256, hash).last("LIMIT 1"));
        if (duplicate != null && Files.exists(resolve(duplicate.getStoredPath()))) return view(duplicate);

        String fileId = UUID.randomUUID().toString().replace("-", "");
        String relative = LocalDate.now() + "/" + fileId + "." + extension;
        Path destination = resolve(relative);
        try {
            Files.createDirectories(destination.getParent());
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件存储失败");
        }

        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(fileId);
        metadata.setOriginalName(original);
        metadata.setStoredPath(relative.replace('\\', '/'));
        metadata.setFileSize(file.getSize());
        metadata.setMimeType(MIME_BY_EXTENSION.get(extension));
        metadata.setSha256(hash);
        metadata.setUploader(uploader);
        metadata.setUploadTime(LocalDateTime.now());
        metadata.setBizRef(bizRef);
        try {
            metadataMapper.insert(metadata);
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
            throw e;
        }
        return view(metadata);
    }

    @Override
    public StoredResource load(String fileId) {
        FileMetadata metadata = find(fileId);
        Path path = resolve(metadata.getStoredPath());
        if (!Files.isRegularFile(path)) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "文件内容不存在");
        return new StoredResource(metadata, new FileSystemResource(path), metadata.getMimeType());
    }

    @Override
    public StoredResource thumbnail(String fileId) {
        StoredResource source = load(fileId);
        String extension = extension(source.metadata().getOriginalName());
        if (extension.equals("pdf")) throw new BusinessException(ErrorCode.PARAM_ERROR, "PDF 不支持缩略图");
        Path sourcePath = resolve(source.metadata().getStoredPath());
        Path thumbnail = resolve(".thumbnails/" + fileId + ".jpg");
        if (!Files.exists(thumbnail)) createThumbnail(sourcePath, thumbnail, extension);
        return new StoredResource(source.metadata(), new FileSystemResource(thumbnail), "image/jpeg");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String fileId) {
        FileMetadata metadata = find(fileId);
        metadataMapper.deleteById(metadata.getId());
        try {
            Files.deleteIfExists(resolve(metadata.getStoredPath()));
            Files.deleteIfExists(resolve(".thumbnails/" + fileId + ".jpg"));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败");
        }
    }

    private void createThumbnail(Path source, Path target, String extension) {
        try {
            Files.createDirectories(target.getParent());
            BufferedImage image;
            if (extension.equals("mp4")) {
                try (var channel = NIOUtils.readableChannel(source.toFile())) {
                    Picture picture = FrameGrab.createFrameGrab(channel).getNativeFrame();
                    image = AWTUtil.toBufferedImage(picture);
                }
            } else {
                image = ImageIO.read(source.toFile());
            }
            if (image == null) throw new IOException("Unsupported image data");
            int width = Math.min(320, image.getWidth());
            int height = Math.max(1, image.getHeight() * width / image.getWidth());
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, width, height, Color.WHITE, null);
            graphics.dispose();
            ImageIO.write(scaled, "jpg", target.toFile());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缩略图生成失败");
        }
    }

    private FileMetadata find(String fileId) {
        if (!StringUtils.hasText(fileId)) throw new BusinessException(ErrorCode.PARAM_MISSING, "fileId 不能为空");
        FileMetadata metadata = metadataMapper.selectOne(Wrappers.<FileMetadata>lambdaQuery().eq(FileMetadata::getFileId, fileId));
        if (metadata == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "文件不存在");
        return metadata;
    }

    private Path resolve(String relative) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) throw new BusinessException(ErrorCode.FORBIDDEN, "非法文件路径");
        return resolved;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void validateSignature(byte[] bytes, String extension) {
        boolean valid = switch (extension) {
            case "jpg", "jpeg" -> starts(bytes, 0xFF, 0xD8, 0xFF);
            case "png" -> starts(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "pdf" -> starts(bytes, 0x25, 0x50, 0x44, 0x46);
            case "mp4" -> bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
            default -> false;
        };
        if (!valid) throw new BusinessException(ErrorCode.PARAM_ERROR, "文件内容与扩展名不匹配");
    }

    private boolean starts(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((bytes[i] & 0xff) != signature[i]) return false;
        return true;
    }

    private FileInfoVO view(FileMetadata metadata) {
        return new FileInfoVO(metadata.getFileId(), metadata.getOriginalName(), metadata.getFileSize(),
                metadata.getMimeType(), metadata.getSha256(), "/api/v1/file/" + metadata.getFileId());
    }
}
