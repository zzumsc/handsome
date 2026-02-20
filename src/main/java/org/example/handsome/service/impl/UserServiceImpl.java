package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.example.handsome.dao.LoginDao;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.User;
import org.example.handsome.service.UserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.example.handsome.service.impl.RedisConstants.*;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private AuthenticationManager authenticationManager; // 注入认证管理器
    @Resource
    private LoginDao loginDao; // 注入 MyBatis Mapper
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private JavaMailSender javaMailSender;

/*
    // 发送短信验证码
    @Override
    public Result sendSmsCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(SMS_CODE_KEY + phone, code, CODE_TTL, TimeUnit.MINUTES);
        // 实际项目调用短信API，此处用日志模拟
        System.out.println("短信验证码：" + code);
        return Result.ok("短信验证码已发送");
    }

    // 短信登录
    @Override
    public Result loginBySms(String phone, String code) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 从Redis获取验证码
        String redisCode = stringRedisTemplate.opsForValue().get(SMS_CODE_KEY + phone);
        if (redisCode == null || !redisCode.equals(code)) {
            return Result.fail("验证码错误或已过期");
        }

        // 用 MyBatis Mapper 查询用户
        User user = userMapper.selectByPhone(phone);
        // 不存在则创建用户
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user); // 用 Mapper 插入
        }

        return Result.ok("登录成功").put("user", user);
    }
*/

    // 发送邮箱验证码
    @Override
    public Result sendEmailCode(String email) {
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式错误！");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + email, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 发送邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(EMAIL_FROM);
            message.setTo(email);
            message.setSubject("小帅选课登录验证码");
            message.setText("您的验证码是：" + code + "（5分钟内有效）");
            javaMailSender.send(message);
            System.out.println(code);
            return Result.ok("邮箱验证码已发送");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail("验证码发送失败");
        }
    }

    // 邮箱登录
    @Override
    public Result loginByEmail(String email, String code) {
        if (RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式错误！");
        }
        // 从Redis获取验证码
        String redisCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        if (redisCode == null || !redisCode.equals(code)) {
            return Result.fail("验证码错误或已过期");
        }

        // 用 MyBatis Mapper 查询用户
        User user = loginDao.selectByEmail(email);
        // 不存在
        if (user == null) {
            Result.fail("用户不存在，请联系管理员");
        }
        log.info("登录接口被调用：email={}, code={}", email, code);
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole().toString()) // 使用 user.getRole() 获取角色
        );
        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(email, "", authorities);
        Authentication authenticated = authenticationManager.authenticate(authRequest);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authenticated); // 存入认证结果
        SecurityContextHolder.setContext(securityContext);

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession(true); // true：不存在则创建 Session
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return Result.ok("登录成功").put("user", user);
    }
}