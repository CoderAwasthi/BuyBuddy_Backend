package com.samarthyatech.price_drop_service.repo;

import com.samarthyatech.price_drop_service.model.AnalyticsEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface AnalyticsEventRepository extends ReactiveMongoRepository<AnalyticsEvent, String> {
}

