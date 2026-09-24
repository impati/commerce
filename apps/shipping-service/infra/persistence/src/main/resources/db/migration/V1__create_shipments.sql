create table shipments (
    id                   varchar(64)  primary key,
    order_id             varchar(64)  not null,
    member_id            varchar(64)  not null,
    status               varchar(32)  not null,
    tracking_number      varchar(64)  not null,
    ship_address_id      varchar(64),
    ship_alias           varchar(64),
    ship_recipient       varchar(128) not null,
    ship_phone           varchar(64),
    ship_line1           varchar(255) not null,
    ship_city            varchar(128),
    ship_postal_code     varchar(32),
    ship_default_address boolean      not null
);

create index idx_shipments_order_id on shipments (order_id);
