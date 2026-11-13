package com.example.website.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器
 * 统一兜底：返回结构化 JSON（不泄露堆栈细节），防止信息泄露与白页。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 兜底异常处理：统一返回错误 JSON，不向客户端泄露内部异常信息（防信息泄露）。
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ApiResult<?> handleException(Exception e, HttpServletRequest request) {
        log.error("[全局异常] 请求 {} 处理异常", request.getRequestURI(), e);
        return ApiResult.fail(500, "服务器内部错误，请稍后再试");
    }
}
