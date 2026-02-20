package org.example.handsome.dao;

import org.apache.ibatis.annotations.*;
import org.example.handsome.pojo.Course;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CourseDao {

    @Insert("INSERT INTO courses (" +
            "course_code, name, teacher_name, credit, max_students, " +
            "current_students, last_year_score, start_time, end_time, status, version" +
            ") VALUES (" +
            "#{courseCode}, #{name}, #{teacherName}, #{credit}, #{maxStudents}, " +
            "#{currentStudents}, #{lastYearScore}, #{startTime}, #{endTime}, #{status}, #{version}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Course course);

    @Delete("DELETE FROM courses WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM courses WHERE id = #{id}")
    Course selectById(Long id);

    @Update("UPDATE courses SET " +
            "course_code = #{courseCode}, name = #{name}, teacher_name = #{teacherName}, " +
            "credit = #{credit}, max_students = #{maxStudents}, current_students = #{currentStudents}, " +
            "last_year_score = #{lastYearScore}, start_time = #{startTime}, end_time = #{endTime}, " +
            "status = #{status}, version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int update(Course course);

    @Update("UPDATE courses SET " +
            "status = #{newStatus}, version = version + 1 " +
            "WHERE id = #{courseId} AND version = #{currentVersion} AND status = #{oldStatus}")
    int updateStatus(
            @Param("courseId") Long courseId,
            @Param("oldStatus") String oldStatus,
            @Param("newStatus") String newStatus,
            @Param("currentVersion") Integer currentVersion
    );

    // 查询已发布和已结束的课程（学生可见）
    @Select("SELECT * FROM courses WHERE status IN ('published', 'closed')")
    List<Course> selectAvailableCourses();

    // 查询指定状态的课程
    @Select("SELECT * FROM courses WHERE status = #{status}")
    List<Course> selectCoursesByStatus(@Param("status") String status);

    // 结算时：批量更新课程当前选课人数（乐观锁）
    @Update("UPDATE courses SET " +
            "current_students = #{currentStudents}, " +
            "version = version + 1 " +
            "WHERE id = #{courseId} AND version = #{version}")
    int updateCurrentStudents(
            @Param("courseId") Long courseId,
            @Param("currentStudents") int currentStudents,
            @Param("version") Integer version
    );

    // 结算时：更新往年录取分（本次最低录取积分）
    @Update("UPDATE courses SET " +
            "last_year_score = #{lastYearScore}, " +
            "version = version + 1 " +
            "WHERE id = #{courseId}")
    int updateLastYearScore(
            @Param("courseId") Long courseId,
            @Param("lastYearScore") BigDecimal lastYearScore
    );

    // 分页查询所有课程（管理员）
    @Select("<script>" +
            "SELECT * FROM courses " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (name LIKE CONCAT('%', #{keyword}, '%') OR course_code LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY id ASC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<Course> selectAllByPage(
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("keyword") String keyword
    );

    @Select("<script>" +
            "SELECT COUNT(*) FROM courses " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR course_code LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "</script>")
    int countByKeyword(@Param("keyword") String keyword);

    @Select("SELECT * FROM courses WHERE course_code = #{courseCode}")
    Course getCourseByCourseCode(@Param("courseCode") String courseCode);

    // 查询待开始的课程：draft状态 + startTime已到
    @Select("SELECT * FROM courses WHERE status = 'draft' AND start_time IS NOT NULL AND start_time <= NOW()")
    List<Course> selectCoursesToStart();

    // 查询待结束的课程：published状态 + endTime已到
    @Select("SELECT * FROM courses WHERE status = 'published' AND end_time IS NOT NULL AND end_time <= NOW()")
    List<Course> selectCoursesToEnd();

    // 查询未来待开始的课程（用于重启恢复调度）
    @Select("SELECT * FROM courses WHERE status = 'draft' AND start_time IS NOT NULL AND start_time > NOW()")
    List<Course> selectFutureCoursesToStart();

    // 查询未来待结束的课程（用于重启恢复调度）
    @Select("SELECT * FROM courses WHERE status = 'published' AND end_time IS NOT NULL AND end_time > NOW()")
    List<Course> selectFutureCoursesToEnd();
}
