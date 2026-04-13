package com.samarthyatech.price_drop_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "click_events")
public class ClickEvent {

    @Id
    private String id;

    private String asin;
    private String userId;
    private String domain;

    private String source; // "extension"
    private String device; // optional

    private LocalDateTime createdAt;
}