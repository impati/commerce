alter table carts add column version bigint not null default 0;

create table cart_checkout_snapshots (
    order_id     varchar(64) primary key,
    member_id    varchar(64) not null,
    cart_version bigint      not null
);

create table cart_checkout_snapshot_lines (
    order_id varchar(64) not null,
    sku_id   varchar(64) not null,
    line_no  int         not null,
    quantity int         not null,
    primary key (order_id, sku_id),
    constraint fk_cart_checkout_snapshot_lines
        foreign key (order_id) references cart_checkout_snapshots (order_id),
    constraint ck_cart_checkout_snapshot_quantity check (quantity > 0)
);

create index idx_cart_checkout_snapshot_lines_order
    on cart_checkout_snapshot_lines (order_id, line_no);
