package com.samarthyatech.price_drop_service.repo;

import com.samarthyatech.price_drop_service.model.ClickEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

public interface ClickEventRepo extends ReactiveMongoRepository<ClickEvent, String> {

    Flux<ClickEvent> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    Flux<ClickEvent> findByCreatedAtAfter(LocalDateTime from);

    Flux<ClickEvent> findByCreatedAtBefore(LocalDateTime to);
}
