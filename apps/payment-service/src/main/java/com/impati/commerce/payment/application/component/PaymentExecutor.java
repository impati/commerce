package com.impati.commerce.payment.application.component;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.payment.application.port.in.AuthorizedPayment;
import com.impati.commerce.payment.application.port.in.CancelledPayment;
import com.impati.commerce.payment.application.port.in.CapturedPayment;
import com.impati.commerce.payment.application.port.in.PaymentDetails;
import com.impati.commerce.payment.application.port.in.PaymentUseCase;
import com.impati.commerce.payment.application.port.in.RefundedPayment;
import com.impati.commerce.payment.application.port.in.ReturnRefundDetails;
import com.impati.commerce.payment.application.port.out.PaymentGateway;
import com.impati.commerce.payment.application.port.out.PaymentRepository;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import com.impati.commerce.payment.domain.PaymentModels.RefundOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * {@link PaymentUseCase} 구현.
 *
 *
 * <p>다섯 동작이 저장소와 대행사를 함께 쓰므로 한 클래스에 둔다 — 생성자 의존성을 메서드별
 * 사용으로 매핑해도 겹치지 않는 집합으로 갈리지 않는다.
 *
 * <p>계약은 포트에 있다. 여기에는 구현 사정만 적는다.
 */
@Component
public class PaymentExecutor implements PaymentUseCase {
    private static final Logger log = LoggerFactory.getLogger(PaymentExecutor.class);
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactions;

