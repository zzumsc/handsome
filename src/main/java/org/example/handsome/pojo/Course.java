package org.example.handsome.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    /** 上一年该课程最低录取积分（仅供参考展示） */
    private BigDecimal lastYearScore;

    /** 计划开始选课时间 */
    private LocalDateTime startTime;

    /** 计划结束选课时间 */
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private CourseStatus status = CourseStatus.draft;

    @Version
    private Integer version = 0;

    /** 当前竞价人数（瞬态字段，从Redis读取，不映射数据库） */
    @Transient
    private int bidCount;

    public enum CourseStatus {
        draft,    // 草稿（未发布）
        published,// 已发布（可选课）
        closed    // 已结束（不可选课）
    }

    public Course() {
    }

    public Course(Long id, String courseCode, String name, String teacherName, int credit,
                  int maxStudents, int currentStudents, BigDecimal lastYearScore,
                  LocalDateTime startTime, LocalDateTime endTime,
                  CourseStatus status, Integer version) {
        this.id = id;
        this.courseCode = courseCode;
        this.name = name;
        this.teacherName = teacherName;
        this.credit = credit;
        this.maxStudents = maxStudents;
        this.currentStudents = currentStudents;
        this.lastYearScore = lastYearScore;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.version = version;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
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

    public BigDecimal getLastYearScore() {
        return lastYearScore;
    }

    public void setLastYearScore(BigDecimal lastYearScore) {
        this.lastYearScore = lastYearScore;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
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

    public int getBidCount() {
        return bidCount;
    }

    public void setBidCount(int bidCount) {
        this.bidCount = bidCount;
    }
}
