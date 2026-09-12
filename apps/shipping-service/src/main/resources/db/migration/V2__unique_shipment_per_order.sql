alter table shipments add constraint uq_shipments_order_id unique (order_id);
drop index idx_shipments_order_id on shipments;
