# 서비스 간 클라이언트가 프로토콜 오류를 그대로 흘린다

- **ID:** BL-0036
- **기록일:** 2026-08-22

## 배경

order-service의 HTTP 클라이언트 7개 중 2개만 프로토콜 오류를 도메인 언어로 옮긴다. `HttpPaymentClient`와 `HttpShippingClient`가 그렇고, member·cart·catalog·inventory·notification은 `RestClientResponseException`을 그대로 응용 계층까지 올려보낸다.

CLAUDE.md는 프로토콜 오류를 도메인 언어로 옮기는 것도 어댑터의 일이라고 정한다. 지키지 않으면 두 가지가 어긋난다.

- **응답이 계약을 벗어난다.** 옮겨지지 않은 예외는 `ApiExceptionHandler`가 잡지 못하므로 `ErrorResponse` 형태가 아닌 응답이 나간다. 게이트웨이를 지나 브라우저까지 그 형태로 도달한다.
- **응용 계층이 통신 방식을 알게 된다.** saga가 HTTP 예외를 다루게 되면 협력자를 다른 방식으로 부르도록 바꿀 때 응용 계층이 함께 바뀐다.

같은 문제가 다른 서비스의 클라이언트에도 있는지는 확인하지 않았다.

## 목표

서비스 간 호출의 실패가 전부 도메인 예외로 응용 계층에 도달한다. 어떤 협력자가 실패해도 응답이 `ErrorResponse` 계약을 지킨다. 옮기는 규칙이 클라이언트마다 흩어져 어긋나지 않도록 어디에 두어야 하는지도 함께 정한다.
