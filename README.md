# springboot_throttle



> 前言： Java作为一门广泛应用于后端开发的语言，其丰富的开源生态系统与庞大的社区在不断推动Java的发展。但是，随着互联网应用的不断发展，高并发、大流量等问题开始变得越来越突出，如何保证系统的稳定性和鲁棒性成为了Java开发中不可避免的问题。**本文将从Java限流的概念开始，详细讲解Java限流的原理和实现方法，并结合Spring Boot框架进行代码演示，帮助读者更好地了解如何有效地实现限流**。

---
[TOC]



## 限流简介

每个系统都有服务的上线，所以当流量超过服务极限能力时，系统可能会出现卡死、崩溃的情况，所以就有了降级和限流。限流其实就是：当高并发或者瞬时高并发时，为了保证系统的稳定性、可用性，系统以牺牲部分请求为代价或者延迟处理请求为代价，保证系统整体服务可用。

###  算法

令牌桶(Token Bucket)、漏桶(leaky bucket)和计数器算法是最常用的三种限流的算法。

###  分类

#### 应用级 - 单机

应用级限流方式只是单应用内的请求限流，不能进行全局限流。

1. 限流总资源数
2. 限流总并发/连接/请求数
3. 限流某个接口的总并发/请求数
4. 限流某个接口的时间窗请求数
5. 平滑限流某个接口的请求数
6. Guava RateLimiter

#### 分布式

我们需要**分布式限流**和**接入层限流**来进行全局限流。

1. redis+lua实现中的lua脚本
2. 使用Nginx+Lua实现的Lua脚本
3. 使用 OpenResty 开源的限流方案
4. 限流框架，比如Sentinel实现降级限流熔断

##  方案一：令牌桶方式(Token Bucket)

> 令牌桶算法是网络流量整形（Traffic Shaping）和速率限制（Rate Limiting）中最常使用的一种算法。先有一个木桶，系统按照固定速度，往桶里加入Token，如果桶已经满了就不再添加。当有请求到来时，会各自拿走一个Token，取到Token 才能继续进行请求处理，没有Token 就拒绝服务。

