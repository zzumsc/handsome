package org.example.handsome.controller;

import jakarta.annotation.Resource;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.service.StudentCourseService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/student/courses")
public class StudentCourseController {

    @Resource
    private StudentCourseService studentCourseService;

    // 查看所有可选择/已结束的课程
    @GetMapping
    public Result listAvailableCourses() {
        return studentCourseService.listAvailableCourses();
    }

    // 选课
    @PostMapping("/{courseId}/{studentId}/select")
    public Result selectCourse(@PathVariable Long courseId,@PathVariable Long studentId) {
        return studentCourseService.selectCourse(courseId,studentId);
    }

    // 退课
    @PostMapping("/{courseId}/drop")
    public Result dropCourse(@PathVariable Long courseId) {
        return studentCourseService.dropCourse(courseId);
    }
}