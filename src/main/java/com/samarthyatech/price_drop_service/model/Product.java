package com.samarthyatech.price_drop_service.model;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    /** Accepts both "currentPrice" and "price" from extension payloads. */
    @JsonAlias("price")
    private Double currentPrice;

    private String currency;
    private Double lowestPrice;
    private Double mrp;

    private String rating;
    private String reviews;

    /** Amazon domain e.g. www.amazon.in */
    private String domain;

    /** Availability string e.g. IN_STOCK, OUT_OF_STOCK */
    private String availability;

    /** Convenience flag derived from availability */
    private Boolean outOfStock;

    @Indexed
    private String category;
    @Indexed
    private String subCategory;

    /**
     * High-level discovery source of this product.
     * Allowed values: MAIN_VIEWED_PRODUCT, RELATED_PRODUCT, RELATED_PRODUCTS,
     * CUSTOMERS_ALSO_VIEWED, FEATURED_PRODUCTS, EXPLORE_MORE_ITEMS.
     */
    @Indexed
    private ProductSource source;

    /**
     * Granular UI section detail where product appeared
     * e.g. "explore_more_items", "sponsored", "frequently_bought_together".
     */
    private String sourceDetail;

    /**
     * ASIN of the main product that was being viewed when this related product was discovered.
     */
    @Indexed
    private String relatedMainAsin;

    /**
     * Client-side epoch timestamp (ms) when this product was captured by the extension.
     */
    private Long timestamp;

    private List<String> breadcrumb;
    private Double lastNotifiedPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}