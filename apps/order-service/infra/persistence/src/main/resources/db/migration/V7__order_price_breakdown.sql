alter table orders
    add column product_amount bigint,
    add column shipping_fee_amount bigint,
    add column total_amount bigint,
    add column amount_currency varchar(8);

-- 운영 주문 이력은 없다. 기존 로컬 데모 주문은 당시 청구처럼 배송비 0원으로 보존한다.
update orders o
join (
    select order_id,
           sum(unit_amount * quantity) as product_amount,
           min(unit_currency) as amount_currency
      from order_lines
     group by order_id
) order_totals on order_totals.order_id = o.id
   set o.product_amount = order_totals.product_amount,
       o.shipping_fee_amount = 0,
       o.total_amount = order_totals.product_amount,
       o.amount_currency = order_totals.amount_currency;

alter table orders
    modify column product_amount bigint not null,
    modify column shipping_fee_amount bigint not null,
    modify column total_amount bigint not null,
    modify column amount_currency varchar(8) not null;
