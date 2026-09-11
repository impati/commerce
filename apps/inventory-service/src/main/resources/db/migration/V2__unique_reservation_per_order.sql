alter table reservations add constraint uq_reservations_order_id unique (order_id);
drop index idx_reservations_order_id on reservations;
