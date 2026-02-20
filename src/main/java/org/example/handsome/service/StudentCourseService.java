package org.example.handsome.service;

import org.example.handsome.pojo.DTO.Result;

import java.math.BigDecimal;

public interface StudentCourseService {
    // 查看所有已发布/已结束的课程（含竞价人数、往年录取分）
    Result listAvailableCourses();

    // 竞价预选课程（学生指定投入积分）
    Result bidCourse(Long courseId, BigDecimal bidPoints);

    // 修改竞价积分
    Result updateBid(Long courseId, BigDecimal newBidPoints);

    // 取消预选（选课期间全额退还）
    Result cancelBid(Long courseId);

    // 查询学生在某课程的竞价信息
    Result getMyBid(Long courseId);
}
