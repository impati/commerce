package com.impati.commerce.payment.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.payment.application.port.out.PaymentRepository;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import com.impati.commerce.payment.domain.PaymentModels.RefundOperation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 */
@Repository
public class JdbcPaymentRepository implements PaymentRepository {
    private static final String COLUMNS =
            "id, order_id, member_id, amount, refunded_amount, currency, method, status, transaction_id";

    private static final String INSERT = """
            insert into payments (id, order_id, member_id, amount, refunded_amount, currency, method, status, transaction_id)
            values (:id, :order_id, :member_id, :amount, :refunded_amount, :currency, :method, :status, :transaction_id)
            """;

    private static final String UPDATE = """
            update payments
               set status = :status, refunded_amount = :refunded_amount
             where id = :id
            """;

    private static final String SELECT_BY_ID = "select " + COLUMNS + " from payments where id = :id";
    private static final String SELECT_BY_ID_FOR_UPDATE = SELECT_BY_ID + " for update";

    private static final String SELECT_BY_ORDER =
            "select " + COLUMNS + " from payments where order_id = :order_id";

    private static final RowMapper<Payment> ROW_MAPPER = (rs, rowNum) -> Payment.restore(
            rs.getString("id"),
            rs.getString("order_id"),
            rs.getString("member_id"),
            new Money(rs.getLong("amount"), rs.getString("currency")),
            rs.getString("method"),
            rs.getString("status"),
            rs.getString("transaction_id"),
            rs.getLong("refunded_amount")
    );

    private static final RowMapper<RefundOperation> REFUND_ROW_MAPPER = (rs, rowNum) -> new RefundOperation(
            rs.getString("return_id"), rs.getString("payment_id"),
            new Money(rs.getLong("amount"), rs.getString("currency")),
            rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPaymentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 유일 제약 위반을 "이미 있다"로 옮긴다. 조회 후 넣는 사이에 다른 요청이 넣을 수 있으므로
     * 판정은 DB가 하고, 어댑터가 그것을 응용 계층의 언어로 바꾼다.
     */
    @Override
    @Transactional
    public boolean insertIfAbsent(Payment payment) {
        try {
            jdbc.update(INSERT, params(payment));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    /** 바뀌는 것은 상태뿐이다. 나머지 값은 승인 시점에 확정된다. */
    @Override
    @Transactional
    public void update(Payment payment) {
        jdbc.update(UPDATE, new MapSqlParameterSource()
                .addValue("id", payment.id())
                .addValue("status", payment.status())
                .addValue("refunded_amount", payment.refundedAmount()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(String paymentId) {
        return jdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", paymentId), ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public Optional<Payment> findByIdForUpdate(String paymentId) {
        return jdbc.query(SELECT_BY_ID_FOR_UPDATE, new MapSqlParameterSource("id", paymentId), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(String orderId) {
        return jdbc.query(SELECT_BY_ORDER, new MapSqlParameterSource("order_id", orderId), ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public boolean insertRefundIfAbsent(RefundOperation refund) {
        try {
            jdbc.update("""
                    insert into payment_refunds (return_id, payment_id, amount, currency, status, attempts, last_error)
                    values (:return_id, :payment_id, :amount, :currency, :status, :attempts, :last_error)
                    """, refundParams(refund));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundOperation> findRefund(String returnId) {
        return jdbc.query("select return_id, payment_id, amount, currency, status, attempts, last_error "
                        + "from payment_refunds where return_id = :return_id",
                new MapSqlParameterSource("return_id", returnId), REFUND_ROW_MAPPER).stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<RefundOperation> findRefundForUpdate(String returnId) {
        return jdbc.query("select return_id, payment_id, amount, currency, status, attempts, last_error "
                        + "from payment_refunds where return_id = :return_id for update",
                new MapSqlParameterSource("return_id", returnId), REFUND_ROW_MAPPER).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundOperation> findPendingRefunds(int limit) {
        return jdbc.query("select return_id, payment_id, amount, currency, status, attempts, last_error "
                        + "from payment_refunds where status = 'PENDING' order by updated_at limit :limit",
                new MapSqlParameterSource("limit", limit), REFUND_ROW_MAPPER);
    }

    @Override
    @Transactional
    public void updateRefund(RefundOperation refund) {
        jdbc.update("""
                update payment_refunds
                   set status = :status, attempts = :attempts, last_error = :last_error,
                       updated_at = current_timestamp(6)
                 where return_id = :return_id
                """, refundParams(refund));
    }

    private static MapSqlParameterSource params(Payment payment) {
        return new MapSqlParameterSource()
                .addValue("id", payment.id())
                .addValue("order_id", payment.orderId())
                .addValue("member_id", payment.memberId())
                .addValue("amount", payment.amount().amount())
                .addValue("refunded_amount", payment.refundedAmount())
                .addValue("currency", payment.amount().currency())
                .addValue("method", payment.method())
                .addValue("status", payment.status())
                .addValue("transaction_id", payment.transactionId());
    }

    private static MapSqlParameterSource refundParams(RefundOperation refund) {
        return new MapSqlParameterSource()
                .addValue("return_id", refund.returnId())
                .addValue("payment_id", refund.paymentId())
                .addValue("amount", refund.amount().amount())
                .addValue("currency", refund.amount().currency())
                .addValue("status", refund.status())
                .addValue("attempts", refund.attempts())
                .addValue("last_error", refund.lastError());
    }
}
