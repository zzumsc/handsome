package org.example.handsome.controller;

import jakarta.annotation.Resource;
import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.service.AdminCourseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/courses")
public class AdminCourseController {

    @Resource
    private AdminCourseService courseService;

    // 新增课程
    @PostMapping
    public Result addCourse(@RequestBody Course course) {
        return courseService.addCourse(course);
    }

    // 按ID删除课程
    @DeleteMapping("/{id}")
    public Result deleteCourse(@PathVariable Long id) {
        return courseService.deleteCourse(id);
    }

    // 按ID查询课程详情
    @GetMapping("/{id}")
    public Result getCourseById(@PathVariable Long id) {
        return courseService.getCourseById(id);
    }

    // 更新课程信息
    @PutMapping("/{id}")
    public Result updateCourse(
            @PathVariable Long id,
            @RequestBody Course course) {
        course.setId(id);
        return courseService.updateCourse(course);
    }

    // 单个开始选课（发布课程）
    @PutMapping("/{id}/start")
    public Result startCourseSelection(@PathVariable Long id) {
        return courseService.startCourseSelection(id);
    }

    // 单个结束选课
    @PutMapping("/{id}/end")
    public Result endCourseSelection(@PathVariable Long id) {
        return courseService.endCourseSelection(id);
    }

    // 批量开始选课
    @PutMapping("/batch/start")
    public Result batchStartCourseSelection(@RequestBody List<Long> courseIds) {
        return courseService.batchStartCourseSelection(courseIds);
    }

    // 批量结束选课
    @PutMapping("/batch/end")
    public Result batchEndCourseSelection(@RequestBody List<Long> courseIds) {
        return courseService.batchEndCourseSelection(courseIds);
    }

    // 按课程ID查询选课学生
    @GetMapping("/{id}/students")
    public Result getStudentsByCourseId(@PathVariable Long id) {
        return courseService.getStudentsByCourseId(id);
    }

    // 分页查询所有课程（管理员视角）
    @GetMapping
    public Result getAllCourses(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        return courseService.getAllCourses(page, size, keyword);
    }
}
