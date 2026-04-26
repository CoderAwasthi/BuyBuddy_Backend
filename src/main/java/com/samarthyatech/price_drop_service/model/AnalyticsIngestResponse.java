package com.samarthyatech.price_drop_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnalyticsIngestResponse {

    private boolean ok;
    private int received;
    private int processed;
    private String message;
}

