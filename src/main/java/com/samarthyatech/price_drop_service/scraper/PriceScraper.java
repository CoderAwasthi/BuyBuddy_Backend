package com.samarthyatech.price_drop_service.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class PriceScraper {

    private static final Logger logger = LoggerFactory.getLogger(PriceScraper.class);

    // Common price selectors for various e-commerce sites
    private static final List<String> PRICE_SELECTORS = Arrays.asList(
        ".a-price .a-offscreen",           // Amazon main price
        "#priceblock_ourprice",            // Amazon our price
        "#priceblock_dealprice",           // Amazon deal price
        ".a-color-price",                  // Amazon color price
        ".price",                          // Generic price class
        ".product-price",                  // Generic product price
        ".sale-price",                     // Sale price
        "[data-price]",                    // Data attribute
        ".current-price"                   // Current price
    );

    public Double extractPrice(String html) {

        try {

            if (html == null || html.isBlank()) {
                return null;
            }

            if (html.contains("captcha") || html.contains("validateCaptcha")) {
                logger.warn("CAPTCHA detected in HTML");
                return null;
            }

            Document doc = Jsoup.parse(html);

            // Try specific selectors first
            for (String selector : PRICE_SELECTORS) {
                Elements elements = doc.select(selector);
                if (!elements.isEmpty()) {
                    String priceText = elements.first().text().trim();
                    Double price = parsePriceText(priceText);
                    if (price != null) {
                        logger.debug("Extracted price using selector '{}': {}", selector, price);
                        return price;
                    }
                }
            }

            // Fallback: search for price patterns in text
            String text = doc.text();
            return extractPriceFromText(text);

        } catch (Exception e) {
            logger.warn("Failed to extract price from HTML", e);
            return null;
        }
    }

    private Double parsePriceText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            // Remove currency symbols, commas, and extra spaces
            String cleaned = text.replaceAll("[₹$€£¥\\s,]", "").trim();

            // Handle cases like "1,234.56" -> "1234.56"
            cleaned = cleaned.replace(",", "");

            if (cleaned.isBlank() || !cleaned.matches("\\d+(\\.\\d+)?")) {
                return null;
            }

            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse price text: {}", text);
            return null;
        }
    }

    private Double extractPriceFromText(String text) {
        try {
            // Fallback to original method
            String cleaned = text.replaceAll("[^0-9.]", "");

            if (cleaned.isBlank()) {
                return null;
            }

            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            logger.debug("Fallback price extraction failed for text: {}", text);
            return null;
        }
    }
}