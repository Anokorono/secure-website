package com.example.website.util;

import java.util.regex.Pattern;

/**
 * 密码强度校验工具
 * 规则：长度不少于8位，必须同时包含大写字母、小写字母和数字
 */
public final class PasswordValidator {

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private PasswordValidator() {}

    /**
     * 校验密码强度
     * @return 错误信息，null 表示通过
     */
    public static String validate(String password) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }
        if (password.length() < 8) {
            return "密码长度不能少于8位";
        }
        if (password.length() > 64) {
            return "密码长度不能超过64位";
        }
        if (!UPPER.matcher(password).find()) {
            return "密码必须包含大写字母";
        }
        if (!LOWER.matcher(password).find()) {
            return "密码必须包含小写字母";
        }
        if (!DIGIT.matcher(password).find()) {
            return "密码必须包含数字";
        }
        return null;
    }

    /**
     * 校验用户名（仅字母数字下划线，3-20位）
     */
    public static String validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "用户名不能为空";
        }
        if (username.length() < 3 || username.length() > 20) {
            return "用户名长度需在3-20位之间";
        }
        if (!Pattern.matches("^[A-Za-z0-9_]+$", username)) {
            return "用户名只能包含字母、数字和下划线";
        }
        return null;
    }
}
