package com.impati.commerce.inventory.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.inventory.application.port.in.InventoryUseCase;
import com.impati.commerce.inventory.application.port.in.ReservationDetails;
import com.impati.commerce.inventory.application.port.in.StockDetails;
import com.impati.commerce.inventory.application.port.in.StockLine;
import com.impati.commerce.inventory.application.port.out.InventoryRepository;
import com.impati.commerce.inventory.domain.InventoryModels.Reservation;
import com.impati.commerce.inventory.domain.InventoryModels.ReservedLine;
import com.impati.commerce.inventory.domain.InventoryModels.StockItem;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 재고 변경은 모두 트랜잭션 안에서 대상 행을 잠근 뒤 수행한다.
 *
 * <p>이전에는 메서드에 {@code synchronized}를 걸었다. 단일 프로세스와 맵을 가정한 동기화이므로
 * DB로 옮기면서 걷어냈다. 인스턴스가 여러 개면 JVM 락은 아무것도 보호하지 못한다.
 */
@Component
public class InventoryExecutor implements InventoryUseCase {
    private final InventoryRepository inventoryRepository;

    public InventoryExecutor(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    @Override
    public StockDetails addStock(String skuId, int quantity) {
        var stock = inventoryRepository.lockStock(List.of(skuId)).stream()
                .findFirst()
                .orElseGet(() -> new StockItem(skuId));
        stock.add(quantity);
        inventoryRepository.saveStock(stock);
        return InventoryMapper.toDetails(stock);
    }

    @Transactional
    @Override
    public ReservationDetails reserve(String orderId, List<StockLine> lines) {
        var reservedLines = lines.stream()
                .map(line -> new ReservedLine(line.skuId(), line.quantity()))
                .toList();
        var locked = lockFor(reservedLines);

        for (var line : reservedLines) {
            var stock = requireStock(locked, line.skuId());
            if (stock.available() < line.quantity()) {
                throw DomainException.conflict("insufficient stock for " + line.skuId());
            }
        }
        for (var line : reservedLines) {
            var stock = requireStock(locked, line.skuId());
            stock.reserve(line.quantity());
            inventoryRepository.saveStock(stock);
        }

        var reservation = new Reservation(orderId, reservedLines);
        inventoryRepository.saveReservation(reservation);
        return InventoryMapper.toDetails(reservation);
    }

    @Transactional
    @Override
    public ReservationDetails commit(String reservationId) {
        var reservation = getReservation(reservationId);
        var locked = lockFor(reservation.lines());
        for (var line : reservation.lines()) {
            var stock = requireStock(locked, line.skuId());
            stock.commit(line.quantity());
            inventoryRepository.saveStock(stock);
        }
        reservation.commit();
        inventoryRepository.saveReservation(reservation);
        return InventoryMapper.toDetails(reservation);
    }

    @Transactional
    @Override
    public ReservationDetails release(String reservationId) {
        var reservation = getReservation(reservationId);
        var locked = lockFor(reservation.lines());
        for (var line : reservation.lines()) {
            var stock = requireStock(locked, line.skuId());
            stock.release(line.quantity());
            inventoryRepository.saveStock(stock);
        }
        reservation.release();
        inventoryRepository.saveReservation(reservation);
        return InventoryMapper.toDetails(reservation);
    }

    @Transactional(readOnly = true)
    @Override
    public List<StockDetails> stock() {
        return inventoryRepository.stock().stream().map(InventoryMapper::toDetails).toList();
    }

    /** 시드가 이미 들어가 있는지 확인한다. 파일 DB에서는 재시작마다 시드를 넣으면 재고가 늘어난다. */
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

    private Reservation getReservation(String reservationId) {
        return inventoryRepository.findReservation(reservationId)
                .orElseThrow(() -> DomainException.notFound("reservation not found"));
    }
}
