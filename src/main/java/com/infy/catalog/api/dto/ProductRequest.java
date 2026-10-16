package com.infy.catalog.api.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

/**
 * Body of POST /products and PUT /products/{id} (all writable fields).
 * JSON: {"sku":"..","name":"..","price":10.00,"stock":1}
 * XML:  &lt;product&gt;&lt;sku&gt;..&lt;/sku&gt;...&lt;/product&gt;
 * The id is never read from the body.
 */
@XmlRootElement(name = "product")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "productRequest", propOrder = {"sku", "name", "description", "category", "price", "stock"})
public class ProductRequest {

    private String sku;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private Integer stock;

    public ProductRequest() {
        // for JAXB and Jackson
    }

    public ProductRequest(String sku, String name, String description, String category,
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
