create table shipment_events (
    seq bigint not null auto_increment primary key,
    id varchar(80) not null,
    type varchar(80) not null,
    shipment_id varchar(80) not null,
    order_id varchar(80) not null,
    member_id varchar(80) not null,
    occurred_at datetime(6) not null,
    payload varchar(2000) not null,
    publish_status varchar(20) not null,
    attempts int not null default 0,
    last_error varchar(500),
    claim_id varchar(80),
    next_attempt_after datetime(6),
    unique key uk_shipment_events_id (id),
    key idx_shipment_events_publish (publish_status, next_attempt_after, shipment_id, seq)
);
