package com.renjie.inspection.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应。
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String role;
    /** token 有效期（毫秒），前端据此决定何时提示重新登录 */
    private Long expire;
}
