-- 竞价预选课程 Lua脚本
-- KEYS[1] = user:point:{userId}    学生积分key
-- KEYS[2] = course:bid:{courseId}   课程竞价Hash key
-- KEYS[3] = course:bid:count:{courseId} 竞价人数计数器key
-- KEYS[4] = course:admitted:{courseId}  已录取学生Set key
-- ARGV[1] = userId (string)
-- ARGV[2] = bidPoints (long, 放大1000倍后的积分)
-- ARGV[3] = currentTimeMillis (long, 当前时间戳毫秒)
--
-- 返回值:
--   0 = 成功
--   1 = 积分不足
--   2 = 已经竞价过该课程
--   3 = 已经被录取（往期），不可重复选课

-- 0. 检查是否已经被录取（往期）
if redis.call('SISMEMBER', KEYS[4], ARGV[1]) == 1 then
    return 3
end

-- 1. 检查是否已经竞价过
local existingBid = redis.call('HGET', KEYS[2], ARGV[1])
if existingBid then
    return 2
end

-- 2. 检查积分余额
local currentPoints = tonumber(redis.call('GET', KEYS[1]) or '0')
local bidPoints = tonumber(ARGV[2])
if currentPoints < bidPoints then
    return 1
end

-- 3. 原子扣减积分
redis.call('DECRBY', KEYS[1], bidPoints)

-- 4. 写入竞价Hash: field=userId, value=JSON{points, time}
local bidData = '{"points":' .. ARGV[2] .. ',"time":' .. ARGV[3] .. '}'
redis.call('HSET', KEYS[2], ARGV[1], bidData)

-- 5. 递增竞价人数计数器
redis.call('INCR', KEYS[3])

return 0

