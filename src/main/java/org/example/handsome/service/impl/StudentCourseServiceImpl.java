package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.example.handsome.dao.CourseDao;
import org.example.handsome.dao.SelectionDao;
import org.example.handsome.dao.StudentPointDao;
import org.example.handsome.dao.UserDao;
import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.Selection;
import org.example.handsome.pojo.User;
import org.example.handsome.service.StudentCourseService;
//import org.springframework.retry.annotation.Retryable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class StudentCourseServiceImpl implements StudentCourseService {

    @Resource
    private CourseDao courseDao;
    @Resource
    private SelectionDao selectionDao;
    @Resource
    private StudentPointDao studentPointDao;
    @Resource
    private UserDao userDao;

    // 重试次数：乐观锁冲突时重试2次
    private static final int RETRY_TIMES = 2;

    /**
     * 核心：获取当前登录学生ID
     */
    private Long getCurrentStudentId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User student = userDao.selectByEmail(auth.getName());
        if (student == null || !User.Role.student.equals(student.getRole())) {
            throw new SecurityException("仅学生可操作");
        }
        return student.getId();
    }

    /**
     * 学生选课：新增选课记录+更新课程人数+触发积分联动
     */
    @Override
    @Transactional
    //@Retryable(maxAttempts = RETRY_TIMES, retryFor = {Exception.class}) // 乐观锁冲突时重试
    public Result selectCourse(Long courseId) {
        Long studentId = getCurrentStudentId();
        int retryCount = 0;

        try {
            // 1. 校验课程状态和信息
            Course course = courseDao.selectById(courseId);
            if (course == null) {
                return Result.fail("课程不存在");
            }
            if (course.getStatus() != Course.CourseStatus.published) {
                return Result.fail("仅已发布的课程可选择");
            }

            // 2. 校验是否已选该课程
            int exists = selectionDao.checkSelectionExists(studentId, courseId);
            if (exists > 0) {
                return Result.fail("已选该课程，无需重复选择");
            }

            // 3. 校验课程是否满员
            if (course.getCurrentStudents() >= course.getMaxStudents()) {
                return Result.fail("课程已选满，无法选择");
            }

            // 4. 校验学生积分是否足够（当前课程的current_points）
            BigDecimal requiredPoints = course.getCurrentPoints();
            BigDecimal studentPoints = studentPointDao.getStudentPoints(studentId);
            if (studentPoints.compareTo(requiredPoints) < 0) {
                return Result.fail("积分不足，当前课程需" + requiredPoints + "积分，您当前有" + studentPoints + "积分");
            }

            // 5. 扣减学生积分（负数表示扣减）
            int pointRows = studentPointDao.updateStudentPoints(studentId, requiredPoints.negate());
            if (pointRows <= 0) {
                throw new RuntimeException("积分扣减失败");
            }

            // 6. 增加课程当前人数（乐观锁控制，防止超卖）
            int courseRows = courseDao.incrementStudentCount(courseId, course.getVersion());
            if (courseRows <= 0) {
                // 乐观锁冲突，重试（由@Retryable触发）
                if (retryCount >= RETRY_TIMES) {
                    throw new RuntimeException("选课人数更新失败，可能已被抢满，请重试");
                }
                retryCount++;
                throw new RuntimeException("乐观锁冲突，重试中...");
            }

            // 7. 创建选课记录
            Selection selection = new Selection();
            selection.setStudentId(studentId);
            selection.setCourseId(courseId);
            selection.setSelectionTime(new Date());
            selection.setPointsUsed(requiredPoints);
            int insertRows = selectionDao.insert(selection);
            if (insertRows <= 0) {
                throw new RuntimeException("选课记录创建失败");
            }

            // 8. 触发全局积分联动（关键步骤）
            List<Course> allPublishedCourses = courseDao.selectCoursesByStatus(Course.CourseStatus.published.name());
            boolean linkageSuccess = CoursePointCalculator.triggerLinkage(allPublishedCourses, courseDao);
            if (!linkageSuccess) {
                // 积分更新失败（乐观锁冲突），重试
                if (retryCount >= RETRY_TIMES) {
                    throw new RuntimeException("积分联动失败，请重试");
                }
                retryCount++;
                throw new RuntimeException("积分更新冲突，重试中...");
            }

            // 9. 返回结果（查询最新积分）
            BigDecimal remainingPoints = studentPointDao.getStudentPoints(studentId);
            return Result.ok("选课成功，消耗积分：" + requiredPoints)
                    .put("remainingPoints", remainingPoints)
                    .put("courseCurrentPoints", courseDao.selectById(courseId).getCurrentPoints());

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("选课失败：" + e.getMessage());
        }
    }

    /**
     * 学生退课：删除选课记录+更新课程人数+触发积分联动（返还80%积分）
     */
    @Override
    @Transactional
    //@Retryable(maxAttempts = RETRY_TIMES, retryFor = {Exception.class})
    public Result dropCourse(Long courseId) {
        Long studentId = getCurrentStudentId();
        int retryCount = 0;

        try {
            // 1. 校验课程和选课记录
            Course course = courseDao.selectById(courseId);
            if (course == null) {
                return Result.fail("课程不存在");
            }
            Selection selection = selectionDao.selectByStudentAndCourse(studentId, courseId);
            if (selection == null) {
                return Result.fail("未选择该课程，无法退课");
            }

            // 2. 计算返还积分（消耗积分的80%，保留3位小数）
            BigDecimal pointsUsed = selection.getPointsUsed();
            BigDecimal refundPoints = pointsUsed.multiply(new BigDecimal("0.8")).setScale(3, BigDecimal.ROUND_HALF_UP);

            // 3. 返还学生积分
            int pointRows = studentPointDao.updateStudentPoints(studentId, refundPoints);
            if (pointRows <= 0) {
                throw new RuntimeException("积分返还失败");
            }

            // 4. 减少课程当前人数（乐观锁控制）
            int courseRows = courseDao.decrementStudentCount(courseId, course.getVersion());
            if (courseRows <= 0) {
                if (retryCount >= RETRY_TIMES) {
                    throw new RuntimeException("课程人数更新失败，请重试");
                }
                retryCount++;
                throw new RuntimeException("乐观锁冲突，重试中...");
            }

            // 5. 删除选课记录
            int deleteRows = selectionDao.deleteByStudentAndCourse(studentId, courseId);
            if (deleteRows <= 0) {
                throw new RuntimeException("选课记录删除失败");
            }

            // 6. 触发全局积分联动
            List<Course> allPublishedCourses = courseDao.selectCoursesByStatus(Course.CourseStatus.published.name());
            boolean linkageSuccess = CoursePointCalculator.triggerLinkage(allPublishedCourses, courseDao);
            if (!linkageSuccess) {
                if (retryCount >= RETRY_TIMES) {
                    throw new RuntimeException("积分联动失败，请重试");
                }
                retryCount++;
                throw new RuntimeException("积分更新冲突，重试中...");
            }

            // 7. 返回结果
            BigDecimal remainingPoints = studentPointDao.getStudentPoints(studentId);
            return Result.ok("退课成功，返还积分：" + refundPoints)
                    .put("remainingPoints", remainingPoints)
                    .put("courseCurrentPoints", courseDao.selectById(courseId).getCurrentPoints());

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("退课失败：" + e.getMessage());
        }
    }

    // 其他方法（查看可用课程）省略...
    @Override
    public Result listAvailableCourses() {
        List<Course> courses = courseDao.selectAvailableCourses();
        return Result.ok("查询成功").put("courses", courses);
    }
}