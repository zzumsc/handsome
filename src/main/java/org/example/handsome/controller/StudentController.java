package org.example.handsome.controller;

import jakarta.annotation.Resource;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.User;
import org.example.handsome.service.SelectionService;
import org.example.handsome.service.StudentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/student")
public class StudentController {

    @Resource
    private StudentService studentService;

    // 获取自身信息
    @GetMapping("/info")
    public Result getMyInfo() {
        return studentService.getMyInfo();
    }

    // 更新自身信息
    @PutMapping("/info")
    public Result updateMyInfo(@RequestBody User user) {
        return studentService.updateMyInfo(user);
    }


    @Resource
    private SelectionService selectionService;

    @GetMapping("/selections")
    public Result getMySelections() {
        return selectionService.getSelectionsByStudentId(null);
    }

    @GetMapping("/points")
    public Result getMyPoints() {
        return studentService.getMyPoints();
    }
}