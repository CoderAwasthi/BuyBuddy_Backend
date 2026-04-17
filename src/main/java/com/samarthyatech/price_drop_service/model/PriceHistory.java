package com.samarthyatech.price_drop_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "price_history")
@CompoundIndex(def = "{'asin': 1, 'date': -1}")
public class PriceHistory {

    @Id
    private String id;

    @Indexed
    private String asin;
    private Double price;
    @Indexed
    private LocalDateTime date;
}
