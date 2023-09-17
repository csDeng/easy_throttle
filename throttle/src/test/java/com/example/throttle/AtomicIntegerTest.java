package com.example.throttle;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 计算器限流
 */
@SpringBootTest
public class AtomicIntegerTest {


    @Test
    void simple() {
        var atomicInteger = new AtomicInteger(0);
        var throttle = 10;
        for (int i = 0; i < 100; i++) {
            try {
                new Thread(() -> {
                    try {
                        if (atomicInteger.incrementAndGet() > throttle) {
                            System.out.println("太快啦");
                            throw new RuntimeException("拒绝");
                        }
                        System.out.println(Thread.currentThread().getId() + "处理啦");

                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        System.out.println("请求被拒绝");
                    } finally {
                        int i1 = atomicInteger.decrementAndGet();
                    }
                }).start();

            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }
}
