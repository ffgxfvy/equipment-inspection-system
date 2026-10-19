package com.renjie.inspection.security;

import com.renjie.inspection.common.ErrorCode;
import com.renjie.inspection.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 角色鉴权拦截器：处理 {@link RequireRole} 注解。
 *
 * <p>执行顺序在 {@link AuthInterceptor} 之后（见 WebConfig 里的 order），
 * 因此这里可以假定 {@link UserContext} 已经有值。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法上的注解优先，其次看类上的注解
        RequireRole annotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequireRole.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequireRole.class);
        }
        if (annotation == null) {
            return true;
        }

        String role = UserContext.get() == null ? null : UserContext.get().getRole();
        boolean allowed = role != null && Arrays.asList(annotation.value()).contains(role);
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    R.fail(ErrorCode.FORBIDDEN, "需要权限：" + String.join("/", annotation.value()))));
            return false;
        }
        return true;
    }
}
