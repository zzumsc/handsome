package org.example.handsome.service.impl;

import jakarta.annotation.Resource;
import org.example.handsome.dao.CourseDao;
import org.example.handsome.pojo.Course;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 课程定时调度器
 * 使用 TaskScheduler 精确调度课程的自动开始和结束。
 *
 * 调度策略：
 * - 定时开始：提前1分钟预加载学生积分，到点再执行开始
 * - 手动开始：由 AdminCourseServiceImpl 先预加载再开始
 *
 * 触发时机：
 * 1. 管理员创建/修改课程时，注册定时任务
 * 2. 应用启动完成后（ApplicationReadyEvent），恢复所有待触发的课程
 */
@Component
public class CourseScheduler {

    private static final Logger log = LoggerFactory.getLogger(CourseScheduler.class);

    /** 预加载提前时间（分钟） */
    private static final long PRELOAD_AHEAD_MINUTES = 1;

    @Resource
    private CourseDao courseDao;

    @Lazy
    @Autowired
    private AdminCourseServiceImpl adminCourseService;

    @Autowired
    private TaskScheduler taskScheduler;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    // ============================== 启动恢复 ==============================

    /**
     * 应用完全就绪后恢复所有待调度的课程任务
     * 使用 ApplicationReadyEvent 而非 @PostConstruct，确保所有Bean（含@Lazy）已初始化完毕
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reloadPendingSchedules() {
        log.info("CourseScheduler启动：恢复待调度课程...");

        // 1. 注册未来待开始的课程（draft + startTime > NOW）
        List<Course> futureStarts = courseDao.selectFutureCoursesToStart();
        for (Course c : futureStarts) {
            registerPreloadAndStartTasks(c);
        }
        log.info("恢复待开始课程：{}门", futureStarts.size());

        // 2. 注册未来待结束的课程（published + endTime > NOW）
        List<Course> futureEnds = courseDao.selectFutureCoursesToEnd();
        for (Course c : futureEnds) {
            registerEndTask(c);
        }
        log.info("恢复待结束课程：{}门", futureEnds.size());

        // 3. 补偿：startTime已过但仍是draft的课程 → 立即预加载并开始
        List<Course> overdueStarts = courseDao.selectCoursesToStart();
        for (Course c : overdueStarts) {
            log.warn("补偿执行：课程[{}]开始时间已过但未开始，立即预加载并触发", c.getId());
            executePreloadThenStart(c.getId());
        }

        // 4. 补偿：endTime已过但仍是published的课程 → 立即结束
        List<Course> overdueEnds = courseDao.selectCoursesToEnd();
        for (Course c : overdueEnds) {
            log.warn("补偿执行：课程[{}]结束时间已过但未结束，立即触发", c.getId());
            executeEnd(c.getId());
        }
    }

    // ============================== 注册调度任务 ==============================

    /**
     * 为课程注册开始任务（含提前1分钟的预加载任务）
     * 如果startTime已过则立即预加载并执行
     */
    public void scheduleStart(Course course) {
        if (course.getStartTime() == null) {
            return;
        }

        cancelStartTasks(course.getId());

        // 如果endTime也已过，不再开始（课程已过期）
        if (course.getEndTime() != null && !course.getEndTime().isAfter(LocalDateTime.now())) {
            log.warn("课程[{}]开始和结束时间均已过，跳过", course.getId());
            return;
        }

        if (!course.getStartTime().isAfter(LocalDateTime.now())) {
            // startTime已到或已过 → 立即预加载并执行开始
            log.info("课程[{}]开始时间已到({})，立即预加载并执行", course.getId(), course.getStartTime());
            taskScheduler.schedule(() -> executePreloadThenStart(course.getId()), Instant.now());
            return;
        }

        registerPreloadAndStartTasks(course);
    }

    /**
     * 为课程注册结束任务（如果endTime已过则立即执行）
     */
    public void scheduleEnd(Course course) {
        if (course.getEndTime() == null) {
            return;
        }

        String taskKey = "end:" + course.getId();
        cancelTask(taskKey);

        if (!course.getEndTime().isAfter(LocalDateTime.now())) {
            log.info("课程[{}]结束时间已到({})，立即执行结束", course.getId(), course.getEndTime());
            taskScheduler.schedule(() -> executeEnd(course.getId()), Instant.now());
            return;
        }

        registerEndTask(course);
    }

    /**
     * 为课程同时注册开始（含预加载）和结束任务
     */
    public void scheduleCourse(Course course) {
        scheduleStart(course);
        scheduleEnd(course);
    }

    // ============================== 内部注册 ==============================

