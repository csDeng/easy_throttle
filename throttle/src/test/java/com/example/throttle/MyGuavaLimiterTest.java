package com.example.throttle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 简单模拟Guava限流器
 */
@SpringBootTest
public class MyGuavaLimiterTest {
    private final RateLimiter  limiter = new RateLimiter(100, 10);
    @Test
    void test() throws InterruptedException {

        /**
         * 生成令牌，在实际应用时可以转 @Schedule
         */
        new Thread(()->{
            try {
                limiter.refillTokens();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(3000);
        for (int i = 0; i < 100; i++) {
            new Thread(()->{
                try {
                    boolean b = limiter.tryAcquire();
                    long id = Thread.currentThread().getId();
                    if(!b) {
                        System.out.println(id +"拒绝服务");
                        return;
                    }
                    System.out.println(id +"处理中");
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

        Thread.sleep(30000);
    }

}


class RateLimiter {
    private final long capacity; // 桶的容量
    private final double rate; // 令牌生成速率 每秒生成几个令牌
    private long tokens; // 当前令牌数量
    private long lastRefillTime; // 上次令牌刷新时间

    public RateLimiter(long capacity, double rate) {
        this.capacity = capacity;
        this.rate = rate;
        this.tokens = 0;
        this.lastRefillTime = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }

    public void refillTokens() {
        long currentTime = System.nanoTime();
        long elapsedTime = currentTime - lastRefillTime;
        long newTokens = (long) (elapsedTime * rate / TimeUnit.SECONDS.toNanos(1));
        if (newTokens > 0) {
            tokens = Math.min(tokens + newTokens, capacity);
            System.out.println("当前令牌"+tokens);
            lastRefillTime = currentTime;
        }
    }
}
