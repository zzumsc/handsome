package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.handsome.dao.StudentPointDao;
import org.example.handsome.dao.UserDao;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.User;
import org.example.handsome.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class StudentServiceImpl implements StudentService {

    @Resource
    private UserDao userDao;

    @Override
    public Result getMyInfo() {
        // 1. 获取当前登录用户的认证信息（邮箱为登录标识）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        // 2. 查询当前用户
        User user = userDao.selectByEmail(email);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 3. 校验角色（必须为学生）
        if (user.getRole() != User.Role.student) {
            return Result.fail("当前用户非学生角色，无权操作");
        }

        return Result.ok("查询成功").put("user", user);
    }

    @Override
    @Transactional
    public Result updateMyInfo(User user) {
        // 1. 获取当前登录用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User currentUser = userDao.selectByEmail(email);

        if (currentUser == null) {
            return Result.fail("用户不存在");
        }
        if (currentUser.getRole() != User.Role.student) {
            return Result.fail("当前用户非学生角色，无权操作");
        }

        // 2. 强制校验：仅能更新自身ID的记录
        if (user.getId() == null || !user.getId().equals(currentUser.getId())) {
            return Result.fail("学生仅能更新自身信息，ID不匹配");
        }

        // 3. 执行更新 + 返回最新数据
        int rows = userDao.update(user);
        if (rows > 0) {
            User updatedUser = userDao.selectById(user.getId());
            return Result.ok("更新成功").put("user", updatedUser);
        } else {
            return Result.fail("更新失败（无数据变更或ID不存在）");
        }
    }

    @Resource
    StudentPointDao studentPointDao;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result getMyPoints() {
        try{
            // 1. 获取当前登录用户的身份信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String loginEmail = auth.getName(); // 假设登录账号是邮箱

            // 2. 根据邮箱查询用户信息（需包含 student_id）
            User loginUser = userDao.selectByEmail(loginEmail);
            if (loginUser == null || loginUser.getId() == null) {
                throw new RuntimeException("用户信息异常，无法获取学生ID");
            }

            // 3. 根据学生ID查询积分
            Long studentId = loginUser.getId();
            return Result.ok("获取积分成功").put("point",studentPointDao.getStudentPoints(studentId,redisTemplate));
        }
        catch (Exception e) {
            log.error("获取学生积分失败", e);
            return Result.fail("获取积分失败，请稍后重试");
        }
    }
}
