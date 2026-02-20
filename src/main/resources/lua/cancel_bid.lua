-- 取消预选（全额退还积分） Lua脚本
-- KEYS[1] = user:point:{userId}    学生积分key
-- KEYS[2] = course:bid:{courseId}   课程竞价Hash key
-- KEYS[3] = course:bid:count:{courseId} 竞价人数计数器key
-- ARGV[1] = userId (string)
--
-- 返回值:
--   0 = 成功
--   1 = 未找到竞价记录

-- 1. 读取竞价记录
local existingBid = redis.call('HGET', KEYS[2], ARGV[1])
if not existingBid then
    return 1
end

-- 2. 解析竞价积分
local bidPoints = tonumber(string.match(existingBid, '"points":(%d+)'))

-- 3. 全额退还积分
redis.call('INCRBY', KEYS[1], bidPoints)

-- 4. 删除竞价Hash记录
redis.call('HDEL', KEYS[2], ARGV[1])

-- 5. 递减竞价人数计数器
redis.call('DECR', KEYS[3])

return 0

