package com.impati.commerce.shipping.application.port.out;

/**
 * 택배사 접수·취소 프로토콜의 경계.
 *
 * <p>호출자는 같은 업무에 항상 같은 {@code idempotencyKey}를 사용한다. 구현체는 같은 키의
 * 재호출과 결과 조회가 하나의 택배사 작업으로 수렴하도록 보장하고, 업체별 인증·오류·상태를
 * 아래의 공통 결과로 변환한다.
 */
public interface CarrierGateway {
    CarrierRegistration register(RegistrationCommand command);

    CarrierRegistration registration(RegistrationCommand command);

    CarrierCancellation cancel(CancellationCommand command);

    CarrierCancellation cancellation(CancellationCommand command);

    enum Outcome {
        CONFIRMED,
        ABSENT,
        UNKNOWN,
        REJECTED
    }

    record RegistrationCommand(String idempotencyKey, String shipmentId) { }

    record CancellationCommand(String idempotencyKey, String shipmentId, String trackingNumber) { }

    record CarrierRegistration(
            Outcome outcome,
            String carrierCode,
            String carrierName,
            String trackingNumber,
            String message
    ) {
        public static CarrierRegistration confirmed(String carrierCode, String carrierName, String trackingNumber) {
            return new CarrierRegistration(Outcome.CONFIRMED, carrierCode, carrierName, trackingNumber, null);
        }

        public static CarrierRegistration absent() {
            return new CarrierRegistration(Outcome.ABSENT, null, null, null, null);
        }

        public static CarrierRegistration unknown(String message) {
            return new CarrierRegistration(Outcome.UNKNOWN, null, null, null, message);
        }

        public static CarrierRegistration rejected(String message) {
            return new CarrierRegistration(Outcome.REJECTED, null, null, null, message);
        }
    }

    record CarrierCancellation(Outcome outcome, String message) {
        public static CarrierCancellation confirmed() {
            return new CarrierCancellation(Outcome.CONFIRMED, null);
        }

        public static CarrierCancellation absent() {
            return new CarrierCancellation(Outcome.ABSENT, null);
        }

        public static CarrierCancellation unknown(String message) {
            return new CarrierCancellation(Outcome.UNKNOWN, message);
        }

        public static CarrierCancellation rejected(String message) {
            return new CarrierCancellation(Outcome.REJECTED, message);
        }
    }
}
