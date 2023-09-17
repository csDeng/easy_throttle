package com.example.throttle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * 信号量实现单接口限流
 */
@SpringBootTest
public class SemaphoreTest {

    @Test
    void test() throws InterruptedException {
        SemaphoreTest test = new SemaphoreTest();

        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                test.myMethod();
            }).start();
        }
        Thread.sleep(1500);
    }

    private final Semaphore semaphore = new Semaphore(10);

    public void myMethod() {
        try {
            if (semaphore.tryAcquire(500, TimeUnit.MILLISECONDS)) {
                System.out.println(Thread.currentThread().getId() + "处理中");
                Thread.sleep(500);
                semaphore.release();
            } else {
                System.out.println(Thread.currentThread().getId() + "被拒绝");
                throw new RuntimeException("请求被拒绝");
            }
        } catch (Exception e) {
            System.out.println();
            Thread.currentThread().interrupt();
        }
    }
}
