package com.renjie.inspection.controller;

import com.renjie.inspection.common.R;
import com.renjie.inspection.dto.LoginRequest;
import com.renjie.inspection.dto.LoginResponse;
import com.renjie.inspection.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@Tag(name = "01. 认证", description = "登录获取 token")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "演示账号：admin/admin123（管理员），zhangsan/123456（巡检员）")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok("登录成功", authService.login(request));
    }
}
