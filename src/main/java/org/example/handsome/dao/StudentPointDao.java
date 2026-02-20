package org.example.handsome.dao;

import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentPointDao {

    @Select("SELECT points FROM points WHERE student_id = #{studentId}")
    BigDecimal getStudentPointsFromDb(Long studentId);

    // 批量查询全部学生积分（用于预加载），返回 Map key 为列名 student_id / points
    @Select("SELECT student_id, points FROM points WHERE student_id IS NOT NULL AND points IS NOT NULL")
    List<Map<String, Object>> selectAllStudentPoints();

    @Update("UPDATE points SET " +
            "points = points + #{amount}, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE student_id = #{studentId}")
    int updateStudentPointsInDb(
            @Param("studentId") Long studentId,
            @Param("amount") BigDecimal amount
    );
}
