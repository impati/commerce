package com.impati.commerce.order.application.component;

import com.impati.commerce.order.application.port.out.OrderEventRepository;
import com.impati.commerce.order.application.port.out.OrderWriter;
import com.impati.commerce.order.application.port.out.TransactionSection;
import com.impati.commerce.order.domain.OrderModels.Order;
import org.springframework.stereotype.Component;

/**
 * 주문에 일어난 변경을 확정한다 (ADR-0012).
 *
 * <p>지키는 것은 하나다 — <b>주문의 상태와 그 상태를 만든 사건은 함께 성립한다.</b> 둘이
 * 갈라지면 결제된 주문에 알릴 의도가 없거나, 일어나지 않은 일이 소비자에게 간다.
 *
 * <p>이 응집 단위가 이름을 갖는 이유는 {@code checkout}이 saga이기 때문이다. 다른 서비스는
 * 유스케이스 하나가 곧 커밋 단위여서 유스케이스 메서드에 트랜잭션을 걸면 끝났다. saga는
 * 외부 호출을 품고 있어 감쌀 수 없으므로 커밋 단위가 유스케이스보다 작고, 그러면 그 단위가
 * 어딘가에 얹혀 갈 수 없다.
 *
 * <p><b>유스케이스 실행자는 이것 말고 주문을 저장할 방법이 없다.</b> {@link OrderWriter}를
 * 여기서만 쓰는 것이 그 게이트다 — 사건 없이 저장하는 코드가 컴파일되지 않는다. 규칙을
 * 문장으로 적어두면 새고, 타입으로 막으면 새지 않는다.
 *
 * <p>실행자와 종류가 다르다. 실행자는 유스케이스의 흐름을 조율하고, 이것은 한 애그리거트의
 * 변경을 확정한다.
 */
@Component
public class OrderChanges {
    private final OrderWriter orderWriter;
    private final OrderEventRepository orderEventRepository;
    private final TransactionSection transactionSection;

    public OrderChanges(
            OrderWriter orderWriter,
            OrderEventRepository orderEventRepository,
            TransactionSection transactionSection
    ) {
        this.orderWriter = orderWriter;
        this.orderEventRepository = orderEventRepository;
        this.transactionSection = transactionSection;
    }

    /**
     * 주문과 그로부터 나온 사건을 함께 확정한다.
     *
     * <p>사건이 없는 변경도 여기로 온다. 협력자 id를 붙이는 것처럼 status를 바꾸지 않는 변경이
     * 그렇고, 그때 사건 목록은 비어 있다. 부르는 쪽이 "이번엔 사건이 있나"를 판단하지 않는 것이
     * 요점이다 — 판단하게 만들면 틀릴 자리가 생긴다.
     *
     * <p>사건을 <b>가져가며 비운다</b>. 같은 주문을 여러 번 확정해도 이미 넘긴 사건이 다시
     * 쓰이지 않는다.
     */
    public void commit(Order order) {
        transactionSection.run(() -> {
            orderWriter.save(order);
            orderEventRepository.saveAll(order.drainPendingEvents());
        });
    }
}
