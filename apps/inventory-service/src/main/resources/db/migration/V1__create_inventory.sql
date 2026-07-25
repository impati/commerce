create table stock_items (
    sku_id   varchar(64) primary key,
    on_hand  int         not null,
    reserved int         not null,
    -- 락과 별개인 최후 방어선. 애플리케이션이 실수해도 음수 재고는 저장되지 않는다.
    constraint ck_stock_items_non_negative check (on_hand >= 0 and reserved >= 0 and on_hand >= reserved)
);

create table reservations (
    id       varchar(64) primary key,
    order_id varchar(64) not null,
    status   varchar(32) not null
);

create index idx_reservations_order_id on reservations (order_id);

create table reservation_lines (
    reservation_id varchar(64) not null,
    sku_id         varchar(64) not null,
    line_no        int         not null,
    quantity       int         not null,
    primary key (reservation_id, sku_id),
    constraint fk_reservation_lines_reservation
        foreign key (reservation_id) references reservations (id),
    constraint ck_reservation_lines_quantity check (quantity > 0)
);

create index idx_reservation_lines_order on reservation_lines (reservation_id, line_no);
