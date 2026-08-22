package com.impati.commerce.payment.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

public final class PaymentModels {
    private PaymentModels() {
    }

    /**
     * 한 주문의 결제. 승인으로 만들어지고 매입이나 취소로 끝난다 (PD-0011-R1, PD-0011-R3).
     *
     * <p>매입과 취소는 여러 번 요청되어도 첫 결과를 유지한다 (PD-0011-R4). 되돌리는 경로에서
     * 호출되고 그 경로 자체가 재시도될 수 있으므로, 같은 요청이 두 번 오는 것이 정상이다.
     */
    public static final class Payment {
        public static final String AUTHORIZED = "AUTHORIZED";
        public static final String CAPTURED = "CAPTURED";
        public static final String CANCELLED = "CANCELLED";
        public static final String REFUNDED = "REFUNDED";

        private final String id;
        private final String orderId;
        private final String memberId;
        private final Money amount;
        private final String method;
        private String status;
        private final String transactionId;

        /**
         * 승인된 결제를 만든다.
         *
         * <p>거래 식별자와 결제 수단은 대행사가 정한 값을 받는다. 도메인이 만들지 않는다 —
         * 거래 식별자는 대사의 기준이므로 대행사가 아는 값과 달라서는 안 된다 (ADR-0005).
         */
        public Payment(String orderId, String memberId, Money amount, String transactionId, String method) {
            this(Ids.newId("pay"), orderId, memberId, amount, method, AUTHORIZED, transactionId);
        }

        private Payment(
                String id,
                String orderId,
                String memberId,
                Money amount,
                String method,
                String status,
                String transactionId
        ) {
            this.id = id;
            this.orderId = orderId;
            this.memberId = memberId;
            this.amount = amount;
            this.method = method;
            this.status = status;
            this.transactionId = transactionId;
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static Payment restore(
                String id,
                String orderId,
                String memberId,
                Money amount,
                String method,
                String status,
                String transactionId
        ) {
            return new Payment(id, orderId, memberId, amount, method, status, transactionId);
        }

        /**
         * 승인된 대금을 청구로 확정한다.
         *
         * <p>실제로 전이했으면 {@code true}, 이미 매입돼 있어 할 일이 없으면 {@code false}를
         * 돌려준다 (PD-0011-R4). 호출하는 쪽은 이 값으로 대행사에 요청할지 판단한다 — 이미
         * 끝난 일을 다시 요청하지 않기 위해서다.
         */
        public boolean capture() {
            if (status.equals(CAPTURED)) {
                return false;
            }
            if (!status.equals(AUTHORIZED)) {
                throw DomainException.conflict("payment is no longer authorized and cannot be captured");
            }
            status = CAPTURED;
            return true;
        }

        /** 승인을 취소한다. 규약은 {@link #capture()}와 같다 (PD-0011-R4). */
        public boolean cancel() {
            if (status.equals(CANCELLED)) {
                return false;
            }
            if (!status.equals(AUTHORIZED)) {
                throw DomainException.conflict("captured payment cannot be cancelled");
            }
            status = CANCELLED;
            return true;
        }

        /**
         * 매입된 대금을 되돌린다 (PD-0011-R8). 이미 환불됐으면 아무것도 하지 않는다.
         *
         * <p>승인 취소와 달리 사용자 명세서에 청구와 환불 두 줄이 남는다. 그래서 이 경로는
         * 정상 흐름이 아니라 매입 결과를 확인하지 못한 체크아웃을 정리할 때만 쓴다.
         *
         * <p>반환값 규약은 {@link #capture()}와 같다.
         */
        public boolean refund() {
            if (status.equals(REFUNDED)) {
                return false;
            }
            if (!status.equals(CAPTURED)) {
                throw DomainException.conflict("only a captured payment can be refunded");
            }
            status = REFUNDED;
            return true;
        }

        public String id() {
            return id;
        }

        public String orderId() {
            return orderId;
        }

        public String memberId() {
            return memberId;
        }

        public Money amount() {
            return amount;
        }

        public String method() {
            return method;
        }

        public String status() {
            return status;
        }

        public String transactionId() {
            return transactionId;
        }
    }
}
