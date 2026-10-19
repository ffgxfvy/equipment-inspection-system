package com.renjie.inspection.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>Service 层遇到"业务规则不允许"的情况（比如设备编号重复、任务已完成不能再提交记录）
 * 直接抛这个异常，由 {@link GlobalExceptionHandler} 统一转成响应，
 * 避免在每个 Controller 里写 try-catch。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException notFound(String what) {
        return new BizException(ErrorCode.NOT_FOUND, what + "不存在");
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }
}
