local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1
end
if stock < tonumber(ARGV[1]) then
    return 0
end
redis.call('DECRBY', KEYS[1], ARGV[1])
return 1
