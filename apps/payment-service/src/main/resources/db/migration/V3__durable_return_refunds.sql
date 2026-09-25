alter table payments
    add column refunded_amount bigint not null default 0 after amount;

create table payment_refunds (
    return_id       varchar(64) primary key,
    payment_id      varchar(64) not null,
    amount          bigint      not null,
    currency        varchar(8)  not null,
    status          varchar(24) not null,
    attempts        integer     not null default 0,
    last_error      varchar(400),
    created_at      datetime(6) not null default current_timestamp(6),
    updated_at      datetime(6) not null default current_timestamp(6),
    key idx_payment_refunds_pending (status, updated_at),
    constraint fk_payment_refunds_payment foreign key (payment_id) references payments (id)
);
