package org.example.handsome.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "courses")
public class Course {
    @Id
    private Long id;

    private String courseCode;

    private String name;

    private String teacherName;

    private int credit;

    private int maxStudents;

    private int currentStudents;

    private BigDecimal initialPoints;

    private BigDecimal currentPoints;

    @Transient // @Transient 表示该字段不映射数据库列
    private BigDecimal weight;

    @Enumerated(EnumType.STRING)
    private CourseStatus status = CourseStatus.draft;

    @Version
    private Integer version = 0;

    public enum CourseStatus {
        draft,    // 草稿（未发布）
        published,// 已发布（可选课）
        closed    // 已结束（不可选课）
    }


    public Course() {
    }

    public Course(Long id, String courseCode, String name, String teacherName, int credit, int maxStudents, int currentStudents, BigDecimal initialPoints, BigDecimal currentPoints, BigDecimal weight, CourseStatus status, Integer version) {
        this.id = id;
        this.courseCode = courseCode;
        this.name = name;
        this.teacherName = teacherName;
        this.credit = credit;
        this.maxStudents = maxStudents;
        this.currentStudents = currentStudents;
        this.initialPoints = initialPoints;
        this.currentPoints = currentPoints;
        this.weight = weight;
        this.status = status;
        this.version = version;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCredit() {
        return credit;
    }

    public void setCredit(int credit) {
        this.credit = credit;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public BigDecimal getInitialPoints() {
        return initialPoints;
    }

    public void setInitialPoints(BigDecimal initialPoints) {
        this.initialPoints = initialPoints;
    }

    public int getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(int maxStudents) {
        this.maxStudents = maxStudents;
    }

    public int getCurrentStudents() {
        return currentStudents;
    }

    public void setCurrentStudents(int currentStudents) {
        this.currentStudents = currentStudents;
    }

    public BigDecimal getCurrentPoints() {
        return currentPoints;
    }

    public void setCurrentPoints(BigDecimal currentPoints) {
        this.currentPoints = currentPoints;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public void setStatus(CourseStatus status) {
        this.status = status;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
