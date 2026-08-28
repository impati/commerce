package com.impati.commerce.member.application.port.in;

/**
 * 한 주기의 결과.
 *
 * <p>셋이 갈라지는 지점이 서로 다른 사실을 말한다. {@code candidates}와 {@code claimed}가
 * 다르면 다른 인스턴스가 함께 돌고 있다는 뜻이고, {@code claimed}와 {@code sent}가 다르면
 * 알림 서비스가 받지 못하고 있다는 뜻이다.
 */
public record VerificationMailDispatchSummary(int candidates, int claimed, int sent) {
}
