package com.firefly.domain.banking.cards.web.openapi;

import org.fireflyframework.web.openapi.EnableOpenApiGen;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;

@EnableOpenApiGen
@ComponentScan(basePackages = "com.firefly.domain.banking.cards.web.controllers")
public class OpenApiGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenApiGenApplication.class, args);
    }
}
