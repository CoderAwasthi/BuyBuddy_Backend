package com.samarthyatech.price_drop_service.controller;

import com.samarthyatech.price_drop_service.model.DealResponse;
import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.service.DealService;
import com.samarthyatech.price_drop_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for product tracking and deal analysis operations.
 * Provides endpoints for tracking products, retrieving price history, and analyzing deals.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final DealService dealService;

    /**
     * Tracks a new product or updates an existing one.
     * Extracts product details and saves/updates in the database.
     *
     * @param product The product to track
     * @return Mono that completes when tracking is done
     */
    @PostMapping("/track")
    public Mono<Void> track(@RequestBody Product product) {
        return productService.trackProduct(product);
    }

    /**
     * Retrieves the price history for a specific product.
     *
     * @param asin The ASIN of the product
     * @return Flux of price history entries ordered by date ascending
     */
    @GetMapping("/history")
    public Flux<PriceHistory> history(@RequestParam String asin) {
        return productService.getHistory(asin);
    }

    /**
     * Retrieves all price history entries across all products.
     * Use with caution as this can return large datasets.
     *
     * @return Flux of all price history entries
     */
    @GetMapping("/allhistory")
    public Flux<PriceHistory> allhistory() {
        return productService.getAllHistory();
    }

    /**
     * Retrieves deals filtered by category and subCategory, with optional exclusion.
     *
     * @param category The category to filter deals by
     * @param subCategory The subCategory to filter deals by
     * @param excludeAsin Optional ASIN to exclude from results
     * @return Flux of deal responses matching the criteria
     */
    @GetMapping("/deals")
    public Flux<DealResponse> deals(@RequestParam String category,@RequestParam String subCategory,@RequestParam(required = false) String excludeAsin) {
        return dealService.getDeals(category,subCategory,excludeAsin);
    }

    /**
     * Analyzes deal information for a specific product.
     *
     * @param asin The ASIN of the product to analyze
     * @return Mono containing the deal analysis response
     */
    @GetMapping("/analyze")
    public Mono<DealResponse> analyze(@RequestParam String asin) {
        return dealService.analyze(asin);
    }
}