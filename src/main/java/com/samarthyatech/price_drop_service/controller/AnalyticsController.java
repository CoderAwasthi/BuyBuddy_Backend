package com.samarthyatech.price_drop_service.controller;

import com.samarthyatech.price_drop_service.model.ClickEvent;
import com.samarthyatech.price_drop_service.model.DealResponse;
import com.samarthyatech.price_drop_service.model.TopProductResponse;
import com.samarthyatech.price_drop_service.repo.ClickEventRepo;
import com.samarthyatech.price_drop_service.service.DealService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ClickEventRepo repo;

    @GetMapping("/analytics/clicks")
    public Mono<Long> totalClicks() {
        return repo.count();
    }

        @GetMapping("/analytics/top-products")
    public Flux<TopProductResponse> topProducts() {
        return repo.findAll()
                .groupBy(ClickEvent::getAsin)
                .flatMap(group ->
                        group.count()
                                .map(count -> new TopProductResponse(group.key(), count))
                )
                .sort((a, b) -> Long.compare(b.getClicks(), a.getClicks()))
                .take(10);
    }

    @GetMapping("/analytics/daily")
    public Flux<Map<String, Object>> dailyClicks() {

        return repo.findAll()
                .groupBy(e -> e.getCreatedAt().toLocalDate())
                .flatMap(group ->
                        group.count()
                                .map(count -> Map.of(
                                        "date", group.key().toString(),
                                        "clicks", count
                                ))
                );
    }

}
