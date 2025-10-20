package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.example.handsome.dao.CourseDao;
import org.example.handsome.dao.SelectionDao;
import org.example.handsome.dao.StudentPointDao;
import org.example.handsome.dao.UserDao;
import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.Selection;
import org.example.handsome.pojo.SelectionMessage;
import org.example.handsome.pojo.User;
import org.example.handsome.service.StudentCourseService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
public class StudentCourseServiceImpl implements StudentCourseService {

    private static final Logger log = LoggerFactory.getLogger(StudentCourseServiceImpl.class);

    // ====================== 常量定义 ======================
    /** 热门课程判断阈值 - 剩余名额小于此值则视为热门课程 */
    private static final int HOT_COURSE_THRESHOLD = 10;
    /** 热门课程最大排队长度 */
    private static final int MAX_QUEUE_LENGTH = 50;
    /** 缓存过期时间(分钟) */
    private static final long CACHE_EXPIRE = 30;
    /** 选课标记缓存过期时间(小时) */
    private static final long SELECTION_FLAG_EXPIRE = 1;
    /** 可用课程列表缓存过期时间(秒) */
    private static final long COURSES_AVAILABLE_EXPIRE = 30;
    // 分布式锁过期时间（应大于数据库查询最大耗时）
    private static final long LOCK_EXPIRE = 30;
    // 锁的键前缀
    private static final String LOCK_KEY_PREFIX = "lock:courses:";
    // ====================== 常量定义 ======================
    private static final String COURSE_OPERATE_TOPIC = "student_course_operate_topic";
    private static final String OPERATE_TYPE_SELECT = "SELECT"; // 选课操作
    private static final String OPERATE_TYPE_DROP = "DROP";     // 退课操作

    // ====================== 依赖注入 ======================
// 新增：RocketMQ模板（Spring封装的工具类，简化消息发送）
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    // ====================== 依赖注入 ======================
    @Resource
    private CourseDao courseDao;

    @Resource
    private SelectionDao selectionDao;

    @Resource
    private StudentPointDao studentPointDao;

    @Resource
    private UserDao userDao;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // ====================== 核心工具方法 ======================
    // 通用消息发送方法：发送选课/退课消息到RocketMQ

    private void sendCourseOperateMessage(SelectionMessage message) {
        try {
            // 发送消息到指定主题+标签（标签=操作类型，便于消费端按类型过滤）
            rocketMQTemplate.convertAndSend(
                    COURSE_OPERATE_TOPIC + ":" + message.getOperateType(),
                    message
            );
            log.info("RocketMQ消息发送成功：操作类型[{}]，学生[{}]，课程[{}]",
                    message.getOperateType(), message.getStudentId(), message.getCourseId());
        } catch (Exception e) {
            // 消息发送失败：记录日志（后续可扩展本地消息表+定时重试，确保消息不丢失）
            log.error("RocketMQ消息发送失败：操作类型[{}]，学生[{}]，课程[{}]",
                    message.getOperateType(), message.getStudentId(), message.getCourseId(), e);
        }
    }
    //获取当前登录学生ID并验证身份
    private Long getCurrentStudentId() {
        // 从Security上下文获取当前登录用户认证信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // 通过邮箱查询用户信息
        User student = userDao.selectByEmail(auth.getName());

        // 验证用户是否存在且角色为学生
        if (student == null || !User.Role.student.equals(student.getRole())) {
            log.warn("非学生用户[{}]尝试进行选课操作", auth.getName());
            throw new SecurityException("仅学生可操作");
        }

        // 初始化学生积分缓存
        String pointKey = RedisKeyUtils.getStudentPointKey(student.getId());
        if (!redisTemplate.hasKey(pointKey)) {
            BigDecimal dbPoint = studentPointDao.getStudentPointsFromDb(student.getId());
            // 使用工具类进行积分放大处理
            long scaledPoint = PointUtils.scaleUp(dbPoint);
            redisTemplate.opsForValue().set(pointKey, scaledPoint, CACHE_EXPIRE, TimeUnit.MINUTES);
            log.debug("初始化学生[{}]积分缓存: {}", student.getId(), scaledPoint);
        }

        return student.getId();
    }

