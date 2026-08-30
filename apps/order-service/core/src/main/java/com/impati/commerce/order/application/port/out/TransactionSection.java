package com.impati.commerce.order.application.port.out;

/**
 * 함께 성립해야 하는 구간.
 *
 * <p>응용 계층은 <b>무엇이 함께 성립해야 하는지</b>만 말하고, 그것을 어떤 트랜잭션으로 실현할지는
 * 어댑터가 정한다. 응용 계층이 스프링의 트랜잭션 타입을 직접 들면 보장의 선언과 실현이 한 곳에
 * 붙어버린다.
 *
 * <p>"함께 성립해야 한다"를 지키는 수단이 트랜잭션만은 아니다. 이 저장소에는 셋이 있다 —
 * 같은 저장소를 공유하면 트랜잭션(주문과 그 사건), 저장소가 갈라져 있고 되돌릴 수 있으면
 * 보상(checkout saga), 되돌릴 수 없고 반복이 안전하면 멱등과 재시도(알림 발송). 이 포트는
 * 첫째를 쓰기로 정한 구간에만 쓴다.
 */
@FunctionalInterface
public interface TransactionSection {
    void run(Runnable body);
}
