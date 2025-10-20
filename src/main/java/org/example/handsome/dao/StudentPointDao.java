package org.example.handsome.dao;

import jakarta.annotation.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Mapper
@Component
public interface StudentPointDao {

//    @Resource
//    RedisTemplate<String,Object> redisTemplate;
    // Redis缓存键前缀
    String POINT_CACHE_KEY_PREFIX = "student:point:";

    @Select("SELECT points FROM points WHERE student_id = #{studentId}")
    BigDecimal getStudentPointsFromDb(Long studentId);

    @Update("UPDATE points SET " +
            "points = points + #{amount}, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE student_id = #{studentId}")
    int updateStudentPointsInDb(
            @Param("studentId") Long studentId,
            @Param("amount") BigDecimal amount
    );

    /**
     * 查询学生积分（带缓存）
     */
    default BigDecimal getStudentPoints(Long studentId,RedisTemplate<String, Object> redisTemplate) {
        // 检查RedisTemplate是否初始化
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            throw new IllegalStateException("RedisTemplate未正确初始化");
        }

        String cacheKey = POINT_CACHE_KEY_PREFIX + studentId;

        // 1. 先查询Redis缓存
        BigDecimal points = (BigDecimal) redisTemplate.opsForValue().get(cacheKey);
        if (points != null) {
            return points;
        }

        // 2. 缓存未命中，查询数据库
        points = getStudentPointsFromDb(studentId);

        // 3. 将查询结果存入Redis，设置合理的过期时间
        if (points != null) {
            redisTemplate.opsForValue().set(cacheKey, points, 30, TimeUnit.MINUTES);
        }

        return points;
    }

    /**
     * 更新学生积分（更新数据库后同步更新缓存）
     */
    default int updateStudentPoints(
            @Param("studentId") Long studentId,
            @Param("amount") BigDecimal amount,
            RedisTemplate<String,Object> redisTemplate) {

        // 检查RedisTemplate是否初始化
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            throw new IllegalStateException("RedisTemplate未正确初始化");
        }

        String cacheKey = POINT_CACHE_KEY_PREFIX + studentId;

        // 1. 先更新数据库
        int affectedRows = updateStudentPointsInDb(studentId, amount);

        // 2. 数据库更新成功后，删除缓存
        if (affectedRows > 0) {
            redisTemplate.delete(cacheKey);
        }

        return affectedRows;
    }
}
