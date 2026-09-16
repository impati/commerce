alter table orders add column created_at datetime(6);
alter table order_events add column occurred_at datetime(6);

-- 기존 로컬 데모 행도 새 NOT NULL 제약을 만족하게 한다. 운영 주문 이력은 아직 없다.
update orders set created_at = utc_timestamp(6) where created_at is null;
update order_events e join orders o on e.order_id = o.id
    set e.occurred_at = o.created_at where e.occurred_at is null;

alter table orders modify column created_at datetime(6) not null;
alter table order_events modify column occurred_at datetime(6) not null;
create index idx_orders_member_created_id on orders (member_id, created_at desc, id desc);
