package com.impati.commerce.storefront.adapter.in.web;

import com.impati.commerce.common.ApiContracts.PurchaseQuoteLineResponse;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.common.ApiContracts.StorefrontCartLineResponse;
import com.impati.commerce.common.ApiContracts.StorefrontCartResponse;
import com.impati.commerce.storefront.application.model.CartPage;

final class CartPageResponseMapper {
    private CartPageResponseMapper() {
    }

    static StorefrontCartResponse from(CartPage page) {
        var lines = page.lines().stream().map(CartPageResponseMapper::line).toList();
        return new StorefrontCartResponse(
                page.memberId(), page.version(), lines, quote(page.quote()), page.unavailable(), page.checkoutAllowed());
    }

    private static StorefrontCartLineResponse line(CartPage.Line line) {
        return new StorefrontCartLineResponse(
                line.skuId(), line.quantity(), line.productName(), line.skuName(),
                line.availableQuantity(), line.informationAvailable());
    }

    private static PurchaseQuoteResponse quote(CartPage.Quote quote) {
        if (quote == null) {
            return null;
        }
        var lines = quote.lines().stream().map(CartPageResponseMapper::priceLine).toList();
        return new PurchaseQuoteResponse(quote.id(), quote.cartVersion(), lines, quote.total());
    }

    private static PurchaseQuoteLineResponse priceLine(CartPage.PriceLine line) {
        return new PurchaseQuoteLineResponse(
                line.skuId(), line.quantity(), line.unitPrice(), line.lineTotal());
    }
}
