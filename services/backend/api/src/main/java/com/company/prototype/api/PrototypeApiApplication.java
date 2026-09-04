package com.company.prototype.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.company.prototype.api")
@EntityScan(basePackages = "com.company.prototype.persistence")
@EnableJpaRepositories(basePackages = "com.company.prototype.persistence")
@EnableAspectJAutoProxy
public class PrototypeApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrototypeApiApplication.class, args);
    }

    @Bean
    public PublicIdGenerator publicIdGenerator() {
        return new UlidPublicIdGenerator();
    }
}
