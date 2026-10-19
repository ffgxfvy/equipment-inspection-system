package com.renjie.inspection;

import com.renjie.inspection.common.ErrorCode;
import com.renjie.inspection.common.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 统一响应体与错误码的单元测试。
 *
 * <p>这里守的是一条容易被忽略的约定：**HTTP 状态码恒为 200**，业务结果放在 body 的 code 里。
 * 如果哪天有人给错误处理器加了 {@code @ResponseStatus}，前端的 axios 拦截器就会走
 * error 分支，把"异常结论必须填写异常描述"这类提示丢掉，界面只会显示"请求失败（400）"。
 * 这类回归很难在手工测试中发现，所以固化成测试。
 */
class ApiResponseTest {

    @Test
    @DisplayName("成功响应带数据")
    void okWithData() {
        R<String> r = R.ok("hello");
        assertEquals(ErrorCode.SUCCESS.getCode(), r.getCode());
        assertEquals("hello", r.getData());
    }

    @Test
    @DisplayName("成功响应不带数据时 data 为 null")
    void okWithoutData() {
        R<Void> r = R.ok();
        assertEquals(200, r.getCode());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("失败响应携带枚举里的业务码与消息")
    void failWithErrorCode() {
        R<Void> r = R.fail(ErrorCode.NOT_FOUND);
        assertEquals(404, r.getCode());
        assertEquals(ErrorCode.NOT_FOUND.getMessage(), r.getMessage());
    }

    @Test
    @DisplayName("失败响应可以用自定义消息覆盖默认消息")
    void failWithCustomMessage() {
        R<Void> r = R.fail(ErrorCode.BAD_REQUEST, "结论为异常时，必须填写异常描述");
        assertEquals(400, r.getCode());
        assertEquals("结论为异常时，必须填写异常描述", r.getMessage());
    }

    @Test
    @DisplayName("错误码取值符合约定")
    void errorCodesAreStable() {
        assertEquals(200, ErrorCode.SUCCESS.getCode());
        assertEquals(400, ErrorCode.BAD_REQUEST.getCode());
        assertEquals(401, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals(403, ErrorCode.FORBIDDEN.getCode());
        assertEquals(404, ErrorCode.NOT_FOUND.getCode());
        assertEquals(405, ErrorCode.METHOD_NOT_ALLOWED.getCode());
        assertEquals(409, ErrorCode.CONFLICT.getCode());
        assertEquals(500, ErrorCode.INTERNAL_ERROR.getCode());
    }
}
