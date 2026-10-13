package com.nutrinight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NutriNightApplication {

    public static void main(String[] args) {
        SpringApplication.run(NutriNightApplication.class, args);
    }
}
