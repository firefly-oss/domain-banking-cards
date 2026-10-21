/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.banking.cards.web;

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
        "io.fireflysoftware.domain.banking.cards",
        "org.fireflyframework.web"
    }
)
@EnableWebFlux
@ConfigurationPropertiesScan(basePackages = "io.fireflysoftware.domain.banking.cards")
@OpenAPIDefinition(
    info = @Info(
        title = "Domain Banking Cards API",
        version = "1.0.0",
        description = "Domain layer service for banking card orchestration - manages card lifecycle, issuance, security, and credit lines",
        contact = @Contact(
            name = "Firefly Software Foundation",
            email = "dev@fireflysoftware.io"
        )
    ),
    servers = {
        @Server(url = "https://openplatform.fireflysoftware.io/domain-banking-cards", description = "Development Environment"),
        @Server(url = "/", description = "Local Development Environment")
    }
)
public class DomainBankingCardsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DomainBankingCardsApplication.class, args);
    }
}
