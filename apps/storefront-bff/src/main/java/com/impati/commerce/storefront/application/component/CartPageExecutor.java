package com.impati.commerce.storefront.application.component;

import com.impati.commerce.common.ApiContracts.*;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.storefront.application.model.CartPage;
import com.impati.commerce.storefront.application.port.in.CartPageUseCase;
import com.impati.commerce.storefront.application.port.out.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CartPageExecutor implements CartPageUseCase {
    private final CartClient cartClient;
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final OrderClient orderClient;
    private final ExecutorService queries;

    public CartPageExecutor(CartClient cartClient, CatalogClient catalogClient, InventoryClient inventoryClient,
            OrderClient orderClient, ExecutorService queries) {
        this.cartClient = cartClient; this.catalogClient = catalogClient;
        this.inventoryClient = inventoryClient; this.orderClient = orderClient; this.queries = queries;
    }

    @Override
    public CartPage get(String memberId) {
        var cart = cartClient.get(memberId); // Cart 실패는 빈 상태로 바꾸지 않는다.
        if (!memberId.equals(cart.memberId())) throw DomainException.downstreamError("cart identity mismatch");
        if (cart.lines().isEmpty()) return new CartPage(memberId, cart.version(), List.of(), null, List.of(), false);
        var stocks = cart.lines().stream().map(line -> CompletableFuture.supplyAsync(
                () -> available(() -> inventoryClient.get(line.skuId())), queries)).toList();
        var quote = CompletableFuture.supplyAsync(() -> available(() -> orderClient.quote(memberId, cart.version())), queries);
        var descriptions = cart.lines().stream().map(line -> CompletableFuture.supplyAsync(() -> available(() -> {
            var sku = catalogClient.sku(line.skuId());
            var product = catalogClient.product(sku.productId());
            return new Description(product.name(), sku.name());
        }), queries)).toList();
        var stockResult = stocks.stream().map(CompletableFuture::join).filter(java.util.Objects::nonNull).toList();
        var quoteResult = quote.join();
        // Order가 조회한 버전과 줄이 다른 응답은 부분 합계로 사용하지 않는다.
        if (quoteResult != null && (quoteResult.cartVersion() != cart.version() || !sameLines(cart, quoteResult))) quoteResult = null;
        Map<String, Integer> quantities = stockResult.stream()
                .collect(Collectors.toMap(StockResponse::skuId, StockResponse::available));
        var failures = new ArrayList<String>();
        var lines = new ArrayList<CartPage.Line>();
        for (int i = 0; i < cart.lines().size(); i++) {
            var line = cart.lines().get(i);
            var description = descriptions.get(i).join();
            var quantity = quantities.get(line.skuId());
            if (description == null) failures.add("product:" + line.skuId());
            if (quantity == null) failures.add("inventory:" + line.skuId());
            lines.add(new CartPage.Line(line.skuId(), line.quantity(), description == null ? null : description.productName(),
                    description == null ? null : description.skuName(), quantity, description != null));
        }
        if (quoteResult == null) failures.add("quote");
        var converted = quoteResult == null ? null : new CartPage.Quote(quoteResult.id(), quoteResult.cartVersion(),
                quoteResult.lines().stream().map(line -> new CartPage.PriceLine(line.skuId(), line.quantity(), line.unitPrice(), line.lineTotal())).toList(), quoteResult.total());
        boolean canCheckout = converted != null && lines.stream().allMatch(line -> line.availableQuantity() != null && line.availableQuantity() >= line.quantity());
        return new CartPage(memberId, cart.version(), List.copyOf(lines), converted, List.copyOf(failures), canCheckout);
    }

    private boolean sameLines(CartResponse cart, PurchaseQuoteResponse quote) {
        return cart.lines().size() == quote.lines().size() && cart.lines().stream().allMatch(line ->
                quote.lines().stream().anyMatch(price -> line.skuId().equals(price.skuId()) && line.quantity() == price.quantity()));
    }

    private <T> T available(Supplier<T> call) {
        try { return call.get(); }
        catch (DomainException failure) { return null; }
    }
    private record Description(String productName, String skuName) { }
}
