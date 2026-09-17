package com.impati.commerce.storefront.adapter.in.web;

import com.impati.commerce.common.ApiContracts.PurchaseQuoteLineResponse;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.common.ApiContracts.StorefrontCartLineResponse;
import com.impati.commerce.common.ApiContracts.StorefrontCartResponse;
import com.impati.commerce.storefront.application.model.CartPage;

final class CartPageResponseMapper {
    private CartPageResponseMapper() { }
    static StorefrontCartResponse from(CartPage page) {
        var quote = page.quote();
        return new StorefrontCartResponse(page.memberId(), page.version(), page.lines().stream().map(line ->
                new StorefrontCartLineResponse(line.skuId(), line.quantity(), line.productName(), line.skuName(), line.availableQuantity(), line.informationAvailable())).toList(),
                quote == null ? null : new PurchaseQuoteResponse(quote.id(), quote.cartVersion(), quote.lines().stream().map(line ->
                        new PurchaseQuoteLineResponse(line.skuId(), line.quantity(), line.unitPrice(), line.lineTotal())).toList(), quote.total()),
                page.unavailable(), page.checkoutAllowed());
    }
}
