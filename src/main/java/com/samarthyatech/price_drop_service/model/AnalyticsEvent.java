package com.samarthyatech.price_drop_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Document(collection = "analytics_events")
public class AnalyticsEvent {

    @Id
    private String id;

    @Indexed
    private String eventId;

    @Indexed
    private String eventName;

    private Instant timestamp;
    private Map<String, Object> payload;
}

