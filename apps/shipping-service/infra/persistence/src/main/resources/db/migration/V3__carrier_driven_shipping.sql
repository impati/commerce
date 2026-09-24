alter table shipments modify tracking_number varchar(64) null;
alter table shipments add column registration_status varchar(32) not null default 'NOT_REQUESTED' after status;
alter table shipments add column carrier_code varchar(64) null after registration_status;
alter table shipments add column carrier_name varchar(128) null after carrier_code;
alter table shipments add column last_carrier_event_at datetime(6) null after tracking_number;

create unique index uq_shipments_carrier_tracking on shipments (carrier_code, tracking_number);

create table shipment_carrier_events (
    event_id          varchar(128) primary key,
    shipment_id       varchar(64)  not null,
    carrier_code      varchar(64)  not null,
    tracking_number   varchar(64)  not null,
    event_type        varchar(32)  not null,
    occurred_at       datetime(6)  not null,
    received_at       datetime(6)  not null,
    last_received_at  datetime(6)  not null,
    processing_result varchar(32)  not null,
    shipment_status   varchar(32)  not null,
    duplicate_count   integer      not null default 0,
    constraint fk_carrier_events_shipment foreign key (shipment_id) references shipments (id)
);

create index idx_carrier_events_shipment on shipment_carrier_events (shipment_id, occurred_at);
