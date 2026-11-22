package com.example.empiktask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EmpikTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmpikTaskApplication.class, args);
    }

}
