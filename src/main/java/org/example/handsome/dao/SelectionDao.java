package org.example.handsome.dao;

import org.apache.ibatis.annotations.*;
import org.example.handsome.pojo.Selection;

import java.util.List;

@Mapper
public interface SelectionDao {

    @Select("<script>" +
            "SELECT " +
            "s.id AS s_id, s.student_id AS s_studentId, s.course_id AS s_courseId, " +
            "s.selection_time AS s_selectionTime, s.points_used AS s_pointsUsed, " +
            "u.id AS u_id, u.name AS u_name, u.no AS u_no, u.email AS u_email, u.role AS u_role, " +
            "c.id AS c_id, c.course_code AS c_courseCode, c.name AS c_name, c.status AS c_status, " +
            "c.teacher_name AS c_teacherName, c.credit AS c_credit, c.last_year_score AS c_lastYearScore, " +
            "c.current_students AS c_currentStudents, c.max_students AS c_maxStudents " +
            "FROM selections s " +
            "LEFT JOIN users u ON s.student_id = u.id " +
            "LEFT JOIN courses c ON s.course_id = c.id " +
            "<if test='studentId != null'>" +
            "WHERE s.student_id = #{studentId}" +
            "</if>" +
            "</script>")
    @Results({
            @Result(column = "s_id", property = "id"),
            @Result(column = "s_studentId", property = "studentId"),
            @Result(column = "s_courseId", property = "courseId"),
            @Result(column = "s_selectionTime", property = "selectionTime"),
            @Result(column = "s_pointsUsed", property = "pointsUsed"),
            @Result(column = "u_id", property = "student.id"),
            @Result(column = "u_name", property = "student.name"),
            @Result(column = "u_no", property = "student.no"),
            @Result(column = "u_email", property = "student.email"),
            @Result(column = "u_role", property = "student.role"),
            @Result(column = "c_id", property = "course.id"),
            @Result(column = "c_courseCode", property = "course.courseCode"),
            @Result(column = "c_name", property = "course.name"),
            @Result(column = "c_teacherName", property = "course.teacherName"),
            @Result(column = "c_credit", property = "course.credit"),
            @Result(column = "c_lastYearScore", property = "course.lastYearScore"),
            @Result(column = "c_status", property = "course.status"),
            @Result(column = "c_currentStudents", property = "course.currentStudents"),
            @Result(column = "c_maxStudents", property = "course.maxStudents")
    })
    List<Selection> selectByStudentId(Long studentId);

    @Select("SELECT " +
            "s.id AS s_id, s.selection_time AS s_selectionTime, s.points_used AS s_pointsUsed, " +
            "u.id AS u_id, u.name AS u_name, u.no AS u_no, u.email AS u_email, u.role AS u_role " +
            "FROM selections s " +
            "LEFT JOIN users u ON s.student_id = u.id " +
            "WHERE s.course_id = #{courseId}")
    @Results({
            @Result(column = "s_id", property = "id"),
            @Result(column = "s_selectionTime", property = "selectionTime"),
            @Result(column = "s_pointsUsed", property = "pointsUsed"),
            @Result(column = "u_id", property = "student.id"),
            @Result(column = "u_name", property = "student.name"),
            @Result(column = "u_no", property = "student.no"),
            @Result(column = "u_email", property = "student.email"),
            @Result(column = "u_role", property = "student.role")
    })
    List<Selection> selectStudentsByCourseId(Long courseId);

    // 查询课程已录取的学生ID列表（轻量级，用于Redis预加载）
    @Select("SELECT student_id FROM selections WHERE course_id = #{courseId}")
    List<Long> selectAdmittedStudentIds(Long courseId);

    // 检查学生是否已选该课程
    @Select("SELECT COUNT(1) FROM selections WHERE student_id = #{studentId} AND course_id = #{courseId}")
    int checkSelectionExists(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId
    );

    // 按学生ID和课程ID删除选课记录
    @Delete("DELETE FROM selections WHERE student_id = #{studentId} AND course_id = #{courseId}")
    int deleteByStudentAndCourse(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId
    );

    @Insert("INSERT INTO selections (" +
            "student_id, course_id, selection_time, points_used" +
            ") VALUES (" +
            "#{studentId}, #{courseId}, #{selectionTime}, #{pointsUsed}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Selection selection);

    @Select("SELECT * FROM selections " +
            "WHERE student_id = #{studentId} AND course_id = #{courseId}")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "student_id", property = "studentId"),
            @Result(column = "course_id", property = "courseId"),
            @Result(column = "selection_time", property = "selectionTime"),
            @Result(column = "points_used", property = "pointsUsed")
    })
    Selection selectByStudentAndCourse(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId
    );

    // 结算时：批量插入录取的选课记录
    @Insert("<script>" +
            "INSERT INTO selections (student_id, course_id, selection_time, points_used) VALUES " +
            "<foreach collection='selections' item='s' separator=','>" +
            "(#{s.studentId}, #{s.courseId}, #{s.selectionTime}, #{s.pointsUsed})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("selections") List<Selection> selections);
}
