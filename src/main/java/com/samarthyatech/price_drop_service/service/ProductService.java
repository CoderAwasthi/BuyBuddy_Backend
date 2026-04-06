package com.samarthyatech.price_drop_service.service;

import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Product;
import com.samarthyatech.price_drop_service.repo.PriceHistoryRepository;
import com.samarthyatech.price_drop_service.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepo;
    private final PriceHistoryRepository historyRepo;

    public Mono<Void> trackProduct(Product product) {

//        return productRepo.findByAsin(product.getAsin())
//                .flatMap(existing -> updateProduct(existing, product.getCurrentPrice()))
//                .switchIfEmpty(createNew(product))
//                .then();
        return productRepo.findByAsinAndUserId(product.getAsin(), product.getUserId())
                .flatMap(existing -> updateProduct(existing, product))
                .switchIfEmpty(createNew(product))
                .then();
    }

    private Mono<Product> updateProduct(Product existing, Product incoming) {

//        if (newPrice < existing.getLowestPrice()) {
//            existing.setLowestPrice(newPrice);
//        }
//
//        existing.setCurrentPrice(newPrice);
//
//        return productRepo.save(existing)
//                .then(saveHistory(existing.getAsin(), newPrice))
//                .thenReturn(existing);
        Double newPrice = incoming.getCurrentPrice();

        if (newPrice < existing.getLowestPrice()) {
            existing.setLowestPrice(newPrice);
        }

        existing.setCurrentPrice(newPrice);
        existing.setMrp(incoming.getMrp());
        existing.setRating(incoming.getRating());
        existing.setReviews(incoming.getReviews());
        existing.setImage(incoming.getImage());

        return productRepo.save(existing)
                .then(saveHistory(existing.getAsin(), newPrice))
                .thenReturn(existing);
    }

    private Mono<Product> createNew(Product product) {

        product.setLowestPrice(product.getCurrentPrice());
        product.setCreatedAt(LocalDateTime.now());

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