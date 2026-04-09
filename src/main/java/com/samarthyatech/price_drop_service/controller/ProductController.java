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

    private final ProductService productService;
    private final DealService dealService;

    @PostMapping("/track")
    public Mono<Void> track(@RequestBody Product product) {
        return productService.trackProduct(product);
    }

    @GetMapping("/history")
    public Flux<PriceHistory> history(@RequestParam String asin) {
        return productService.getHistory(asin);
    }

    @GetMapping("/allhistory")
    public Flux<PriceHistory> allhistory() {
        return productService.getAllHistory();
    }

//    @GetMapping("/deals")
//    public Flux<DealResponse> deals() {
//        return dealService.getTopDeals();
//    }

    @GetMapping("/deals")
    public Flux<DealResponse> deals(@RequestParam String category,@RequestParam String subCategory) {
        return dealService.getDeals(category,subCategory);
    }

    @GetMapping("/analyze")
    public Mono<DealResponse> analyze(@RequestParam String asin) {
        return dealService.analyze(asin);
    }
}