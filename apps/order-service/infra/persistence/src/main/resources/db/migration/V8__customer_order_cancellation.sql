create table cancellation_progress (
    order_id         varchar(64)  primary key,
    member_id        varchar(64)  not null,
    stage            varchar(32)  not null,
    failure_code     varchar(64),
    last_error       varchar(500),
    resume_stage     varchar(32),
    next_attempt_at  datetime(6),
    lease_until      datetime(6),
    lease_generation bigint       not null default 0,
    constraint fk_cancellation_progress_order foreign key (order_id) references orders (id)
);

create index idx_cancellation_progress_recovery
    on cancellation_progress (stage, next_attempt_at, lease_until);

update order_events set type = 'CHECKOUT_FAILED' where type = 'ORDER_CANCELLED';
