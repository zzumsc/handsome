package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.example.handsome.dao.CourseDao;
import org.example.handsome.dao.SelectionDao;
import org.example.handsome.dao.StudentPointDao;
import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.Selection;
import org.example.handsome.pojo.SelectionMessage;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

import java.math.BigDecimal;

@Component
@RocketMQMessageListener(
        topic = "student_course_operate_topic", // 与发送端主题一致
        consumerGroup = "student_course_db_consumer", // 消费组（独立组，避免与其他消费者冲突）
        selectorExpression = "*" // 监听所有标签（SELECT/DROP）
)
public class SelectionConsumer implements RocketMQListener<SelectionMessage> {
    private static final Logger log = LoggerFactory.getLogger(SelectionConsumer.class);
    // 注入DAO层：用于更新数据库
    @Autowired
    private StudentPointDao studentPointDao;
    @Autowired
    private CourseDao courseDao;
    @Autowired
    private SelectionDao selectionDao;
    @Resource
    RedisTemplate<String,Object> redisTemplate;

    @Override
    public void onMessage(SelectionMessage message) {
        log.info("开始消费消息：操作类型[{}]，学生[{}]，课程[{}]",
                message.getOperateType(), message.getStudentId(), message.getCourseId());

        try {
            // 分支1：处理选课消息（SELECT）
            if (OPERATE_TYPE_SELECT.equals(message.getOperateType())) {
                handleSelectCourseMessage(message);
            }
            // 分支2：处理退课消息（DROP）
            else if (OPERATE_TYPE_DROP.equals(message.getOperateType())) {
                handleDropCourseMessage(message);
            }
            // 分支3：未知操作类型（忽略或报警）
            else {
                log.warn("未知消息操作类型：{}，消息内容：{}", message.getOperateType(), message);
                return;
            }

            log.info("消息消费成功：操作类型[{}]，学生[{}]，课程[{}]",
                    message.getOperateType(), message.getStudentId(), message.getCourseId());

        } catch (Exception e) {
            log.error("消息消费失败：操作类型[{}]，学生[{}]，课程[{}]",
                    message.getOperateType(), message.getStudentId(), message.getCourseId(), e);
            // 抛出异常：触发RocketMQ重试（重试次数由retryTimesWhenConsumeFailed控制）
        }
    }

    /**
     * 处理选课消息：更新3张表（选课记录、学生积分、课程人数）
     */
    // 注入Redisson客户端（Spring Boot自动配置，直接注入即可）
    @Autowired
    private RedissonClient redissonClient;

    // 常量：分布式锁的key前缀（避免与其他锁冲突）
    private static final String COURSE_UPDATE_LOCK_PREFIX = "lock:course:update:";

    @Transactional(rollbackFor = Exception.class)
    public void handleSelectCourseMessage(SelectionMessage message) {
        int maxRetry = 3;
        int retryCount = 0;
        Long studentId = message.getStudentId();
        Long courseId = message.getCourseId();
        BigDecimal pointChange = message.getPointChange();
        // 1. 生成当前课程的分布式锁key（细粒度锁：一个课程一个锁）
        String lockKey = COURSE_UPDATE_LOCK_PREFIX + courseId;
        // 获取Redisson分布式锁（可重入锁，默认30秒看门狗自动续期）
        RLock courseLock = redissonClient.getLock(lockKey);

        try {
            while (retryCount < maxRetry) {
                // 2. 尝试获取锁：最多等待5秒（waitTime），获取后30秒自动释放（leaseTime，看门狗会续期）
                boolean isLockAcquired = courseLock.tryLock(5, 30, java.util.concurrent.TimeUnit.SECONDS);
                if (!isLockAcquired) {
                    // 未获取到锁：重试次数+1，短暂休眠后重试
                    retryCount++;
                    log.warn("选课获取课程锁失败：课程ID={}，重试次数={}", courseId, retryCount);
                    Thread.sleep(100 * retryCount); // 重试间隔递增，减少冲突
                    continue;
                }

                try {
                    // 3. 成功获取锁：执行DB操作（事务内）
                    // 3.1 新增选课记录
                    Selection selection = new Selection();
                    selection.setStudentId(studentId);
                    selection.setCourseId(courseId);
                    selection.setPointsUsed(pointChange.negate());
                    selection.setSelectionTime(message.getOperateTime());
                    selectionDao.insert(selection);

                    // 3.2 扣减学生积分
                    studentPointDao.updateStudentPoints(studentId, pointChange, redisTemplate);

                    // 3.3 更新课程人数（此时无并发冲突，因锁已控制）
                    int updateRows = courseDao.incrementStudentCount(courseId);
                    if (updateRows > 0) {
                        // 更新成功：跳出循环，结束重试
                        log.info("选课成功：课程ID={}，学生ID={}", courseId, studentId);
                        break;
                    } else {
                        // 更新失败（如课程已选满）：重试次数+1
                        retryCount++;
                        log.warn("选课更新课程人数失败：课程ID={}，重试次数={}", courseId, retryCount);
                        // 重试前需回滚当前事务内的DB操作（抛出异常触发事务回滚）
                        if (retryCount >= maxRetry) {
                            throw new RuntimeException("重试次数耗尽，选课更新课程人数失败：课程ID=" + courseId);
                        }
                    }
                } finally {
                    // 4. 释放锁：无论操作成功/失败，都必须释放锁（避免死锁）
                    if (courseLock.isHeldByCurrentThread()) {
                        courseLock.unlock();
                        log.debug("释放课程锁：课程ID={}", courseId);
                    }
                }
            }

            // 5. 重试次数耗尽仍失败：抛出异常，触发RocketMQ消息重试
            if (retryCount >= maxRetry) {
                throw new RuntimeException("选课失败：重试次数耗尽，课程ID=" + courseId + "，学生ID=" + studentId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("选课获取锁被中断：课程ID={}", courseId, e);
            throw new RuntimeException("选课中断，课程ID=" + courseId);
        }
    }

    /**
     * 处理退课消息：更新3张表（删除选课记录、返还学生积分、减少课程人数）
     */

    @Transactional(rollbackFor = Exception.class) // 事务：确保DB操作要么全成功，要么全回滚
    public void handleDropCourseMessage(SelectionMessage message) {
        Long studentId = message.getStudentId();
        Long courseId = message.getCourseId();
        Long selectionId = message.getSelectionId(); // 关联的选课记录ID
        BigDecimal pointChange = message.getPointChange(); // 正：返还积分

        // 1. 删除选课记录（Selection表）
        int deleteRows = selectionDao.deleteByStudentAndCourse(studentId, courseId);
        if (deleteRows == 0) {
            log.warn("退课删除记录不存在：选课ID={}，学生ID={}，课程ID={}",
                    selectionId, studentId, courseId);
            // 若记录已删除，无需继续（避免重复操作）
            return;
        }

        // 2. 返还学生积分（StudentPoint表）
        studentPointDao.updateStudentPoints(studentId, pointChange,redisTemplate);

        // 3. 减少课程当前选课人数（Course表）
        // 乐观锁防并发：where条件加version
        Course course = courseDao.selectById(courseId);
        int updateRows = courseDao.decrementStudentCount(courseId, course.getVersion());
        if (updateRows == 0) {
            log.warn("退课更新课程人数失败：乐观锁冲突，课程ID=" + courseId);
        }
    }

    // 常量：与Service层一致（也可抽为公共常量类）
    private static final String OPERATE_TYPE_SELECT = "SELECT";
    private static final String OPERATE_TYPE_DROP = "DROP";
}
