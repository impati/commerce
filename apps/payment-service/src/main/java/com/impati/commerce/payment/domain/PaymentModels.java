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

        public Payment(String orderId, String memberId, Money amount) {
            this(Ids.newId("pay"), orderId, memberId, amount, "CARD", AUTHORIZED, Ids.newId("txn"));
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

        /** 승인된 대금을 청구로 확정한다. 이미 매입됐으면 아무것도 하지 않는다 (PD-0011-R4). */
        public void capture() {
            if (status.equals(CAPTURED)) {
                return;
            }
            if (!status.equals(AUTHORIZED)) {
                throw DomainException.conflict("payment is no longer authorized and cannot be captured");
            }
            status = CAPTURED;
        }

        /** 승인을 취소한다. 이미 취소됐으면 아무것도 하지 않는다 (PD-0011-R4). */
        public void cancel() {
            if (status.equals(CANCELLED)) {
                return;
            }
            if (!status.equals(AUTHORIZED)) {
                throw DomainException.conflict("captured payment cannot be cancelled");
            }
            status = CANCELLED;
        }

        /**
         * 매입된 대금을 되돌린다 (PD-0011-R8). 이미 환불됐으면 아무것도 하지 않는다.
         *
         * <p>승인 취소와 달리 사용자 명세서에 청구와 환불 두 줄이 남는다. 그래서 이 경로는
         * 정상 흐름이 아니라 매입 결과를 확인하지 못한 체크아웃을 정리할 때만 쓴다.
         */
        public void refund() {
            if (status.equals(REFUNDED)) {
                return;
            }
            if (!status.equals(CAPTURED)) {
                throw DomainException.conflict("only a captured payment can be refunded");
            }
            status = REFUNDED;
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
