package com.impati.commerce.payment.domain;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.ApiContracts.PaymentResponse;
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
            this.id = Ids.newId("pay");
            this.orderId = orderId;
            this.memberId = memberId;
            this.amount = amount;
            this.method = "CARD";
            this.status = "CAPTURED";
            this.transactionId = Ids.newId("txn");
        }

        public String id() {
            return id;
        }

        public PaymentResponse toResponse() {
            return new PaymentResponse(id, orderId, memberId, amount, method, status, transactionId);
        }
    }
}

