package org.example.handsome.dao;

import org.apache.ibatis.annotations.*;
import org.example.handsome.pojo.Course;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CourseDao {

    @Insert("INSERT INTO courses (" +
            "course_code, name, teacher_name, credit, max_students, " +
            "current_students, initial_points, current_points, status, version" +
            ") VALUES (" +
            "#{courseCode}, #{name}, #{teacherName}, #{credit}, #{maxStudents}, " +
            "#{currentStudents}, #{initialPoints}, #{currentPoints}, #{status}, #{version}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id") // 返回自增ID
    int insert(Course course);

    @Delete("DELETE FROM courses WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM courses WHERE id = #{id}")
    Course selectById(Long id);

    @Update("UPDATE courses SET " +
            "course_code = #{courseCode}, name = #{name}, teacher_name = #{teacherName}, " +
            "credit = #{credit}, max_students = #{maxStudents}, current_students = #{currentStudents}, " +
            "initial_points = #{initialPoints}, current_points = #{currentPoints}, " +
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

    // 减少课程当前选课人数（乐观锁控制）
    @Update("UPDATE courses SET " +
            "current_students = current_students - 1, " +
            "version = version + 1 " +
            "WHERE id = #{courseId} AND current_students > 0 AND version = #{version}")
    int decrementStudentCount(
            @Param("courseId") Long courseId,
            @Param("version") Integer version
    );

    // 查询已发布和已结束的课程（学生可见）
    @Select("SELECT * FROM courses WHERE status IN ('published', 'closed')")
    List<Course> selectAvailableCourses();

    // 1. 查询所有已发布课程（用于积分联动计算）
    @Select("SELECT * FROM courses WHERE status = #{status}")
    List<Course> selectCoursesByStatus(@Param("status") String status);

    // 2. 批量更新课程当前积分和乐观锁（积分联动后更新数据库）
    @Update("<script>" +
            "UPDATE courses " +
            "SET " +
            "  current_points = CASE id " +
            "    <foreach collection='courses' item='course' separator=''>" +
            "      WHEN #{course.id} THEN #{course.currentPoints} " +
            "    </foreach>" +
            "  END, " +
            "  version = version + 1 " + // 乐观锁统一自增
            "WHERE " +
            "  id IN (" +
            "    <foreach collection='courses' item='course' separator=','>" +
            "      #{course.id} " +
            "    </foreach>" +
            "  ) " +
            "  AND (" +
            "    <foreach collection='courses' item='course' separator=' OR '>" +
            "      (id = #{course.id} AND version = #{course.version}) " +
            "    </foreach>" +
            "  )" +
            "</script>")
    int batchUpdateCurrentPoints(@Param("courses") List<Course> courses);

    //乐观锁修改
    @Update("UPDATE courses SET current_students = current_students + 1 " +
            "WHERE id = #{courseId} AND current_students < max_students")
    int incrementStudentCount(@Param("courseId") Long courseId);

    @Select("<script>"+
            "SELECT * FROM courses " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<Course> selectAllByPage(
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("keyword") String keyword
    );

    @Select("<script>"+
            "SELECT COUNT(*) FROM courses " +
            "WHERE 1=1 " +  // 占位条件，方便拼接AND
            "<if test='keyword != null and keyword != \"\"'>" +  // 动态判断关键词是否存在
            "AND (name LIKE CONCAT('%', #{keyword}, '%') " +  // 匹配课程名
            "OR description LIKE CONCAT('%', #{keyword}, '%'))" +  // 匹配课程描述
            "</if>"+
            "</script>")
    int countByKeyword(@Param("keyword") String keyword);

    @Select("select * from courses where course_code=#{courseCode}")
    Course getCourseByCourseCode(@Param("courseCode") String courseCode);
}