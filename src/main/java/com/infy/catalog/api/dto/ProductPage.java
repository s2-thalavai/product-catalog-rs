package com.infy.catalog.api.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of products.
 * JSON: {"items":[...],"page":0,"size":20,"totalElements":25,"totalPages":2,"hasNext":true,"hasPrevious":false}
 * XML:  &lt;productPage&gt;&lt;items&gt;&lt;product&gt;..&lt;/product&gt;&lt;/items&gt;&lt;page&gt;0&lt;/page&gt;...&lt;/productPage&gt;
 * Navigation links are also sent in the HTTP Link header (first, prev, next, last).
 */
@XmlRootElement(name = "productPage")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "productPage", propOrder = {
        "items", "page", "size", "totalElements", "totalPages", "hasNext", "hasPrevious"})
public class ProductPage {

    @XmlElementWrapper(name = "items")
    @XmlElement(name = "product")
    private List<ProductResponse> items = new ArrayList<>();

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public ProductPage() {
        // for JAXB and Jackson
    }

    public ProductPage(List<ProductResponse> items, int page, int size, long totalElements,
                       int totalPages, boolean hasNext, boolean hasPrevious) {
        this.items = new ArrayList<>(items);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }

    public List<ProductResponse> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean hasNext() { return hasNext; }
    public boolean hasPrevious() { return hasPrevious; }
}
