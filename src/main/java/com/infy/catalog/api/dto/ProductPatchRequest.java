package com.infy.catalog.api.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

/**
 * Body of PATCH /products/{id}: only the fields to change (JSON Merge Patch style).
 * Omitted fields stay as they are; "" clears description or category.
 */
@XmlRootElement(name = "product")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "productPatch", propOrder = {"sku", "name", "description", "category", "price", "stock"})
public class ProductPatchRequest {

    private String sku;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private Integer stock;

    public ProductPatchRequest() {
        // for JAXB and Jackson
    }

    public ProductPatchRequest(String sku, String name, String description, String category,
                               BigDecimal price, Integer stock) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public Integer getStock() { return stock; }
}
