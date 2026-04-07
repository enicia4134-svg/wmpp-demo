package com.zqw.wmpp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WmppDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmppDemoApplication.class, args);
    }

}
