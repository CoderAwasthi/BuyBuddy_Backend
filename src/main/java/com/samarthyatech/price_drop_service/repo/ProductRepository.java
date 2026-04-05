package com.samarthyatech.price_drop_service.repo;

import com.samarthyatech.price_drop_service.model.Product;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ProductRepository extends ReactiveMongoRepository<Product, String> {
    Mono<Product> findByAsin(String asin);
}