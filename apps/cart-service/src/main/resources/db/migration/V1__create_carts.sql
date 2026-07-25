-- 빈 장바구니와 없는 장바구니를 구별해야 하므로 carts 행이 따로 필요하다.
-- 조회만으로 이 행을 만들지 않는다. problem/001 참고.
create table carts (
    member_id varchar(64) primary key
);

create table cart_lines (
    member_id varchar(64) not null,
    sku_id    varchar(64) not null,
    line_no   int         not null,
    quantity  int         not null,
    primary key (member_id, sku_id),
    constraint fk_cart_lines_cart foreign key (member_id) references carts (member_id),
    constraint ck_cart_lines_quantity check (quantity > 0)
);

create index idx_cart_lines_order on cart_lines (member_id, line_no);
