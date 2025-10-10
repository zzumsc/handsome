package org.example.handsome.service;

import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.DTO.Result;

public interface AdminCourseService {
    // 新增课程
    Result addCourse(Course course);

    // 按ID删除课程
    Result deleteCourse(Long id);

    // 按ID查询课程详情
    Result getCourseById(Long id);

    // 更新课程信息
    Result updateCourse(Course course);

    // 开始选课（draft->published）
    Result startCourseSelection(Long courseId);

    // 结束选课（published->closed）
    Result endCourseSelection(Long courseId);

    // 按课程ID查询所有选课学生
    Result getStudentsByCourseId(Long courseId);


    Result getAllCourses(Integer page, Integer size, String keyword);
}