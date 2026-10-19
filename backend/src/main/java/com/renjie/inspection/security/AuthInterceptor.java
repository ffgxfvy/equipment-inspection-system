package com.renjie.inspection.security;

import com.renjie.inspection.common.ErrorCode;
import com.renjie.inspection.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 JWT 并把用户放进 {@link UserContext}。
 *
 * <p>拦截器 vs 过滤器：拦截器能拿到 Spring 的 Handler 信息（比如要调哪个 Controller 方法），
 * 更适合做"接口级鉴权"；过滤器更靠前，适合做编码、跨域这类与业务无关的事。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.jwt.header:Authorization}")
    private String header;

    @Value("${app.jwt.prefix:Bearer }")
    private String prefix;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 浏览器预检请求直接放行，否则跨域会失败
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String raw = request.getHeader(header);
        if (raw == null || !raw.startsWith(prefix)) {
            writeUnauthorized(response, "缺少登录凭证");
            return false;
        }

        String token = raw.substring(prefix.length()).trim();
        try {
            UserContext.set(jwtUtil.parse(token));
            return true;
        } catch (Exception ex) {
            writeUnauthorized(response, ex.getMessage());
            return false;
        }
    }

    /**
     * 请求结束后必须清理 ThreadLocal。
     * 漏掉这一步会导致线程复用时的用户串号问题，且非常难排查。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);   // 业务码放在 body 里，前端统一处理
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(R.fail(ErrorCode.UNAUTHORIZED, message)));
    }
}
