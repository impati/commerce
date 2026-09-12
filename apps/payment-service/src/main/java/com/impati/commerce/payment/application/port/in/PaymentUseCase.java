package com.impati.commerce.payment.application.port.in;

import com.impati.commerce.common.ApiContracts.Money;

/**
 * 결제로 할 수 있는 일 전부.
 *
 * <p>동작 하나당 인터페이스를 두지 않는다. 포트는 하나의 대화이지 하나의 동작이 아니며,
 * 쪼개서 얻는 것("호출하는 쪽이 자기가 쓰는 것만 안다")은 소비자가 갈릴 때만 생긴다. 지금은
 * 컨트롤러 하나가 다섯을 전부 쓰므로 쪼개면 같은 빈이 다섯 번 주입될 뿐이다.
 *
 * <p>계약은 여기 적고 구현 사정은 구현 클래스에 적는다.
 *
 * <p>동작마다 결과 타입이 다르다. 서비스 간 HTTP 계약({@code ApiContracts})을 돌려주지 않는
 * 이유는 유스케이스의 반환값이 서비스 간에 주고받는 것이 아니기 때문이다 — 인바운드 어댑터가
 * 늘어나면 각자 자기 표현으로 옮긴다.
 */
public interface PaymentUseCase {
    /**
     * 대금을 확보한다. 청구는 확정되지 않는다 (PD-0011-R1).
     *
     * <p>같은 주문의 승인이 이미 있으면 새로 만들지 않고 그것을 돌려준다 (PD-0011-R2).
     * 타임아웃은 요청이 실패했다는 뜻이 아니라 결과를 모른다는 뜻이므로 재요청이 정상이다.
     *
     * <p>발급사가 거절하면 {@code DomainException.paymentDeclined}가 올라온다. 거절은 시스템
     * 오류와 구분되는 결과다 (PD-0011-R6).
     */
    AuthorizedPayment authorize(String orderId, String memberId, Money amount, String paymentToken);

    /**
     * 확보한 대금을 청구로 확정한다 (PD-0011-R1).
     *
     * <p>여러 번 도착해도 첫 결과를 유지한다 (PD-0011-R4). 되돌리는 경로에서 호출되고 그
     * 경로가 재시도될 수 있으므로 같은 요청이 두 번 오는 것이 정상이다.
     */
    CapturedPayment capture(String paymentId);

    /**
     * 승인을 취소한다. 사용자에게 흔적이 남지 않는다 (PD-0011-R3).
     *
     * <p>매입된 결제에는 통하지 않는다. 그때 되돌리는 수단은 {@link #refund}다.
     */
    CancelledPayment cancel(String paymentId);

    /**
     * 매입된 대금을 되돌린다 (PD-0011-R8).
     *
     * <p>승인 취소로 정리할 수 없는 경우에만 쓴다. 매입 결과를 확인하지 못한 체크아웃이
     * 그것이다 (PD-0017-R6). 사용자 명세서에 청구와 환불 두 줄이 남으므로 정상 흐름에는
     * 쓰지 않는다.
     */
    RefundedPayment refund(String paymentId);

    /**
     * 결제의 현재 상태를 돌려준다 (PD-0011-R9).
     *
     * <p>응답을 받지 못한 호출자가 결과를 확정하는 경로다. 이것이 없으면 모르는 상태를
     * 영원히 확정할 수 없다.
     */
    PaymentDetails get(String paymentId);

    PaymentDetails getForOrder(String orderId);
}
