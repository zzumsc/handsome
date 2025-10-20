package org.example.handsome.service.impl;

public class RedisKeyUtils {
    /** 课程剩余名额键前缀 */
    private static final String COURSE_REMAIN_KEY = "course:remain:%s";

    /** 学生积分键前缀 */
    private static final String STUDENT_POINT_KEY = "user:point:%s";

    /** 课程积分键前缀 */
    private static final String COURSE_POINT_KEY = "course:point:%s";

    /** 课程排队键前缀 */
    private static final String COURSE_QUEUE_KEY = "course:queue:%s";

    /** 选课标记键前缀 */
    private static final String SELECTED_FLAG_KEY = "selection:flag:%s:%s";

    /** 课程信息键前缀 */
    private static final String COURSE_INFO_KEY = "course:info:%s";

    public static String getCourseRemainKey(Long courseId) {
        return String.format(COURSE_REMAIN_KEY, courseId);
    }

    public static String getStudentPointKey(Long studentId) {
        return String.format(STUDENT_POINT_KEY, studentId);
    }

    public static String getCoursePointKey(Long courseId) {
        return String.format(COURSE_POINT_KEY, courseId);
    }

    public static String getSelectedFlagKey(Long studentId, Long courseId) {
        return String.format(SELECTED_FLAG_KEY, studentId, courseId);
    }

    public static String getCourseInfoKey(Long courseId) {
        return String.format(COURSE_INFO_KEY, courseId);
    }
}
