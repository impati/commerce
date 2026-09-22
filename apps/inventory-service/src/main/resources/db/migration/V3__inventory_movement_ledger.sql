create table inventory_movements (
    id             varchar(64)  primary key,
    reason         varchar(64)  not null,
    order_id       varchar(64),
    reservation_id varchar(64),
    occurred_at    datetime(6)  not null,
    constraint fk_inventory_movements_reservation
        foreign key (reservation_id) references reservations (id)
);

create index idx_inventory_movements_order on inventory_movements (order_id);
create index idx_inventory_movements_reservation on inventory_movements (reservation_id);

create table inventory_movement_lines (
    movement_id   varchar(64) not null,
    line_no       int         not null,
    sku_id        varchar(64) not null,
    on_hand_delta int         not null,
    reserved_delta int        not null,
    on_hand_after int         not null,
    reserved_after int        not null,
    primary key (movement_id, line_no),
    constraint uq_inventory_movement_lines_sku unique (movement_id, sku_id),
    constraint fk_inventory_movement_lines_movement
        foreign key (movement_id) references inventory_movements (id),
    constraint fk_inventory_movement_lines_stock
        foreign key (sku_id) references stock_items (sku_id),
    constraint ck_inventory_movement_lines_number check (line_no >= 0),
    constraint ck_inventory_movement_lines_change
        check (on_hand_delta <> 0 or reserved_delta <> 0),
    constraint ck_inventory_movement_lines_after
        check (on_hand_after >= 0 and reserved_after >= 0 and on_hand_after >= reserved_after),
    constraint ck_inventory_movement_lines_before
        check (
            on_hand_after - on_hand_delta >= 0
            and reserved_after - reserved_delta >= 0
            and on_hand_after - on_hand_delta >= reserved_after - reserved_delta
        )
);

create index idx_inventory_movement_lines_sku on inventory_movement_lines (sku_id);
