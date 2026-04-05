package com.samarthyatech.price_drop_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "price_history")
public class PriceHistory {

    @Id
    private String id;

    private String asin;
    private Double price;
    private LocalDateTime date;
}
