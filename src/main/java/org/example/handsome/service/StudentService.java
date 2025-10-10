package org.example.handsome.service;

import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.User;

public interface StudentService {
    Result getMyInfo();      // 获取当前学生自身信息

    Result updateMyInfo(User user); // 更新当前学生自身信息

    Result getMyPoints();

}