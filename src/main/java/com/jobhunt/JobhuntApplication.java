package com.jobhunt;

import com.jobhunt.config.JobhuntProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JobhuntProperties.class)
public class JobhuntApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobhuntApplication.class, args);
    }
}
