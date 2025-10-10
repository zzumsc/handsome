package org.example.handsome.service;

import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.DTO.Result;
import java.util.List;

public interface StudentCourseService {
    // 查看所有已发布/已结束的课程
    Result listAvailableCourses();

    // 选课（仅已发布课程）
    Result selectCourse(Long courseId);

    // 退课（返还80%积分）
    Result dropCourse(Long courseId);
}