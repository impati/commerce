package com.impati.commerce.order.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OrderModels {
    private OrderModels() {
    }

    /**
     * 주문에 일어날 수 있는 사건 (ADR-0012).
     *
     * <p><b>status를 바꾸는 전이만 여기 있다.</b> {@code attachReservation}과
     * {@code attachPayment}는 협력자 id를 적을 뿐이고, 결제 결과 불명 표시는 운영 플래그다.
     * 셋 다 saga 내부 단계이므로 사건으로 노출하면 그 실행 순서가 밖에서 관측 가능한 계약이
     * 되고, 순서를 바꾸는 리팩터링이 소비자를 깨게 된다.
     *
     * <p>새 전이가 생기면 새 사건이다. 누가 소비하는지는 기준이 아니다 — 애그리거트는 자기에게
     * 일어난 일을 말할 뿐이고 소비자 목록은 발행자의 관심사가 아니다.
     */
    public enum OrderEventType {
        ORDER_CREATED,
        ORDER_PAID,
        SHIPMENT_CREATED,
        ORDER_DELIVERED,
        ORDER_CANCELLED
    }

    /**
     * 사건의 발행 상태.
     *
     * <p>PENDING이 남아 있는 것 자체가 관측 대상이다. 실패를 삼키지 않기 위해 FAILED를 따로 둔다.
     */
    public enum PublishStatus {
        PENDING, PUBLISHED, FAILED
    }

    /**
     * 주문에 일어난 사실 하나. 상태 전이마다 쌓이고 주문 저장과 같은 트랜잭션에 커밋된다.
     *
     * <p><b>지시가 아니라 사실이다.</b> "이 문구로 알려라"가 아니라 "주문이 결제됐다"를 담는다.
     * 알림 문구는 소비자 한 명의 표현이므로 여기 넣으면 두 번째 소비자가 쓸 수 없다.
     *
     * <p>{@code payload}는 사건별로 다른 사실이다. 도메인은 이것을 문자열 맵으로만 알고,
     * 어떤 형식으로 저장할지는 영속화 어댑터가 정한다.
     */
    public static final class OrderEvent {
        /**
         * 사건에 담는 자유 문자열의 상한.
         *
         * <p>사유와 오류 메시지는 바깥에서 온다 — 하위 서비스의 예외 메시지가 그대로 들어오므로
         * 길이가 통제되지 않는다. 자르지 않으면 저장 시점에 컬럼 길이를 넘겨 <b>사건을 쓰려다
         * 그 사건이 속한 변경까지 롤백시킨다.</b> 취소 사유가 길다는 이유로 주문이 취소되지
         * 않는 것은 조용히 일어나므로 뜨는 것보다 나쁘다.
         *
         * <p>값은 저장 컬럼에서 나온다 — 사유는 {@code order_events.payload}(2000)와 알림
         * 본문(2000)에, 오류는 {@code order_events.last_error}(500)에 들어간다. 마이그레이션이
         * 컬럼을 줄이면 이 값도 함께 움직여야 한다.
         *
         * <p>원인 식별에는 앞부분이면 충분하다. 전체 트레이스를 남기는 것은 여기의 일이 아니다.
         */
        public static final int MAX_REASON_LENGTH = 500;
        public static final int MAX_ERROR_LENGTH = 400;

        private final String id;
        private final OrderEventType type;
        private final String orderId;
        private final String memberId;
        private final Map<String, String> payload;
        private PublishStatus publishStatus;
        private int attempts;
        private String lastError;

        private OrderEvent(
                String id,
                OrderEventType type,
                String orderId,
                String memberId,
                Map<String, String> payload,
                PublishStatus publishStatus,
                int attempts,
                String lastError
        ) {
            this.id = id;
            this.type = type;
            this.orderId = orderId;
            this.memberId = memberId;
            this.payload = Map.copyOf(payload);
            this.publishStatus = publishStatus;
            this.attempts = attempts;
            this.lastError = lastError;
        }

        private static OrderEvent occurred(
                OrderEventType type, String orderId, String memberId, Map<String, String> payload) {
            return new OrderEvent(
                    Ids.newId("evt"), type, orderId, memberId, payload, PublishStatus.PENDING, 0, null);
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static OrderEvent restore(
                String id,
                OrderEventType type,
                String orderId,
                String memberId,
                Map<String, String> payload,
                PublishStatus publishStatus,
                int attempts,
                String lastError
        ) {
            return new OrderEvent(id, type, orderId, memberId, payload, publishStatus, attempts, lastError);
        }

        public String id() {
            return id;
        }

        public OrderEventType type() {
            return type;
        }

        public String orderId() {
            return orderId;
        }

        public String memberId() {
            return memberId;
        }

        public Map<String, String> payload() {
            return payload;
        }

        /**
         * 순서를 지키는 단위.
         *
         * <p>같은 주문의 사건은 같은 키를 가지므로 브로커에서 한 파티션에 떨어지고, 그 안에서는
         * 넣은 순서로 나온다. 발행하는 쪽이 한 주문을 통째로 점유해 순서대로 보내므로 넣는
         * 순서가 일어난 순서다 (ADR-0016).
         */
        public String partitionKey() {
            return orderId;
        }

        public PublishStatus publishStatus() {
            return publishStatus;
        }

        public int attempts() {
            return attempts;
        }

        public String lastError() {
            return lastError;
        }

        public boolean isPublished() {
            return publishStatus == PublishStatus.PUBLISHED;
        }

        public void markPublished() {
            this.attempts += 1;
            this.publishStatus = PublishStatus.PUBLISHED;
            this.lastError = null;
        }

        /**
         * 실패를 기록으로 남긴다. 한도 안이면 PENDING으로 되돌려 다음 주기에 다시 집는다.
         *
         * <p>한도를 넘기면 FAILED로 끝낸다. 무한 재시도는 이미 지나간 사건을 계속 보내려 든다.
         */
        public void markFailed(String error, int maxAttempts) {
            this.attempts += 1;
            this.lastError = truncate(error, MAX_ERROR_LENGTH);
            this.publishStatus = attempts >= maxAttempts ? PublishStatus.FAILED : PublishStatus.PENDING;
        }

        static String truncate(String value, int limit) {
            if (value == null || value.length() <= limit) {
                return value;
            }
            return value.substring(0, limit);
        }
    }

    /**
     * 주문 시점의 배송지 스냅샷. 회원의 주소록이 바뀌어도 주문에 남은 값은 변하지 않는다.
     *
     * <p>alias와 defaultAddress는 회원 주소록의 개념이고 배송에는 쓰이지 않는다.
     * 응답 형태를 유지하기 위해 함께 스냅샷할 뿐이다.
     */
    public record Address(
            String id,
            String alias,
            String recipient,
            String phone,
            String line1,
            String city,
            String postalCode,
            boolean defaultAddress
    ) {
        public Address {
            if (recipient == null || recipient.isBlank()) {
                throw DomainException.validation("recipient is required");
            }
            if (line1 == null || line1.isBlank()) {
                throw DomainException.validation("address line is required");
            }
        }
    }

    public record OrderLine(
            String skuId,
            String productId,
            String productName,
            String skuName,
            int quantity,
            Money unitPrice
    ) {
        public OrderLine {
            if (quantity <= 0) {
                throw DomainException.validation("order quantity must be positive");
            }
        }

        public Money lineTotal() {
            return new Money(unitPrice.amount() * quantity, unitPrice.currency());
        }
    }

    public static final class Order {
        private final String id;
        private final String memberId;
        private final List<OrderLine> lines;
        private final Address shippingAddress;
        private String status = "CREATED";
        private String paymentId;
        private String shipmentId;
        private String inventoryReservationId;

        /**
         * 아직 저장되지 않은 사건.
         *
         * <p>저장소가 주문 행과 함께 커밋하고 비운다. 여기 쌓는 것과 커밋하는 것이 한
         * 트랜잭션이므로 "결제됐는데 알릴 의도가 없다"가 구조적으로 생기지 않는다.
         */
        private final List<OrderEvent> pendingEvents = new ArrayList<>();

        public Order(String memberId, List<OrderLine> lines, Address shippingAddress) {
            this(Ids.newId("ord"), memberId, lines, shippingAddress);
            recordCreated();
        }

        /** 체크아웃 접수 식별자와 외부 멱등 키가 같아야 할 때 서버가 먼저 만든 ID를 사용한다. */
        public static Order create(String id, String memberId, List<OrderLine> lines, Address shippingAddress) {
            var order = new Order(id, memberId, lines, shippingAddress);
            order.recordCreated();
            return order;
        }

        private void recordCreated() {
            record(OrderEventType.ORDER_CREATED, Map.of(
                    "totalAmount", String.valueOf(total().amount()),
                    "totalCurrency", total().currency()));
        }

        private Order(String id, String memberId, List<OrderLine> lines, Address shippingAddress) {
            if (lines.isEmpty()) {
                throw DomainException.validation("order requires at least one line");
            }
            this.id = id;
            this.memberId = memberId;
            this.lines = new ArrayList<>(lines);
            this.shippingAddress = shippingAddress;
        }

        /**
         * 저장된 상태에서 주문을 복원한다.
         *
         * <p>상태 전이 규칙(markPaid 등)을 거치지 않고 status를 그대로 세운다. CANCELLED처럼
         * 전이를 재생해서는 도달할 수 없는 상태가 있기 때문이다. 영속화 어댑터만 쓴다.
         */
        public static Order restore(
                String id,
                String memberId,
                List<OrderLine> lines,
                Address shippingAddress,
                String status,
                String paymentId,
                String shipmentId,
                String inventoryReservationId
        ) {
            var order = new Order(id, memberId, lines, shippingAddress);
            order.status = status;
            order.paymentId = paymentId;
            order.shipmentId = shipmentId;
            order.inventoryReservationId = inventoryReservationId;
            return order;
        }

        public String id() {
            return id;
        }

        public String memberId() {
            return memberId;
        }

        public String status() {
            return status;
        }

        public List<OrderLine> lines() {
            return List.copyOf(lines);
        }

        public Address shippingAddress() {
            return shippingAddress;
        }

        public String paymentId() {
            return paymentId;
        }

        public String shipmentId() {
            return shipmentId;
        }

        public String inventoryReservationId() {
            return inventoryReservationId;
        }

        public Money total() {
            var amount = lines.stream().mapToLong(line -> line.lineTotal().amount()).sum();
            return new Money(amount, "KRW");
        }

        public void attachReservation(String reservationId) {
            this.inventoryReservationId = reservationId;
        }

        /**
         * 승인된 결제를 붙인다. 청구가 아직 확정되지 않았으므로 상태는 그대로다 (PD-0011-R1).
         *
         * <p>주문이 결제됨으로 넘어가는 것은 매입 시점이다 (PD-0003-R1).
         */
        public void attachPayment(String paymentId) {
            this.paymentId = paymentId;
        }

        public void markPaid() {
            if (!status.equals("CREATED")) {
                throw DomainException.conflict("order cannot be paid from current status");
            }
            if (paymentId == null) {
                throw DomainException.conflict("order has no authorized payment");
            }
            this.status = "PAID";
            record(OrderEventType.ORDER_PAID, Map.of("paymentId", paymentId));
        }

        /**
         * 배송을 붙인다.
         *
         * <p>{@code trackingNumber}는 주문이 들고 있지 않는 값이다. 그래도 받는 이유는 사건이
         * 담아야 할 사실의 일부이기 때문이다 — "이 주문의 배송이 시작됐고 운송장은 이것"이
         * 일어난 일이고, 그것을 나중에 배송 서비스에 되물으면 사건이 자기 완결적이지 않게 된다.
         */
        public void attachShipment(String shipmentId, String trackingNumber) {
            if (!status.equals("PAID")) {
                throw DomainException.conflict("shipment can only be attached to paid order");
            }
            this.shipmentId = shipmentId;
            this.status = "FULFILLING";
            record(OrderEventType.SHIPMENT_CREATED, Map.of(
                    "shipmentId", shipmentId,
                    "trackingNumber", trackingNumber));
        }

        public void markDelivered() {
            if (!status.equals("FULFILLING") && !status.equals("PAID")) {
                throw DomainException.conflict("order cannot be delivered from current status");
            }
            this.status = "DELIVERED";
            record(OrderEventType.ORDER_DELIVERED, Map.of());
        }

        /**
         * 주문을 취소한다.
         *
         * <p>{@code reason}은 주문이 들고 있지 않지만 사건에는 필요하다. 왜 취소됐는지가
         * 취소됐다는 사실의 일부이며, 소비자가 그것 없이는 사용자에게 설명할 수 없다.
         *
         * <p>길면 자른다. 하위 서비스의 예외 메시지가 그대로 들어오므로 길이가 통제되지 않고,
         * 자르지 않으면 사유가 길다는 이유로 <b>취소 자체가 롤백된다</b>.
         */
        public void cancel(String reason) {
            if (status.equals("CANCELLED")) {
                return;
            }
            if (status.equals("DELIVERED")) {
                throw DomainException.conflict("delivered order cannot be cancelled");
            }
            this.status = "CANCELLED";
            record(OrderEventType.ORDER_CANCELLED, Map.of(
                    "reason", OrderEvent.truncate(reason == null ? "" : reason, OrderEvent.MAX_REASON_LENGTH)));
        }

        private void record(OrderEventType type, Map<String, String> payload) {
            pendingEvents.add(OrderEvent.occurred(type, id, memberId, payload));
        }

        /**
         * 아직 넘기지 않은 사건을 <b>가져가며 비운다</b>.
         *
         * <p>읽기와 비우기가 한 연산인 이유는 둘을 나누면 한쪽만 하는 조합이 생기기 때문이다 —
         * 읽고 안 비우면 다음 커밋에서 같은 사건이 다시 쓰이고, 안 읽고 비우면 사건이 조용히
         * 사라진다. {@code checkout}은 한 주문을 여러 번 커밋하므로 두 경로 다 실재한다.
         *
         * <p>이름이 {@code get}이 아닌 것은 이 조회가 쓰기이기 때문이다. 조회로 보이는 것이
         * 몰래 바꾸는 상황을 이름으로 없앤다.
         *
         * <p>복원한 주문은 비어 있다. {@link #restore}가 상태 전이를 거치지 않고 status를 그대로
         * 세우기 때문이며, 이미 일어난 일을 다시 사건으로 만들면 소비자가 두 번 본다.
         */
        public List<OrderEvent> drainPendingEvents() {
            var drained = List.copyOf(pendingEvents);
            pendingEvents.clear();
            return drained;
        }

        /** 아직 넘기지 않은 사건이 있는가. 비우지 않는다. */
        public boolean hasPendingEvents() {
            return !pendingEvents.isEmpty();
        }
    }
}
