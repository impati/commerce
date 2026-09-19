package com.impati.commerce.storefront.application.component;

import com.impati.commerce.common.ApiContracts.CartLineResponse;
import com.impati.commerce.common.ApiContracts.CartResponse;
import com.impati.commerce.common.ApiContracts.PurchaseQuoteResponse;
import com.impati.commerce.common.ApiContracts.StockResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.storefront.application.model.CartPage;
import com.impati.commerce.storefront.application.port.in.CartPageUseCase;
import com.impati.commerce.storefront.application.port.out.CartClient;
import com.impati.commerce.storefront.application.port.out.CatalogClient;
import com.impati.commerce.storefront.application.port.out.InventoryClient;
import com.impati.commerce.storefront.application.port.out.OrderClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CartPageExecutor implements CartPageUseCase {

    private static final Logger log = LoggerFactory.getLogger(CartPageExecutor.class);
    private static final String PRODUCT_AREA = "product";
    private static final String INVENTORY_AREA = "inventory";
    private static final String QUOTE_AREA = "quote";

    private final CartClient cartClient;
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final OrderClient orderClient;
    private final ExecutorService queryExecutor;
    private final Duration cartPageTimeout;

    public CartPageExecutor(
            CartClient cartClient,
            CatalogClient catalogClient,
            InventoryClient inventoryClient,
            OrderClient orderClient,
            ExecutorService queryExecutor,
            @Value("${storefront.cart-page.timeout:PT5S}") Duration cartPageTimeout
    ) {
        this.cartClient = cartClient;
        this.catalogClient = catalogClient;
        this.inventoryClient = inventoryClient;
        this.orderClient = orderClient;
        this.queryExecutor = queryExecutor;
        this.cartPageTimeout = cartPageTimeout;
    }

    @Override
    public CartPage get(String memberId) {
        var deadline = new QueryDeadline(cartPageTimeout);
        var cart = awaitCart(memberId, deadline);
        if (cart.lines().isEmpty()) {
            return new CartPage(memberId, cart.version(), List.of(), null, List.of(), false);
        }

        var lineQueries = startLineQueries(cart, deadline);
        var quoteQuery = submitQuery(QUOTE_AREA, () -> orderClient.quote(memberId, cart.version()), deadline);
        try {
            return assemblePage(cart, lineQueries, quoteQuery, deadline);
        } finally {
            quoteQuery.cancel(true);
            lineQueries.forEach(queries -> {
                queries.description().cancel(true);
                queries.stock().cancel(true);
            });
        }
    }

    private CartPage assemblePage(
            CartResponse cart,
            List<LineQueries> lineQueries,
            Future<QueryResult<PurchaseQuoteResponse>> quoteQuery,
            QueryDeadline deadline
    ) {
        var lineResults = lineQueries.stream().map(queries -> awaitLine(queries, deadline)).toList();
        var quoteResult = validateQuote(cart, awaitQuery(QUOTE_AREA, quoteQuery, deadline));

        var lines = lineResults.stream().map(this::toPageLine).toList();
        var quote = toPageQuote(quoteResult);
        var unavailable = unavailableAreas(lineResults, quoteResult);
        return new CartPage(cart.memberId(), cart.version(), lines, quote, unavailable, canCheckout(quote, lines));
    }

    private CartResponse awaitCart(String memberId, QueryDeadline deadline) {
        var query = queryExecutor.submit(() -> loadCart(memberId));
        try {
            return deadline.await(query);
        } catch (TimeoutException failure) {
            throw DomainException.unavailable("cart lookup exceeded the page deadline");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw DomainException.unavailable("cart lookup was interrupted");
        } catch (ExecutionException failure) {
            throw queryFailure(failure);
        } finally {
            query.cancel(true);
        }
    }

    private <T> QueryResult<T> awaitQuery(String area, Future<QueryResult<T>> query, QueryDeadline deadline) {
        try {
            return deadline.await(query);
        } catch (TimeoutException failure) {
            query.cancel(true);
            log.warn("cart page lookup exceeded deadline area={}", area);
            return QueryResult.failed("query_timeout");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw DomainException.unavailable("cart page lookup was interrupted");
        } catch (ExecutionException failure) {
            throw queryFailure(failure);
        }
    }

    private RuntimeException queryFailure(ExecutionException failure) {
        if (failure.getCause() instanceof RuntimeException cause) {
            return cause;
        }
        return new IllegalStateException("cart page query failed", failure.getCause());
    }

    private CartResponse loadCart(String memberId) {
        // Cart 조회 실패는 전파한다. 상품·재고·견적 조회만 부분 실패로 허용한다.
        var cart = cartClient.get(memberId);
        if (!memberId.equals(cart.memberId())) {
            throw DomainException.downstreamError("cart identity mismatch");
        }
        return cart;
    }

    private List<LineQueries> startLineQueries(CartResponse cart, QueryDeadline deadline) {
        return cart.lines().stream().map(line -> new LineQueries(
                line,
                submitQuery(PRODUCT_AREA, () -> loadDescription(line.skuId(), deadline), deadline),
                submitQuery(INVENTORY_AREA, () -> inventoryClient.get(line.skuId()), deadline)
        )).toList();
    }

    private Description loadDescription(String skuId, QueryDeadline deadline) {
        var sku = catalogClient.sku(skuId);
        if (deadline.expired()) {
            throw DomainException.unavailable("product lookup exceeded the page deadline");
        }
        var product = catalogClient.product(sku.productId());
        return new Description(product.name(), sku.name());
    }

    private LineResult awaitLine(LineQueries queries, QueryDeadline deadline) {
        var stock = awaitQuery(INVENTORY_AREA, queries.stock(), deadline);
        if (stock.isAvailable() && !queries.line().skuId().equals(stock.value().skuId())) {
            stock = QueryResult.failed("stock_identity_mismatch");
        }
        return new LineResult(queries.line(), awaitQuery(PRODUCT_AREA, queries.description(), deadline), stock);
    }

    private <T> Future<QueryResult<T>> submitQuery(String area, Supplier<T> query, QueryDeadline deadline) {
        return queryExecutor.submit(() -> {
            if (deadline.expired()) {
                return QueryResult.failed("query_timeout");
            }
            return queryAllowingFailure(area, query);
        });
    }

    private <T> QueryResult<T> queryAllowingFailure(String area, Supplier<T> query) {
        try {
            return QueryResult.succeeded(query.get());
        } catch (DomainException failure) {
            log.warn("cart page lookup failed area={} code={}", area, failure.code());
            return QueryResult.failed(failure.code());
        }
    }

    private QueryResult<PurchaseQuoteResponse> validateQuote(
            CartResponse cart,
            QueryResult<PurchaseQuoteResponse> quote
    ) {
        if (!quote.isAvailable()) {
            return quote;
        }
        if (quote.value().cartVersion() != cart.version() || !matchesCartLines(cart, quote.value())) {
            log.warn("cart page quote does not match cart version={}", cart.version());
            return QueryResult.failed("quote_mismatch");
        }
        return quote;
    }

    private boolean matchesCartLines(CartResponse cart, PurchaseQuoteResponse quote) {
        if (cart.lines().size() != quote.lines().size()) {
            return false;
        }
        var quotedQuantities = new HashMap<String, Integer>();
        for (var line : quote.lines()) {
            if (quotedQuantities.put(line.skuId(), line.quantity()) != null) {
                return false;
            }
        }
        return cart.lines().stream().allMatch(line ->
                Integer.valueOf(line.quantity()).equals(quotedQuantities.get(line.skuId())));
    }

    private CartPage.Line toPageLine(LineResult result) {
        var description = result.description().value();
        var stock = result.stock().value();
        return new CartPage.Line(
                result.line().skuId(),
                result.line().quantity(),
                description == null ? null : description.productName(),
                description == null ? null : description.skuName(),
                stock == null ? null : stock.available(),
                result.description().isAvailable()
        );
    }

    private CartPage.Quote toPageQuote(QueryResult<PurchaseQuoteResponse> result) {
        if (!result.isAvailable()) {
            return null;
        }
        var quote = result.value();
        var lines = quote.lines().stream().map(line -> new CartPage.PriceLine(
                line.skuId(), line.quantity(), line.unitPrice(), line.lineTotal())).toList();
        var priceBreakdown = quote.priceBreakdown();
        return new CartPage.Quote(quote.id(), quote.cartVersion(), lines, quote.total(),
                new CartPage.PriceBreakdown(priceBreakdown.productAmount(), priceBreakdown.shippingFee(),
                        priceBreakdown.totalAmount()));
    }

    private List<String> unavailableAreas(
            List<LineResult> lines,
            QueryResult<PurchaseQuoteResponse> quote
    ) {
        var unavailable = new ArrayList<String>();
        for (var line : lines) {
            if (!line.description().isAvailable()) {
                unavailable.add(PRODUCT_AREA + ":" + line.line().skuId());
            }
            if (!line.stock().isAvailable()) {
                unavailable.add(INVENTORY_AREA + ":" + line.line().skuId());
            }
        }
        if (!quote.isAvailable()) {
            unavailable.add(QUOTE_AREA);
        }
        return List.copyOf(unavailable);
    }

    private boolean canCheckout(CartPage.Quote quote, List<CartPage.Line> lines) {
        return quote != null && lines.stream().allMatch(this::hasSufficientStock);
    }

    private boolean hasSufficientStock(CartPage.Line line) {
        return line.availableQuantity() != null && line.availableQuantity() >= line.quantity();
    }

    private record QueryResult<T>(T value, String failureCode) {

        static <T> QueryResult<T> succeeded(T value) {
            if (value == null) {
                return failed("empty_response");
            }
            return new QueryResult<>(value, null);
        }

        static <T> QueryResult<T> failed(String code) {
            return new QueryResult<>(null, code);
        }

        boolean isAvailable() {
            return failureCode == null;
        }
    }

    private record Description(String productName, String skuName) {

    }

    private record LineQueries(
            CartLineResponse line,
            Future<QueryResult<Description>> description,
            Future<QueryResult<StockResponse>> stock
    ) {

    }

    private record LineResult(
            CartLineResponse line,
            QueryResult<Description> description,
            QueryResult<StockResponse> stock
    ) {

    }
}
