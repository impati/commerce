package com.impati.commerce.shipping.application.component;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.shipping.application.port.in.ShipmentAddress;
import com.impati.commerce.shipping.application.port.in.ShipmentDetails;
import com.impati.commerce.shipping.application.port.in.ShippingUseCase;
import com.impati.commerce.shipping.application.port.out.ShipmentRepository;
import com.impati.commerce.shipping.domain.ShippingModels.Shipment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ShippingExecutor implements ShippingUseCase {
    private final ShipmentRepository shipmentRepository;

    public ShippingExecutor(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @Override
    @Transactional
    public ShipmentDetails create(String orderId, String memberId, ShipmentAddress address) {
        var shipmentAddress = ShipmentMapper.toAddress(address);
        var existing = shipmentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return sameShipment(existing.get(), memberId, shipmentAddress);
        }
        var shipment = new Shipment(orderId, memberId, shipmentAddress);
        if (!shipmentRepository.insertIfAbsent(shipment)) {
            return sameShipment(shipmentRepository.findByOrderId(orderId)
                    .orElseThrow(() -> DomainException.conflict("shipment creation raced without a result")),
                    memberId, shipmentAddress);
        }
        return ShipmentMapper.toDetails(shipment);
    }

    @Override
    public ShipmentDetails get(String shipmentId) {
        return ShipmentMapper.toDetails(getShipment(shipmentId));
    }

    @Override
    public ShipmentDetails getForOrder(String orderId) {
        return ShipmentMapper.toDetails(shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> DomainException.notFound("shipment not found for order")));
    }

    @Override
    @Transactional
    public ShipmentDetails ship(String shipmentId) {
        var shipment = getShipmentForUpdate(shipmentId);
        shipment.ship();
        shipmentRepository.save(shipment);
        return ShipmentMapper.toDetails(shipment);
    }

    /** 아직 나가지 않은 배송을 없앤다 (PD-0013-R5). 체크아웃 보상이 부른다 (PD-0017-R7). */
    @Override
    @Transactional
    public ShipmentDetails cancel(String shipmentId) {
        var shipment = getShipmentForUpdate(shipmentId);
        shipment.cancel();
        shipmentRepository.save(shipment);
        return ShipmentMapper.toDetails(shipment);
    }

    @Override
    @Transactional
    public ShipmentDetails deliver(String shipmentId) {
        var shipment = getShipmentForUpdate(shipmentId);
        shipment.deliver();
        shipmentRepository.save(shipment);
        return ShipmentMapper.toDetails(shipment);
    }

    private Shipment getShipment(String shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> DomainException.notFound("shipment not found"));
    }

    private Shipment getShipmentForUpdate(String shipmentId) {
        return shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> DomainException.notFound("shipment not found"));
    }

    private ShipmentDetails sameShipment(
            Shipment shipment,
            String memberId,
            com.impati.commerce.shipping.domain.ShippingModels.Address address
    ) {
        if (!shipment.memberId().equals(memberId) || !shipment.address().equals(address)) {
            throw DomainException.conflict("order already has a different shipment");
        }
        return ShipmentMapper.toDetails(shipment);
    }
}
