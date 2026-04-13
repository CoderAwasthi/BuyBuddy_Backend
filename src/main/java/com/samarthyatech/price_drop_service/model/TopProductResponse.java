package com.samarthyatech.price_drop_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopProductResponse {

    private String asin;
    private long clicks;
}