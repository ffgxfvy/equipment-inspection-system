package com.renjie.inspection.common;

import lombok.Getter;

/**
 * 业务错误码。
 *
 * <p>刻意用枚举而不是散落的数字常量：调用方写 {@code ErrorCode.NOT_FOUND} 比写 {@code 404}
 * 更难写错，也让 IDE 能提示全部可用取值。
 *
 * <p><b>与 HTTP 状态码的关系</b>：本项目的约定是 HTTP 状态码恒为 200，
 * 业务结果放在响应体的 {@code code} 字段里（见 docs/api.md 的约定）。
 * 这样做的好处是前端只需在 axios 拦截器里判断一次 {@code code}；
 * 如果把 4xx 也映射成真实的 HTTP 状态码，前端会走错误分支，
 * 详细校验提示（"异常结论必须填写异常描述"之类）就拿不到了。
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数有误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据冲突"),
    PAYLOAD_TOO_LARGE(413, "上传内容过大"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
