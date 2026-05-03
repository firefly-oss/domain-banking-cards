package com.firefly.domain.banking.cards.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication(
    scanBasePackages = {
        "com.firefly.domain.banking.cards",
        "org.fireflyframework.web"
    }
)
@EnableWebFlux
@ConfigurationPropertiesScan(basePackages = "com.firefly.domain.banking.cards")
@OpenAPIDefinition(
    info = @Info(
        title = "Domain Banking Cards API",
        version = "1.0.0",
        description = "Domain layer service for banking card orchestration - manages card lifecycle, issuance, security, and credit lines",
        contact = @Contact(
            name = "Firefly Software Foundation",
            email = "dev@getfirefly.io"
        )
    ),
    servers = {
        @Server(url = "http://domain.getfirefly.io/domain-banking-cards", description = "Development Environment"),
        @Server(url = "/", description = "Local Development Environment")
    }
)
public class DomainBankingCardsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DomainBankingCardsApplication.class, args);
    }
}
