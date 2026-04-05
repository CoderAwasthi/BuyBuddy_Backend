package com.samarthyatech.price_drop_service.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class PriceScraper {

    public Double extractPrice(String html) {

        Document doc = Jsoup.parse(html);

        String priceText = doc.select(".a-price .a-offscreen").text();

        return Double.parseDouble(priceText.replaceAll("[₹,]", ""));
    }
}