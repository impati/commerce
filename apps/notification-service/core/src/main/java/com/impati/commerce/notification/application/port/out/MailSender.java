package com.impati.commerce.notification.application.port.out;

/**
 * 메일을 내보내는 포트. 구현은 {@code adapter/out/mail}에 둔다.
 *
 * <p>로컬에서는 로그로 남기는 대역을 쓰고, 운영에서는 실제 메일 벤더 어댑터로 갈아끼운다.
 * 어느 쪽이든 벤더 개념은 어댑터 밖으로 나오지 않는다.
 */
public interface MailSender {
    /**
     * 한 통을 보낸다.
     *
     * <p>{@code dispatchKey}는 이 발송을 가리키는 식별자이며 재시도해도 같은 값이다.
     * 어댑터는 이 키로 수락 사실을 남겨 {@link #wasAccepted}가 답할 수 있게 해야 한다.
     */
    void send(String recipient, String subject, String body, String dispatchKey);

    /**
     * 이 키의 메일을 이미 수락했는지 묻는다 (ADR-0011).
     *
     * <p>전송에 성공한 뒤 결과를 기록하기 전에 끊기면 그 알림은 대기 상태로 남는다. 다음
     * 주기가 다시 보내지 않으려면 <b>이미 보냈는지</b>를 물을 수 있어야 한다. 한 번만 보낼지를
     * 벤더의 멱등 전송에 맡기지 않고 알림 서비스가 판단하는 것이 이 포트의 목적이다.
     *
     * <p><b>어댑터가 지켜야 할 규약 셋.</b> 하나라도 깨지면 중복이 나가며, 깨지는 방식이
     * 조용하다 — 예외가 아니라 메일 두 통으로 드러난다.
     *
     * <ol>
     *   <li>수락과 키 기록이 원자적일 것. 메일을 받아들인 뒤 키를 나중에 적으면 그 사이가
     *       그대로 중복 창이다.</li>
     *   <li>수락된 키의 조회가 이후 항상 참일 것. 복제 지연이나 캐시로 한 번이라도 거짓이
     *       나오면 그 순간 다시 보낸다.</li>
     *   <li>키 보존 기간이 재시도 지평보다 길 것. 벤더가 키를 먼저 지우면 그 뒤의 재시도가
     *       중복이 된다.</li>
     * </ol>
     *
     * @return 이미 수락했으면 {@code true}
     * @throws RuntimeException 수락 여부를 알 수 없을 때. 호출자는 이 주기에 보내지 않는다 —
     *         모르는 상태에서 보내면 조회 장애가 곧 중복 발송이 된다.
     */
    boolean wasAccepted(String dispatchKey);
}
