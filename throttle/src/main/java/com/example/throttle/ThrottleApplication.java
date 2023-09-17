package com.example.throttle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootApplication
@RestController
@EnableScheduling
public class ThrottleApplication {

    @GetMapping("/hello")
    public Mono<String> hello() {
        return Mono.just("hello");
    }

    public static void main(String[] args) {
        SpringApplication.run(ThrottleApplication.class, args);
    }

}
