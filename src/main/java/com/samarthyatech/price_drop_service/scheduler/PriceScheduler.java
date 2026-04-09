package com.samarthyatech.price_drop_service.scheduler;

import com.samarthyatech.price_drop_service.repo.ProductRepository;
import com.samarthyatech.price_drop_service.scraper.PriceScraper;
import com.samarthyatech.price_drop_service.service.PriceDropService;
import com.samarthyatech.price_drop_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class PriceScheduler {

    private final ProductRepository productRepo;
    private final ProductService service;
    private final WebClient webClient;
    private final PriceScraper scraper;
    private final PriceDropService priceDropService;

    @Scheduled(fixedRateString = "${app.scheduler.interval}")
    public void updatePrices() {

        productRepo.findAll()
                .flatMap(product ->
                        webClient.get()
                                .uri(product.getUrl())
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(scraper::extractPrice)
                                .flatMap(price -> {
                                    product.setCurrentPrice(price);
                                    return service.trackProduct(product);
                                })
                )
                .subscribe();
    }

    @Scheduled(fixedRateString = "${app.scheduler.interval}")
    public void runScheduler() {

        productRepo.findAll()
                .flatMap(priceDropService::processProduct)
                .onErrorContinue((e, obj) -> System.out.println("Error processing: " + obj))
                .subscribe();
    }
}