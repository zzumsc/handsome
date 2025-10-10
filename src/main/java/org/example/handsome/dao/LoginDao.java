package org.example.handsome.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.handsome.pojo.User;

@Mapper
public interface LoginDao {
    // 根据手机号查询用户
    @Select("select * from users where phone = #{phone}")
    User selectByPhone(String phone);

    // 根据邮箱查询用户
    @Select("select * from users where email = #{email}")
    User selectByEmail(String email);

    // 新增用户
    @Insert("insert into users (phone, email, create_time) values (#{phone}, #{email}, #{createTime})")
    void insert(User user);
}