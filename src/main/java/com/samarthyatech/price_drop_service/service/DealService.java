package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.DealResponse;
import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.model.Stats;
import com.samarthyatech.price_drop_service.repo.PriceHistoryRepository;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for analyzing product price deals based on historical data.
 * Calculates deal scores, trends, and insights for price drop detection.
 */
@Service
@RequiredArgsConstructor
public class DealService {

    private static final Logger logger = LoggerFactory.getLogger(DealService.class);

    private final ProductRepository productRepo;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MeterRegistry meterRegistry;

    @Value("${app.deals.limit:10}")
    private int dealLimit;

    private Counter dealsGenerated;

    @PostConstruct
    public void init() {
        dealsGenerated = meterRegistry.counter("deals.generated");
    }

    /**
     * Analyzes a single product's deal information by ASIN.
     *
     * @param asin The Amazon Standard Identification Number of the product
     * @return Mono containing the deal analysis response, or empty if product not found
     */
    public Mono<DealResponse> analyze(String asin) {

        return productRepo.findByAsin(asin)
                .flatMap(product ->
                        Mono.zip(
                                priceHistoryRepository.getStatsByAsin(asin).defaultIfEmpty(new Stats(0.0, 0.0, 0.0)),
                                priceHistoryRepository.findTop3ByAsinOrderByDateDesc(asin).collectList()
                        ).map(tuple -> calculateDeal(product, tuple.getT1(), tuple.getT2()))
                )
                .switchIfEmpty(
                        Mono.just(
                                new DealResponse(
                                        asin,
                                        "No Data",
                                        null,
                                        (double) 0,
                                        (double) 0,
                                        "WAIT",
                                        (double) 0,
                                        "NO_DATA",
                                        "Not enough data yet",
                                        (double) 0, (double) 0, (double) 0,
                                        "-",
                                        "general",
                                        "general"
                                )
                        )
                );
    }