    /**
     * 注册预加载任务（startTime前1分钟）和开始任务（startTime到点执行）
     */
    private void registerPreloadAndStartTasks(Course course) {
        Long courseId = course.getId();
        LocalDateTime startTime = course.getStartTime();
        LocalDateTime preloadTime = startTime.minusMinutes(PRELOAD_AHEAD_MINUTES);

        // 1. 注册预加载任务
        String preloadKey = "preload:" + courseId;
        cancelTask(preloadKey);

        if (preloadTime.isAfter(LocalDateTime.now())) {
            // 预加载时间在未来 → 定时注册
            Instant preloadInstant = preloadTime.atZone(ZoneId.systemDefault()).toInstant();
            ScheduledFuture<?> preloadFuture = taskScheduler.schedule(
                    () -> executePreload(courseId), preloadInstant);
            scheduledTasks.put(preloadKey, preloadFuture);
            log.info("课程[{}]已注册预加载调度：{}（开始前{}分钟）", courseId, preloadTime, PRELOAD_AHEAD_MINUTES);
        } else {
            // 预加载时间已过（距开始<1分钟） → 立即预加载
            log.info("课程[{}]距开始不足{}分钟，立即预加载", courseId, PRELOAD_AHEAD_MINUTES);
            taskScheduler.schedule(() -> executePreload(courseId), Instant.now());
        }

        // 2. 注册开始任务
        String startKey = "start:" + courseId;
        Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
        ScheduledFuture<?> startFuture = taskScheduler.schedule(
                () -> executeStart(courseId), startInstant);
        scheduledTasks.put(startKey, startFuture);
        log.info("课程[{}]已注册开始调度：{}", courseId, startTime);
    }

    private void registerEndTask(Course course) {
        String taskKey = "end:" + course.getId();
        Instant endInstant = course.getEndTime().atZone(ZoneId.systemDefault()).toInstant();
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeEnd(course.getId()), endInstant);
        scheduledTasks.put(taskKey, future);
        log.info("课程[{}]已注册结束调度：{}", course.getId(), course.getEndTime());
    }

    // ============================== 取消任务 ==============================

    public void cancelTask(String taskKey) {
        ScheduledFuture<?> existing = scheduledTasks.remove(taskKey);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
            log.debug("已取消调度任务：{}", taskKey);
        }
    }

    /** 取消课程的开始相关任务（预加载 + 开始） */
    private void cancelStartTasks(Long courseId) {
        cancelTask("preload:" + courseId);
        cancelTask("start:" + courseId);
    }

    /** 取消课程的所有调度任务（预加载 + 开始 + 结束） */
    public void cancelCourse(Long courseId) {
        cancelTask("preload:" + courseId);
        cancelTask("start:" + courseId);
        cancelTask("end:" + courseId);
    }

    // ============================== 执行逻辑 ==============================

    /**
     * 执行预加载：将全部学生积分批量写入Redis
     * 定时场景下在开始前1分钟触发
     */
    private void executePreload(Long courseId) {
        try {
            log.info(">>> 预加载触发：为课程[{}]提前加载学生积分到Redis", courseId);
            adminCourseService.preloadAllStudentPoints();
            log.info("<<< 课程[{}]预加载完成", courseId);
            scheduledTasks.remove("preload:" + courseId);
        } catch (Exception e) {
            log.error("!!! 课程[{}]预加载异常：{}", courseId, e.getMessage(), e);
        }
    }

    /**
     * 执行开始选课（不含预加载，预加载已提前执行）
     * 定时场景下在 startTime 到点触发
     */
    private void executeStart(Long courseId) {
        try {
            log.info(">>> 定时任务触发：课程[{}]开始选课", courseId);

            Map<String, Object> result = adminCourseService.doStartSingleCourse(courseId);
            if ((boolean) result.get("success")) {
                log.info("<<< 课程[{}]定时开始选课成功", courseId);
            } else {
                log.warn("<<< 课程[{}]定时开始选课失败：{}", courseId, result.get("msg"));
            }

            scheduledTasks.remove("start:" + courseId);

        } catch (Exception e) {
            log.error("!!! 课程[{}]定时开始选课异常：{}", courseId, e.getMessage(), e);
        }
    }

    /**
     * 预加载 + 开始选课（顺序执行）
     * 用于补偿场景和立即执行场景，确保预加载在开始之前完成
     */
    private void executePreloadThenStart(Long courseId) {
        executePreload(courseId);
        executeStart(courseId);
    }

    private void executeEnd(Long courseId) {
        try {
            log.info(">>> 定时任务触发：课程[{}]结束选课", courseId);

            Map<String, Object> result = adminCourseService.doEndSingleCourse(courseId);
            if ((boolean) result.get("success")) {
                log.info("<<< 课程[{}]定时结束选课成功", courseId);
            } else {
                log.warn("<<< 课程[{}]定时结束选课失败：{}", courseId, result.get("msg"));
            }

            scheduledTasks.remove("end:" + courseId);

        } catch (Exception e) {
            log.error("!!! 课程[{}]定时结束选课异常：{}", courseId, e.getMessage(), e);
        }
    }
}
