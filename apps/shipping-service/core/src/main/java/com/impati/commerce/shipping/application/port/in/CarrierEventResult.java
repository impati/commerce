package com.impati.commerce.shipping.application.port.in;

public record CarrierEventResult(String eventId, String shipmentId, String result, String shipmentStatus) { }