    public PaymentExecutor(PaymentRepository paymentRepository, PaymentGateway paymentGateway,
            TransactionTemplate transactions) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.transactions = transactions;
    }

    @Override
    @Transactional
    public AuthorizedPayment authorize(String orderId, String memberId, Money amount, String paymentToken) {
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            var payment = existing.get();
            if (!payment.memberId().equals(memberId) || !payment.amount().equals(amount)) {
                throw DomainException.conflict("order already has a different payment");
            }
            return PaymentMapper.toAuthorized(payment);
        }

        var authorization = paymentGateway.authorize(orderId, amount, paymentToken);
        if (!authorization.approved()) {
            throw DomainException.paymentDeclined("payment was declined by issuer: " + authorization.declineReason());
        }

        Payment payment = new Payment(orderId, memberId, amount, authorization.transactionId(), authorization.method());
        if (!paymentRepository.insertIfAbsent(payment)) {
            return PaymentMapper.toAuthorized(paymentRepository.findByOrderId(orderId)
                    .orElseThrow(() -> DomainException.conflict(
                            "payment for order disappeared after concurrent creation: " + orderId)));
        }
        return PaymentMapper.toAuthorized(payment);
    }

    @Override
    @Transactional
    public CapturedPayment capture(String paymentId) {
        var payment = require(paymentId);
        if (payment.capture()) {
            paymentGateway.capture(payment.transactionId());
            paymentRepository.update(payment);
        }
        return PaymentMapper.toCaptured(payment);
    }

    @Override
    @Transactional
    public CancelledPayment cancel(String paymentId) {
        var payment = require(paymentId);
        if (payment.cancel()) {
            paymentGateway.cancel(payment.transactionId());
            paymentRepository.update(payment);
        }
        return PaymentMapper.toCancelled(payment);
    }

    @Override
    @Transactional
    public RefundedPayment refund(String paymentId) {
        var payment = require(paymentId);
        if (payment.refund()) {
            paymentGateway.refund(payment.transactionId());
            paymentRepository.update(payment);
        }
        return PaymentMapper.toRefunded(payment);
    }

    @Override
    public ReturnRefundDetails refundForReturn(String paymentId, String returnId, Money amount) {
        var operation = transactions.execute(status -> {
            var payment = requireForUpdate(paymentId);
            var existing = paymentRepository.findRefundForUpdate(returnId);
            if (existing.isPresent()) {
                requireSameRefund(existing.get(), paymentId, amount);
                return existing.get();
            }
            payment.validateRefund(amount);
            var pending = RefundOperation.pending(returnId, paymentId, amount);
            if (paymentRepository.insertRefundIfAbsent(pending)) {
                return pending;
            }
            var raced = paymentRepository.findRefundForUpdate(returnId)
                    .orElseThrow(() -> DomainException.conflict("refund creation raced without a result"));
            requireSameRefund(raced, paymentId, amount);
            return raced;
        });
        executeReturnRefund(operation);
        var result = requireRefund(returnId);
        if (RefundOperation.REJECTED.equals(result.status())) {
            throw DomainException.downstreamError("payment gateway rejected the return refund");
        }
        return toDetails(result);
    }

    @Override
    public ReturnRefundDetails getReturnRefund(String returnId) {
        return toDetails(requireRefund(returnId));
    }

    @Override
    public int recoverPendingRefunds(int batchSize) {
        var refunds = paymentRepository.findPendingRefunds(batchSize);
        refunds.forEach(this::executeReturnRefund);
        return refunds.size();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDetails get(String paymentId) {
        return PaymentMapper.toDetails(require(paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDetails getForOrder(String orderId) {
        return PaymentMapper.toDetails(requireByOrder(orderId));
    }

    private Payment require(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> DomainException.notFound("payment not found"));
    }

    private Payment requireForUpdate(String paymentId) {
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> DomainException.notFound("payment not found"));
    }

    private RefundOperation requireRefund(String returnId) {
        return paymentRepository.findRefund(returnId)
                .orElseThrow(() -> DomainException.notFound("return refund not found"));
    }

    private void executeReturnRefund(RefundOperation requested) {
        if (!RefundOperation.PENDING.equals(requested.status())) {
            return;
        }
        var payment = require(requested.paymentId());
        var command = new PaymentGateway.RefundCommand(
                requested.returnId(), payment.transactionId(), requested.amount());
        PaymentGateway.RefundResult result;
        try {
            result = requested.attempts() == 0
                    ? paymentGateway.refund(command)
                    : paymentGateway.refundResult(command);
            if (result.outcome() == PaymentGateway.RefundOutcome.ABSENT) {
                result = paymentGateway.refund(command);
            }
        } catch (RuntimeException unknown) {
            log.warn("return refund result is unknown returnId={}", requested.returnId(), unknown);
            recordPendingAttempt(requested.returnId(), unknown.getMessage());
            return;
        }
        switch (result.outcome()) {
            case CONFIRMED -> confirmRefund(requested.returnId());
            case UNKNOWN, ABSENT -> recordPendingAttempt(requested.returnId(), result.message());
            case REJECTED -> rejectRefund(requested.returnId(), result.message());
        }
    }

    private void confirmRefund(String returnId) {
        transactions.executeWithoutResult(status -> {
            var refund = paymentRepository.findRefundForUpdate(returnId)
                    .orElseThrow(() -> DomainException.notFound("return refund not found"));
            if (RefundOperation.SUCCEEDED.equals(refund.status())) {
                return;
            }
            var payment = requireForUpdate(refund.paymentId());
            payment.applyRefund(refund.amount());
            paymentRepository.update(payment);
            paymentRepository.updateRefund(new RefundOperation(
                    refund.returnId(), refund.paymentId(), refund.amount(), RefundOperation.SUCCEEDED,
                    refund.attempts() + 1, null));
        });
    }

    private void recordPendingAttempt(String returnId, String message) {
        transactions.executeWithoutResult(status -> paymentRepository.findRefundForUpdate(returnId)
                .filter(refund -> RefundOperation.PENDING.equals(refund.status()))
                .ifPresent(refund -> paymentRepository.updateRefund(new RefundOperation(
                        refund.returnId(), refund.paymentId(), refund.amount(), RefundOperation.PENDING,
                        refund.attempts() + 1, truncate(message)))));
    }

    private void rejectRefund(String returnId, String message) {
        transactions.executeWithoutResult(status -> paymentRepository.findRefundForUpdate(returnId)
                .filter(refund -> RefundOperation.PENDING.equals(refund.status()))
                .ifPresent(refund -> paymentRepository.updateRefund(new RefundOperation(
                        refund.returnId(), refund.paymentId(), refund.amount(), RefundOperation.REJECTED,
                        refund.attempts() + 1, truncate(message)))));
    }

    private static void requireSameRefund(RefundOperation refund, String paymentId, Money amount) {
        if (!refund.paymentId().equals(paymentId) || !refund.amount().equals(amount)) {
            throw DomainException.conflict("return id already has a different refund");
        }
    }

    private static ReturnRefundDetails toDetails(RefundOperation refund) {
        return new ReturnRefundDetails(refund.returnId(), refund.paymentId(), refund.amount(), refund.status());
    }

    private static String truncate(String message) {
        if (message == null) return "payment gateway result is unknown";
        return message.substring(0, Math.min(message.length(), 400));
    }

    private Payment requireByOrder(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> DomainException.notFound("payment not found for order: " + orderId));
    }
}
