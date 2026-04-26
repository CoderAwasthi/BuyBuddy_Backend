package com.samarthyatech.price_drop_service.model;

import lombok.Data;

import java.util.List;

@Data
public class AnalyticsIngestRequest {

    private List<AnalyticsEventRequest> events;
}

