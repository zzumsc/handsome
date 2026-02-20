package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.example.handsome.dao.CourseDao;
import org.example.handsome.dao.StudentPointDao;
import org.example.handsome.dao.UserDao;
import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.User;
import org.example.handsome.service.StudentCourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class StudentCourseServiceImpl implements StudentCourseService {

    private static final Logger log = LoggerFactory.getLogger(StudentCourseServiceImpl.class);

    // ====================== 常量 ======================
    private static final long CACHE_EXPIRE = 30; // 缓存过期时间(分钟)
    private static final long COURSES_AVAILABLE_EXPIRE = 30; // 可用课程列表缓存(秒)

    // ====================== 依赖注入 ======================
    @Resource
    private CourseDao courseDao;

    @Resource
    private StudentPointDao studentPointDao;

    @Resource
    private UserDao userDao;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 用于所有纯数值/字符串的Redis操作（与Lua脚本交互的key），避免JSON序列化器包装 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ====================== Lua脚本 ======================
    private DefaultRedisScript<Long> bidCourseScript;
    private DefaultRedisScript<Long> updateBidScript;
    private DefaultRedisScript<Long> cancelBidScript;

    @PostConstruct
    public void initLuaScripts() {
        bidCourseScript = new DefaultRedisScript<>();
        bidCourseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/bid_course.lua")));
        bidCourseScript.setResultType(Long.class);

        updateBidScript = new DefaultRedisScript<>();
        updateBidScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/update_bid.lua")));
        updateBidScript.setResultType(Long.class);

        cancelBidScript = new DefaultRedisScript<>();
        cancelBidScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/cancel_bid.lua")));
        cancelBidScript.setResultType(Long.class);
    }

    // ====================== 工具方法 ======================

    /**
     * 获取当前登录学生ID并验证身份
     * 积分已在选课开始前通过Pipeline批量预加载，此处仅做兜底检查
     */
    private Long getCurrentStudentId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User student = userDao.selectByEmail(auth.getName());
        if (student == null || !User.Role.student.equals(student.getRole())) {
            log.warn("非学生用户[{}]尝试进行选课操作", auth.getName());
            throw new SecurityException("仅学生可操作");
        }

        // 兜底：若积分未预加载（如新注册学生），从DB补加载
        // 使用StringRedisTemplate存储纯数值字符串，确保Lua脚本tonumber()能正确解析
        String pointKey = RedisKeyUtils.getStudentPointKey(student.getId());
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(pointKey))) {
            BigDecimal dbPoint = studentPointDao.getStudentPointsFromDb(student.getId());
            long scaledPoint = PointUtils.scaleUp(dbPoint != null ? dbPoint : BigDecimal.ZERO);
            stringRedisTemplate.opsForValue().setIfAbsent(pointKey, String.valueOf(scaledPoint));
            log.warn("兜底加载学生[{}]积分（预加载缺失）: {}", student.getId(), scaledPoint);
        }

        return student.getId();
    }

    /**
     * 校验课程是否存在且已发布
     */
    private Course validatePublishedCourse(Long courseId) {
        String courseInfoKey = RedisKeyUtils.getCourseInfoKey(courseId);
        Course course = (Course) redisTemplate.opsForValue().get(courseInfoKey);
        if (course != null) {
            if (course.getStatus() != Course.CourseStatus.published) {
                throw new RuntimeException("仅已发布的课程可以竞价预选");
            }
            return course;
        }

        // 缓存未命中，查数据库
        course = courseDao.selectById(courseId);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        if (course.getStatus() != Course.CourseStatus.published) {
            throw new RuntimeException("仅已发布的课程可以竞价预选");
        }

        // 写入缓存
        redisTemplate.opsForValue().set(courseInfoKey, course, CACHE_EXPIRE, TimeUnit.MINUTES);
        return course;
    }

    // ====================== 核心业务方法 ======================

    /**
     * 竞价预选课程
     */
    @Override
    public Result bidCourse(Long courseId, BigDecimal bidPoints) {
        try {
            Long studentId = getCurrentStudentId();

            // 1. 校验课程
            Course course = validatePublishedCourse(courseId);

            // 2. 校验竞价积分 > 0
            if (bidPoints == null || bidPoints.compareTo(BigDecimal.ZERO) <= 0) {
                return Result.fail("竞价积分必须大于0");
            }

            // 3. 放大积分
            long scaledBidPoints = PointUtils.scaleUp(bidPoints);

            // 4. 执行Lua脚本：原子操作（检查已录取+检查余额+检查重复+扣减积分+记录竞价+递增计数）
            String pointKey = RedisKeyUtils.getStudentPointKey(studentId);
            String bidKey = RedisKeyUtils.getCourseBidKey(courseId);
            String bidCountKey = RedisKeyUtils.getCourseBidCountKey(courseId);
            String admittedKey = RedisKeyUtils.getCourseAdmittedKey(courseId);

            Long result = stringRedisTemplate.execute(
                    bidCourseScript,
                    Arrays.asList(pointKey, bidKey, bidCountKey, admittedKey),
                    studentId.toString(),
                    String.valueOf(scaledBidPoints),
                    String.valueOf(System.currentTimeMillis())
            );

            if (result == null) {
                return Result.fail("系统异常，请重试");
            }

            switch (result.intValue()) {
                case 0:
                    log.info("学生[{}]竞价预选课程[{}]成功，投入积分: {}", studentId, courseId, bidPoints);
                    return Result.ok("竞价预选成功，投入积分：" + bidPoints);
                case 1:
                    return Result.fail("积分不足，无法完成竞价");
                case 2:
                    return Result.fail("已竞价过该课程，请使用修改竞价功能");
                case 3:
                    return Result.fail("您已被该课程录取，无需再次竞价");
                default:
                    return Result.fail("竞价失败，未知错误");
            }

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("竞价预选失败", e);
            return Result.fail("竞价预选失败：" + e.getMessage());
        }
    }

    /**
     * 修改竞价积分
     */
    @Override
    public Result updateBid(Long courseId, BigDecimal newBidPoints) {
        try {
            Long studentId = getCurrentStudentId();

            // 1. 校验课程
            validatePublishedCourse(courseId);

            // 2. 校验新积分 > 0
            if (newBidPoints == null || newBidPoints.compareTo(BigDecimal.ZERO) <= 0) {
                return Result.fail("竞价积分必须大于0");
            }

            // 3. 放大积分
            long scaledNewPoints = PointUtils.scaleUp(newBidPoints);

            // 4. 执行Lua脚本：原子操作（读取旧值+计算差价+调整余额+更新Hash）
            String pointKey = RedisKeyUtils.getStudentPointKey(studentId);
            String bidKey = RedisKeyUtils.getCourseBidKey(courseId);

            Long result = stringRedisTemplate.execute(
                    updateBidScript,
                    Arrays.asList(pointKey, bidKey),
                    studentId.toString(),
                    String.valueOf(scaledNewPoints),
                    String.valueOf(System.currentTimeMillis())
            );

            if (result == null) {
                return Result.fail("系统异常，请重试");
            }

            switch (result.intValue()) {
                case 0:
                    log.info("学生[{}]修改课程[{}]竞价为: {}", studentId, courseId, newBidPoints);
                    return Result.ok("竞价修改成功，当前投入积分：" + newBidPoints);
                case 1:
                    return Result.fail("积分不足，无法加价");
                case 2:
                    return Result.fail("未找到竞价记录，请先预选课程");
                default:
                    return Result.fail("修改竞价失败，未知错误");
            }

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("修改竞价失败", e);
            return Result.fail("修改竞价失败：" + e.getMessage());
        }
    }

    /**
     * 取消预选（全额退还积分）
     */
    @Override
    public Result cancelBid(Long courseId) {
        try {
            Long studentId = getCurrentStudentId();

            // 1. 校验课程存在且已发布
            validatePublishedCourse(courseId);

            // 2. 执行Lua脚本：原子操作（读取竞价额+退还积分+删除记录+递减计数）
            String pointKey = RedisKeyUtils.getStudentPointKey(studentId);
            String bidKey = RedisKeyUtils.getCourseBidKey(courseId);
            String bidCountKey = RedisKeyUtils.getCourseBidCountKey(courseId);

            Long result = stringRedisTemplate.execute(
                    cancelBidScript,
                    Arrays.asList(pointKey, bidKey, bidCountKey),
                    studentId.toString()
            );

            if (result == null) {
                return Result.fail("系统异常，请重试");
            }

            switch (result.intValue()) {
                case 0:
                    log.info("学生[{}]取消课程[{}]预选成功", studentId, courseId);
                    return Result.ok("取消预选成功，积分已全额退还");
                case 1:
                    return Result.fail("未找到竞价记录，无需取消");
                default:
                    return Result.fail("取消预选失败，未知错误");
            }

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("取消预选失败", e);
            return Result.fail("取消预选失败：" + e.getMessage());
        }
    }

    /**
     * 查询学生在某课程的竞价信息
     */
    @Override
    public Result getMyBid(Long courseId) {
        try {
            Long studentId = getCurrentStudentId();

            // 先检查是否已被往期录取
            String admittedKey = RedisKeyUtils.getCourseAdmittedKey(courseId);
            Boolean isAdmitted = stringRedisTemplate.opsForSet().isMember(admittedKey, studentId.toString());
            if (Boolean.TRUE.equals(isAdmitted)) {
                return Result.ok("已录取").put("hasBid", false).put("admitted", true);
            }

            String bidKey = RedisKeyUtils.getCourseBidKey(courseId);
            Object bidData = stringRedisTemplate.opsForHash().get(bidKey, studentId.toString());

            if (bidData == null) {
                return Result.ok("未竞价").put("hasBid", false);
            }

            // 解析JSON: {"points":15000,"time":1708300000000}
            String bidJson = bidData.toString();
            // 简单解析
            long scaledPoints = Long.parseLong(bidJson.replaceAll(".*\"points\":(\\d+).*", "$1"));
            long time = Long.parseLong(bidJson.replaceAll(".*\"time\":(\\d+).*", "$1"));
            BigDecimal actualPoints = PointUtils.scaleDown(scaledPoints);

            Map<String, Object> bidInfo = new HashMap<>();
            bidInfo.put("courseId", courseId);
            bidInfo.put("bidPoints", actualPoints);
            bidInfo.put("bidTime", new Date(time));
            bidInfo.put("hasBid", true);

            return Result.ok("查询成功").put("bid", bidInfo);

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("查询竞价信息失败", e);
            return Result.fail("查询竞价信息失败：" + e.getMessage());
        }
    }

    /**
     * 查看所有可选课程（含竞价人数、往年录取分）
     */
    @Override
    public Result listAvailableCourses() {
        try {
            String cacheKey = RedisKeyUtils.COURSES_AVAILABLE_KEY;

            // 先查缓存
            @SuppressWarnings("unchecked")
            List<Course> courses = (List<Course>) redisTemplate.opsForValue().get(cacheKey);
            if (courses != null && !courses.isEmpty()) {
                // 附加竞价人数（从Redis实时读取）
                enrichBidCounts(courses);
                return Result.ok("查询成功").put("courses", courses);
            }

            // 缓存未命中，查数据库
            courses = courseDao.selectAvailableCourses();
            if (courses != null && !courses.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, courses, COURSES_AVAILABLE_EXPIRE, TimeUnit.SECONDS);
                enrichBidCounts(courses);
            }

            return Result.ok("查询成功").put("courses", courses != null ? courses : Collections.emptyList());

        } catch (Exception e) {
            log.error("查询可用课程失败", e);
            return Result.fail("查询失败：" + e.getMessage());
        }
    }

    /**
     * 为课程列表附加实时竞价人数（从Redis读取）
     */
    private void enrichBidCounts(List<Course> courses) {
        for (Course course : courses) {
            if (course.getStatus() == Course.CourseStatus.published) {
                String bidCountKey = RedisKeyUtils.getCourseBidCountKey(course.getId());
                String countStr = stringRedisTemplate.opsForValue().get(bidCountKey);
                int count = 0;
                if (countStr != null) {
                    count = Integer.parseInt(countStr);
                }
                course.setBidCount(count);
            }
        }
    }
}
