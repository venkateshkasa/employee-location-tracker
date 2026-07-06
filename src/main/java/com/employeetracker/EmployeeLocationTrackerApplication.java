package com.employeetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmployeeLocationTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeLocationTrackerApplication.class, args);
    }
}
