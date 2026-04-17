package com.samarthyatech.price_drop_service.config;


import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

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

        // Configure Netty HttpClient with connection settings
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .responseTimeout(java.time.Duration.ofSeconds(30));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader("User-Agent", appConfig.getScraping().getUserAgent())
                .defaultHeader("Accept-Language", "en-IN,en;q=0.9")
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
    }
}