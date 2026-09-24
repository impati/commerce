package com.impati.commerce.shipping.adapter.out.carrier;

import com.impati.commerce.shipping.application.port.out.CarrierGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로컬에서 택배사 접수 계약을 재현하는 교체 가능한 어댑터. */
@Component
public class LocalCarrierGateway implements CarrierGateway {
    private final String carrierCode;
    private final String carrierName;
    private final String trackingPrefix;

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
    public CarrierRegistration register(String shipmentId) {
        return new CarrierRegistration(carrierCode, carrierName, trackingPrefix + shipmentId);
    }

    @Override
    public void cancel(String shipmentId, String trackingNumber) {
        // 실제 어댑터는 같은 shipmentId를 멱등 키로 택배 접수를 취소한다.
    }
}
