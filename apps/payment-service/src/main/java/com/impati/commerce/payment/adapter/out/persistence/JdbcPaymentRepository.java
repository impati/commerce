package com.impati.commerce.payment.adapter.out.persistence;

import com.impati.commerce.common.ApiContracts.Money;
import com.impati.commerce.payment.application.PaymentRepository;
import com.impati.commerce.payment.domain.PaymentModels.Payment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 */
@Repository
public class JdbcPaymentRepository implements PaymentRepository {
    private static final String COLUMNS =
            "id, order_id, member_id, amount, currency, method, status, transaction_id";

    private static final String INSERT = """
            insert into payments (id, order_id, member_id, amount, currency, method, status, transaction_id)
            values (:id, :order_id, :member_id, :amount, :currency, :method, :status, :transaction_id)
            """;

    private static final String UPDATE = """
            update payments
               set order_id = :order_id,
                   member_id = :member_id,
                   amount = :amount,
                   currency = :currency,
                   method = :method,
                   status = :status,
                   transaction_id = :transaction_id
             where id = :id
            """;

    private static final String SELECT = "select " + COLUMNS + " from payments where id = :id";

    private static final RowMapper<Payment> ROW_MAPPER = (rs, rowNum) -> Payment.restore(
            rs.getString("id"),
            rs.getString("order_id"),
            rs.getString("member_id"),
            new Money(rs.getLong("amount"), rs.getString("currency")),
            rs.getString("method"),
            rs.getString("status"),
            rs.getString("transaction_id")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPaymentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Payment payment) {
        var params = new MapSqlParameterSource()
                .addValue("id", payment.id())
                .addValue("order_id", payment.orderId())
                .addValue("member_id", payment.memberId())
                .addValue("amount", payment.amount().amount())
                .addValue("currency", payment.amount().currency())
                .addValue("method", payment.method())
                .addValue("status", payment.status())
                .addValue("transaction_id", payment.transactionId());
        if (jdbc.update(UPDATE, params) == 0) {
            jdbc.update(INSERT, params);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(String paymentId) {
        return jdbc.query(SELECT, new MapSqlParameterSource("id", paymentId), ROW_MAPPER)
                .stream()
                .findFirst();
    }
}
