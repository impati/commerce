create table checkout_progress (
    order_id               varchar(64)  primary key,
    member_id              varchar(64)  not null,
    idempotency_key        varchar(128) not null,
    request_fingerprint    varchar(64)  not null,
    payment_token          varchar(255),
    expected_cart_version  bigint       not null,
    stage                  varchar(32)  not null,
    outcome                varchar(32)  not null,
    failure_code           varchar(64),
    payment_cleanup_status varchar(32)  not null,
    last_error             varchar(500),
    reservation_id         varchar(64),
    payment_id             varchar(64),
    shipment_id            varchar(64),
    tracking_number        varchar(64),
    resume_stage           varchar(32),
    next_attempt_at        datetime(6),
    lease_until            datetime(6),
    lease_generation       bigint       not null default 0,
    constraint uq_checkout_member_key unique (member_id, idempotency_key),
    constraint fk_checkout_progress_order foreign key (order_id) references orders (id)
);

create index idx_checkout_progress_recovery
    on checkout_progress (stage, next_attempt_at, lease_until);

-- 기존 결제 미확인 주문도 새 보상 실행기가 이어받는다.
insert into checkout_progress (
    order_id, member_id, idempotency_key, request_fingerprint, payment_token,
    expected_cart_version, stage, outcome, failure_code, payment_cleanup_status,
    last_error, reservation_id, payment_id, shipment_id, tracking_number,
    resume_stage, next_attempt_at, lease_until, lease_generation
)
select id, member_id, concat('legacy-', id), repeat('0', 64), '',
       0, 'COMPENSATING', 'FAILED', 'CHECKOUT_FAILED', 'CHECKING',
       'migrated from payment_outcome_unknown', inventory_reservation_id, payment_id,
       shipment_id, null, null, payment_reconcile_after, null, 0
 from orders
 where payment_outcome_unknown = true;

-- 이관 뒤 런타임의 정본은 checkout_progress 하나다. 이전 정리기가 보던 표시는 닫는다.
update orders
   set payment_outcome_unknown = false,
       payment_reconcile_after = null
 where payment_outcome_unknown = true;
