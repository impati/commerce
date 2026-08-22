package com.impati.commerce.display.adapter.in.web;

import com.impati.commerce.common.ApiContracts.DisplayHomeResponse;
import com.impati.commerce.common.ApiContracts.DisplaySection;
import com.impati.commerce.common.ApiContracts.ProductCard;
import com.impati.commerce.display.application.port.in.HomePage;
import com.impati.commerce.display.application.port.in.HomeProductCard;
import com.impati.commerce.display.application.port.in.HomeSection;

/**
 * 유스케이스 결과를 서비스 간 HTTP 계약으로 옮긴다.
 *
 * <p>계약이 <b>서비스 간</b>의 것이므로 어댑터가 안다. 응용 계층이 알면 인바운드 어댑터가
 * 늘어날 때마다 응용이 바뀐다.
 */
final class DisplayResponseMapper {
    private DisplayResponseMapper() {
    }

    static DisplayHomeResponse from(HomePage home) {
        return new DisplayHomeResponse(
                home.title(),
                home.subtitle(),
                home.sections().stream().map(DisplayResponseMapper::from).toList()
        );
    }

    private static DisplaySection from(HomeSection section) {
        return new DisplaySection(
                section.key(),
                section.title(),
                section.products().stream().map(DisplayResponseMapper::from).toList()
        );
    }

    private static ProductCard from(HomeProductCard card) {
        return new ProductCard(
                card.productId(),
                card.name(),
                card.brand(),
                card.category(),
                card.price(),
                card.tags()
        );
    }
}
