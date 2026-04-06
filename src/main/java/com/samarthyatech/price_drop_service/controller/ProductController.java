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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;
    private final DealService dealService;

    @PostMapping("/track")
    public Mono<Void> track(@RequestBody Product product) {
        return service.trackProduct(product);
    }

    @GetMapping("/history")
    public Flux<PriceHistory> history(@RequestParam String asin) {
        return service.getHistory(asin);
    }

    @GetMapping("/allhistory")
    public Flux<PriceHistory> allhistory() {
        return service.getAllHistory();
    }

    @GetMapping("/deals")
    public Flux<DealResponse> deals() {
        return dealService.getTopDeals();
    }
}