package org.example.handsome.controller;

import jakarta.annotation.Resource;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.service.StudentCourseService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/student/courses")
public class StudentCourseController {

    @Resource
    private StudentCourseService studentCourseService;

    // 查看所有可选择/已结束的课程（含竞价人数、往年录取分）
    @GetMapping
    public Result listAvailableCourses() {
        return studentCourseService.listAvailableCourses();
    }

    // 竞价预选课程
    @PostMapping("/{courseId}/bid")
    public Result bidCourse(@PathVariable Long courseId, @RequestBody Map<String, Object> body) {
        BigDecimal bidPoints = new BigDecimal(body.get("bidPoints").toString());
        return studentCourseService.bidCourse(courseId, bidPoints);
    }

    // 修改竞价积分
    @PutMapping("/{courseId}/bid")
    public Result updateBid(@PathVariable Long courseId, @RequestBody Map<String, Object> body) {
        BigDecimal newBidPoints = new BigDecimal(body.get("bidPoints").toString());
        return studentCourseService.updateBid(courseId, newBidPoints);
    }

    // 取消预选
    @DeleteMapping("/{courseId}/bid")
    public Result cancelBid(@PathVariable Long courseId) {
        return studentCourseService.cancelBid(courseId);
    }

    // 查询我在某课程的竞价信息
    @GetMapping("/{courseId}/bid")
    public Result getMyBid(@PathVariable Long courseId) {
        return studentCourseService.getMyBid(courseId);
    }
}
