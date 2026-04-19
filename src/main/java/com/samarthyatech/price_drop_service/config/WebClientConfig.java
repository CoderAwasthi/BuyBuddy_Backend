package com.samarthyatech.price_drop_service.config;


import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration for WebClient with enhanced buffer limits and connection settings.
 * Increases buffer size to handle large HTML responses from e-commerce sites.
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final AppConfig appConfig;

    /**
     * Configures WebClient with enhanced buffer limits and connection settings.
     * Increases buffer size to handle large HTML responses from e-commerce sites.
     *
     * @return Configured WebClient bean
     */
    @Bean
    public WebClient webClient() {
        // Configure buffer size to 10MB to handle large HTML pages
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        int timeoutMs = Math.max(appConfig.getScraping().getTimeout(), 5000);

        // Configure Netty HttpClient with connection settings
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .responseTimeout(Duration.ofMillis(timeoutMs));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.USER_AGENT, appConfig.getScraping().getUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-IN,en;q=0.9,en-US;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .defaultHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .defaultHeader("Pragma", "no-cache")
                .defaultHeader("Upgrade-Insecure-Requests", "1")
                .defaultHeader("DNT", "1")
                .defaultHeader("Sec-Fetch-Site", "none")
                .defaultHeader("Sec-Fetch-Mode", "navigate")
                .defaultHeader("Sec-Fetch-Dest", "document")
                .defaultHeader(HttpHeaders.REFERER, "https://www.amazon.in/")
                .build();
    }
}