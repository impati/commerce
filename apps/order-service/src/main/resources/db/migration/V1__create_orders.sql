create table orders (
    id                       varchar(64)  primary key,
    member_id                varchar(64)  not null,
    status                   varchar(32)  not null,
    payment_id               varchar(64),
    shipment_id              varchar(64),
    inventory_reservation_id varchar(64),
    ship_address_id          varchar(64),
    ship_alias               varchar(64),
    ship_recipient           varchar(128) not null,
    ship_phone               varchar(64),
    ship_line1               varchar(255) not null,
    ship_city                varchar(128),
    ship_postal_code         varchar(32),
    ship_default_address     boolean      not null
);

create table order_lines (
    order_id      varchar(64)  not null,
    line_no       int          not null,
    sku_id        varchar(64)  not null,
    product_id    varchar(64)  not null,
    product_name  varchar(255) not null,
    sku_name      varchar(255) not null,
    quantity      int          not null,
    unit_amount   bigint       not null,
    unit_currency varchar(8)   not null,
    primary key (order_id, line_no),
    constraint fk_order_lines_order foreign key (order_id) references orders (id)
);

create index idx_orders_member_id on orders (member_id);
