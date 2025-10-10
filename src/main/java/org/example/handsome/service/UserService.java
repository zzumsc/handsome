package org.example.handsome.service;

import org.example.handsome.pojo.DTO.Result;

public interface UserService {
    Result sendEmailCode(String email);// 发送邮箱验证码

    Result loginByEmail(String email, String code); // 邮箱登录

}