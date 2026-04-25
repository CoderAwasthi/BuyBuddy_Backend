package com.samarthyatech.price_drop_service.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Source context where a product was discovered in Amazon UI.
 */
public enum ProductSource {
    MAIN_VIEWED_PRODUCT,
    RELATED_PRODUCT,
    RELATED_PRODUCTS,
    CUSTOMERS_ALSO_VIEWED,
    FEATURED_PRODUCTS,
    EXPLORE_MORE_ITEMS;

    @JsonCreator
    public static ProductSource fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return ProductSource.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null; // unknown values are silently ignored
        }
    }
}
