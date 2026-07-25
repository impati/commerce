create table members (
    id               varchar(64)  primary key,
    email            varchar(255) not null,
    email_normalized varchar(255) not null,
    name             varchar(128) not null,
    status           varchar(32)  not null
);

-- 이메일 조회는 대소문자를 구분하지 않는다. H2는 함수 기반 인덱스를 지원하지 않으므로
-- 소문자로 정규화한 컬럼을 따로 두고 여기에 유니크 제약을 건다.
create unique index ux_members_email_normalized on members (email_normalized);

create table member_addresses (
    id              varchar(64)  primary key,
    member_id       varchar(64)  not null,
    address_no      int          not null,
    alias           varchar(64)  not null,
    recipient       varchar(128) not null,
    phone           varchar(64)  not null,
    line1           varchar(255) not null,
    city            varchar(128) not null,
    postal_code     varchar(32)  not null,
    default_address boolean      not null,
    constraint fk_member_addresses_member foreign key (member_id) references members (id)
);

create index idx_member_addresses_order on member_addresses (member_id, address_no);
