package com.renjie.inspection.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>把异常统一转成 {@link R} 结构，好处：
 * <ul>
 *   <li>前端不用处理 HTML 错误页，永远是 JSON；</li>
 *   <li>业务异常（BizException）与系统异常分开记日志——前者是 warn，后者是 error 带堆栈；</li>
 *   <li>参数校验失败时把具体哪个字段错了返回给前端，而不是笼统的"参数错误"。</li>
 * </ul>
 *
 * <p><b>为什么这里刻意不加 {@code @ResponseStatus}</b>
 *
 * <p>项目的约定是「HTTP 状态码恒为 200，业务结果放在响应体 code 里」。一开始我给这些
 * 处理器加了 {@code @ResponseStatus(BAD_REQUEST)}，让 HTTP 也返回 400——看起来更符合
 * REST 规范，但会造成一个实际问题：
 *
 * <p>前端 axios 拦截器对 HTTP 4xx 会走 <i>error</i> 分支，那里只能拿到状态码，
 * 于是界面显示的是"请求失败（400）"，而服务端精心写的
 * "结论为异常时，必须填写异常描述"这类提示被丢掉了。
 *
 * <p>所以统一成 HTTP 200 + 业务码。REST 规范性与"用户能看到有用的错误提示"之间，
 * 这个项目选了后者——**这是实际跑通前后端联调后才暴露出来的问题**，
 * 只看代码是发现不了的。
 *
 * <p>唯一例外是 500：那属于服务端故障，让 HTTP 也返回 500 便于监控和网关识别。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 客户端错误 → HTTP 200 + 业务码 ====================

    /** 业务异常：预期内的分支，记 warn 即可，不打堆栈避免污染日志 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return R.fail(ex.getErrorCode(), ex.getMessage());
    }

    /** @RequestBody 上的参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describeFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return R.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /** 表单/查询参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describeFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", detail);
        return R.fail(ErrorCode.BAD_REQUEST, detail);
    }

    /**
     * 请求体无法解析：JSON 语法错误、字段类型不匹配（比如给 int 字段传了字符串）。
     *
     * <p>没有这个处理器时会落到下面的 Exception 兜底，返回 500——
     * 那是把客户端的错误算到服务端头上，会让调用方误判并触发无意义的重试。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return R.fail(ErrorCode.BAD_REQUEST, "请求体格式有误，请确认是合法的 JSON 且字段类型正确");
    }

    /** 缺少必填的查询参数，例如 PUT /tasks/1/status 没传 status */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        return R.fail(ErrorCode.BAD_REQUEST, "缺少必填参数：" + ex.getParameterName());
    }

    /** 参数类型不匹配，例如把 ?pageNum=abc 传给 int 参数 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {} = {}", ex.getName(), ex.getValue());
        return R.fail(ErrorCode.BAD_REQUEST, "参数 " + ex.getName() + " 的取值类型不正确");
    }

    /** 请求方法不支持，例如用 DELETE 调只支持 GET 的接口 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return R.fail(ErrorCode.METHOD_NOT_ALLOWED,
                "该接口不支持 " + ex.getMethod() + " 方法，支持："
                        + String.join("/", ex.getSupportedMethods() == null
                        ? new String[]{"未知"} : ex.getSupportedMethods()));
    }

    /** 上传文件超过限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("上传文件过大: {}", ex.getMessage());
        return R.fail(ErrorCode.PAYLOAD_TOO_LARGE, "上传文件过大，单文件不能超过 10 MB");
    }

    /**
     * 访问了不存在的接口路径。
     *
     * <p>Spring Boot 3.2 起，未匹配到任何 Controller 的请求由静态资源处理器抛出
     * {@link NoResourceFoundException}；如果只靠 Exception 兜底就会返回 500，
     * 而正确语义是 404（调用方拼错了 URL，不是服务端故障）。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public R<Void> handleNoHandler(Exception ex, HttpServletRequest request) {
        log.warn("接口不存在: {} {}", request.getMethod(), request.getRequestURI());
        return R.fail(ErrorCode.NOT_FOUND, "接口不存在：" + request.getRequestURI());
    }

    // ==================== 服务端错误 → HTTP 500 ====================

    /**
     * 其余未预期异常：必须打完整堆栈，否则线上问题无法定位。
     *
     * <p>这是唯一保留真实 HTTP 状态码（500）的处理器——服务端故障要能被网关和监控
     * 直接识别出来，不能伪装成 200 藏在响应体里。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("未预期异常, uri={}", request.getRequestURI(), ex);
        return R.fail(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * 把一个字段错误翻译成能给用户看的中文提示。
     *
     * <p>三种情况分开处理：
     * <ol>
     *   <li><b>类型转换失败</b>（isBindingFailure）——Spring 的原始消息是
     *       "Failed to convert property value of type 'java.lang.String' ..."，
     *       这种英文技术细节不该直接返回给前端，改写成"参数 X 的取值类型不正确"；</li>
     *   <li><b>有自定义校验消息</b>——直接用（来自 DTO 上的注解，例如"异常描述最多 500 字"）；</li>
     *   <li>其余情况——用字段名拼一个兜底提示。</li>
     * </ol>
     */
    private static String describeFieldError(FieldError error) {
        if (error.isBindingFailure()) {
            return "参数 " + error.getField() + " 的取值类型不正确";
        }
        String message = error.getDefaultMessage();
        return (message != null && !message.isBlank())
                ? message
                : error.getField() + " 取值不合法";
    }
}
