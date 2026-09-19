package com.impati.commerce.order.adapter.in.web;

import com.impati.commerce.common.ApiContracts.PurchaseQuoteLineResponse;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.common.ApiContracts.PriceBreakdownResponse;
import com.impati.commerce.order.application.model.PurchaseQuoteDetails;

final class PurchaseQuoteResponseMapper {
    private PurchaseQuoteResponseMapper() {
    }

    static PurchaseQuoteResponse from(PurchaseQuoteDetails quote) {
        var lines = quote.lines().stream().map(PurchaseQuoteResponseMapper::line).toList();
        var priceBreakdown = quote.priceBreakdown();
        return new PurchaseQuoteResponse(quote.id(), quote.cartVersion(), lines, quote.total(),
                new PriceBreakdownResponse(priceBreakdown.productAmount(), priceBreakdown.shippingFee(),
                        priceBreakdown.totalAmount()));
    }

    private static PurchaseQuoteLineResponse line(PurchaseQuoteDetails.Line line) {
        return new PurchaseQuoteLineResponse(
                line.skuId(), line.quantity(), line.unitPrice(), line.lineTotal());
    }
}
