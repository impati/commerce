# 게이트웨이가 노출하는 경로와 내부 경로를 구분한다

- **ID:** BL-0017
- **기록일:** 2026-08-16

## 배경

[ADR-0002](../adr/0002-network-segmentation-as-trust-boundary.md)에서 서비스 간 신뢰 경계를 배포 토폴로지로 강제하기로 정했다. 게이트웨이만 퍼블릭 인그레스에 두고 나머지는 프라이빗망에 둔다.

그런데 그 결정을 실행하려면 **각 서비스에서 무엇을 노출하면 안 되는지 알 수 있어야 하는데, 지금은 코드를 읽어서 알 수 없다.** 등급이 프리픽스로 드러나는 것은 member-service의 `/members/internal/**` 하나뿐이고, 나머지 6개 서비스는 게이트웨이가 노출하는 경로와 형제 서비스만 부르는 경로가 같은 컨트롤러에 섞여 있다.

호출자를 추적해보면 게이트웨이가 노출하지 않는 경로가 11개 있다.

| 서비스 | 경로 | 부르는 쪽 |
| --- | --- | --- |
| cart | `POST /carts/clear` | order |
| catalog | `GET /skus/{skuId}` | order, cart |
| inventory | `POST /reservations`, `.../commit`, `.../release` | order |
| inventory | `POST /stock` | 없음 (재입고·운영용) |
| payment | `POST /payments/capture` | order |
| shipping | `POST /shipments` | order |
| shipping | `GET /shipments/{shipmentId}` | 없음 |
| notification | `POST /notifications/events` | order |
| notification | `POST /notifications/email-verifications` | member |
| notification | `GET /notifications/outbox` | 없음 (운영용) |

등급의 기준이 "누가 부르는가"가 아니라는 점도 드러난다. 게이트웨이 자신이 `/members/internal/sessions/resolve`를 부르고, `/carts`와 `/products`는 게이트웨이와 형제 서비스가 함께 부른다. 기준은 **게이트웨이가 브라우저에 노출하는가**여야 한다.

## 목표

각 서비스에서 노출하면 안 되는 경로가 코드를 읽으면 드러나고, 배포 계층이 소비할 수 있는 형태로 표현된다. 새 경로를 추가하는 사람이 등급을 판단하지 않고 지나칠 수 없다.

내부 경로를 실수로 게이트웨이에 뚫는 사고는 자동으로 잡힌다.
