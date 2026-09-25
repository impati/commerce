create table return_inventory_actions (
    return_id       varchar(64) primary key,
    reservation_id varchar(64) not null,
    member_id       varchar(64) not null,
    disposition     varchar(24) not null,
    item_condition  varchar(64) not null,
    created_at      datetime(6) not null default current_timestamp(6),
    unique key uq_return_inventory_reservation (reservation_id),
    constraint fk_return_inventory_reservation
        foreign key (reservation_id) references reservations (id)
);
