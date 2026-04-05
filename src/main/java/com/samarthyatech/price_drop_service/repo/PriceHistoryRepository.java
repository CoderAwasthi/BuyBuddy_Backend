package com.samarthyatech.price_drop_service.repo;

import com.samarthyatech.price_drop_service.model.PriceHistory;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface PriceHistoryRepository extends ReactiveMongoRepository<PriceHistory, String> {
    Flux<PriceHistory> findByAsinOrderByDateAsc(String asin);
}