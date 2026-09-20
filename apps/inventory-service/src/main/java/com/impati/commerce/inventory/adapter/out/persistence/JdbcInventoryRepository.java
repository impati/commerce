package com.impati.commerce.inventory.adapter.out.persistence;

import com.impati.commerce.inventory.application.port.out.InventoryRepository;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.ReservationStatus;
import com.impati.commerce.inventory.domain.InventoryModels.ReservedLine;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이름 바인딩만 쓴다. 위치 기반 {@code ?}는 타입이 같은 인접 컬럼의 값이 뒤바뀌어도 잡히지 않는다.
 */
@Repository
public class JdbcInventoryRepository implements InventoryRepository {

    private static final String SELECT_STOCK =
            "select sku_id, on_hand, reserved from stock_items where sku_id = :sku_id";
    private static final String SELECT_ALL_STOCK =
            "select sku_id, on_hand, reserved from stock_items order by sku_id";

    /**
     * 잠금은 sku_id 순서로 건다. 두 요청이 겹치는 SKU 집합을 서로 다른 순서로 잠그면 데드락이 난다.
     */
    private static final String LOCK_STOCK = """
            select sku_id, on_hand, reserved
              from stock_items
             where sku_id in (:sku_ids)
             order by sku_id
               for update
            """;

    private static final String INSERT_STOCK = """
            insert into stock_items (sku_id, on_hand, reserved)
            values (:sku_id, :on_hand, :reserved)
            """;
    private static final String UPDATE_STOCK = """
            update stock_items
               set on_hand = :on_hand, reserved = :reserved
             where sku_id = :sku_id
            """;

    private static final String SELECT_RESERVATION =
            "select id, order_id, status from reservations where id = :id for update";
    private static final String SELECT_RESERVATION_BY_ORDER =
            "select id, order_id, status from reservations where order_id = :order_id";
    private static final String INSERT_RESERVATION = """
            insert into reservations (id, order_id, status)
            values (:id, :order_id, :status)
            """;
    private static final String UPDATE_RESERVATION = """
            update reservations set order_id = :order_id, status = :status where id = :id
            """;
    private static final String DELETE_RESERVATION_LINES =
            "delete from reservation_lines where reservation_id = :reservation_id";
    private static final String INSERT_RESERVATION_LINE = """
            insert into reservation_lines (reservation_id, sku_id, line_no, quantity)
            values (:reservation_id, :sku_id, :line_no, :quantity)
            """;
    private static final String SELECT_RESERVATION_LINES = """
            select sku_id, quantity
              from reservation_lines
             where reservation_id = :reservation_id
             order by line_no
            """;

    private static final RowMapper<StockItem> STOCK_MAPPER = (rs, rowNum) -> StockItem.restore(
            rs.getString("sku_id"),
            rs.getInt("on_hand"),
            rs.getInt("reserved")
    );

    private static final RowMapper<ReservedLine> LINE_MAPPER = (rs, rowNum) -> new ReservedLine(
            rs.getString("sku_id"),
            rs.getInt("quantity")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcInventoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockItem> findStock(String skuId) {
        return jdbc.query(SELECT_STOCK, new MapSqlParameterSource("sku_id", skuId), STOCK_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public List<StockItem> lockStock(Collection<String> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(LOCK_STOCK, new MapSqlParameterSource("sku_ids", skuIds), STOCK_MAPPER);
    }

    @Override
    @Transactional
    public void saveStock(StockItem item) {
        var params = new MapSqlParameterSource()
                .addValue("sku_id", item.skuId())
                .addValue("on_hand", item.onHand())
                .addValue("reserved", item.reserved());
        if (jdbc.update(UPDATE_STOCK, params) == 0) {
            jdbc.update(INSERT_STOCK, params);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<StockItem> stock() {
        return jdbc.query(SELECT_ALL_STOCK, STOCK_MAPPER);
    }

    @Override
    @Transactional
    public void saveReservation(Reservation reservation) {
        var params = new MapSqlParameterSource()
                .addValue("id", reservation.id())
                .addValue("order_id", reservation.orderId())
                .addValue("status", reservation.status().name());
        if (jdbc.update(UPDATE_RESERVATION, params) == 0) {
            jdbc.update(INSERT_RESERVATION, params);
        }

        jdbc.update(DELETE_RESERVATION_LINES, new MapSqlParameterSource("reservation_id", reservation.id()));
        var lines = reservation.lines();
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            jdbc.update(INSERT_RESERVATION_LINE, new MapSqlParameterSource()
                    .addValue("reservation_id", reservation.id())
                    .addValue("sku_id", line.skuId())
                    .addValue("line_no", index)
                    .addValue("quantity", line.quantity()));
        }
    }

    @Override
    @Transactional
    public Optional<Reservation> findReservationForUpdate(String reservationId) {
        return jdbc.query(
                        SELECT_RESERVATION,
                        new MapSqlParameterSource("id", reservationId),
                        (rs, rowNum) -> Reservation.restore(
                                rs.getString("id"),
                                rs.getString("order_id"),
                                findLines(rs.getString("id")),
                                ReservationStatus.valueOf(rs.getString("status"))
                        )
                )
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findReservationByOrderId(String orderId) {
        return jdbc.query(
                        SELECT_RESERVATION_BY_ORDER,
                        new MapSqlParameterSource("order_id", orderId),
                        (rs, rowNum) -> Reservation.restore(
                                rs.getString("id"),
                                rs.getString("order_id"),
                                findLines(rs.getString("id")),
                                ReservationStatus.valueOf(rs.getString("status"))
                        ))
                .stream()
                .findFirst();
    }

    private List<ReservedLine> findLines(String reservationId) {
        return jdbc.query(
                SELECT_RESERVATION_LINES,
                new MapSqlParameterSource("reservation_id", reservationId),
                LINE_MAPPER
        );
    }
}
