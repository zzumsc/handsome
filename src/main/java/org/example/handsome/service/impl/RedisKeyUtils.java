package org.example.handsome.service.impl;

public class RedisKeyUtils {

    private RedisKeyUtils() {}

    // ====================== 学生积分 ======================
    /** 学生可用积分（放大1000倍的long值） */
    private static final String STUDENT_POINT_KEY = "user:point:%s";

    // ====================== 课程信息缓存 ======================
    /** 课程基本信息缓存 */
    private static final String COURSE_INFO_KEY = "course:info:%s";

    // ====================== 竞价选课相关 ======================
    /** 课程竞价Hash：field=userId, value=JSON{points,time} */
    private static final String COURSE_BID_KEY = "course:bid:%s";

    /** 课程当前竞价人数（冗余计数器，加速查询） */
    private static final String COURSE_BID_COUNT_KEY = "course:bid:count:%s";

    /** 课程已录取学生Set（存储往期已录取的studentId，防止重复竞价） */
    private static final String COURSE_ADMITTED_KEY = "course:admitted:%s";

    // ====================== 可用课程列表缓存 ======================
    public static final String COURSES_AVAILABLE_KEY = "courses:available";

    // ====================== 方法 ======================

    public static String getStudentPointKey(Long studentId) {
        return String.format(STUDENT_POINT_KEY, studentId);
    }

    public static String getCourseInfoKey(Long courseId) {
        return String.format(COURSE_INFO_KEY, courseId);
    }

    public static String getCourseBidKey(Long courseId) {
        return String.format(COURSE_BID_KEY, courseId);
    }

    public static String getCourseBidCountKey(Long courseId) {
        return String.format(COURSE_BID_COUNT_KEY, courseId);
    }

    public static String getCourseAdmittedKey(Long courseId) {
        return String.format(COURSE_ADMITTED_KEY, courseId);
    }
}
