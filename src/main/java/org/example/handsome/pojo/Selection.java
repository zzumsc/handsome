package org.example.handsome.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Table(name = "selections")
public class Selection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false) // 与数据库列名一致
    private Long studentId;

    @Column(name = "course_id", nullable = false) // 与数据库列名一致
    private Long courseId;

    @Column(name = "selection_time", nullable = false)
    private Date selectionTime;

    @Column(name = "points_used", nullable = false)
    private BigDecimal pointsUsed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "student_id",
            insertable = false,
            updatable = false
    )
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "course_id",
            insertable = false,
            updatable = false
    )
    private Course course;

    public Selection() {
    }

    public Selection(Long id, Long studentId, Long courseId, Date selectionTime, BigDecimal pointsUsed, User student, Course course) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.selectionTime = selectionTime;
        this.pointsUsed = pointsUsed;
        this.student = student;
        this.course = course;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Date getSelectionTime() {
        return selectionTime;
    }

    public void setSelectionTime(Date selectionTime) {
        this.selectionTime = selectionTime;
    }

    public BigDecimal getPointsUsed() {
        return pointsUsed;
    }

    public void setPointsUsed(BigDecimal pointsUsed) {
        this.pointsUsed = pointsUsed;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }
}
