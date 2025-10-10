package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.example.handsome.dao.CourseDao;
import org.example.handsome.dao.SelectionDao;
import org.example.handsome.dao.UserDao;
import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.Selection;
import org.example.handsome.pojo.User;
import org.example.handsome.service.AdminCourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminCourseServiceImpl implements AdminCourseService {

    @Resource
    private CourseDao courseDao;
    @Resource
    private SelectionDao selectionDao;
    @Resource
    private UserDao userDao;

    // 校验当前登录用户是否为管理员（复用方法）
    private void checkAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userDao.selectByEmail(auth.getName());
        if (loginUser == null || !User.Role.admin.equals(loginUser.getRole())) {
            throw new SecurityException("仅管理员可操作");
        }
    }


    @Override
    @Transactional
    public Result addCourse(Course course) {
        try {
            checkAdminRole(); // 权限校验

            Course existingCourse = courseDao.getCourseByCourseCode(course.getCourseCode());
            if (existingCourse != null) {
                return Result.fail("课程代码不能相同");
            }

            // 新增课程默认值补充
            course.setStatus(Course.CourseStatus.draft); // 初始状态为草稿
            course.setCurrentStudents(0);                // 初始选课人数0
            course.setVersion(0);                        // 乐观锁初始版本
            course.setCurrentPoints(course.getInitialPoints());

            // 保存课程
            int rows = courseDao.insert(course);
            if (rows > 0) {
                return Result.ok("课程添加成功").put("courseId", course.getId());
            }
            return Result.fail("课程添加失败");
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("添加失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result deleteCourse(Long id) {
        try {
            checkAdminRole();

            // 校验课程是否存在
            Course course = courseDao.selectById(id);
            if (course == null) {
                return Result.fail("课程不存在");
            }

            // 执行删除
            int rows = courseDao.deleteById(id);
            return rows > 0 ? Result.ok("课程删除成功") : Result.fail("删除失败");
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("删除失败：" + e.getMessage());
        }
    }

    @Override
    public Result getCourseById(Long id) {
        try {
            checkAdminRole();

            Course course = courseDao.selectById(id);
            if (course == null) {
                return Result.fail("课程不存在");
            }
            return Result.ok("查询成功").put("course", course);
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result updateCourse(Course course) {
        try {
            checkAdminRole();

            // 校验课程是否存在
            Course existing = courseDao.selectById(course.getId());
            if (existing == null) {
                return Result.fail("课程不存在");
            }

            // 执行更新（乐观锁控制并发）
            int rows = courseDao.update(course);
            if (rows > 0) {
                return Result.ok("课程更新成功").put("course", courseDao.selectById(course.getId()));
            } else {
                return Result.fail("更新失败（可能已被其他管理员修改，请刷新后重试）");
            }
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        }
    }

    @Override
    @Transactional // 事务确保：状态更新 + 积分计算 一致性
    public Result startCourseSelection(Long courseId) {
        try {
            checkAdminRole();

            // 1. 校验课程存在
            Course course = courseDao.selectById(courseId);
            if (course == null) {
                return Result.fail("课程不存在");
            }

            int rows;

            // 2. 乐观锁更新状态：draft → published
            if(course.getStatus().equals(Course.CourseStatus.draft)){
                rows = courseDao.updateStatus(
                        courseId,
                        Course.CourseStatus.draft.name(),       // 原状态（乐观锁条件）
                        Course.CourseStatus.published.name(),    // 目标状态
                        course.getVersion()               // 乐观锁版本
                );
            }
            else{
                rows = courseDao.updateStatus(
                        courseId,
                        Course.CourseStatus.closed.name(),
                        Course.CourseStatus.published.name(),
                        course.getVersion()
                );
            }

            // 3. 状态更新成功 → 触发积分联动（关键：新课程纳入积分计算）
            if (rows > 0) {
                // 3.1 获取所有已发布课程（含刚发布的课程，状态已变为published）
                List<Course> allPublishedCourses = courseDao.selectCoursesByStatus(
                        Course.CourseStatus.published.name()
                );
                // 3.2 调用现有积分计算逻辑，重新分配所有课程积分
                boolean linkageSuccess = CoursePointCalculator.triggerLinkage(
                        allPublishedCourses,
                        courseDao
                );
                // 3.3 积分联动失败 → 回滚事务
                if (!linkageSuccess) {
                    throw new RuntimeException("课程发布成功，但积分重新计算失败，请重试");
                }

                // 4. 返回成功结果（含更新后的课程状态和积分提示）
                return Result.ok("课程已发布，开始选课（积分已重新计算）")
                        .put("courseId", courseId)
                        .put("newStatus", Course.CourseStatus.published.name());
            }

            // 5. 状态更新失败（乐观锁冲突）
            return Result.fail("操作失败（可能已被其他管理员修改，请刷新后重试）");

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("发布课程失败：" + e.getMessage());
        }
    }

    // ------------------------------ 结束课程（新增积分联动）------------------------------
    @Override
    @Transactional // 事务确保：状态更新 + 积分计算 一致性
    public Result endCourseSelection(Long courseId) {
        try {
            checkAdminRole();

            // 1. 校验课程存在且状态为
            Course course = courseDao.selectById(courseId);
            if (course == null) {
                return Result.fail("课程不存在");
            }

            // 2. 乐观锁更新状态：published → closed
            int rows = courseDao.updateStatus(
                    courseId,
                    Course.CourseStatus.published.name(),    // 原状态（乐观锁条件）
                    Course.CourseStatus.closed.name(),       // 目标状态
                    course.getVersion()               // 乐观锁版本
            );

            // 3. 状态更新成功 → 触发积分联动（关键：课程从积分计算中排除）
            if (rows > 0) {
                // 3.1 获取所有已发布课程（不含刚结束的课程，状态已变为closed）
                List<Course> allPublishedCourses = courseDao.selectCoursesByStatus(
                        Course.CourseStatus.published.name()
                );
                // 3.2 调用现有积分计算逻辑，重新分配剩余课程积分
                boolean linkageSuccess = CoursePointCalculator.triggerLinkage(
                        allPublishedCourses,
                        courseDao
                );
                // 3.3 积分联动失败 → 回滚事务
                if (!linkageSuccess) {
                    throw new RuntimeException("课程结束成功，但积分重新计算失败，请重试");
                }

                // 4. 返回成功结果（含更新后的课程状态和积分提示）
                return Result.ok("课程已结束选课（积分已重新计算）")
                        .put("courseId", courseId)
                        .put("newStatus", Course.CourseStatus.closed.name());
            }

            // 5. 状态更新失败（乐观锁冲突）
            return Result.fail("操作失败（可能已被其他管理员修改，请刷新后重试）");

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("结束课程失败：" + e.getMessage());
        }
    }

    @Override
    public Result getStudentsByCourseId(Long courseId) {
        try {
            checkAdminRole();

            // 校验课程是否存在
            Course course = courseDao.selectById(courseId);
            if (course == null) {
                return Result.fail("课程不存在");
            }

            // 查询该课程的所有选课学生
            List<Selection> selections = selectionDao.selectStudentsByCourseId(courseId);
            return Result.ok("查询成功")
                    .put("students", selections) // 包含学生信息和选课时间/积分
                    .put("total", selections.size());
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        }
    }
    // AdminCourseServiceImpl.java
    @Override
    public Result getAllCourses(Integer page, Integer size, String keyword) {
        try {
            checkAdminRole();
            // 计算分页偏移量
            int offset = (page - 1) * size;
            // 调用DAO查询课程
            List<Course> courses = courseDao.selectAllByPage(offset, size, keyword);
            // 查询总条数
            int total = courseDao.countByKeyword(keyword);
            // 封装分页结果
            Map<String, Object> data = new HashMap<>();
            data.put("records", courses);
            data.put("total", total);
            data.put("page", page);
            data.put("size", size);
            return Result.ok("查询成功").put("data", data);
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        }
    }
}