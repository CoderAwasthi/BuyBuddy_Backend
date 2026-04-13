package com.samarthyatech.price_drop_service.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PriceScraper {

    private static final Logger logger = LoggerFactory.getLogger(PriceScraper.class);


    public Double extractPrice(String text) {

        try {

            if (text == null || text.isBlank()) {
                return null;
            }

            if (text.contains("captcha") || text.contains("validateCaptcha")) {
                logger.warn("Amazon CAPTCHA detected");
                return null;
            }

            // remove currency + commas
            String cleaned = text.replaceAll("[^0-9.]", "");

            if (cleaned.isBlank()) {
                return null;
            }

            return Double.parseDouble(cleaned);

        } catch (Exception e) {
            //log.warn("Failed to parse price: {}", text);
            return null;
        }
    }
}