package com.samarthyatech.price_drop_service.repo;

import com.samarthyatech.price_drop_service.model.ClickEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ClickEventRepo extends ReactiveMongoRepository<ClickEvent, String> {
}
