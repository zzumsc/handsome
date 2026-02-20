package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.example.handsome.dao.CourseDao;
import org.example.handsome.dao.SelectionDao;
import org.example.handsome.dao.StudentPointDao;
import org.example.handsome.pojo.Course;
import org.example.handsome.pojo.Selection;
import org.example.handsome.pojo.SettlementMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 结算结果MQ消费者
 * 接收结算线程发送的结算结果消息，执行：
 * 1. 幂等检查
 * 2. 批量写入selections表
 * 3. 更新courses表（人数 + lastYearScore）
 * 4. 更新points表（扣减录取者积分）
 * 5. 删除Redis缓存
 */
@Component
@RocketMQMessageListener(
        topic = "course_settlement_topic",
        consumerGroup = "course_settlement_db_consumer",
        selectorExpression = "*"
)
public class SettlementConsumer implements RocketMQListener<SettlementMessage> {

    private static final Logger log = LoggerFactory.getLogger(SettlementConsumer.class);

    @Resource
    private CourseDao courseDao;
    @Resource
    private SelectionDao selectionDao;
    @Resource
    private StudentPointDao studentPointDao;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(SettlementMessage message) {
        log.info("收到结算消息：{}", message);
        try {
            handleSettlement(message);
        } catch (Exception e) {
            log.error("结算消息处理失败：courseId={}", message.getCourseId(), e);
            // RocketMQ会自动重试
            throw new RuntimeException("结算入库失败，等待重试", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleSettlement(SettlementMessage message) {
        Long courseId = message.getCourseId();

        // ========== 1. 幂等检查：如果已有该课程的录取记录，说明已处理过 ==========
        List<Selection> existing = selectionDao.selectStudentsByCourseId(courseId);
        if (existing != null && !existing.isEmpty()) {
            log.warn("课程[{}]已有录取记录（{}条），跳过重复处理", courseId, existing.size());
            // 仍然执行缓存清理（防止上次处理完没清理）
            cleanupCache(courseId, message);
            return;
        }

        List<SettlementMessage.AdmittedStudent> admittedStudents = message.getAdmittedStudents();

        if (admittedStudents != null && !admittedStudents.isEmpty()) {
            // ========== 2. 批量插入录取记录到selections表 ==========
            List<Selection> selections = new ArrayList<>();
            for (SettlementMessage.AdmittedStudent admitted : admittedStudents) {
                Selection sel = new Selection();
                sel.setStudentId(admitted.getStudentId());
                sel.setCourseId(courseId);
                sel.setSelectionTime(new Date(admitted.getBidTimeMillis()));
                sel.setPointsUsed(admitted.getPointsUsed());
                selections.add(sel);
            }
            selectionDao.batchInsert(selections);
            log.info("课程[{}]批量插入{}条录取记录", courseId, selections.size());

            // ========== 3. 更新课程当前选课人数 ==========
            Course course = courseDao.selectById(courseId);
            if (course != null) {
                courseDao.updateCurrentStudents(courseId, message.getAdmittedCount(), course.getVersion());
            }

            // ========== 4. 更新录取者的数据库积分（扣减） ==========
            for (SettlementMessage.AdmittedStudent admitted : admittedStudents) {
                BigDecimal pointChange = admitted.getPointsUsed().negate();
                studentPointDao.updateStudentPointsInDb(admitted.getStudentId(), pointChange);
            }
            log.info("课程[{}]录取者积分扣减完成", courseId);
        }

        // ========== 5. 更新往年录取分 = 本次最低录取积分 ==========
        BigDecimal cutoffScore = message.getCutoffScore() != null ? message.getCutoffScore() : BigDecimal.ZERO;
        courseDao.updateLastYearScore(courseId, cutoffScore);
        log.info("课程[{}]往年录取分更新为: {}", courseId, cutoffScore);

        // ========== 6. DB写完后删除缓存 ==========
        cleanupCache(courseId, message);

        log.info("课程[{}]结算入库完成：录取{}人，淘汰{}人",
                courseId, message.getAdmittedCount(), message.getRejectedCount());
    }

    /**
     * 清理该课程相关的所有Redis缓存
     */
    private void cleanupCache(Long courseId, SettlementMessage message) {
        // 1. 删除竞价数据（结算线程可能已删，这里兜底）
        redisTemplate.delete(RedisKeyUtils.getCourseBidKey(courseId));
        redisTemplate.delete(RedisKeyUtils.getCourseBidCountKey(courseId));

        // 2. 删除课程信息缓存（下次查询时重建）
        redisTemplate.delete(RedisKeyUtils.getCourseInfoKey(courseId));

        // 2.5 删除往期已录取学生Set（课程结束后不再需要）
        redisTemplate.delete(RedisKeyUtils.getCourseAdmittedKey(courseId));

        // 3. 删除可用课程列表缓存
        redisTemplate.delete(RedisKeyUtils.COURSES_AVAILABLE_KEY);

        // 4. 删除录取者的积分缓存（下次查询时从DB重建）
        if (message.getAdmittedStudents() != null) {
            for (SettlementMessage.AdmittedStudent admitted : message.getAdmittedStudents()) {
                redisTemplate.delete(RedisKeyUtils.getStudentPointKey(admitted.getStudentId()));
            }
        }

        // 5. 删除淘汰者的积分缓存（退还积分后Redis值已过期，需从DB重建）
        if (message.getRejectedStudentIds() != null) {
            for (Long rejectedId : message.getRejectedStudentIds()) {
                redisTemplate.delete(RedisKeyUtils.getStudentPointKey(rejectedId));
            }
        }

        log.info("课程[{}]缓存清理完成", courseId);
    }
}

