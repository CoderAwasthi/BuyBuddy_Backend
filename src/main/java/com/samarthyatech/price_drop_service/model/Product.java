package com.samarthyatech.price_drop_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed
    private String asin;

    @Indexed
    private String userId;
    private String title;
    private String url;
    private String image;

    private Double currentPrice;
    private String currency;
    private Double lowestPrice;
    private Double mrp;

    private String rating;
    private String reviews;
    @Indexed
    private String category;
    @Indexed
    private String subCategory;
    private List<String> breadcrumb;
    private Double lastNotifiedPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}