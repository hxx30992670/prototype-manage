package com.company.prototype.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.company.prototype.worker")
@EntityScan(basePackages = "com.company.prototype.persistence")
@EnableJpaRepositories(basePackages = "com.company.prototype.persistence")
public class PublishWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PublishWorkerApplication.class, args);
    }
}
