package com.example.throttle;

import com.google.common.util.concurrent.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;


/**
 * 令牌桶限流
 */
@SpringBootTest
public class GuavaLimiterTest {



    @Test
    public void bucket() {
        RateLimiter limiter = RateLimiter.create(1); // 表示桶容量为1且每秒新增1个令牌，即每隔200毫秒新增一个令牌；
        for (int i = 0; i < 10; i++) {
            System.out.println(limiter.acquire());
        }
    }

    @Test
    public void warmup() {
        RateLimiter limiter = RateLimiter.create(5, 1, TimeUnit.SECONDS); // 表示桶容量为1且每秒新增1个令牌，即每隔200毫秒新增一个令牌；
        for (int i = 0; i < 10; i++) {
            System.out.println(limiter.acquire());
        }
    }

}
