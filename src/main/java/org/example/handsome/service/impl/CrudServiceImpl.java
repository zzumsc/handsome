package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.example.handsome.dao.UserDao;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.User;
import org.example.handsome.pojo.UserQuery;
import org.example.handsome.service.CrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CrudServiceImpl implements CrudService {

    @Resource
    private UserDao userDao;

    @Override
    @Transactional
    public Result add(User user) {
        if (user.getRole() == null || (user.getRole() != User.Role.admin && user.getRole() != User.Role.student)) {
            return Result.fail("角色必须是admin或student");
        }

        User existingUser = userDao.selectByEmail(user.getEmail());
        if (existingUser != null) {
            return Result.fail("email不能相同");
        }

        User existingUser2 = userDao.selectByNo(user.getNo());
        if (existingUser2 != null) {
            return Result.fail("学号/工号不能相同");
        }

        int rows = userDao.insert(user);
        if (rows > 0) {
            return Result.ok("新增成功").put("userId", user.getId());
        } else {
            return Result.fail("新增失败：数据库操作无响应");
        }
    }

    @Override
    @Transactional
    public Result delete(Long id) {
        if (id == null) {
            return Result.fail("删除失败：ID不能为空");
        }
        int rows = userDao.deleteById(id);
        if (rows > 0) {
            return Result.ok("删除成功");
        } else {
            return Result.fail("删除失败：该ID不存在");
        }
    }

    @Override
    @Transactional
    public Result update(User user) {
        if (user.getId() == null) {
            return Result.fail("更新失败：ID不能为空");
        }
        if (user.getRole() == null || (user.getRole() != User.Role.admin && user.getRole() != User.Role.student)) {
            return Result.fail("角色必须是admin或student");
        }
        User existUser = userDao.selectById(user.getId());
        if (existUser == null) {
            return Result.fail("更新失败：该ID不存在");
        }
        int rows = userDao.update(user);
        return rows > 0 ? Result.ok("更新成功") : Result.fail("更新失败：无数据变更");
    }

    @Override
    public Result getById(Long id) {
        if (id == null) {
            return Result.fail("查询失败：ID不能为空");
        }
        User user = userDao.selectById(id);
        if (user != null) {
            return Result.ok("查询成功").put("user", user);
        } else {
            return Result.fail("查询失败：该ID不存在");
        }
    }


    public Result listByCondition(UserQuery query) {
        // 验证排序字段的合法性，防止SQL注入
        validateSortField(query);

        List<User> users = userDao.selectByCondition(query);

        // 如果需要分页处理
        int total = users.size();
        /*if (query.getPage() != null && query.getSize() != null) {
            int start = (query.getPage() - 1) * query.getSize();
            int end = Math.min(start + query.getSize(), total);
            if (start < end) {
                users = users.subList(start, end);
            } else {
                users = new ArrayList<>();
            }
        }*/

        return Result.ok("查询成功")
                .put("users", users)
                .put("total", total);
    }

    // 添加排序字段验证方法，增强安全性
    private void validateSortField(UserQuery query) {
        if (query.getSortField() == null || query.getSortField().isEmpty()) {
            return;
        }

        // 允许的排序字段列表
        List<String> allowedFields = Arrays.asList("id", "no", "name", "role", "email");
        if (!allowedFields.contains(query.getSortField())) {
            throw new IllegalArgumentException("Invalid sort field: " + query.getSortField());
        }

        // 验证排序方向
        if (query.getSortDir() != null && !query.getSortDir().isEmpty()) {
            if (!"asc".equalsIgnoreCase(query.getSortDir()) && !"desc".equalsIgnoreCase(query.getSortDir())) {
                throw new IllegalArgumentException("Invalid sort direction: " + query.getSortDir());
            }
            // 统一转为大写，符合SQL规范
            query.setSortDir(query.getSortDir().toUpperCase());
        }
    }
    /*private List<User> sortUsers(List<User> users, UserQuery query) {
        if (query.getSortField() == null || query.getSortField().isEmpty()) {
            return users.stream()
                    .sorted(Comparator.comparingLong(User::getId))
                    .collect(Collectors.toList());
        }

        Comparator<User> comparator = switch (query.getSortField()) {
            case "no" -> Comparator.comparing(User::getNo);
            case "name" -> Comparator.comparing(User::getName);
            case "role" -> Comparator.comparing(User::getRole);
            default -> Comparator.comparingLong(User::getId); // 默认按 id
        };

        if ("desc".equalsIgnoreCase(query.getSortDir())) {
            comparator = comparator.reversed();
        }

        return users.stream().sorted(comparator).collect(Collectors.toList());
    }*/
}