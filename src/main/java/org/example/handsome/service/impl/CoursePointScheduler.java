package org.example.handsome.service.impl;

import org.example.handsome.dao.CourseDao;
import org.example.handsome.pojo.Course;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CoursePointScheduler {

    @Autowired
    private CourseDao courseDao;

    @Scheduled(cron = "*/10 * * * * ?")
    public void scheduleCoursePointCalculation() {
        // 调用计算方法
        List<Course> allPublishedCourses = courseDao.selectCoursesByStatus(
            Course.CourseStatus.published.name()
        );
        // 3.2 调用现有积分计算逻辑，重新分配所有课程积分
        boolean linkageSuccess = CoursePointCalculator.triggerLinkage(allPublishedCourses, courseDao);
        // 3.3 积分联动失败 → 回滚事务
        if (!linkageSuccess) {
            throw new RuntimeException("积分计算失败，请重试");
        }
    }
}