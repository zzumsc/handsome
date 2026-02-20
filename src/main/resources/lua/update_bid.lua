-- 修改竞价积分 Lua脚本
-- KEYS[1] = user:point:{userId}    学生积分key
-- KEYS[2] = course:bid:{courseId}   课程竞价Hash key
-- ARGV[1] = userId (string)
-- ARGV[2] = newBidPoints (long, 新竞价积分，放大1000倍)
-- ARGV[3] = currentTimeMillis (long, 更新时间戳毫秒)
--
-- 返回值:
--   0 = 成功
--   1 = 积分不足（加价场景）
--   2 = 未找到原竞价记录

-- 1. 读取旧竞价记录
local existingBid = redis.call('HGET', KEYS[2], ARGV[1])
if not existingBid then
    return 2
end

-- 2. 解析旧竞价积分
local oldPoints = tonumber(string.match(existingBid, '"points":(%d+)'))
local newPoints = tonumber(ARGV[2])
local diff = newPoints - oldPoints

-- 3. 如果加价，检查积分余额
if diff > 0 then
    local currentBalance = tonumber(redis.call('GET', KEYS[1]) or '0')
    if currentBalance < diff then
        return 1
    end
    -- 扣减差价
    redis.call('DECRBY', KEYS[1], diff)
elseif diff < 0 then
    -- 减价，退还差价
    redis.call('INCRBY', KEYS[1], -diff)
end
-- diff == 0 则什么都不做

-- 4. 更新竞价Hash（更新积分和时间）
local bidData = '{"points":' .. ARGV[2] .. ',"time":' .. ARGV[3] .. '}'
redis.call('HSET', KEYS[2], ARGV[1], bidData)

return 0

