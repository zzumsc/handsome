package org.example.handsome.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequestMapping("/user")
public class LoginController {
    @Autowired
    private UserService userService;

    @GetMapping("/send")
    public Result sendEmailCode(@RequestParam String email) {
        // 发送
        return userService.sendEmailCode(email);
    }

    @PostMapping("/login")
    public Result loginByEmail(
            @RequestParam String email,
            @RequestParam String code
    ) {
        // 登录
        return userService.loginByEmail(email, code);
    }

    @PostMapping("/logout")
    public Result logout() {
        // 清除Security上下文
        SecurityContextHolder.clearContext();
        // 使当前session失效
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Result.ok("登出成功");
    }
}