    //校验课程有效性并初始化缓存
    private Course validateCourse(Long courseId) {
        String courseInfoKey = RedisKeyUtils.getCourseInfoKey(courseId);
        // 1. 先查缓存（无锁，快速返回）
        Course course = (Course) redisTemplate.opsForValue().get(courseInfoKey);

        // 缓存命中且课程已发布：直接返回（原有逻辑不变）
        if (course != null) {
            if (course.getStatus() != Course.CourseStatus.published) {
                throw new RuntimeException("仅已发布的课程可选择");
            }
            return course;
        }

        // 2. 缓存未命中：加分布式锁，控制数据库查询+写缓存的原子性
        String lockKey = LOCK_KEY_PREFIX + "validate:" + courseId; // 锁key绑定课程ID，细粒度控制
        String lockValue = UUID.randomUUID().toString();
        boolean isLocked = false;
        try {
            // 尝试获取锁（30秒过期，防止死锁；5秒等待，避免频繁抢锁）
            isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 30, TimeUnit.SECONDS);
            if (isLocked) {
                // 3. 双重检查缓存：防止等待锁期间其他线程已更新缓存
                course = (Course) redisTemplate.opsForValue().get(courseInfoKey);
                if (course != null) {
                    if (course.getStatus() != Course.CourseStatus.published) {
                        throw new RuntimeException("仅已发布的课程可选择");
                    }
                    return course;
                }

                // 4. 真正执行数据库查询（原有逻辑不变）
                course = courseDao.selectById(courseId);
                if (course == null || course.getStatus() != Course.CourseStatus.published) {
                    // 缓存空值防穿透
                    redisTemplate.opsForValue().set(courseInfoKey, null, CACHE_EXPIRE, TimeUnit.MINUTES);
                    return null;
                }

                // 5. 写缓存（原有逻辑不变）
                redisTemplate.opsForValue().set(courseInfoKey, course, CACHE_EXPIRE, TimeUnit.MINUTES);
                String remainKey = RedisKeyUtils.getCourseRemainKey(courseId);
                long remainQuota = selectionDao.remainStudents(courseId);
                redisTemplate.opsForValue().set(remainKey, remainQuota, CACHE_EXPIRE, TimeUnit.MINUTES);
                log.debug("初始化课程[{}]缓存，剩余名额: {}", courseId, remainQuota);
            } else {
                // 6. 未获取到锁：等待100ms后递归重试（或循环重试），直到获取缓存
                Thread.sleep(100);
                return validateCourse(courseId); // 递归重试，确保最终能读到缓存
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("课程[{}]缓存初始化等待锁被中断", courseId, e);
            throw new RuntimeException("课程校验失败，请重试");
        } finally {
            // 7. 释放锁：仅锁持有者能释放（避免误删其他线程的锁）
            if (isLocked) {
                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                        Collections.singletonList(lockKey), lockValue);
            }
        }

