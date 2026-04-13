package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.Notification;
import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.repo.NotificationRepository;
import com.samarthyatech.price_drop_service.repo.PriceHistoryRepository;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import com.samarthyatech.price_drop_service.scraper.PriceScraper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PriceDropService {

    private static final Logger logger = LoggerFactory.getLogger(PriceDropService.class);

    private final ProductRepository productRepo;
    private final PriceHistoryRepository historyRepo;
    private final NotificationRepository notificationRepo;
    private final WebClient webClient;
    private final PriceScraper scraper;

    public Mono<Void> processProduct(Product product) {

        return webClient.get()
                .uri(product.getUrl())
                .retrieve()
                .bodyToMono(String.class)
                .map(scraper::extractPrice)
                .flatMap(newPrice -> {

                    Double oldPrice = product.getCurrentPrice();

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

                    return productRepo.save(product)
                            .then(saveHistory(product.getAsin(), newPrice))
                            .then(notifyMono);
                });
    }

    private boolean shouldNotify(Product p, Double newPrice) {

        if (p.getLastNotifiedPrice() == null) return true;

        // Avoid spam: notify only if drop > ₹50
        return (p.getLastNotifiedPrice() - newPrice) >= 50;
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

        return notificationRepo.save(n);
    }

    private Mono<Void> saveHistory(String asin, Double price) {

        PriceHistory h = new PriceHistory();
        h.setAsin(asin);
        h.setPrice(price);
        h.setDate(LocalDateTime.now());

        return historyRepo.save(h).then();
    }
}
