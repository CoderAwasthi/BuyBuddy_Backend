package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.DealResponse;
import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.repo.PriceHistoryRepository;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DealService {

    private final ProductRepository productRepo;
    private final PriceHistoryRepository priceHistoryRepository;

    public Mono<DealResponse> analyze(String asin) {

        return productRepo.findByAsin(asin)
                .flatMap(product ->
                        priceHistoryRepository.findByAsinOrderByDateAsc(asin)
                                .collectList()
                                .map(history -> calculateDeal(product, history))
                );
    }

    public Flux<DealResponse> getTopDeals() {

        return productRepo.findAll()
                .flatMap(p ->
                        priceHistoryRepository.findByAsinOrderByDateAsc(p.getAsin())
                                .collectList()
                                .map(history -> calculateDeal(p, history))
                )
                .sort((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .take(10);
    }

    public Flux<DealResponse> getDealsByCategory(String category) {

        return productRepo.findAll()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .flatMap(p ->
                        priceHistoryRepository.findByAsinOrderByDateAsc(p.getAsin())
                                .collectList()
                                .map(history -> calculateDeal(p, history))
                )
                .sort((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .take(10);
    }

    public Flux<DealResponse> getDeals(String category, String subCategory) {

        return productRepo.findAll()
                .filter(p -> {

                    if (subCategory != null && !subCategory.equals("general")) {
                        return subCategory.equalsIgnoreCase(p.getSubCategory());
                    }

                    return category.equalsIgnoreCase(p.getCategory());
                })
                .flatMap(p ->
                        priceHistoryRepository.findByAsinOrderByDateAsc(p.getAsin())
                                .collectList()
                                .map(h -> calculateDeal(p, h))
                )
                .sort((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .take(10);
    }

    private DealResponse calculateDeal(Product p, List<PriceHistory> history) {

        double current = p.getCurrentPrice();
        double lowest = p.getLowestPrice();

        double discount = (p.getMrp() != null && p.getMrp() > 0)
                ? (p.getMrp() - current) / p.getMrp()
                : 0;

        double score =
                ((lowest / current) * 7) +
                        (discount * 3);

        score = Math.min(score, 10);

        String decision = getDecision(score);

        // 🔥 NEW: Trend + Insight
        String trend = getTrend(history);
        String insight = generateInsight(history, current, lowest);
        Map<String, Double> stats = calculateStats(history);

        return new DealResponse(
                p.getAsin(),
                p.getTitle(),
                p.getImage(),
                current,
                round(score),
                decision,
                round(discount * 100),
                trend,
                insight,
                stats.get("min"),
                stats.get("max"),
                stats.get("avg")
        );
    }

    private String getTrend(List<PriceHistory> history) {

        if (history.size() < 3) return "STABLE";

        int n = history.size();

        double p1 = history.get(n - 1).getPrice(); // latest
        double p2 = history.get(n - 2).getPrice();
        double p3 = history.get(n - 3).getPrice();

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

    private Map<String, Double> calculateStats(List<PriceHistory> history) {

        double min = history.stream().mapToDouble(PriceHistory::getPrice).min().orElse(0);
        double max = history.stream().mapToDouble(PriceHistory::getPrice).max().orElse(0);
        double avg = history.stream().mapToDouble(PriceHistory::getPrice).average().orElse(0);

        return Map.of(
                "min", min,
                "max", max,
                "avg", avg
        );
    }

    private double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}