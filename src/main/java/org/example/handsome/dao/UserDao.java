package org.example.handsome.dao;

import org.apache.ibatis.annotations.*;
import org.example.handsome.pojo.User;
import org.example.handsome.pojo.UserQuery;

import java.util.List;

@Mapper // 确保 MyBatis 扫描该接口
public interface UserDao {

    // 新增用户
    @Insert("INSERT INTO users (email, name, role, no) VALUES (#{email}, #{name}, #{role}, #{no})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user); // 返回影响行数

    // 根据ID删除用户
    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(Long id);

    // 更新用户：动态更新非空字段
    @Update("<script>" +
            "UPDATE users " +
            "<set>" +
            "   <if test='email != null'>email = #{email},</if>" +
            "   <if test='name != null'>name = #{name},</if>" +
            "   <if test='role != null'>role = #{role},</if>" +
            "   <if test='no != null'>no = #{no}</if>" +
            "</set>" +
            "WHERE id = #{id}" +
            "</script>")
    int update(User user); // 返回影响行数

    // 根据ID查询用户
    @Select("SELECT id, email, name, role, no FROM users WHERE id = #{id}")
    User selectById(Long id); // 无数据返回null

    @Select("<script>" +
            "SELECT id, email, name, role, no FROM users " +
            "WHERE 1=1 " +
            "<if test='name != null and name != \"\"'>AND name LIKE CONCAT('%', #{name}, '%')</if>" +
            "<if test='email != null and email != \"\"'>AND email LIKE CONCAT('%', #{email}, '%')</if>" +
            "<if test='no != null and no != \"\"'>AND no LIKE CONCAT('%', #{no}, '%')</if>" +
            "<if test='role != null and role != \"\"'>AND role = #{role}</if>" +
            "<choose>" +
            "   <when test='sortField != null and sortField != \"\" and sortDir != null and sortDir != \"\"'>" +
            "       ORDER BY ${sortField} ${sortDir}" +
            "   </when>" +
            "   <otherwise>" +
            "       ORDER BY id ASC" +
            "   </otherwise>" +
            "</choose>" +
            "</script>")
    List<User> selectByCondition(UserQuery query);


    // 根据邮箱查询用户
    @Select("SELECT id, email, name, role, no FROM users WHERE email = #{username}")
    User selectByEmail(String username); // 无数据返回null

    @Select("select * from users where no = #{no}")
    User selectByNo(String no);
}