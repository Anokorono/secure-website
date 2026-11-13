package com.example.website.common;

/**
 * 统一 API 返回结果
 */
public class ApiResult<T> {
    private int code;
    private String message;
    private T data;
    private String redirect;

    public static <T> ApiResult<T> success(String message) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.message = message;
        return r;
    }

    public static <T> ApiResult<T> success(String message, T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> ApiResult<T> fail(String message) {
        return fail(400, message);
    }

    public ApiResult<T> redirect(String url) {
        this.redirect = url;
        return this;
    }

    public ApiResult<T> data(T data) {
        this.data = data;
        return this;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getRedirect() { return redirect; }
    public void setRedirect(String redirect) { this.redirect = redirect; }
}
