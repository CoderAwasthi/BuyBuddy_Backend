package com.samarthyatech.price_drop_service.model;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class AnalyticsEventRequest {

    private String eventId;
    private String eventName;
    private Instant timestamp;
    private Map<String, Object> payload;
}

