package com.samarthyatech.price_drop_service.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PriceScraper {

    private static final Logger logger = LoggerFactory.getLogger(PriceScraper.class);
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final List<String> SUSPICIOUS_CONTEXT_KEYWORDS = Arrays.asList(
        "delivery",
        "shipping",
        "per month",
        "/month",
        "emi",
        "coupon",
        "cashback",
        "save",
        "discount",
        "off",
        "exchange",
        "bank offer",
        "per count",
        "per item",
        "item(s)",
        "protection",
        "warranty"
    );

    // Common price selectors for various e-commerce sites
    private static final List<String> PRICE_SELECTORS = Arrays.asList(
        "#corePrice_feature_div .a-price .a-offscreen",
        "#corePriceDisplay_desktop_feature_div .a-price .a-offscreen",
        "#apex_desktop .a-price .a-offscreen",
        ".apexPriceToPay .a-offscreen",
        ".reinventPricePriceToPayMargin .a-offscreen",
        ".a-price.aok-align-center .a-offscreen",
        ".a-price .a-offscreen",           // Amazon main price
        "#priceblock_ourprice",            // Amazon our price
        "#priceblock_dealprice",           // Amazon deal price
        ".price",                          // Generic price class
        ".product-price",                  // Generic product price
        ".sale-price",                     // Sale price
        ".current-price"                   // Current price
    );

    public Double extractPrice(String html) {

        try {

            if (html == null || html.isBlank()) {
                return null;
            }

            if (containsCaptcha(html)) {
                return null;
            }

            Document doc = Jsoup.parse(html);

            Double amazonPrice = extractAmazonPrice(doc);
            if (amazonPrice != null) {
                logger.debug("Extracted Amazon price: {}", amazonPrice);
                return amazonPrice;
            }

            // Try specific selectors first
            for (String selector : PRICE_SELECTORS) {
                Elements elements = doc.select(selector);
                for (Element element : elements) {
                    String priceText = extractCandidateText(element);
                    Double price = parsePriceText(priceText);
                    if (!isValidPriceCandidate(element, priceText, price)) {
                        continue;
                    }

                    logger.debug("Extracted price using selector '{}': {}", selector, price);
                    return price;
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

    public boolean containsCaptcha(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }

        String normalizedHtml = html.toLowerCase();
        return normalizedHtml.contains("validatecaptcha")
                || normalizedHtml.contains("enter the characters you see below")
                || normalizedHtml.contains("type the characters you see in this image")
                || normalizedHtml.contains("captcha");
    }

    private Double parsePriceText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            Matcher matcher = PRICE_PATTERN.matcher(text);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1).replace(",", ""));
            }

            // Remove currency symbols, commas, and extra spaces
            String cleaned = text.replaceAll("[₹$€£¥\\s,]", "").trim();

            if (cleaned.isBlank() || !cleaned.matches("\\d{2,}(\\.\\d{1,2})?")) {
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
            if (text == null || text.isBlank()) {
                return null;
            }

            Matcher matcher = PRICE_PATTERN.matcher(text);
            if (!matcher.find()) {
                return null;
            }

            String cleaned = matcher.group(1).replace(",", "").trim();
            double price = Double.parseDouble(cleaned);
            return price >= 10 ? price : null;
        } catch (Exception e) {
            logger.debug("Fallback price extraction failed for text length: {}", text.length());
            return null;
        }
    }

    private Double extractAmazonPrice(Document doc) {
        List<String> amazonSelectors = Arrays.asList(
            "#corePrice_feature_div .a-price",
            "#corePriceDisplay_desktop_feature_div .a-price",
            "#apex_desktop .a-price",
            ".apexPriceToPay .a-price",
            ".reinventPricePriceToPayMargin .a-price",
            ".a-price"
        );

        for (String selector : amazonSelectors) {
            for (Element priceElement : doc.select(selector)) {
                String candidateText = extractCandidateText(priceElement);
                Double price = parsePriceText(candidateText);
                if (isValidPriceCandidate(priceElement, candidateText, price)) {
                    return price;
                }
            }
        }

        return null;
    }

    private String extractCandidateText(Element element) {
        if (element == null) {
            return "";
        }

        Element offscreen = element.selectFirst(".a-offscreen");
        if (offscreen != null && !offscreen.text().isBlank()) {
            return offscreen.text().trim();
        }

        Element whole = element.selectFirst(".a-price-whole");
        if (whole != null) {
            String fraction = "";
            Element fractionElement = element.selectFirst(".a-price-fraction");
            if (fractionElement != null && !fractionElement.text().isBlank()) {
                fraction = "." + fractionElement.text().trim();
            }
            return (whole.text() + fraction).trim();
        }

        if (element.hasAttr("data-price")) {
            return element.attr("data-price").trim();
        }

        return element.text().trim();
    }

    private boolean isValidPriceCandidate(Element element, String priceText, Double price) {
        if (price == null || price < 10) {
            return false;
        }

        String context = buildContext(element, priceText);
        return SUSPICIOUS_CONTEXT_KEYWORDS.stream().noneMatch(context::contains);
    }

    private String buildContext(Element element, String priceText) {
        StringBuilder context = new StringBuilder(priceText == null ? "" : priceText);

        Element current = element;
        for (int i = 0; i < 2 && current != null; i++) {
            context.append(' ').append(current.text());
            current = current.parent();
        }

        return context.toString().toLowerCase();
    }
}