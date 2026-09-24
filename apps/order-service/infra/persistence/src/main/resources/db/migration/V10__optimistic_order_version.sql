alter table orders
    add column version bigint not null default 0;
