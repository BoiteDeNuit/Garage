package com.example.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RateLimiter {
    private final static Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean allow(String key, int limit, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            redis.expire(key, window);
            return count != null && count <= limit;
        } catch (DataAccessException e) {
            log.warn("Redis Недоступен,ограничение попыток входа временно отключено: {}", e.getMessage());
            return true;
        }
    }
}

