package com.impati.commerce.inventory.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.inventory.application.port.in.InventoryUseCase;
import com.impati.commerce.inventory.application.port.in.ReservationDetails;
import com.impati.commerce.inventory.application.port.in.StockDetails;
import com.impati.commerce.inventory.application.port.in.StockLine;
import com.impati.commerce.inventory.application.port.out.InventoryRepository;
import com.impati.commerce.inventory.domain.InventoryModels.InventoryMovement;
import com.impati.commerce.inventory.domain.InventoryModels.MovementLine;
import com.impati.commerce.inventory.domain.InventoryModels.MovementReason;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.ReservedLine;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;
import com.impati.commerce.inventory.domain.InventoryModels.TransitionOutcome;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 변경은 모두 트랜잭션 안에서 대상 행을 잠근 뒤 수행한다.
 *
 * <p>이전에는 메서드에 {@code synchronized}를 걸었다. 단일 프로세스와 맵을 가정한 동기화이므로
 * DB로 옮기면서 걷어냈다. 인스턴스가 여러 개면 JVM 락은 아무것도 보호하지 못한다.
 */
@Component
public class InventoryExecutor implements InventoryUseCase {

    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    public InventoryExecutor(InventoryRepository inventoryRepository, Clock clock) {
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    @Transactional
    @Override
    public StockDetails addStock(String skuId, int quantity) {
        var stock = inventoryRepository.lockStock(List.of(skuId)).stream()
                .findFirst()
                .orElseGet(() -> new StockItem(skuId));
        var movementLine = stock.add(quantity);
        inventoryRepository.saveStock(stock);
        inventoryRepository.saveMovement(InventoryMovement.stockIncreased(now(), movementLine));
        return InventoryMapper.toDetails(stock);
    }

    @Transactional
    @Override
    public ReservationDetails reserve(String orderId, List<StockLine> lines) {
        var reservedLines = lines.stream()
                .map(line -> new ReservedLine(line.skuId(), line.quantity()))
                .toList();
        var existing = inventoryRepository.findReservationByOrderId(orderId);
        if (existing.isPresent()) {
            return sameReservation(existing.get(), reservedLines);
        }
        var locked = lockFor(reservedLines);

        existing = inventoryRepository.findReservationByOrderId(orderId);
        if (existing.isPresent()) {
            return sameReservation(existing.get(), reservedLines);
        }

        for (var line : reservedLines) {
            var stock = requireStock(locked, line.skuId());
            if (stock.available() < line.quantity()) {
                throw DomainException.outOfStock("insufficient stock for " + line.skuId());
            }
        }
        var movementLines = new ArrayList<MovementLine>();
        for (var line : reservedLines) {
            var stock = requireStock(locked, line.skuId());
            movementLines.add(stock.reserve(line.quantity()));
            inventoryRepository.saveStock(stock);
        }

        var reservation = new Reservation(orderId, reservedLines);
        inventoryRepository.saveReservation(reservation);
        inventoryRepository.saveMovement(InventoryMovement.forReservation(
                MovementReason.RESERVATION_CREATED,
                reservation,
                now(),
                movementLines
        ));
        return InventoryMapper.toDetails(reservation);
    }

    @Transactional
    @Override
    public ReservationDetails commit(String reservationId) {
        var reservation = inventoryRepository.findReservationForUpdate(reservationId)
                .orElseThrow(() -> DomainException.notFound("reservation not found"));
        if (reservation.commit() == TransitionOutcome.UNCHANGED) {
            return InventoryMapper.toDetails(reservation);
        }
        var locked = lockFor(reservation.lines());
        var movementLines = new ArrayList<MovementLine>();
        for (var line : reservation.lines()) {
            var stock = requireStock(locked, line.skuId());
            movementLines.add(stock.commit(line.quantity()));
            inventoryRepository.saveStock(stock);
        }
        inventoryRepository.saveReservation(reservation);
        inventoryRepository.saveMovement(InventoryMovement.forReservation(
                MovementReason.RESERVATION_COMMITTED,
                reservation,
                now(),
                movementLines
        ));
        return InventoryMapper.toDetails(reservation);
    }

    @Transactional
    @Override
    public ReservationDetails release(String reservationId) {
        var reservation = inventoryRepository.findReservationForUpdate(reservationId)
                .orElseThrow(() -> DomainException.notFound("reservation not found"));
        if (reservation.release() == TransitionOutcome.UNCHANGED) {
            return InventoryMapper.toDetails(reservation);
        }
        var locked = lockFor(reservation.lines());
        var movementLines = new ArrayList<MovementLine>();
        for (var line : reservation.lines()) {
            var stock = requireStock(locked, line.skuId());
            movementLines.add(stock.release(line.quantity()));
            inventoryRepository.saveStock(stock);
        }
        inventoryRepository.saveReservation(reservation);
        inventoryRepository.saveMovement(InventoryMovement.forReservation(
                MovementReason.RESERVATION_RELEASED,
                reservation,
                now(),
                movementLines
        ));
        return InventoryMapper.toDetails(reservation);
    }

    @Transactional
    @Override
    public ReservationDetails restore(String reservationId) {
        var reservation = inventoryRepository.findReservationForUpdate(reservationId)
                .orElseThrow(() -> DomainException.notFound("reservation not found"));
        if (reservation.restore() == TransitionOutcome.UNCHANGED) {
            return InventoryMapper.toDetails(reservation);
        }
        var locked = lockFor(reservation.lines());
        var movementLines = new ArrayList<MovementLine>();
        for (var line : reservation.lines()) {
            var stock = requireStock(locked, line.skuId());
            movementLines.add(stock.restore(line.quantity()));
            inventoryRepository.saveStock(stock);
        }
        inventoryRepository.saveReservation(reservation);
        inventoryRepository.saveMovement(InventoryMovement.forReservation(
                MovementReason.RESERVATION_RESTORED,
                reservation,
                now(),
                movementLines
        ));
        return InventoryMapper.toDetails(reservation);
    }

    @Transactional(readOnly = true)
    @Override
    public List<StockDetails> stock() {
        return inventoryRepository.stock().stream().map(InventoryMapper::toDetails).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockDetails getStock(String skuId) {
        return InventoryMapper.toDetails(inventoryRepository.findStock(skuId)
                .orElseThrow(() -> DomainException.notFound("stock not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationDetails reservationForOrder(String orderId) {
        return InventoryMapper.toDetails(inventoryRepository.findReservationByOrderId(orderId)
                .orElseThrow(() -> DomainException.notFound("reservation not found for order")));
    }

    private ReservationDetails sameReservation(Reservation existing, List<ReservedLine> requested) {
        if (!existing.lines().equals(requested)) {
            throw DomainException.conflict("order already has a different inventory reservation");
        }
        return InventoryMapper.toDetails(existing);
    }

    /**
     * 시드가 이미 들어가 있는지 확인한다. 파일 DB에서는 재시작마다 시드를 넣으면 재고가 늘어난다.
     */
    @Transactional(readOnly = true)
    @Override
    public boolean isEmpty() {
        return inventoryRepository.stock().isEmpty();
    }

    private Map<String, StockItem> lockFor(List<ReservedLine> lines) {
        var skuIds = lines.stream().map(ReservedLine::skuId).distinct().toList();
        Map<String, StockItem> locked = new LinkedHashMap<>();
        inventoryRepository.lockStock(skuIds).forEach(stock -> locked.put(stock.skuId(), stock));
        return locked;
    }

    private StockItem requireStock(Map<String, StockItem> locked, String skuId) {
        var stock = locked.get(skuId);
        if (stock == null) {
            throw DomainException.notFound("stock not found for " + skuId);
        }
        return stock;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
