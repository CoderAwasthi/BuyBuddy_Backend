package com.samarthyatech.price_drop_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    private String asin;
    private String title;
    private String url;

    private Double currentPrice;
    private Double lowestPrice;

    private LocalDateTime createdAt;
}