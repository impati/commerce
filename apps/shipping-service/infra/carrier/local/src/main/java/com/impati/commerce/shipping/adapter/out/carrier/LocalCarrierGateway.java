package com.impati.commerce.shipping.adapter.out.carrier;

import com.impati.commerce.shipping.application.port.out.CarrierGateway;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로컬에서 택배사 접수 계약을 재현하는 교체 가능한 어댑터. */
@Component
public class LocalCarrierGateway implements CarrierGateway {
    private final String carrierCode;
    private final String carrierName;
    private final String trackingPrefix;
    private final ConcurrentHashMap<String, CarrierRegistration> registrations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CarrierCancellation> cancellations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CarrierRegistration> pickups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CarrierCancellation> pickupCancellations = new ConcurrentHashMap<>();

    public LocalCarrierGateway(
            @Value("${shipping.carrier.code:PRIMARY}") String carrierCode,
            @Value("${shipping.carrier.name:기본 택배사}") String carrierName,
            @Value("${shipping.carrier.tracking-prefix:TRK-}") String trackingPrefix
    ) {
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.trackingPrefix = trackingPrefix;
    }

    @Override
    public CarrierRegistration register(RegistrationCommand command) {
        return registrations.computeIfAbsent(command.idempotencyKey(), ignored -> CarrierRegistration.confirmed(
                carrierCode, carrierName, trackingPrefix + command.shipmentId()));
    }

    @Override
    public CarrierRegistration registration(RegistrationCommand command) {
        return registrations.getOrDefault(command.idempotencyKey(), CarrierRegistration.absent());
    }

    @Override
    public CarrierCancellation cancel(CancellationCommand command) {
        return cancellations.computeIfAbsent(command.idempotencyKey(), ignored -> CarrierCancellation.confirmed());
    }

    @Override
    public CarrierCancellation cancellation(CancellationCommand command) {
        return cancellations.getOrDefault(command.idempotencyKey(), CarrierCancellation.absent());
    }

    @Override
    public CarrierRegistration schedulePickup(PickupCommand command) {
        return pickups.computeIfAbsent(command.idempotencyKey(), ignored -> CarrierRegistration.confirmed(
                carrierCode, carrierName, trackingPrefix + "R-" + command.shipmentId()
                        + "-" + Math.abs(command.idempotencyKey().hashCode())));
    }

    @Override
    public CarrierRegistration pickup(PickupCommand command) {
        return pickups.getOrDefault(command.idempotencyKey(), CarrierRegistration.absent());
    }

    @Override
    public CarrierCancellation cancelPickup(CancellationCommand command) {
        return pickupCancellations.computeIfAbsent(command.idempotencyKey(),
                ignored -> CarrierCancellation.confirmed());
    }

    @Override
    public CarrierCancellation pickupCancellation(CancellationCommand command) {
        return pickupCancellations.getOrDefault(command.idempotencyKey(), CarrierCancellation.absent());
    }
}
