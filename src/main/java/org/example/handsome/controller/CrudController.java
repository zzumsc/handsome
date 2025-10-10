package org.example.handsome.controller;

import jakarta.annotation.Resource;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.User;
import org.example.handsome.pojo.UserQuery;
import org.example.handsome.service.CrudService;
import org.example.handsome.service.SelectionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class CrudController {

    @Resource
    private CrudService crudService;

    @PostMapping("/crud")
    public Result add(@RequestBody User user) {
        return crudService.add(user);
    }

    @DeleteMapping("/crud/{id}")
    public Result delete(@PathVariable Long id) {
        return crudService.delete(id);
    }

    @PutMapping("/crud")
    public Result update(@RequestBody User user) {
        return crudService.update(user);
    }

    @GetMapping("/crud/{id}")
    public Result getById(@PathVariable Long id) {
        return crudService.getById(id);
    }

    @GetMapping("/crud")
    public Result listByCondition(UserQuery query) {
        return crudService.listByCondition(query);
    }

    @Resource
    private SelectionService selectionService;

    @GetMapping("/selections")
    public Result getSelections(
            @RequestParam(required = false) Long studentId) {
        return selectionService.getSelectionsByStudentId(studentId);
    }
}