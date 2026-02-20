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
import org.example.handsome.pojo.SettlementMessage;
import org.example.handsome.pojo.User;
import org.example.handsome.service.AdminCourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AdminCourseServiceImpl implements AdminCourseService {

    private static final Logger log = LoggerFactory.getLogger(AdminCourseServiceImpl.class);

    static final String SETTLEMENT_TOPIC = "course_settlement_topic";

    @Resource
    private CourseDao courseDao;
    @Resource
    private SelectionDao selectionDao;
    @Resource
    private UserDao userDao;
    @Resource
    private StudentPointDao studentPointDao;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 用于所有纯数值/字符串的Redis操作（与Lua脚本交互的key），避免JSON序列化器包装 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private CourseScheduler courseScheduler;

    // 校验当前登录用户是否为管理员
    private void checkAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userDao.selectByEmail(auth.getName());
        if (loginUser == null || !User.Role.admin.equals(loginUser.getRole())) {
            throw new SecurityException("仅管理员可操作");
        }
    }

    // ============================== 课程CRUD ==============================

    @Override
    @Transactional
    public Result addCourse(Course course) {
        try {
            checkAdminRole();

            Course existingCourse = courseDao.getCourseByCourseCode(course.getCourseCode());
            if (existingCourse != null) {
                return Result.fail("课程代码不能相同");
            }

            course.setStatus(Course.CourseStatus.draft);
            course.setCurrentStudents(0);
            course.setVersion(0);
            if (course.getLastYearScore() == null) {
                course.setLastYearScore(BigDecimal.ZERO);
            }

            int rows = courseDao.insert(course);
            if (rows > 0) {
                // 注册定时调度（如果设置了 startTime/endTime）
                courseScheduler.scheduleCourse(course);
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

            Course course = courseDao.selectById(id);
            if (course == null) {
                return Result.fail("课程不存在");
            }

            int rows = courseDao.deleteById(id);
            if (rows > 0) {
                courseScheduler.cancelCourse(id);
            }
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

            Course existing = courseDao.selectById(course.getId());
            if (existing == null) {
                return Result.fail("课程不存在");
            }

            int rows = courseDao.update(course);
            if (rows > 0) {
                redisTemplate.delete(RedisKeyUtils.getCourseInfoKey(course.getId()));
                redisTemplate.delete(RedisKeyUtils.COURSES_AVAILABLE_KEY);
                // 重新注册定时调度（先取消旧的再注册新的）
                courseScheduler.cancelCourse(course.getId());
                courseScheduler.scheduleCourse(courseDao.selectById(course.getId()));
                return Result.ok("课程更新成功").put("course", courseDao.selectById(course.getId()));
            } else {
                return Result.fail("更新失败（可能已被其他管理员修改，请刷新后重试）");
            }
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        }
    }

    // ============================== 单个开始选课 ==============================

    @Override
    @Transactional
    public Result startCourseSelection(Long courseId) {
        try {
            checkAdminRole();
            // 手动开始选课前，先预加载全部学生积分到Redis
            preloadAllStudentPoints();
            Map<String, Object> result = doStartSingleCourse(courseId);
            if ((boolean) result.get("success")) {
                return Result.ok((String) result.get("msg"))
                        .put("courseId", courseId)
                        .put("newStatus", Course.CourseStatus.published.name());
            } else {
                return Result.fail((String) result.get("msg"));
            }
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("发布课程失败：" + e.getMessage());
        }
    }

    // ============================== 批量开始选课 ==============================

    @Override
    @Transactional
    public Result batchStartCourseSelection(List<Long> courseIds) {
        try {
            checkAdminRole();

            if (courseIds == null || courseIds.isEmpty()) {
                return Result.fail("请选择至少一门课程");
            }

            // 批量开始前，统一预加载全部学生积分到Redis（只做一次）
            preloadAllStudentPoints();

            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (Long courseId : courseIds) {
                Map<String, Object> r = doStartSingleCourse(courseId);
                r.put("courseId", courseId);
                results.add(r);
                if ((boolean) r.get("success")) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            // 清除可用课程列表缓存（一次就够）
            redisTemplate.delete(RedisKeyUtils.COURSES_AVAILABLE_KEY);

            return Result.ok(String.format("批量开始选课完成：成功%d门，失败%d门", successCount, failCount))
                    .put("details", results)
                    .put("successCount", successCount)
                    .put("failCount", failCount);

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("批量开始选课失败：" + e.getMessage());
        }
    }

    /**
     * 单门课程开始选课的内部逻辑（复用于单个、批量、定时任务）
     */
    Map<String, Object> doStartSingleCourse(Long courseId) {
        Map<String, Object> result = new HashMap<>();

        Course course = courseDao.selectById(courseId);
        if (course == null) {
            result.put("success", false);
            result.put("msg", "课程不存在");
            return result;
        }

        int rows;
        if (course.getStatus().equals(Course.CourseStatus.draft)) {
            rows = courseDao.updateStatus(courseId,
                    Course.CourseStatus.draft.name(),
                    Course.CourseStatus.published.name(),
                    course.getVersion());
        } else if (course.getStatus().equals(Course.CourseStatus.closed)) {
            rows = courseDao.updateStatus(courseId,
                    Course.CourseStatus.closed.name(),
                    Course.CourseStatus.published.name(),
                    course.getVersion());
        } else {
            result.put("success", false);
            result.put("msg", "课程当前状态不允许开始选课（已在选课中）");
            return result;
        }

        if (rows > 0) {
            initRedisForCourse(courseId);
            result.put("success", true);
            result.put("msg", "课程已发布，开始竞价选课");
        } else {
            result.put("success", false);
            result.put("msg", "操作失败（可能已被其他管理员修改）");
        }
        return result;
    }

    // ============================== 预加载 ==============================

    /**
     * 批量预加载全部学生积分到Redis（Pipeline，一次网络往返）
     * 在选课开始前调用，避免选课高峰期逐个查DB
     */
    void preloadAllStudentPoints() {
        List<Map<String, Object>> allPoints = studentPointDao.selectAllStudentPoints();
        if (allPoints == null || allPoints.isEmpty()) {
            log.warn("预加载学生积分：无数据");
            return;
        }

        int loadedCount = 0;
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Map<String, Object> row : allPoints) {
                    Object sidObj = row.get("student_id");
                    Object ptsObj = row.get("points");
                    if (sidObj == null || ptsObj == null) {
                        continue;
                    }
                    Long studentId = ((Number) sidObj).longValue();
                    BigDecimal points = (BigDecimal) ptsObj;
                    String key = RedisKeyUtils.getStudentPointKey(studentId);
                    long scaledPoints = PointUtils.scaleUp(points);
                    connection.stringCommands().set(
                            key.getBytes(),
                            String.valueOf(scaledPoints).getBytes()
                    );
                }
                return null;
            }
        });

        log.info("Pipeline预加载完成：{}名学生积分已写入Redis", allPoints.size());
    }

    /**
     * 初始化Redis竞价数据：竞价Hash + 竞价人数计数器 + 课程信息缓存
     */
    void initRedisForCourse(Long courseId) {
        // 初始化竞价人数计数器为0（使用StringRedisTemplate，存储纯数字字符串，Lua脚本INCR/DECR可正常操作）
        stringRedisTemplate.opsForValue().set(RedisKeyUtils.getCourseBidCountKey(courseId), "0");

        // 确保竞价Hash干净（防上一轮残留）
        stringRedisTemplate.delete(RedisKeyUtils.getCourseBidKey(courseId));

        // 加载往期已录取学生ID到Redis Set（重开课程时，阻止已录取学生重复竞价）
        String admittedKey = RedisKeyUtils.getCourseAdmittedKey(courseId);
        stringRedisTemplate.delete(admittedKey);
        List<Long> admittedIds = selectionDao.selectAdmittedStudentIds(courseId);
        if (!admittedIds.isEmpty()) {
            String[] idArray = admittedIds.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(admittedKey, idArray);
            log.info("课程[{}]已加载{}名往期录取学生到Redis", courseId, admittedIds.size());
        }

        // 刷新课程信息缓存
        Course freshCourse = courseDao.selectById(courseId);
        redisTemplate.opsForValue().set(
                RedisKeyUtils.getCourseInfoKey(courseId),
                freshCourse,
                30, TimeUnit.MINUTES);

        log.info("课程[{}]Redis竞价数据初始化完成", courseId);
    }

    // ============================== 单个结束选课 ==============================

    @Override
    public Result endCourseSelection(Long courseId) {
        try {
            checkAdminRole();

            Map<String, Object> result = doEndSingleCourse(courseId);
            if ((boolean) result.get("success")) {
                return Result.ok((String) result.get("msg"))
                        .put("courseId", courseId);
            } else {
                return Result.fail((String) result.get("msg"));
            }
        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("结束选课失败：" + e.getMessage());
        }
    }

    // ============================== 批量结束选课 ==============================

    @Override
    public Result batchEndCourseSelection(List<Long> courseIds) {
        try {
            checkAdminRole();

            if (courseIds == null || courseIds.isEmpty()) {
                return Result.fail("请选择至少一门课程");
            }

            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (Long courseId : courseIds) {
                Map<String, Object> r = doEndSingleCourse(courseId);
                r.put("courseId", courseId);
                results.add(r);
                if ((boolean) r.get("success")) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            return Result.ok(String.format(
                    "批量结束选课完成：成功%d门，失败%d门",
                    successCount, failCount))
                    .put("details", results)
                    .put("successCount", successCount)
                    .put("failCount", failCount);

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            return Result.fail("批量结束选课失败：" + e.getMessage());
        }
    }

    /**
     * 单门课程结束选课的内部逻辑（复用于单个、批量、定时任务）
     * 同步执行：更新状态 → 删缓存 → Redis结算 → 发MQ
     */
    Map<String, Object> doEndSingleCourse(Long courseId) {
        Map<String, Object> result = new HashMap<>();

        // 1. 校验课程
        Course course = courseDao.selectById(courseId);
        if (course == null) {
            result.put("success", false);
            result.put("msg", "课程不存在");
            return result;
        }
        if (!Course.CourseStatus.published.equals(course.getStatus())) {
            result.put("success", false);
            result.put("msg", "仅已发布的课程可以结束选课");
            return result;
        }

        // 2. 乐观锁更新状态：published → closed
        //    WHERE status='published' 保证只有一次能成功（MySQL行锁）
        int rows = courseDao.updateStatus(
                courseId,
                Course.CourseStatus.published.name(),
                Course.CourseStatus.closed.name(),
                course.getVersion()
        );
        if (rows == 0) {
            result.put("success", false);
            result.put("msg", "操作失败（课程状态已变更）");
            return result;
        }

        // 3. 立即删除课程信息缓存，阻止新竞价
        redisTemplate.delete(RedisKeyUtils.getCourseInfoKey(courseId));
        redisTemplate.delete(RedisKeyUtils.COURSES_AVAILABLE_KEY);

        // 4. 同步执行Redis结算 + 发MQ（无锁、无线程池）
        try {
            doSettlementAndSendMQ(courseId, course.getMaxStudents());
        } catch (Exception e) {
            log.error("课程[{}]结算失败", courseId, e);
            result.put("success", true);
            result.put("msg", "课程已结束选课，但结算异常：" + e.getMessage());
            return result;
        }

        result.put("success", true);
        result.put("msg", "课程已结束选课，结算完成");
        return result;
    }

    // ============================== 结算核心逻辑（同步，无锁） ==============================

    /**
     * 竞价记录内部类
     */
    private static class BidEntry {
        Long studentId;
        long scaledPoints;
        long time;

        BidEntry(Long studentId, long scaledPoints, long time) {
            this.studentId = studentId;
            this.scaledPoints = scaledPoints;
            this.time = time;
        }
    }

    /**
     * 同步执行Redis结算 + 发送MQ消息
     * 前置条件：status已成功更新为closed（保证不会被重复调用）
     */
    void doSettlementAndSendMQ(Long courseId, int maxStudents) {
        // ========== Step 1: 从Redis读取全部竞价记录（使用StringRedisTemplate读取Lua写入的原始JSON） ==========
        String bidKey = RedisKeyUtils.getCourseBidKey(courseId);
        Map<Object, Object> allBids = stringRedisTemplate.opsForHash().entries(bidKey);

        if (allBids == null || allBids.isEmpty()) {
            log.info("课程[{}]无竞价记录，发送空结算消息", courseId);
            SettlementMessage emptyMsg = new SettlementMessage(
                    courseId, Collections.emptyList(), BigDecimal.ZERO, 0, 0, Collections.emptyList());
            rocketMQTemplate.syncSend(SETTLEMENT_TOPIC, emptyMsg);
            return;
        }

        // ========== Step 2: 解析竞价数据 ==========
        List<BidEntry> bidEntries = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : allBids.entrySet()) {
            Long studentId = Long.parseLong(entry.getKey().toString());
            String bidJson = entry.getValue().toString();
            try {
                long points = Long.parseLong(bidJson.replaceAll(".*\"points\":(\\d+).*", "$1"));
                long time = Long.parseLong(bidJson.replaceAll(".*\"time\":(\\d+).*", "$1"));
                bidEntries.add(new BidEntry(studentId, points, time));
            } catch (Exception e) {
                log.error("解析竞价数据失败：studentId={}, data={}", studentId, bidJson, e);
            }
        }

        // ========== Step 3: 排序 — 积分降序 → 同积分按时间升序 ==========
        bidEntries.sort((a, b) -> {
            int cmp = Long.compare(b.scaledPoints, a.scaledPoints);
            if (cmp != 0) return cmp;
            return Long.compare(a.time, b.time);
        });

        // ========== Step 4: 取前maxStudents个录取，其余淘汰 ==========
        List<BidEntry> admitted = new ArrayList<>();
        List<BidEntry> rejected = new ArrayList<>();
        for (int i = 0; i < bidEntries.size(); i++) {
            if (i < maxStudents) {
                admitted.add(bidEntries.get(i));
            } else {
                rejected.add(bidEntries.get(i));
            }
        }

        // ========== Step 5: 退还淘汰者积分（Redis原子操作，使用StringRedisTemplate确保纯数值操作） ==========
        for (BidEntry rej : rejected) {
            String pointKey = RedisKeyUtils.getStudentPointKey(rej.studentId);
            stringRedisTemplate.opsForValue().increment(pointKey, rej.scaledPoints);
            log.debug("退还学生[{}]积分: {}", rej.studentId, PointUtils.scaleDown(rej.scaledPoints));
        }

        // ========== Step 6: 计算最低录取分 ==========
        BigDecimal cutoffScore = BigDecimal.ZERO;
        if (!admitted.isEmpty()) {
            cutoffScore = PointUtils.scaleDown(admitted.get(admitted.size() - 1).scaledPoints);
        }

        log.info("课程[{}]Redis结算完成：录取{}人，淘汰{}人，最低分: {}",
                courseId, admitted.size(), rejected.size(), cutoffScore);

        // ========== Step 7: 构建MQ消息并发送 ==========
        List<SettlementMessage.AdmittedStudent> admittedList = admitted.stream()
                .map(a -> new SettlementMessage.AdmittedStudent(
                        a.studentId,
                        PointUtils.scaleDown(a.scaledPoints),
                        a.time))
                .collect(Collectors.toList());

        List<Long> rejectedStudentIds = rejected.stream()
                .map(r -> r.studentId)
                .collect(Collectors.toList());

        SettlementMessage message = new SettlementMessage(
                courseId, admittedList, cutoffScore, admitted.size(), rejected.size(), rejectedStudentIds);

        // 同步发送，确保消息不丢失
        rocketMQTemplate.syncSend(SETTLEMENT_TOPIC, message);
        log.info("课程[{}]结算消息已发送到MQ", courseId);

        // 注意：不在这里删除Redis竞价缓存！由MQ消费者写完DB后统一删除
    }

    // ============================== 查询 ==============================

    @Override
    public Result getStudentsByCourseId(Long courseId) {
        try {
            checkAdminRole();

            Course course = courseDao.selectById(courseId);
            if (course == null) {
                return Result.fail("课程不存在");
            }

            // 已结束 → 从DB查录取学生
            if (Course.CourseStatus.closed.equals(course.getStatus())) {
                List<Selection> selections = selectionDao.selectStudentsByCourseId(courseId);
                return Result.ok("查询成功（已录取学生）")
                        .put("students", selections)
                        .put("total", selections.size());
            }

            // 竞价中 → 从Redis查竞价学生 + 从DB补全学生信息
            if (Course.CourseStatus.published.equals(course.getStatus())) {
                String bidKey = RedisKeyUtils.getCourseBidKey(courseId);
                Map<Object, Object> allBids = stringRedisTemplate.opsForHash().entries(bidKey);

                // 1. 解析竞价记录
                List<Map<String, Object>> bidders = new ArrayList<>();
                List<Long> studentIds = new ArrayList<>();
                for (Map.Entry<Object, Object> entry : allBids.entrySet()) {
                    Long studentId = Long.parseLong(entry.getKey().toString());
                    String bidJson = entry.getValue().toString();

                    long points = Long.parseLong(bidJson.replaceAll(".*\"points\":(\\d+).*", "$1"));
                    long time = Long.parseLong(bidJson.replaceAll(".*\"time\":(\\d+).*", "$1"));

                    Map<String, Object> bidder = new HashMap<>();
                    bidder.put("studentId", studentId);
                    bidder.put("bidPoints", PointUtils.scaleDown(points));
                    bidder.put("bidTime", new Date(time));
                    bidders.add(bidder);
                    studentIds.add(studentId);
                }

                // 2. 批量查DB补全姓名、邮箱、学号
                if (!studentIds.isEmpty()) {
                    List<User> users = userDao.selectByIds(studentIds);
                    Map<Long, User> userMap = new HashMap<>();
                    for (User u : users) {
                        userMap.put(u.getId(), u);
                    }
                    for (Map<String, Object> bidder : bidders) {
                        Long sid = (Long) bidder.get("studentId");
                        User u = userMap.get(sid);
                        if (u != null) {
                            bidder.put("studentName", u.getName());
                            bidder.put("studentEmail", u.getEmail());
                            bidder.put("studentNo", u.getNo());
                        }
                    }
                }

                bidders.sort((a, b) -> ((BigDecimal) b.get("bidPoints")).compareTo((BigDecimal) a.get("bidPoints")));

                // 3. 查询往期已录取学生（重开课程场景）
                List<Selection> admittedStudents = selectionDao.selectStudentsByCourseId(courseId);

                // 4. 同时返回往期已录取 + 当前竞价
                return Result.ok("查询成功（竞价中的学生）")
                        .put("bidders", bidders)
                        .put("total", bidders.size())
                        .put("admittedStudents", admittedStudents)
                        .put("admittedTotal", admittedStudents.size())
                        .put("dbCurrentStudents", course.getCurrentStudents());
            }

            return Result.ok("查询成功").put("students", Collections.emptyList()).put("total", 0);

        } catch (SecurityException e) {
            return Result.fail(e.getMessage());
        }
    }

    @Override
    public Result getAllCourses(Integer page, Integer size, String keyword) {
        try {
            checkAdminRole();
            int offset = (page - 1) * size;
            List<Course> courses = courseDao.selectAllByPage(offset, size, keyword);
            int total = courseDao.countByKeyword(keyword);

            for (Course c : courses) {
                if (Course.CourseStatus.published.equals(c.getStatus())) {
                    String bidCountKey = RedisKeyUtils.getCourseBidCountKey(c.getId());
                    String countStr = stringRedisTemplate.opsForValue().get(bidCountKey);
                    c.setBidCount(countStr != null ? Integer.parseInt(countStr) : 0);
                }
            }

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
