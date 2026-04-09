package com.samarthyatech.price_drop_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    private String userId;
    private String asin;
    private String title;

    private Double oldPrice;
    private Double newPrice;

    private boolean sent; // important for polling

    private LocalDateTime createdAt;
}