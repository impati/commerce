create table payments (
    id             varchar(64) primary key,
    order_id       varchar(64) not null,
    member_id      varchar(64) not null,
    amount         bigint      not null,
    currency       varchar(8)  not null,
    method         varchar(32) not null,
    status         varchar(32) not null,
    transaction_id varchar(64) not null
);

create index idx_payments_order_id on payments (order_id);
