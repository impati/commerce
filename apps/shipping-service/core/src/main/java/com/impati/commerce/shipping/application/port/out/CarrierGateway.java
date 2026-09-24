package com.impati.commerce.shipping.application.port.out;

/** 택배사 접수·취소 경계. 구현체만 실제 업체인지 로컬 대역인지 안다. */
public interface CarrierGateway {
    CarrierRegistration register(String shipmentId);

    void cancel(String shipmentId, String trackingNumber);

    record CarrierRegistration(String carrierCode, String carrierName, String trackingNumber) { }
}
