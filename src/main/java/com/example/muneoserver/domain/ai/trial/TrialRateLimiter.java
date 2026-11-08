package com.example.muneoserver.domain.ai.trial;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class TrialRateLimiter {
    private final StringRedisTemplate redis;
    private final int sessionLimit;
    private final int globalLimit;
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
        local session = tonumber(redis.call('GET', KEYS[1]) or '0')
        local global = tonumber(redis.call('GET', KEYS[2]) or '0')
        if session >= tonumber(ARGV[1]) or global >= tonumber(ARGV[2]) then return 0 end
        if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], 1800) end
        if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], 3600) end
        return 1
        """, Long.class);

    public TrialRateLimiter(StringRedisTemplate redis,
            @Value("${trial.session-limit:3}") int sessionLimit,
            @Value("${trial.global-hourly-limit:60}") int globalLimit) {
        this.redis = redis;
        this.sessionLimit = sessionLimit;
        this.globalLimit = globalLimit;
    }

    public boolean reserve(String token) {
        // 전체 한도는 쿠키를 새로 발급받아도 우회할 수 없다. Redis 장애 시 분석 호출 전에 실패한다.
        Long allowed = redis.execute(RESERVE,
                List.of("trial:{budget}:session:" + token, "trial:{budget}:global"),
                String.valueOf(sessionLimit), String.valueOf(globalLimit));
        return Long.valueOf(1).equals(allowed);
    }
}
