package com.samarthyatech.price_drop_service.repo;

import com.samarthyatech.price_drop_service.model.PriceHistory;
import com.samarthyatech.price_drop_service.model.Stats;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PriceHistoryRepository extends ReactiveMongoRepository<PriceHistory, String> {
    Flux<PriceHistory> findByAsinOrderByDateAsc(String asin);

    @Aggregation(pipeline = {
        "{ $match: { asin: ?0 } }",
        "{ $group: { _id: null, min: { $min: '$price' }, max: { $max: '$price' }, avg: { $avg: '$price' } } }",
        "{ $project: { _id: 0, min: 1, max: 1, avg: 1 } }"
    })
    Mono<Stats> getStatsByAsin(String asin);

    Flux<PriceHistory> findTop3ByAsinOrderByDateDesc(String asin);
}