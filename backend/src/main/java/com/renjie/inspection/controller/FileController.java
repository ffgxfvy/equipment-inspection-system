package com.renjie.inspection.controller;

import com.renjie.inspection.common.R;
import com.renjie.inspection.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传接口。
 */
@Tag(name = "07. 文件", description = "巡检现场照片上传")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "上传现场照片",
            description = "返回 relativeUrl，把它填入巡检记录的 photoUrl 字段即可。"
                    + "限制：仅图片格式，单文件不超过 10 MB。")
    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.save(file);
        return R.ok("上传成功", Map.of("relativeUrl", url));
    }
}
