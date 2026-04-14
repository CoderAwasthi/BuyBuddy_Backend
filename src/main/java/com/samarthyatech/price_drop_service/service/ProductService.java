package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.CategoryResult;
import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.repo.PriceHistoryRepository;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import com.samarthyatech.price_drop_service.scraper.PriceScraper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepo;
    private final PriceHistoryRepository historyRepo;
    private final CategoryService categoryService;

    public Mono<Void> trackProduct(Product product) {
        return productRepo.findByAsinAndUserId(product.getAsin(), product.getUserId())
                .flatMap(existing -> updateProduct(existing, product))
                .switchIfEmpty(createNew(product))
                .then();
    }

    private Mono<Product> updateProduct(Product existing, Product incoming) {
        Double newPrice = incoming.getCurrentPrice();

        if (newPrice < existing.getLowestPrice()) {
            existing.setLowestPrice(newPrice);
        }

        CategoryResult result = categoryService.detect(incoming.getTitle(),incoming.getBreadcrumb());
        existing.setCurrentPrice(newPrice);
        existing.setCurrency(incoming.getCurrency());
        existing.setMrp(incoming.getMrp());
        existing.setRating(incoming.getRating());
        existing.setReviews(incoming.getReviews());
        existing.setImage(incoming.getImage());
        existing.setCategory(result.getCategory());
        existing.setSubCategory(result.getSubCategory());

        return productRepo.save(existing)
                .then(saveHistory(existing.getAsin(), newPrice))
                .thenReturn(existing);
    }

    private Mono<Product> createNew(Product product) {

        product.setLowestPrice(product.getCurrentPrice());
        product.setCreatedAt(LocalDateTime.now());
        CategoryResult result = categoryService.detect(product.getTitle(),product.getBreadcrumb());

        product.setCategory(result.getCategory());
        product.setSubCategory(result.getSubCategory());

        return productRepo.save(product)
                .then(saveHistory(product.getAsin(), product.getCurrentPrice()))
                .thenReturn(product);
    }

    private Mono<Void> saveHistory(String asin, Double price) {

        PriceHistory history = new PriceHistory();
        history.setAsin(asin);
        history.setPrice(price);
        history.setDate(LocalDateTime.now());

        return historyRepo.save(history).then();
    }

    public Flux<PriceHistory> getHistory(String asin) {
        return historyRepo.findByAsinOrderByDateAsc(asin);
    }
    public Flux<PriceHistory> getAllHistory() {
        return historyRepo.findAll();
    }

}