![image-20230917213544758](./README//image-20230917213544758.png)

这里如果一段时间没有请求时，桶内就会积累一些Token，下次一旦有突发流量，只要Token足够，也能一次处理，所以令牌桶算法的特点是*允许突发流量*。

### 举例：Guava RateLimiter - 平滑突发限流(SmoothBursty)

> Guava RateLimiter提供了令牌桶算法实现：平滑突发限流(SmoothBursty)和平滑预热限流(SmoothWarmingUp)实现。

- Case 1

```java
package com.example.throttle;

import com.google.common.util.concurrent.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GuavaLimiterTest {


    @Test
    public void bucket() {
        RateLimiter limiter = RateLimiter.create(1); // 表示桶容量为1且每秒新增1个令牌，即每隔200毫秒新增一个令牌；
        for (int i = 0; i < 10; i++) {
            System.out.println(limiter.acquire());
        }
    }

}

// 0.0
// 0.999589
// 0.994164
// 0.993863
// 0.997597
```

1、`RateLimiter.create(1)`表示桶容量为1且每秒新增1个令牌；

2、`limiter.acquire()`表示消费一个令牌，如果当前桶中有足够令牌则成功（返回值为0），如果桶中没有令牌则暂停一段时间，比如发令牌间隔是1秒，则等待1秒后再去消费令牌（如上测试用例返回的为0.99，差不多等待了1秒桶中才有令牌可用），这种实现将突发请求速率平均为了固定请求速率。如果结构不想等待可以采用tryAcquire立刻返回！

- Case 2 - RateLimiter的突发情况处理:

```java
RateLimiter limiter = RateLimiter.create(5);
System.out.println(limiter.acquire(5));
System.out.println(limiter.acquire(1));
System.out.println(limiter.acquire(1))

// 将得到类似如下的输出：
0.0
0.98745
0.183553
0.199909
```

limiter.acquire(5)表示桶的容量为5且每秒新增5个令牌，令牌桶算法允许一定程度的突发，所以可以一次性消费5个令牌，但接下来的limiter.acquire(1)将等待差不多1秒桶中才能有令牌，且接下来的请求也整形为固定速率了。

- Case 3 - RateLimiter的突发情况处理:

```java
RateLimiter limiter = RateLimiter.create(5);
System.out.println(limiter.acquire(10));
System.out.println(limiter.acquire(1));
System.out.println(limiter.acquire(1));

// 将得到类似如下的输出：
0.0
1.997428
0.192273
0.200616
```

同上边的例子类似，第一秒突发了10个请求，**令牌桶算法也允许了这种突发（允许消费未来的令牌）**，但接下来的limiter.acquire(1)将等待差不多2秒桶中才能有令牌，且接下来的请求也整形为固定速率了。

- Case 4

```java
RateLimiter limiter = RateLimiter.create(2);
System.out.println(limiter.acquire());
Thread.sleep(2000L);
System.out.println(limiter.acquire());
System.out.println(limiter.acquire());
System.out.println(limiter.acquire());
System.out.println(limiter.acquire());
System.out.println(limiter.acquire());

// 将得到类似如下的输出：
0.0
0.0
0.0
0.0
0.499876
0.495799
```

1、创建了一个桶容量为2且每秒新增2个令牌； 

2、首先调用limiter.acquire()消费一个令牌，此时令牌桶可以满足（返回值为0）； 

3、然后线程暂停2秒，接下来的两个limiter.acquire()都能消费到令牌，第三个limiter.acquire()也同样消费到了令牌，到第四个时就需要等待500毫秒了。

此处可以看到我们设置的桶容量为2（即允许的突发量），这是因为SmoothBursty中有一个参数：最大突发秒数（maxBurstSeconds）默认值是1s，突发量/桶容量=速率*maxBurstSeconds，所以本示例桶容量/突发量为2，例子中前两个是消费了之前积攒的突发量，而第三个开始就是正常计算的了。令牌桶算法允许将一段时间内没有消费的令牌暂存到令牌桶中，留待未来使用，并允许未来请求的这种突发.

SmoothBursty通过平均速率和最后一次新增令牌的时间计算出下次新增令牌的时间的，另外需要一个桶暂存一段时间内没有使用的令牌（即可以突发的令牌数）。另外RateLimiter还提供了tryAcquire方法来进行无阻塞或可超时的令牌消费。

因为SmoothBursty允许一定程度的突发，会有人担心如果允许这种突发，假设突然间来了很大的流量，那么系统很可能扛不住这种突发。因此需要一种平滑速率的限流工具，从而系统冷启动后慢慢的趋于平均固定速率（即刚开始速率小一些，然后慢慢趋于我们设置的固定速率）。Guava也提供了SmoothWarmingUp来实现这种需求类似漏桶算法;

### 举例：Guava RateLimiter - SmoothWarmingUp

SmoothWarmingUp创建方式：`RateLimiter.create(doublepermitsPerSecond, long warmupPeriod, TimeUnit unit)`

`permitsPerSecond`表示每秒新增的令牌数，`warmupPeriod` 表示在从冷启动速率过渡到平均速率的时间间隔。

```java
RateLimiter limiter = RateLimiter.create(5,1000, TimeUnit.MILLISECONDS);
for(inti =1; i < 5;i++) {
    System.out.println(limiter.acquire());
}
Thread.sleep(1000L);
for(inti =1; i < 5;i++) {
    System.out.println(limiter.acquire());
}

// 将得到类似如下的输出：
0.0
0.51767
0.357814
0.219992
0.199984
0.0
0.360826
0.220166
0.199723
0.199555
```

速率是梯形上升速率的，也就是说冷启动时会以一个比较大的速率慢慢到平均速率；然后趋于平均速率（梯形下降到平均速率）。可以通过调节warmupPeriod参数实现一开始就是平滑固定速率。

## 方案二：漏桶方式

水(请求)先进入到漏桶里,漏桶以一定的速度出水(接口有响应速率),当水流入速度过大会直接溢出（访问频率超过接口响应速率),然后就拒绝请求,可以看出漏桶算法能强行限制数据的传输速率。

