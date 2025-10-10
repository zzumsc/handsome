package org.example.handsome.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

@Mapper
public interface StudentPointDao {

    @Select("SELECT points FROM points WHERE student_id = #{studentId}")
    BigDecimal getStudentPoints(Long studentId);


    @Update("UPDATE points SET " +
            "points = points + #{amount}, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE student_id = #{studentId}")
    int updateStudentPoints(
            @Param("studentId") Long studentId,
            @Param("amount") BigDecimal amount
    );
}