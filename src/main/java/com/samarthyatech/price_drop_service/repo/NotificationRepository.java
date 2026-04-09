package com.samarthyatech.price_drop_service.repo;

import com.samarthyatech.price_drop_service.model.Notification;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface NotificationRepository
        extends ReactiveMongoRepository<Notification, String> {

    Flux<Notification> findByUserIdAndSentFalse(String userId);
}
