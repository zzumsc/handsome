package org.example.handsome.service.impl;

import java.util.Random;

public class RandomUtil {
    // 生成指定长度的数字验证码
    public static String randomNumbers(int length) {
        if (length <= 0) return "";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}