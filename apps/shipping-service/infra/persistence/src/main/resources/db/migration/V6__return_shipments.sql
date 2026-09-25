alter table shipments
    add column shipment_kind varchar(16) not null default 'OUTBOUND' after member_id,
    add column return_id varchar(64) null after shipment_kind,
    add column pickup_attempt integer not null default 0 after return_id;

alter table shipments drop index uq_shipments_order_id;
alter table shipments
    add column outbound_order_id varchar(64)
        generated always as (case when shipment_kind = 'OUTBOUND' then order_id else null end) stored;
alter table shipments add constraint uq_shipments_outbound_order unique (outbound_order_id);
alter table shipments add constraint uq_shipments_return_id unique (return_id);

create index idx_carrier_operations_shipment on carrier_operations (shipment_id);
alter table carrier_operations drop index uq_carrier_operations_shipment_type;
create index idx_carrier_operations_shipment_type on carrier_operations (shipment_id, operation_type);