![image-20230917213612282](./README//image-20230917213612282.png)

可见这里有两个变量,一个是桶的大小,支持流量突发增多时可以存多少的水(burst),另一个是水桶漏洞的大小(rate)。

因为漏桶的漏出速率是固定的参数,所以,即使网络中不存在资源冲突(没有发生拥塞),漏桶算法也不能使流突发(burst)到端口速率.因此,漏桶算法对于存在突发特性的流量来说缺乏效率.

### 令牌桶和漏桶对比

- 令牌桶是按照固定速率往桶中添加令牌，请求是否被处理需要看桶中令牌是否足够，当令牌数减为零时则拒绝新的请求；
- 漏桶则是按照常量固定速率流出请求，流入请求速率任意，当流入的请求数累积到漏桶容量时，则新流入的请求被拒绝；
- 令牌桶限制的是平均流入速率（允许突发请求，只要有令牌就可以处理，支持一次拿3个令牌，4个令牌），并允许一定程度突发流量；
- 漏桶限制的是常量流出速率（即流出速率是一个固定常量值，比如都是1的速率流出，而不能一次是1，下次又是2），从而平滑突发流入速率；
- 令牌桶允许一定程度的突发，而漏桶主要目的是平滑流入速率；
- 两个算法实现可以一样，但是方向是相反的，对于相同的参数得到的限流效果是一样的。

## 方案三：计数器方式

计数器限流算法也是比较常用的，主要用来限制总并发数，比如数据库连接池大小、线程池大小、程序访问并发数等都是使用计数器算法。也是最简单粗暴的算法。

### 采用AtomicInteger

使用AomicInteger来进行统计当前正在并发执行的次数，如果超过域值就简单粗暴的直接响应给用户，说明系统繁忙，请稍后再试或其它跟业务相关的信息。

弊端：使用 AomicInteger 简单粗暴超过域值就拒绝请求，可能只是瞬时的请求量高，也会拒绝请求。

```java
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

```



### 采用令牌Semaphore

使用Semaphore信号量来控制并发执行的次数，如果超过域值信号量，则进入阻塞队列中排队等待获取信号量进行执行。如果阻塞队列中排队的请求过多超出系统处理能力，则可以在拒绝请求。

相对Atomic优点：如果是瞬时的高并发，可以使请求在阻塞队列中排队，而不是马上拒绝请求，从而达到一个流量削峰的目的。

```java
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

```




---


## 方案四、限流某个接口的时间窗请求数

对于限流某个接口的时间窗请求数，可以采用以下方法进行实现：

```java
LoadingCache<Long, AtomicLong> counter =
        CacheBuilder.newBuilder()
                .expireAfterWrite(2, TimeUnit.SECONDS)
                .build(new CacheLoader<Long, AtomicLong>() {
                    @Override
                    public AtomicLong load(Long seconds) throws Exception {
                        return new AtomicLong(0);
                    }
                });
long limit = 1000;
while(true) {
    //得到当前秒
    long currentSeconds = System.currentTimeMillis() / 1000;
    if(counter.get(currentSeconds).incrementAndGet() > limit) {
        System.out.println("限流了:" + currentSeconds);
        continue;
    }
    //业务处理
}
```

这里统计每秒内的请求次数总和，过期时间设置为大于1s的时间即可，保证1s内的统计不会过期。这种方法不受业务执行快慢影响，只统计1s钟内执行了多少次。

## 方案五、其他


### 1. 使用Hystrix实现Java限流

Hystrix是一个开源的容错框架，用于控制系统中的访问速度。通过设置系统的最大并发请求量和超时时间，我们可以有效地控制系统的负载，防止系统资源被耗尽。下面是使用Hystrix实现Java限流的示例代码：

```java
@RestController
public class RateLimitController {

    // 创建一个HystrixCommand配置
    private HystrixCommand.Setter commandConfig = HystrixCommand.Setter
            .withGroupKey(HystrixCommandGroupKey.Factory.asKey("group1"))
            .andCommandKey(HystrixCommandKey.Factory.asKey("command1"))
            .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("threadPool1"))
            .andCommandPropertiesDefaults(
                    HystrixCommandProperties.Setter()
                            .withExecutionIsolationSemaphoreMaxConcurrentRequests(10)
                            .withExecutionTimeoutInMilliseconds(500)
                            .withFallbackIsolationSemaphoreMaxConcurrentRequests(100)
            );

    // 创建一个HystrixCommand实例
    private HystrixCommand<String> command = new HystrixCommand<String>(commandConfig) {
        @Override
        protected String run() throws Exception {
            // 执行业务逻辑
            return "success";
        }

        @Override
        protected String getFallback() {
            // 返回失败信息
            return "fail";
        }
    };

    @GetMapping("/test")
    public String test() throws Exception {
        // 执行HystrixCommand实例
        return command.execute();
    }
}
```

在上述代码中，我们通过创建一个HystrixCommand配置，并设置最大并发请求量和超时时间来控制系统的负载。在执行业务逻辑时，我们通过HystrixCommand实例来执行业务逻辑，如果执行成功，则返回成功信息；如果执行失败，则返回失败信息。

### 2. 使用Redis实现Java限流

Redis是一个开源的内存数据库，用于存储大量的数据和缓存数据。通过使用Redis中的计数器和过期时间，我们可以实现Java限流的功能。下面是使用Redis实现Java限流的示例代码：

```java
@RestController
public class RateLimitController {

    // 创建Redis实例
    private RedisTemplate<String, Integer> redisTemplate = new RedisTemplate<>();
    private ValueOperations<String, Integer> valueOperations = redisTemplate.opsForValue();

    @GetMapping("/test")
    public String test() {
        // 获取当前时间
        long current = System.currentTimeMillis();

        // 获取Redis中的计数器值
        Integer count = valueOperations.get("count");

        // 如果计数器不存在，则初始化计数器
        if (count == null) {
            valueOperations.set("count", 0, Duration.ofSeconds(1));
            count = 0;
        }

        // 如果计数器达到最大值，则限流
        if (count >= 10) {
            return "fail";
        }

        // 更新计数器的值
        valueOperations.increment("count");

        // 执行业务逻辑
        return "success";
    }
}
```

在上述代码中，我们通过创建一个Redis实例，并使用Redis中的计数器来实现Java限流的功能。在每次请求处理之前，我们首先获取Redis中的计数器值，如果计数器不存在，则初始化计数器。然后，我们判断计数器的值是否达到最大值，如果达到最大值，则限流。最后，我们更新计数器的值，并执行业务逻辑。