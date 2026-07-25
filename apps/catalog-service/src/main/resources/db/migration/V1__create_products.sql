create table products (
    id          varchar(64)   primary key,
    name        varchar(255)  not null,
    brand       varchar(128)  not null,
    category    varchar(64)   not null,
    description varchar(2000) not null,
    status      varchar(32)   not null
);

create index idx_products_category on products (category);

create table product_tags (
    product_id varchar(64) not null,
    tag_no     int         not null,
    tag        varchar(64) not null,
    primary key (product_id, tag_no),
    constraint fk_product_tags_product foreign key (product_id) references products (id)
);

create index idx_product_tags_tag on product_tags (tag);

create table product_skus (
    id             varchar(64)  primary key,
    product_id     varchar(64)  not null,
    sku_no         int          not null,
    name           varchar(255) not null,
    price_amount   bigint       not null,
    price_currency varchar(8)   not null,
    status         varchar(32)  not null,
    constraint fk_product_skus_product foreign key (product_id) references products (id)
);

create index idx_product_skus_order on product_skus (product_id, sku_no);

create table sku_attributes (
    sku_id     varchar(64)  not null,
    attr_key   varchar(64)  not null,
    attr_value varchar(255) not null,
    primary key (sku_id, attr_key),
    constraint fk_sku_attributes_sku foreign key (sku_id) references product_skus (id)
);
