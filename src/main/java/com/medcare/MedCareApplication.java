package com.medcare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class MedCareApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedCareApplication.class, args);
    }
}
