package com.samarthyatech.price_drop_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {

    private Scheduler scheduler;
    private Scraping scraping;
    private Affiliate affiliate;

    @Data
    public static class Scheduler {
        private boolean enabled;
        private long interval;
    }

    @Data
    public static class Scraping {
        private String userAgent;
        private int timeout;
        private int maxConcurrency = 1;
        private long requestDelayMs = 1500;
        private int maxRetries = 1;
        private long captchaCooldownMinutes = 30;
    }

    @Data
    public static class Affiliate {
        private String tag;
    }
}