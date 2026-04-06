package com.samarthyatech.price_drop_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DealResponse {

    private String asin;
    private String title;
    private String image;
    private Double price;
    private Double score;
    private String decision;
    private Double discount;
}