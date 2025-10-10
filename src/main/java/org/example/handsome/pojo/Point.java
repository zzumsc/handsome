package org.example.handsome.pojo;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "points")
public class Point {
    @Id
    private Long id;

    @OneToOne
    @JoinColumn(name = "student_id")
    private User student; // 关联学生用户

    private BigDecimal points; // 积分

    private LocalDateTime updatedAt;

    public Point() {
    }

    public Point(Long id, User student, BigDecimal points, LocalDateTime updatedAt) {
        this.id = id;
        this.student = student;
        this.points = points;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public void setPoints(BigDecimal points) {
        this.points = points;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
