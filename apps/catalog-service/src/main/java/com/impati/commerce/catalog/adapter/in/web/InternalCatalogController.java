package com.impati.commerce.catalog.adapter.in.web;

import com.impati.commerce.catalog.application.CatalogService;
import com.impati.commerce.common.ApiContracts.SkuResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게이트웨이가 노출하지 않는 경로. 형제 서비스만 부른다 (ADR-0003).
 *
 * <p>SKU 단건 조회는 order·cart가 가격을 확인할 때 쓴다. 사용자에게는 상품 단위로 노출되고
 * 변형은 상품 응답에 포함되므로, SKU 식별자로 직접 찌르는 경로를 밖에 둘 이유가 없다.
 */
@RestController
@RequestMapping("/internal")
public class InternalCatalogController {
    private final CatalogService catalog;

    public InternalCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/skus/{skuId}")
    SkuResponse sku(@PathVariable String skuId) {
        return catalog.getSku(skuId);
    }
}
