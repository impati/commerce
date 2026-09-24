alter table shipments
    add column cancellation_status varchar(32) not null default 'NOT_REQUESTED' after registration_status;

create table carrier_operations (
    idempotency_key  varchar(128) primary key,
    shipment_id      varchar(64)  not null,
    operation_type   varchar(32)  not null,
    operation_status varchar(32)  not null,
    claim_generation bigint       not null default 0,
    claim_until      datetime(6),
    next_attempt_at  datetime(6)  not null,
    attempts         integer      not null default 0,
    last_error       varchar(400),
    created_at       datetime(6)  not null,
    updated_at       datetime(6)  not null,
    unique key uq_carrier_operations_shipment_type (shipment_id, operation_type),
    key idx_carrier_operations_due (operation_status, next_attempt_at, claim_until),
    constraint fk_carrier_operations_shipment foreign key (shipment_id) references shipments (id)
);
