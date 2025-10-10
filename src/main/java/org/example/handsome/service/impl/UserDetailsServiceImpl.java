package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.handsome.dao.LoginDao;
import org.example.handsome.pojo.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService{

    @Resource
    private LoginDao loginDao; // 假设用MyBatis的Mapper查询用户

    public UserDetailsServiceImpl() {
        log.info("UserDetailsServiceImpl 被 Spring 初始化");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = loginDao.selectByEmail(username); // 替换为你的查询逻辑（如按邮箱查）
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().toString());
        System.out.println(user.getRole().toString());
        List<GrantedAuthority> authorities = Collections.singletonList(authority);

        String password = "{noop}";

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                password,
                authorities
        );
    }
}