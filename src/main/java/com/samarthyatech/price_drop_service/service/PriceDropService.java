package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.config.AppConfig;
import com.samarthyatech.price_drop_service.model.Notification;
import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.repo.NotificationRepository;
import com.samarthyatech.price_drop_service.repo.PriceHistoryRepository;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import com.samarthyatech.price_drop_service.scraper.PriceScraper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for processing product price updates through web scraping.
 * Handles price extraction, history saving, and notification creation for price drops.
 */
@Service
@RequiredArgsConstructor
public class PriceDropService {

    private static final Logger logger = LoggerFactory.getLogger(PriceDropService.class);

    private final ProductRepository productRepo;
    private final PriceHistoryRepository historyRepo;
    private final NotificationRepository notificationRepo;
    private final WebClient webClient;
    private final PriceScraper scraper;
    private final MeterRegistry meterRegistry;
    private final AppConfig appConfig;

    private Counter scrapeSuccess, scrapeFailure, notificationsSent;
    private final AtomicReference<Instant> captchaCooldownUntil = new AtomicReference<>(Instant.EPOCH);

    @PostConstruct
    public void init() {
        scrapeSuccess = meterRegistry.counter("scraping.success");
        scrapeFailure = meterRegistry.counter("scraping.failure");
        notificationsSent = meterRegistry.counter("notifications.sent");
    }

    /**
     * Processes a product by scraping its current price from the web.
     * Updates the product's price, saves history, and creates notifications if applicable.
     * Includes retry logic for HTTP requests and metrics collection.
     *
     * @param product The product to process
     * @return Mono that completes when processing is done
     */
    public Mono<Void> processProduct(Product product) {

        Instant blockedUntil = captchaCooldownUntil.get();
        if (Instant.now().isBefore(blockedUntil)) {
            logger.debug("Skipping scrape for ASIN: {} because CAPTCHA cooldown is active until {}",
                    product.getAsin(), blockedUntil);
            return Mono.empty();
        }

        Mono<String> htmlMono = webClient.get()
                .uri(product.getUrl())
                .retrieve()
                .bodyToMono(String.class);

        int maxRetries = Math.max(appConfig.getScraping().getMaxRetries(), 0);
        if (maxRetries > 0) {
            htmlMono = htmlMono.retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(5)));
        }

        return htmlMono.flatMap(html -> {
                    if (scraper.containsCaptcha(html)) {
                        activateCaptchaCooldown(product.getAsin());
                        scrapeFailure.increment();
                        return Mono.empty();
                    }

                    return Mono.justOrEmpty(scraper.extractPrice(html))
                        .doOnNext(price -> {
                            scrapeSuccess.increment();
                            logger.debug("Extracted price {} for ASIN: {}", price, product.getAsin());
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            scrapeFailure.increment();
                            logger.warn("Could not extract price from scraped HTML for ASIN: {}", product.getAsin());
                            return Mono.empty();
                        }));
                })
                .flatMap(newPrice -> {

                    Double oldPrice = product.getCurrentPrice();

                    if (isSuspiciousPrice(oldPrice, newPrice)) {
                        logger.warn("Rejected suspicious scraped price {} for ASIN: {}. Existing price: {}",
                                newPrice, product.getAsin(), oldPrice);
                        scrapeFailure.increment();
                        return Mono.empty();
                    }

                    boolean isDrop =
                            oldPrice != null && newPrice < oldPrice;

                    product.setCurrentPrice(newPrice);

                    if (product.getLowestPrice() == null ||
                            newPrice < product.getLowestPrice()) {
                        product.setLowestPrice(newPrice);
                    }

                    Mono<Void> notifyMono = Mono.empty();

                    if (isDrop && shouldNotify(product, newPrice)) {

                        notifyMono = createNotification(product, oldPrice, newPrice)
                                .then();

                        product.setLastNotifiedPrice(newPrice);
                    }

                    logger.info("Processed product ASIN: {} - Scraped price: {}, Old price: {}, Is drop: {}", product.getAsin(), newPrice, oldPrice, isDrop);

                    return productRepo.save(product)
                            .then(saveHistory(product.getAsin(), newPrice))
                            .then(notifyMono);
                })
                .doOnError(e -> {
                    logger.error("Failed to scrape price for ASIN: {} after retries", product.getAsin(), e);
                    scrapeFailure.increment();
                })
                .onErrorResume(e -> Mono.empty())
                .then(); // Continue processing other products
    }

    private void activateCaptchaCooldown(String asin) {
        long cooldownMinutes = Math.max(appConfig.getScraping().getCaptchaCooldownMinutes(), 1);
        Instant blockedUntil = Instant.now().plus(Duration.ofMinutes(cooldownMinutes));
        captchaCooldownUntil.set(blockedUntil);
        logger.warn("CAPTCHA detected while scraping ASIN: {}. Pausing further scraping until {}", asin, blockedUntil);
    }

    private boolean isSuspiciousPrice(Double oldPrice, Double newPrice) {

        if (newPrice == null || newPrice <= 0 || newPrice < 10) {
            return true;
        }

        if (oldPrice == null || oldPrice <= 0) {
            return false;
        }

        return oldPrice >= 500 && newPrice <= oldPrice * 0.02;
    }

    private boolean shouldNotify(Product p, Double newPrice) {

        if (p.getLastNotifiedPrice() == null) return true;

        // Avoid spam: notify only if drop > ₹50
        boolean notify = (p.getLastNotifiedPrice() - newPrice) >= 50;
        if (notify) {
            logger.debug("Notifying for ASIN: {} - Drop from {} to {}", p.getAsin(), p.getLastNotifiedPrice(), newPrice);
        }
        return notify;
    }

    private Mono<Notification> createNotification(Product p,
                                                  Double oldPrice,
                                                  Double newPrice) {

        Notification n = new Notification();

        n.setUserId(p.getUserId());
        n.setAsin(p.getAsin());
        n.setTitle(p.getTitle());

        n.setOldPrice(oldPrice);
        n.setNewPrice(newPrice);

        n.setSent(false);
        n.setCreatedAt(LocalDateTime.now());

        logger.info("Created notification for ASIN: {} - Price drop from {} to {}", p.getAsin(), oldPrice, newPrice);

        return notificationRepo.save(n).doOnSuccess(saved -> notificationsSent.increment());
    }

    private Mono<Void> saveHistory(String asin, Double price) {

        PriceHistory h = new PriceHistory();
        h.setAsin(asin);
        h.setPrice(price);
        h.setDate(LocalDateTime.now());

        return historyRepo.save(h).then();
    }
}
