package com.petcare.common.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.config.FileUploadConfig;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.upload.ImageMagicBytesValidator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

/**
 * File upload controller for user and catalog images.
 * Stores files to local disk under {project-root}/uploads/.
 * Returns the accessible URL path.
 */
@RestController
@RequestMapping("/api/v1/upload")
public class FileUploadController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    @PostMapping
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件大小不能超过10MB");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只支持 JPG/PNG/GIF/WebP 格式");
        }

        // M1 安全修复：用魔数校验替代纯 Content-Type 信任。
        // 攻击者可伪造 Content-Type 头上传 SVG/HTML，靠魔数字节判断真实格式。
        if (!verifyMagicBytes(file, contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件内容与声明的类型不匹配");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        String subDir = "images";

        File dir = new File(FileUploadConfig.getUploadDir() + subDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File dest = new File(dir, fileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件上传失败");
        }

        String url = "/uploads/" + subDir + "/" + fileName;
        return ApiResponse.ok(Map.of("url", url));
    }

    /**
     * 读取文件头前 N 字节，用 {@link ImageMagicBytesValidator} 校验真实类型是否匹配声明的 MIME。
     * 防止伪造 Content-Type 上传可执行脚本（M1）。
     */
    private boolean verifyMagicBytes(MultipartFile file, String contentType) {
        int needed = ImageMagicBytesValidator.MAX_BYTES_NEEDED;
        byte[] head = new byte[needed];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(head);
        } catch (IOException e) {
            return false;
        }
        if (read <= 0) {
            return false;
        }
        byte[] actual = read == needed ? head : new byte[read];
        if (read < needed) {
            System.arraycopy(head, 0, actual, 0, read);
        }
        return ImageMagicBytesValidator.matches(contentType, actual);
    }
}
