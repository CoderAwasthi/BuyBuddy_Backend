package com.samarthyatech.price_drop_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Stats {
    private Double min;
    private Double max;
    private Double avg;
}
