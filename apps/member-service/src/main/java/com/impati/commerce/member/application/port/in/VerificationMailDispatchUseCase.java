package com.impati.commerce.member.application.port.in;

/** 아웃박스에 쌓인 인증 메일을 알림 서비스로 내보낸다 (ADR-0010). */
public interface VerificationMailDispatchUseCase {
    VerificationMailDispatchSummary dispatchPending();
}
