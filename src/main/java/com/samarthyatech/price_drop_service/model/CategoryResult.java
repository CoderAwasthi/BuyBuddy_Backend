package com.samarthyatech.price_drop_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CategoryResult {
    private String category;
    private String subCategory;
}
