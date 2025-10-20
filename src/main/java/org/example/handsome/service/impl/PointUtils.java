package org.example.handsome.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;


public class PointUtils {

    public static final int SCALE_FACTOR = 1000;

    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    //积分放大
    public static long scaleUp(BigDecimal point) {
        if (point == null) return 0L;
        return point.multiply(new BigDecimal(SCALE_FACTOR))
                .setScale(0, ROUNDING_MODE)
                .longValue();
    }

    //积分缩小
    public static BigDecimal scaleDown(Long scaledPoint) {
        if (scaledPoint == null) return BigDecimal.ZERO;
        return new BigDecimal(scaledPoint)
                .divide(new BigDecimal(SCALE_FACTOR), 3, ROUNDING_MODE);
    }
}