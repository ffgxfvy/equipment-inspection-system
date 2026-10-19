package com.renjie.inspection.service;

import com.renjie.inspection.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 现场照片存储服务。
 *
 * <p>三处安全性考虑：
 * <ol>
 *   <li><b>不允许用户决定文件名</b>——原名只用来取扩展名，实际文件名是 UUID，
 *       否则 {@code ../../etc/passwd} 这类文件名会造成路径穿越；</li>
 *   <li><b>按日期分目录</b>，避免单个目录下文件过多导致文件系统性能下降；</li>
 *   <li><b>校验扩展名白名单</b>，不依赖 Content-Type（客户端可以伪造）。</li>
 * </ol>
 *
 * <p>生产环境建议改为对象存储（OSS / MinIO），本类只需替换 save 的实现。
 */
@Slf4j
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS =
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final long MAX_SIZE = 10 * 1024 * 1024L;
    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path rootDir;

    public FileStorageService(@Value("${app.upload.dir:./uploads}") String uploadDir) {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + rootDir, e);
        }
        log.info("上传目录: {}", rootDir);
    }

    /**
     * 保存文件，返回可直接写入巡检记录的相对 URL（形如 /files/2026/10/xxx.jpg）。
     */
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.badRequest("文件大小不能超过 10 MB");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BizException.badRequest("只支持图片格式：" + String.join("/", ALLOWED_EXTENSIONS));
        }

        String relativeDir = LocalDate.now().format(DIR_FORMAT);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDir = rootDir.resolve(relativeDir).normalize();

        // 二次校验：拼出来的路径必须仍在根目录内
        if (!targetDir.startsWith(rootDir)) {
            throw BizException.badRequest("非法的存储路径");
        }

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetDir.resolve(fileName));
        } catch (IOException e) {
            log.error("保存上传文件失败", e);
            throw new BizException(com.renjie.inspection.common.ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }

        return "/files/" + relativeDir + "/" + fileName;
    }

    public Path getRootDir() {
        return rootDir;
    }

    private String extensionOf(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
