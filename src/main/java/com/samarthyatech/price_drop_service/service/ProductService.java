package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.CategoryResult;
import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.repo.PriceHistoryRepository;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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

    public Mono<Void> trackProduct(Product product) {
        // Input validation
        if (product == null || product.getAsin() == null || product.getAsin().trim().isEmpty()) {
            logger.warn("Invalid product data: ASIN is required");
            trackProductErrors.increment();
            return Mono.empty();
        }

        if (product.getCurrentPrice() == null || product.getCurrentPrice() <= 0) {
            logger.warn("Invalid product data: Current price must be positive for ASIN: {}", product.getAsin());
            trackProductErrors.increment();
            return Mono.empty();
        }

        logger.info("Tracking product: {}", product.getAsin());
        trackProductCounter.increment();

        return productRepo.findByAsinAndUserId(product.getAsin(), product.getUserId())
                .flatMap(existing -> updateProduct(existing, product))
                .switchIfEmpty(createNew(product))
                .doOnSuccess(v -> logger.info("Successfully tracked product: {}", product.getAsin()))
                .doOnError(e -> {
                    logger.error("Error tracking product: {}", product.getAsin(), e);
                    trackProductErrors.increment();
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

        // Cache category detection to avoid repeated processing
        String cacheKey = generateCacheKey(incoming.getTitle(), incoming.getBreadcrumb());
        CategoryResult result = categoryCache.computeIfAbsent(cacheKey, k -> {
            categoryCacheMisses.increment();
            return categoryService.detect(incoming.getTitle(), incoming.getBreadcrumb());
        });

        if (categoryCache.containsKey(cacheKey)) {
            categoryCacheHits.increment();
        }

        double oldPrice = existing.getCurrentPrice() != null ? existing.getCurrentPrice() : 0;

        existing.setCurrentPrice(newPrice);
        existing.setCurrency(incoming.getCurrency());
        existing.setMrp(incoming.getMrp());
        existing.setRating(incoming.getRating());
        existing.setReviews(incoming.getReviews());
        existing.setImage(incoming.getImage());
        existing.setCategory(result.getCategory());
        existing.setSubCategory(result.getSubCategory());
        existing.setUpdatedAt(LocalDateTime.now());

        // Parallelize product save and history save if price changed
        Mono<Product> saveProduct = productRepo.save(existing);
        Mono<Void> saveHistory = priceChanged ? saveHistory(existing.getAsin(), newPrice) : Mono.empty();

        return Mono.zip(saveProduct, saveHistory)
                .map(tuple -> tuple.getT1())
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

        // Parallelize product save and history save
        Mono<Product> saveProduct = productRepo.save(product);
        Mono<Void> saveHistory = saveHistory(product.getAsin(), product.getCurrentPrice());

        return Mono.zip(saveProduct, saveHistory)
                .map(tuple -> tuple.getT1())
                .doOnSuccess(p -> logger.info("Created new product tracking for ASIN: {}", product.getAsin()));
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

}
