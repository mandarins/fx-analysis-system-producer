package com.forex.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ForexProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForexProducerApplication.class, args);
    }

}
