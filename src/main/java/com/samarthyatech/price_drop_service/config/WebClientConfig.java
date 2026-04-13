package com.samarthyatech.price_drop_service.config;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final AppConfig appConfig;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .defaultHeader("Accept-Language", "en-IN,en;q=0.9")
                .defaultHeader("User-Agent", appConfig.getScraping().getUserAgent())
                .build();
    }
}