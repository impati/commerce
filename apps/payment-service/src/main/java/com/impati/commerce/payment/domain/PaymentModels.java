package com.impati.commerce.payment.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.Ids;

public final class PaymentModels {
    private PaymentModels() {
    }

    public static final class Payment {
        private final String id;
        private final String orderId;
        private final String memberId;
        private final Money amount;
        private final String method;
        private final String status;
        private final String transactionId;

        public Payment(String orderId, String memberId, Money amount) {
            this(Ids.newId("pay"), orderId, memberId, amount, "CARD", "CAPTURED", Ids.newId("txn"));
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