        return course;
    }

    //检查学生是否已选该课程
    private boolean hasSelectedCourse(Long studentId, Long courseId) {
        String selectedFlagKey = RedisKeyUtils.getSelectedFlagKey(studentId, courseId);
        // 先查缓存
        Boolean hasSelected = (Boolean) redisTemplate.opsForValue().get(selectedFlagKey);
        if (hasSelected != null) {
            return hasSelected;
        }

        // 缓存未命中，查数据库
        int count = selectionDao.checkSelectionExists(studentId, courseId);
        boolean result = count > 0;
        // 更新缓存
        redisTemplate.opsForValue().set(selectedFlagKey, result, SELECTION_FLAG_EXPIRE, TimeUnit.HOURS);

        return result;
    }

    //学生选课
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result selectCourse(Long courseId, Long studentId) {

        String remainKey = RedisKeyUtils.getCourseRemainKey(courseId);
        String pointKey = RedisKeyUtils.getStudentPointKey(studentId);
        String coursePointKey = RedisKeyUtils.getCoursePointKey(courseId);
        String selectedFlagKey = RedisKeyUtils.getSelectedFlagKey(studentId, courseId);
        String courseInfoKey = RedisKeyUtils.getCourseInfoKey(courseId);
        try {
            // 1. 基础校验
            Course course = validateCourse(courseId);
            if (course == null) {
                return Result.fail("课程不存在");
            }

            // 检查是否已选该课程
            if (hasSelectedCourse(studentId, courseId)) {
                return Result.fail("已选该课程，无需重复选择");
            }

            // 3. 预占课程名额
            String lockKey = LOCK_KEY_PREFIX + "preoccupy:" + courseId;
            RLock preoccupyLock = redissonClient.getLock(lockKey);

            try {
                // 3.2 尝试获取锁：最多等待2秒（避免用户长时间阻塞），获取后30秒自动释放（看门狗会续期）
                boolean isLocked = preoccupyLock.tryLock(2, 30, TimeUnit.SECONDS);
                if (!isLocked) {
                    // 未获取到锁：直接返回，避免并发争抢过度
                    return Result.fail("系统繁忙，请稍后重试");
                }
                //查缓存，没有则插入
                Integer remain = (Integer) redisTemplate.opsForValue().get(remainKey);
                if(remain == null) {
                    remain=selectionDao.remainStudents(courseId);
                    redisTemplate.opsForValue().set(remainKey, remain, CACHE_EXPIRE, TimeUnit.SECONDS);
                }
                //判断剩余名额
                if (remain<=0) {
                    return Result.fail("课程已选满，无法选择");
                }
                // 3.3.2 Redis原子扣减库存
                redisTemplate.opsForValue().decrement(remainKey);

            } catch (InterruptedException e) {
                // 捕获线程中断异常，恢复中断状态
                Thread.currentThread().interrupt();
                log.error("学生[{}]选课获取锁被中断，课程[{}]", studentId, courseId, e);
                return Result.fail("选课请求被中断，请重试");
            } finally {
                // 3.4 释放锁：仅当前线程持有锁时才释放，避免误删其他线程的锁
                if (preoccupyLock.isHeldByCurrentThread()) {
                    preoccupyLock.unlock();
                    log.debug("释放课程[{}]预占锁，学生[{}]", courseId, studentId);
                }
            }

            // 4. 积分校验与扣减
            BigDecimal requiredPoints = (BigDecimal) redisTemplate.opsForValue().get(coursePointKey);
            if (requiredPoints == null) {
                requiredPoints = course.getCurrentPoints();
                redisTemplate.opsForValue().set(coursePointKey, requiredPoints, CACHE_EXPIRE, TimeUnit.MINUTES);
            }

            // 计算所需积分（放大后）
            long scaledRequired = PointUtils.scaleUp(requiredPoints);

            // 检查积分是否足够
//            Object scaledPointObj = redisTemplate.opsForValue().get(pointKey);
//            Long scaledPoint = scaledPointObj != null ? ((Number) scaledPointObj).longValue() : null;
//            if (scaledPoint == null || scaledPoint < scaledRequired) {
//                // 积分不足，回滚名额
//
//                redisTemplate.opsForValue().increment(remainKey);
//                BigDecimal actualPoint = PointUtils.scaleDown(scaledPoint);
//                return Result.fail("积分不足：需" + requiredPoints + "，当前" + actualPoint);
//            }

            // 5 记录选课信息
            Selection selection = new Selection();
            SelectionMessage selectMsg = new SelectionMessage();
            selectMsg.setOperateType(OPERATE_TYPE_SELECT);       // 操作类型：选课
            selectMsg.setStudentId(studentId);                   // 学生ID
            selectMsg.setCourseId(courseId);                     // 课程ID
            selectMsg.setPointChange(requiredPoints.negate());   // 积分变动：负（扣减）
            selectMsg.setOperateTime(new Date());                // 操作时间
            selectMsg.setSelectionId(selection.getId());         // 选课记录ID（DB自增后获取）
            sendCourseOperateMessage(selectMsg);                 // 调用工具方法发送

            log.info("学生[{}]成功选择课程[{}]，扣减积分: {}", studentId, courseId, requiredPoints);

            // 6. 后删除缓存（保证缓存数据最新）
            redisTemplate.delete(pointKey);          // 删除学生积分缓存

            // 7. 返回结果
            return Result.ok("选课成功，消耗积分：" + requiredPoints);

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            // 异常情况下回滚Redis操作
            try {
                redisTemplate.opsForValue().increment(remainKey);
                redisTemplate.delete(selectedFlagKey);
                redisTemplate.delete(selectedFlagKey);
            } catch (Exception ex) {
                log.error("选课异常回滚失败", ex);
            }
            log.error("学生[{}]选课失败", studentId, e);
            return Result.fail("选课失败：" + e.getMessage());
        }
    }

    //学生退课
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result dropCourse(Long courseId) {
        Long studentId = getCurrentStudentId();
        String remainKey = RedisKeyUtils.getCourseRemainKey(courseId);
        String pointKey = RedisKeyUtils.getStudentPointKey(studentId);
        String selectedFlagKey = RedisKeyUtils.getSelectedFlagKey(studentId, courseId);
        String courseInfoKey = RedisKeyUtils.getCourseInfoKey(courseId);

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

            // 2. 计算返还积分（80%）
            BigDecimal usedPoints = selection.getPointsUsed();
            BigDecimal refundPoints = usedPoints.multiply(new BigDecimal("0.8"))
                    .setScale(3, PointUtils.ROUNDING_MODE);
//
//            // 3. 先更新数据库
//            // 3.1 返还学生积分
//            BigDecimal newPoints = refundPoints;
//            studentPointDao.updateStudentPoints(studentId, newPoints,redisTemplate);
//
//            // 3.2 减少课程当前选课人数
//            //TODO 乐观锁版本号//////////////////////////////////////////////////////////////////////
//            courseDao.decrementStudentCount(courseId,0);
//
//            selectionDao.deleteByStudentAndCourse(studentId, courseId);

            SelectionMessage dropMsg = new SelectionMessage();
            dropMsg.setOperateType(OPERATE_TYPE_DROP);           // 操作类型：退课
            dropMsg.setStudentId(studentId);                     // 学生ID
            dropMsg.setCourseId(courseId);                       // 课程ID
            dropMsg.setPointChange(refundPoints);                // 积分变动：正（返还）
            dropMsg.setOperateTime(new Date());                  // 操作时间
            dropMsg.setSelectionId(selection.getId());           // 关联的选课记录ID
            sendCourseOperateMessage(dropMsg);                   // 调用工具方法发送

            log.info("学生[{}]成功退选课程[{}]，返还积分: {}", studentId, courseId, refundPoints);

            // 4. 后删除缓存
            redisTemplate.delete(pointKey);          // 删除学生积分缓存
            redisTemplate.delete(remainKey);         // 删除课程剩余名额缓存
            redisTemplate.delete(courseInfoKey);     // 删除课程信息缓存
            redisTemplate.delete(selectedFlagKey);   // 删除选课标记缓存

            // 5. 返回结果
            return Result.ok("退课成功，返还积分：" + refundPoints);

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            // 异常情况下回滚Redis操作
            try {
                redisTemplate.opsForValue().decrement(remainKey);
            } catch (Exception ex) {
                log.error("退课异常回滚失败", ex);
            }
            log.error("学生[{}]退课失败", studentId, e);
            return Result.fail("退课失败：" + e.getMessage());
        }
    }
    @Override
    public Result listAvailableCourses() {
        String cacheKey = "courses:available";
        String lockKey = LOCK_KEY_PREFIX + cacheKey;

        // 先查缓存
        List<Course> courses = (List<Course>) redisTemplate.opsForValue().get(cacheKey);
        if (courses != null && !courses.isEmpty()) {
            log.debug("从缓存获取可用课程列表，数量: {}", courses.size());
            return Result.ok("查询成功").put("courses", courses);
        }

        // 缓存未命中，尝试获取分布式锁
        String lockValue = UUID.randomUUID().toString();
        boolean isLocked = false;
        try {
            // 尝试获取锁，设置锁过期时间防止死锁
            isLocked = tryLock(lockKey, lockValue, LOCK_EXPIRE, TimeUnit.SECONDS);

            if (isLocked) {
                // 双重检查缓存，防止释放锁前已有其他线程更新了缓存
                courses = (List<Course>) redisTemplate.opsForValue().get(cacheKey);
                if (courses != null && !courses.isEmpty()) {
                    log.debug("双重检查：从缓存获取可用课程列表，数量: {}", courses.size());
                    return Result.ok("查询成功").put("courses", courses);
                }

                // 锁获取成功，查询数据库
                courses = courseDao.selectAvailableCourses();
                // 更新缓存
                redisTemplate.opsForValue().set(cacheKey, courses, COURSES_AVAILABLE_EXPIRE, TimeUnit.SECONDS);
                log.debug("从数据库获取可用课程列表，数量: {}", courses.size());
                return Result.ok("查询成功").put("courses", courses);
            } else {
                // 未获取到锁，等待一段时间后重试
                log.debug("未获取到锁，等待后重试");
                // 短暂休眠，避免频繁尝试获取锁
                TimeUnit.MILLISECONDS.sleep(50);
                // 递归重试，也可以使用循环实现
                return listAvailableCourses();
            }
        } catch (InterruptedException e) {
            log.error("获取锁过程被中断", e);
            Thread.currentThread().interrupt();
            return Result.fail("查询失败");
        } finally {
            // 释放锁（只有持有锁的线程才能释放）
            if (isLocked) {
                releaseLock(lockKey, lockValue);
            }
        }
    }
    private boolean tryLock(String key, String value, long expire, TimeUnit unit) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, expire, unit);
        return Boolean.TRUE.equals(result);
    }

    private void releaseLock(String key, String value) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

        Long result = redisTemplate.execute(redisScript, Collections.singletonList(key), value);
        if (result != null && result > 0) {
            log.debug("成功释放锁: {}", key);
        } else {
            log.warn("释放锁失败，可能锁已过期或被其他线程持有: {}", key);
        }
    }
}
