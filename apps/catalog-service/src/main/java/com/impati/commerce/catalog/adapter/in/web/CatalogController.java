package com.impati.commerce.catalog.adapter.in.web;

import com.impati.commerce.catalog.application.CatalogService;
import com.impati.commerce.common.ApiContracts.ProductResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    List<ProductResponse> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String query
    ) {
        return catalog.list(category, query);
    }

    @GetMapping("/products/{productId}")
    ProductResponse product(@PathVariable String productId) {
        return catalog.getProduct(productId);
    }
}

