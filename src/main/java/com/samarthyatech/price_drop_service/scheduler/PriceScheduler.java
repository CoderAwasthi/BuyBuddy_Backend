package com.samarthyatech.price_drop_service.scheduler;

import com.samarthyatech.price_drop_service.repo.ProductRepository;
import com.samarthyatech.price_drop_service.scraper.PriceScraper;
import com.samarthyatech.price_drop_service.service.PriceDropService;
import com.samarthyatech.price_drop_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler for periodic price updates of tracked products.
 * Fetches product pages, extracts prices, and updates price history.
 * Runs at configured intervals to keep price data current.
 *
 * NOTE: Only one scheduler method should run at a time. Comment out runScheduler()
 * if you want to use updatePrices() or vice versa.
 */
@Component
@RequiredArgsConstructor
public class PriceScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PriceScheduler.class);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    private final ProductRepository productRepo;
    private final ProductService service;
    private final WebClient webClient;
    private final PriceScraper scraper;
    private final PriceDropService priceDropService;

    @Value("${app.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /**
     * Scheduled task that updates prices for all tracked products.
     * Runs at the interval configured in app.scheduler.interval (milliseconds).
     * Fetches product URLs, extracts prices, and saves to database.
     * Includes error handling to continue processing even if one product fails.
     *
     * IMPORTANT: This method should run ALONE - comment out runScheduler() to use this.
     * Having both methods run causes concurrent issues and resource exhaustion.
     */
    @Scheduled(fixedRateString = "${app.scheduler.interval}", initialDelay = 10000)
    public void updatePrices() {
        if (!schedulerEnabled) {
            return;
        }

        // Prevent concurrent execution
        if (!isProcessing.compareAndSet(false, true)) {
            logger.warn("Price update already in progress, skipping this cycle");
            return;
        }

        try {
            logger.info("Starting scheduled price update job");
            long startTime = System.currentTimeMillis();

            productRepo.findAll()
                    .flatMap(product ->
                            webClient.get()
                                    .uri(product.getUrl())
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(10)))
                                    .map(scraper::extractPrice)
                                    .flatMap(price -> {
                                        if (price != null) {
                                            logger.debug("Extracted price {} for ASIN: {}", price, product.getAsin());
                                            product.setCurrentPrice(price);
                                            return service.trackProduct(product);
                                        } else {
                                            logger.warn("Failed to extract price for ASIN: {}", product.getAsin());
                                            return reactor.core.publisher.Mono.empty();
                                        }
                                    })
                                    .onErrorContinue((e, obj) -> logger.error("Error updating price for product: {}", obj, e))
                    )
                    .doOnComplete(() -> {
                        long duration = System.currentTimeMillis() - startTime;
                        logger.info("Price update job completed successfully in {} ms", duration);
                    })
                    .doOnError(error -> logger.error("Price update job failed", error))
                    .subscribe(
                        unused -> {},
                        error -> logger.error("Unexpected error in price update", error),
                        () -> logger.info("Price update job finished")
                    );
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Scheduled task that processes products for price drop detection.
     * Uses PriceDropService to scrape, analyze, and notify on significant drops.
     * Runs at the interval configured in app.scheduler.interval (milliseconds).
     * Continues processing all products even if individual ones fail.
     *
     * IMPORTANT: This method should run ALONE - comment out updatePrices() to use this.
     * Having both methods run causes concurrent issues and resource exhaustion.
     */
    // COMMENT OUT THIS METHOD IF USING updatePrices() ABOVE
    // @Scheduled(fixedRateString = "${app.scheduler.interval}", initialDelay = 10000)
    public void runScheduler() {
        if (!schedulerEnabled) {
            return;
        }

        // Prevent concurrent execution
        if (!isProcessing.compareAndSet(false, true)) {
            logger.warn("Price drop processing already in progress, skipping this cycle");
            return;
        }

        try {
            logger.info("Starting scheduled price drop processing job");
            long startTime = System.currentTimeMillis();

            productRepo.findAll()
                    .flatMap(product -> {
                        logger.debug("Processing product: {} (ASIN: {})", product.getTitle(), product.getAsin());
                        return priceDropService.processProduct(product);
                    })
                    .onErrorContinue((e, obj) -> logger.error("Error processing product {}: {}", obj, e.getMessage()))
                    .doOnComplete(() -> {
                        long duration = System.currentTimeMillis() - startTime;
                        logger.info("Price drop processing job completed in {} ms", duration);
                    })
                    .doOnError(error -> logger.error("Scheduler processing error", error))
                    .subscribe(
                        unused -> {},
                        error -> logger.error("Unexpected error in scheduler", error),
                        () -> logger.info("Scheduler finished")
                    );
        } finally {
            isProcessing.set(false);
        }
    }
}