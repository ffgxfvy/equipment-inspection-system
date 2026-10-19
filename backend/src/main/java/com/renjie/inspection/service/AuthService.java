package com.renjie.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.renjie.inspection.common.BizException;
import com.renjie.inspection.common.ErrorCode;
import com.renjie.inspection.dto.LoginRequest;
import com.renjie.inspection.dto.LoginResponse;
import com.renjie.inspection.entity.SysUser;
import com.renjie.inspection.mapper.SysUserMapper;
import com.renjie.inspection.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务。
 *
 * <p>密码用 BCrypt 校验（不自己实现哈希比较），因为它内建了加盐与可调计算成本，
 * 且比较过程是恒定时间的，能抵抗时序攻击。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;

    /** 只作为编码器用，不注册成 Bean 以免影响其他安全配置 */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));

        // 用户名不存在与密码错误返回同一句提示，避免暴露"哪些账号存在"
        if (user == null || !ENCODER.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败, username={}", request.getUsername());
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "该账号已被停用，请联系管理员");
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        log.info("登录成功, username={}, role={}", user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getRealName(), user.getRole(), jwtUtil.getExpireMillis());
    }

    /** 供注册/改密使用；演示项目未开放自助注册，保留方法便于扩展 */
    public static String encodePassword(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }
}