    /**
     * Retrieves the top deals across all products, sorted by score descending.
     *
     * @return Flux of deal responses, limited to the configured deal limit
     */
    public Flux<DealResponse> getTopDeals() {

        return productRepo.findAll()
                .flatMap(p ->
                        Mono.zip(
                                priceHistoryRepository.getStatsByAsin(p.getAsin()).defaultIfEmpty(new Stats(0.0, 0.0, 0.0)),
                                priceHistoryRepository.findTop3ByAsinOrderByDateDesc(p.getAsin()).collectList()
                        ).map(tuple -> calculateDeal(p, tuple.getT1(), tuple.getT2()))
                )
                .sort((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .take(dealLimit)
                .doOnComplete(() -> logger.info("Retrieved top {} deals", dealLimit));
    }

    /**
     * Retrieves top deals for a specific category, sorted by score descending.
     *
     * @param category The product category to filter by
     * @return Flux of deal responses for the category, limited to the configured deal limit
     */
    public Flux<DealResponse> getDealsByCategory(String category) {

        return productRepo.findAll()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .flatMap(p ->
                        Mono.zip(
                                priceHistoryRepository.getStatsByAsin(p.getAsin()).defaultIfEmpty(new Stats(0.0, 0.0, 0.0)),
                                priceHistoryRepository.findTop3ByAsinOrderByDateDesc(p.getAsin()).collectList()
                        ).map(tuple -> calculateDeal(p, tuple.getT1(), tuple.getT2()))
                )
                .sort((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .take(dealLimit)
                .doOnComplete(() -> logger.info("Retrieved top {} deals for category: {}", dealLimit, category));
    }

    /**
     * Retrieves deals filtered by category and subCategory, with optional exclusion.
     *
     * @param category The product category to filter by
     * @param subCategory The product subCategory to filter by (optional)
     * @param excludeAsin ASIN to exclude from results (optional)
     * @return Flux of deal responses matching the criteria, limited to the configured deal limit
     */
    public Flux<DealResponse> getDeals(String category, String subCategory, String excludeAsin) {

        return productRepo.findAll()
                .filter(p -> {

                    // ❌ REMOVE CURRENT PRODUCT
                    if (excludeAsin != null && excludeAsin.equals(p.getAsin())) {
                        return false;
                    }

                    if (subCategory != null && !subCategory.equals("general")) {
                        return subCategory.equalsIgnoreCase(p.getSubCategory());
                    }

                    return category.equalsIgnoreCase(p.getCategory());
                })
                .flatMap(p ->
                        Mono.zip(
                                priceHistoryRepository.getStatsByAsin(p.getAsin()).defaultIfEmpty(new Stats(0.0, 0.0, 0.0)),
                                priceHistoryRepository.findTop3ByAsinOrderByDateDesc(p.getAsin()).collectList()
                        ).map(tuple -> calculateDeal(p, tuple.getT1(), tuple.getT2()))
                )
                .sort((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .take(dealLimit)
                .doOnComplete(() -> logger.info("Retrieved top {} deals for category: {}, subCategory: {}, excluding ASIN: {}", dealLimit, category, subCategory, excludeAsin));
    }

    private DealResponse calculateDeal(Product p, Stats stats, List<PriceHistory> history) {

        if (history == null || history.isEmpty()) {
            logger.debug("No price history for ASIN: {}, returning default deal response", p.getAsin());
            double currentPrice = roundCurrency(p.getCurrentPrice());
            return new DealResponse(
                    p.getAsin(),
                    p.getTitle(),
                    p.getImage(),
                    currentPrice,
                    (double) 0,
                    "WAIT",
                    (double) 0,
                    "NO_DATA",
                    "Tracking started recently",
                    currentPrice,
                    currentPrice,
                    currentPrice,
                    p.getCurrency(),
                    p.getCategory(),
                    p.getSubCategory()
            );
        }

        double current = p.getCurrentPrice();
        double lowest = p.getLowestPrice();

        double discount = (p.getMrp() != null && p.getMrp() > 0)
                ? (p.getMrp() - current) / p.getMrp()
                : 0;

        double safeCurrent = current > 0 ? current : 1;
        double score = ((lowest / safeCurrent) * 7) + (discount * 3);

        score = Math.min(score, 10);

        String decision = getDecision(score);

        // 🔥 NEW: Trend + Insight
        String trend = getTrend(history);
        String insight = generateInsight(history, current, lowest);

        double roundedCurrent = roundCurrency(current);
        double roundedMin = roundCurrency(stats.getMin());
        double roundedMax = roundCurrency(stats.getMax());
        double roundedAvg = roundCurrency(stats.getAvg());

        logger.debug("Calculated deal for ASIN: {} - Score: {}, Decision: {}, Trend: {}, Insight: {}", p.getAsin(), round(score), decision, trend, insight);

        dealsGenerated.increment();

        return new DealResponse(
                p.getAsin(),
                p.getTitle(),
                p.getImage(),
                roundedCurrent,
                round(score),
                decision,
                round(discount * 100),
                trend,
                insight,
                roundedMin,
                roundedMax,
                roundedAvg,
                p.getCurrency(),
                p.getCategory(),
                p.getSubCategory()
        );
    }

    private String getTrend(List<PriceHistory> history) {

        if (history.size() < 3) return "STABLE";

        // History is ordered by date desc, so index 0 is latest
        double p1 = history.get(0).getPrice(); // latest
        double p2 = history.get(1).getPrice();
        double p3 = history.get(2).getPrice();

        if (p1 < p2 && p2 < p3) return "FALLING";
        if (p1 > p2 && p2 > p3) return "RISING";

        return "STABLE";
    }

    private String generateInsight(List<PriceHistory> history,
                                   double current,
                                   double lowest) {

        if (history.isEmpty()) return "No data yet";

        double diff = current - lowest;

        if (diff <= 0) {
            return "Lowest price in recent period";
        }

        if (diff < 100) {
            return "Very close to lowest price";
        }

        return "₹" + (int) diff + " above lowest price";
    }
    private String getDecision(double score) {

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return "NO DATA";
        }

        if (score >= 8) {
            return "BUY NOW";
        } else if (score >= 6) {
            return "GOOD DEAL";
        } else if (score >= 4) {
            return "WAIT";
        } else {
            return "OVERPRICED";
        }
    }


    private double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private double roundCurrency(Double val) {
        if (val == null || Double.isNaN(val) || Double.isInfinite(val)) {
            return 0;
        }
        return Math.round(val * 100.0) / 100.0;
    }
}