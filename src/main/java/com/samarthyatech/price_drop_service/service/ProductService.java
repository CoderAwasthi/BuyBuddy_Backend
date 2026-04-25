package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.CategoryResult;
import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.model.ProductSource;
import com.samarthyatech.price_drop_service.repo.PriceHistoryRepository;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.mongodb.MongoSocketReadTimeoutException;
import com.mongodb.MongoTimeoutException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepo;
    private final PriceHistoryRepository historyRepo;
    private final CategoryService categoryService;

    @Autowired
    private MeterRegistry meterRegistry;

    // Cache for category detection results to avoid repeated processing
    private final ConcurrentHashMap<String, CategoryResult> categoryCache = new ConcurrentHashMap<>();

    // Performance metrics - initialized lazily
    private Counter trackProductCounter;
    private Counter trackProductErrors;
    private Counter categoryCacheHits;
    private Counter categoryCacheMisses;

    private static final ProductSource DEFAULT_SOURCE = ProductSource.MAIN_VIEWED_PRODUCT;

    @PostConstruct
    public void initMetrics() {
        if (meterRegistry != null) {
            trackProductCounter = Counter.builder("products.tracked")
                    .description("Number of products tracked")
                    .register(meterRegistry);

            trackProductErrors = Counter.builder("products.track.errors")
                    .description("Number of product tracking errors")
                    .register(meterRegistry);

            categoryCacheHits = Counter.builder("category.cache.hits")
                    .description("Number of category cache hits")
                    .register(meterRegistry);

            categoryCacheMisses = Counter.builder("category.cache.misses")
                    .description("Number of category cache misses")
                    .register(meterRegistry);
        }
    }

    /**
     * Tracks products in bulk using the same validation and persistence flow as single-product tracking.
     * Invalid items are skipped by {@link #trackProduct(Product)}.
     *
     * @param products List of products to track
     * @return Mono that completes when all products are processed
     */
    public Mono<Void> trackProductsBulk(List<Product> products) {
        if (products == null || products.isEmpty()) {
            logger.warn("Bulk track request received with empty payload");
            return Mono.empty();
        }

        logger.info("Tracking {} products in bulk", products.size());

        return Flux.fromIterable(products)
                .concatMap(product -> trackProduct(product)
                        .doOnSuccess(v -> logger.info("Bulk tracked ASIN: {}", product != null ? product.getAsin() : "null"))
                        .onErrorResume(ex -> {
                            logger.error("Bulk track failed for ASIN: {}", product != null ? product.getAsin() : "null", ex);
                            return Mono.empty();
                        }))
                .doOnComplete(() -> logger.info("Completed bulk tracking for {} products", products.size()))
                .then();
    }

    public Mono<Void> trackProduct(Product product) {
        // Input validation
        if (product == null || product.getAsin() == null || product.getAsin().trim().isEmpty()) {
            logger.warn("Invalid product data: ASIN is required");
            incrementCounter(trackProductErrors);
            return Mono.empty();
        }

        if (product.getCurrentPrice() == null || product.getCurrentPrice() <= 0) {
            logger.warn("Invalid product data: Current price must be positive for ASIN: {}", product.getAsin());
            incrementCounter(trackProductErrors);
            return Mono.empty();
        }

        product.setSource(resolveSource(product.getSource(), null));

        logger.info("Tracking product: {}", product.getAsin());
        incrementCounter(trackProductCounter);

        String asin = product.getAsin();

        return productRepo.findByAsin(asin)
                .flatMap(existing -> updateProduct(existing, product))
                .switchIfEmpty(createNew(product))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(300))
                        .filter(this::isTransientMongoError))
                .onErrorResume(DuplicateKeyException.class, ex -> {
                    logger.warn("Duplicate key detected while tracking ASIN: {}. Retrying as update", asin);
                    // Handles race conditions where another request inserts same ASIN first.
                    return productRepo.findByAsin(asin)
                            .flatMap(existing -> updateProduct(existing, product))
                            .switchIfEmpty(Mono.error(ex));
                })
                .doOnSuccess(v -> logger.info("Successfully tracked product: {}", product.getAsin()))
                .doOnError(e -> {
                    logger.error("Error tracking product: {}", product.getAsin(), e);
                    incrementCounter(trackProductErrors);
                })
                .then();
    }

    private Mono<Product> updateProduct(Product existing, Product incoming) {
        Double newPrice = incoming.getCurrentPrice();

        boolean priceChanged = existing.getCurrentPrice() == null || !newPrice.equals(existing.getCurrentPrice());
        boolean lowestPriceUpdated = existing.getLowestPrice() == null || newPrice < existing.getLowestPrice();

        if (lowestPriceUpdated) {
            existing.setLowestPrice(newPrice);
        }

        double oldPrice = existing.getCurrentPrice() != null ? existing.getCurrentPrice() : 0;

        existing.setSource(resolveSource(incoming.getSource(), existing.getSource()));
        existing.setCurrentPrice(newPrice);

        // Cache category detection to avoid repeated processing
        String cacheKey = generateCacheKey(incoming.getTitle(), incoming.getBreadcrumb());
        CategoryResult result = categoryCache.computeIfAbsent(cacheKey, k -> {
            categoryCacheMisses.increment();
            return categoryService.detect(incoming.getTitle(), incoming.getBreadcrumb());
        });

        if (categoryCache.containsKey(cacheKey)) {
            categoryCacheHits.increment();
        }

        existing.setUserId(incoming.getUserId());
        existing.setCurrency(incoming.getCurrency());
        existing.setMrp(incoming.getMrp());
        existing.setRating(incoming.getRating());
        existing.setReviews(incoming.getReviews());
        existing.setImage(incoming.getImage());
        existing.setCategory(result.getCategory());
        existing.setSubCategory(result.getSubCategory());

        // New fields from extension payload
        if (incoming.getDomain() != null)          existing.setDomain(incoming.getDomain());
        if (incoming.getAvailability() != null) {
            existing.setAvailability(incoming.getAvailability());
            // Auto-derive outOfStock when not explicitly provided
            existing.setOutOfStock(incoming.getOutOfStock() != null
                    ? incoming.getOutOfStock()
                    : !incoming.getAvailability().equalsIgnoreCase("IN_STOCK"));
        } else if (incoming.getOutOfStock() != null) {
            existing.setOutOfStock(incoming.getOutOfStock());
        }
        if (incoming.getSourceDetail() != null)    existing.setSourceDetail(incoming.getSourceDetail());
        if (incoming.getRelatedMainAsin() != null) existing.setRelatedMainAsin(incoming.getRelatedMainAsin());
        if (incoming.getTimestamp() != null)       existing.setTimestamp(incoming.getTimestamp());

        existing.setUpdatedAt(LocalDateTime.now());

        // Save product first, then history only when price changes.
        return productRepo.save(existing)
                .flatMap(saved -> {
                    if (!priceChanged) {
                        return Mono.just(saved);
                    }
                    return saveHistory(saved.getAsin(), newPrice).thenReturn(saved);
                })
                .doOnSuccess(p -> {
                    if (priceChanged) {
                        logger.debug("Price updated for ASIN: {} from {} to {}",
                            existing.getAsin(), oldPrice, newPrice);
                    }
                });
    }

    private Mono<Product> createNew(Product product) {
        product.setLowestPrice(product.getCurrentPrice());
        product.setCreatedAt(LocalDateTime.now());

        product.setSource(resolveSource(product.getSource(), null));

        // Cache category detection
        String cacheKey = generateCacheKey(product.getTitle(), product.getBreadcrumb());
        CategoryResult result = categoryCache.computeIfAbsent(cacheKey, k -> {
            categoryCacheMisses.increment();
            return categoryService.detect(product.getTitle(), product.getBreadcrumb());
        });

        if (categoryCache.containsKey(cacheKey)) {
            categoryCacheHits.increment();
        }

        product.setCategory(result.getCategory());
        product.setSubCategory(result.getSubCategory());

        // Derive outOfStock from availability string if not explicitly set
        if (product.getOutOfStock() == null && product.getAvailability() != null) {
            product.setOutOfStock(!product.getAvailability().equalsIgnoreCase("IN_STOCK"));
        }

        return productRepo.save(product)
                .flatMap(saved -> saveHistory(saved.getAsin(), saved.getCurrentPrice()).thenReturn(saved))
                .doOnSuccess(p -> logger.info("Created new product tracking for ASIN: {} from source: {}", product.getAsin(), product.getSource()));
    }

    private String generateCacheKey(String title, List<String> breadcrumb) {
        String breadcrumbStr = breadcrumb != null ? String.join("|", breadcrumb) : "";
        return (title != null ? title : "") + "|" + breadcrumbStr;
    }

    private Mono<Void> saveHistory(String asin, Double price) {
        PriceHistory history = new PriceHistory();
        history.setAsin(asin);
        history.setPrice(price);
        history.setDate(LocalDateTime.now());

        return historyRepo.save(history)
                .doOnSuccess(h -> logger.debug("Saved price history for ASIN: {} at price: {}", asin, price))
                .then();
    }

    public Flux<PriceHistory> getHistory(String asin) {
        return historyRepo.findByAsinOrderByDateAsc(asin);
    }

    public Flux<PriceHistory> getAllHistory() {
        return historyRepo.findAll();
    }

    private void incrementCounter(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private boolean isTransientMongoError(Throwable throwable) {
        return throwable instanceof MongoTimeoutException
                || throwable instanceof MongoSocketReadTimeoutException;
    }

    private ProductSource resolveSource(ProductSource incomingSource, ProductSource fallbackSource) {
        if (incomingSource != null) {
            return incomingSource;
        }
        if (fallbackSource != null) {
            return fallbackSource;
        }
        return DEFAULT_SOURCE;
    }

}
