package org.example.handsome.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectionMessage {
    // 操作类型：SELECT（选课）、DROP（退课）
    private String operateType;
    // 学生ID
    private Long studentId;
    // 课程ID
    private Long courseId;
    // 积分变动值（选课为负，退课为正）
    private BigDecimal pointChange;
    // 操作时间
    private Date operateTime;
    // 选课记录ID（退课时需关联删除，选课时为DB自增ID）
    private Long selectionId;
}