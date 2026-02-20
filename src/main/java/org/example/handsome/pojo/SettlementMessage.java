package org.example.handsome.pojo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 结算结果MQ消息DTO
 * 由结算线程发送，MQ消费者接收后写入DB
 */
public class SettlementMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 课程ID */
    private Long courseId;

    /** 录取学生列表 */
    private List<AdmittedStudent> admittedStudents;

    /** 最低录取积分（更新为lastYearScore） */
    private BigDecimal cutoffScore;

    /** 录取总人数 */
    private int admittedCount;

    /** 淘汰总人数 */
    private int rejectedCount;

    /** 淘汰者学生ID列表（用于清理积分缓存） */
    private List<Long> rejectedStudentIds;

    public SettlementMessage() {
    }

    public SettlementMessage(Long courseId, List<AdmittedStudent> admittedStudents,
                             BigDecimal cutoffScore, int admittedCount, int rejectedCount,
                             List<Long> rejectedStudentIds) {
        this.courseId = courseId;
        this.admittedStudents = admittedStudents;
        this.cutoffScore = cutoffScore;
        this.admittedCount = admittedCount;
        this.rejectedCount = rejectedCount;
        this.rejectedStudentIds = rejectedStudentIds;
    }

    /**
     * 录取学生信息（内部类）
     */
    public static class AdmittedStudent implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long studentId;
        private BigDecimal pointsUsed;  // 实际积分（已缩小）
        private long bidTimeMillis;     // 竞价时间戳

        public AdmittedStudent() {
        }

        public AdmittedStudent(Long studentId, BigDecimal pointsUsed, long bidTimeMillis) {
            this.studentId = studentId;
            this.pointsUsed = pointsUsed;
            this.bidTimeMillis = bidTimeMillis;
        }

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public BigDecimal getPointsUsed() { return pointsUsed; }
        public void setPointsUsed(BigDecimal pointsUsed) { this.pointsUsed = pointsUsed; }
        public long getBidTimeMillis() { return bidTimeMillis; }
        public void setBidTimeMillis(long bidTimeMillis) { this.bidTimeMillis = bidTimeMillis; }
    }

    // Getters and Setters
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public List<AdmittedStudent> getAdmittedStudents() { return admittedStudents; }
    public void setAdmittedStudents(List<AdmittedStudent> admittedStudents) { this.admittedStudents = admittedStudents; }
    public BigDecimal getCutoffScore() { return cutoffScore; }
    public void setCutoffScore(BigDecimal cutoffScore) { this.cutoffScore = cutoffScore; }
    public int getAdmittedCount() { return admittedCount; }
    public void setAdmittedCount(int admittedCount) { this.admittedCount = admittedCount; }
    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }
    public List<Long> getRejectedStudentIds() { return rejectedStudentIds; }
    public void setRejectedStudentIds(List<Long> rejectedStudentIds) { this.rejectedStudentIds = rejectedStudentIds; }

    @Override
    public String toString() {
        return "SettlementMessage{courseId=" + courseId +
                ", admittedCount=" + admittedCount +
                ", rejectedCount=" + rejectedCount +
                ", cutoffScore=" + cutoffScore + "}";
    }
}

