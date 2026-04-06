package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.DealResponse;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class DealService {

    private final ProductRepository productRepo;

    public Flux<DealResponse> getTopDeals() {

        return productRepo.findAll()
                .filter(p -> p.getCurrentPrice() != null)
                .map(this::calculateDeal)
                .sort((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .take(10);
    }

    private DealResponse calculateDeal(Product p) {

        if (p.getCurrentPrice() == null || p.getLowestPrice() == null) {
            return new DealResponse(
                    p.getAsin(),
                    p.getTitle(),
                    p.getImage(),
                    0.0,
                    0.0,
                    "NO DATA",
                    0.0
            );
        }

        double current = p.getCurrentPrice();
        double lowest = p.getLowestPrice();

        double discount = (p.getMrp() != null && p.getMrp() > 0)
                ? (p.getMrp() - current) / p.getMrp()
                : 0;

        double score =
                ((lowest / current) * 7) +
                        (discount * 3);

        score = Math.min(score, 10);

        String decision;

        if (score >= 8) decision = "BUY NOW";
        else if (score >= 6) decision = "GOOD DEAL";
        else if (score >= 4) decision = "WAIT";
        else decision = "OVERPRICED";

        return new DealResponse(
                p.getAsin(),
                p.getTitle(),
                p.getImage(),
                current,
                round(score),
                decision,
                round(discount * 100)
        );
    }

    private double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}