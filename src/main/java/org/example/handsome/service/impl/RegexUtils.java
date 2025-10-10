package org.example.handsome.service.impl;

public class RegexUtils {
    // 手机号正则
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    // 邮箱正则
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$";

    public static boolean isPhoneInvalid(String phone) {
        return phone == null || !phone.matches(PHONE_REGEX);
    }

    public static boolean isEmailInvalid(String email) {
        return email == null || !email.matches(EMAIL_REGEX);
    }
}