package org.example.handsome.service;

import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.DTO.Result;

import java.util.List;

public interface AdminCourseService {
    // 新增课程
    Result addCourse(Course course);

    // 按ID删除课程
    Result deleteCourse(Long id);

    // 按ID查询课程详情
    Result getCourseById(Long id);

    // 更新课程信息
    Result updateCourse(Course course);

    // 单个开始选课
    Result startCourseSelection(Long courseId);

    // 单个结束选课
    Result endCourseSelection(Long courseId);

    // 批量开始选课
    Result batchStartCourseSelection(List<Long> courseIds);

    // 批量结束选课（同步更新状态，异步单线程结算 + MQ入库）
    Result batchEndCourseSelection(List<Long> courseIds);

    // 按课程ID查询所有选课/竞价学生
    Result getStudentsByCourseId(Long courseId);

    // 分页查询所有课程（管理员视角）
    Result getAllCourses(Integer page, Integer size, String keyword);
}
