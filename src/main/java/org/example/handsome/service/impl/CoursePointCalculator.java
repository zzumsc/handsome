package org.example.handsome.service.impl;

import org.example.handsome.dao.CourseDao;
import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.Course.CourseStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

public class CoursePointCalculator {

    private static final BigDecimal DYNAMIC_WEIGHT_MAX_RATIO = new BigDecimal("1"); // 权重
    private static final BigDecimal NON_LINEAR_FACTOR = new BigDecimal("2"); // 非线性系数
    private static final BigDecimal MIN_POINT_RATIO = new BigDecimal("0.2"); // 最低积分
    private static final int SCALE = 3; // 保留3位小数

    public static List<Course> calculateLinkagePoints(List<Course> allPublishedCourses) {
        // 1. 筛选有效课程（已发布+最大人数>0+初始积分>0，避免初始积分为0导致权重异常）
        List<Course> validCourses = allPublishedCourses.stream()
                .filter(course -> CourseStatus.published.equals(course.getStatus()))
                .filter(course -> course.getMaxStudents() > 0)
                .filter(course -> course.getInitialPoints().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        if (validCourses.isEmpty()) {
            return validCourses;
        }

        // 2. 计算全局积分池（所有课程初始积分之和，总量守恒）
        BigDecimal globalPointPool = validCourses.stream()
                .map(Course::getInitialPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. 计算混合权重 = 课程初始积分（基础权重） + 动态权重（人数相关）
        calculateMixedWeights(validCourses);

        // 4. 按混合权重分配基础积分
        allocateBasePoints(validCourses, globalPointPool);

        // 5. 最低积分兜底（避免冷门课积分过低）
        applyMinPointFloor(validCourses);

        // 6. 二次平衡（确保总积分=全局积分池）
        rebalanceTotalPoints(validCourses, globalPointPool);

        return validCourses;
    }

    private static void calculateMixedWeights(List<Course> courses) {
        for (Course course : courses) {
            // 1. 基础权重 = 课程自身初始积分（替代原固定BASE_WEIGHT）
            BigDecimal baseWeight = course.getInitialPoints();

            // 2. 动态权重上限 = 基础权重×比例（如初始积分10→上限5，初始积分20→上限10）
            BigDecimal dynamicWeightMax = baseWeight.multiply(DYNAMIC_WEIGHT_MAX_RATIO);

            // 3. 人数比例（当前人数/最大人数，保留6位小数）
            BigDecimal studentRatio = new BigDecimal(course.getCurrentStudents())
                    .divide(new BigDecimal(course.getMaxStudents()), 6, RoundingMode.HALF_UP);

            // 4. 非线性动态权重（比例^2 × 上限，避免线性增长）
            BigDecimal dynamicWeight = studentRatio.pow(NON_LINEAR_FACTOR.intValue())
                    .multiply(dynamicWeightMax)
                    .setScale(6, RoundingMode.HALF_UP);

            // 5. 混合权重 = 基础权重 + 动态权重，存入实体
            course.setWeight(baseWeight.add(dynamicWeight));
        }
    }

    private static void allocateBasePoints(List<Course> courses, BigDecimal globalPointPool) {
        // 计算总混合权重
        BigDecimal totalMixedWeight = courses.stream()
                .map(Course::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 极端情况：总权重为0，平均分配积分
        if (totalMixedWeight.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal averagePoint = globalPointPool.divide(new BigDecimal(courses.size()), SCALE, RoundingMode.HALF_UP);
            courses.forEach(course -> course.setCurrentPoints(averagePoint));
            return;
        }

        // 正常分配：积分 = 全局池 × (课程权重/总权重)
        for (Course course : courses) {
            BigDecimal courseWeight = course.getWeight();
            BigDecimal allocatedPoint = globalPointPool.multiply(courseWeight)
                    .divide(totalMixedWeight, SCALE, RoundingMode.HALF_UP);
            course.setCurrentPoints(allocatedPoint);
        }
    }

    private static void applyMinPointFloor(List<Course> courses) {
        for (Course course : courses) {
            // 最低积分 = 课程自身初始积分×比例（逻辑不变，基础仍是初始积分）
            BigDecimal minPoint = course.getInitialPoints().multiply(MIN_POINT_RATIO)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            if (course.getCurrentPoints().compareTo(minPoint) < 0) {
                course.setCurrentPoints(minPoint);
            }
        }
    }

    private static void rebalanceTotalPoints(List<Course> courses, BigDecimal globalPointPool) {
        // 计算当前总积分（可能因最低积分兜底超过全局池）
        BigDecimal currentTotal = courses.stream()
                .map(Course::getCurrentPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deviation = currentTotal.subtract(globalPointPool);
        if (deviation.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // 计算可调积分总量（课程当前积分 - 最低积分，仅正数部分可调整）
        BigDecimal totalAdjustablePoints = courses.stream()
                .map(course -> {
                    BigDecimal minPoint = course.getInitialPoints().multiply(MIN_POINT_RATIO).setScale(SCALE, RoundingMode.HALF_UP);
                    return course.getCurrentPoints().subtract(minPoint).max(BigDecimal.ZERO);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAdjustablePoints.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // 按可调积分占比分配偏差（扣减超额部分）
        for (Course course : courses) {
            BigDecimal minPoint = course.getInitialPoints().multiply(MIN_POINT_RATIO).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal adjustable = course.getCurrentPoints().subtract(minPoint).max(BigDecimal.ZERO);
            BigDecimal adjustAmount = deviation.multiply(adjustable)
                    .divide(totalAdjustablePoints, SCALE, RoundingMode.HALF_UP);
            BigDecimal newPoint = course.getCurrentPoints().subtract(adjustAmount).max(minPoint);
            course.setCurrentPoints(newPoint);
        }
    }

    public static boolean triggerLinkage(List<Course> allPublishedCourses, CourseDao courseDao) {
        List<Course> updatedCourses = calculateLinkagePoints(allPublishedCourses);
        int updatedRows = courseDao.batchUpdateCurrentPoints(updatedCourses);
        return updatedRows == updatedCourses.size();
    }
}