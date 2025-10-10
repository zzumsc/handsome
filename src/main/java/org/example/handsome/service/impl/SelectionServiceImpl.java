package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.example.handsome.dao.SelectionDao;
import org.example.handsome.dao.UserDao;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.Selection;
import org.example.handsome.pojo.User;
import org.example.handsome.service.SelectionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SelectionServiceImpl implements SelectionService {

    @Resource
    private SelectionDao selectionDao;
    @Resource
    private UserDao userDao;

    @Override
    public Result getSelectionsByStudentId(Long targetStudentId) {
        // 1. 获取当前登录用户信息（邮箱是登录标识）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String loginEmail = auth.getName();
        User loginUser = userDao.selectByEmail(loginEmail);

        if (loginUser == null) {
            return Result.fail("当前登录用户不存在");
        }

        // 2. 权限控制：判断角色，确定可查询的学生ID
        Long allowStudentId;
        if (User.Role.admin.equals(loginUser.getRole())) {
            // 管理员：可查任意学生（targetStudentId为null时查所有）
            allowStudentId = targetStudentId;
        } else if (User.Role.student.equals(loginUser.getRole())) {
            // 学生：仅能查自己的ID（忽略传入的targetStudentId）
            allowStudentId = loginUser.getId();
            // 若学生传入其他ID，提示无权限（可选增强校验）
            if (targetStudentId != null && !targetStudentId.equals(allowStudentId)) {
                return Result.fail("学生仅能查询自身选课信息");
            }
        } else {
            // 其他角色（异常情况）
            return Result.fail("无选课查询权限");
        }

        // 3. 调用DAO查询选课信息
        List<Selection> selections = selectionDao.selectByStudentId(allowStudentId);
        if (selections.isEmpty()) {
            return Result.ok("查询成功，无选课记录").put("selections", selections);
        }

        // 4. 返回结果（封装选课列表+总数）
        return Result.ok("查询成功")
                .put("selections", selections)
                .put("total", selections.size());
    }
}