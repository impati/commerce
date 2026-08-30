-- 서비스마다 데이터베이스를 나눈다. 인스턴스는 공유하고 경계는 스키마가 만든다.
--
-- 스키마를 만드는 것은 여기이고 테이블을 만드는 것은 각 서비스의 Flyway다. 소유가 갈려 있어야
-- 서비스가 자기 스키마만 진화시킨다.
create database if not exists member_service;
create database if not exists catalog_service;
create database if not exists inventory_service;
create database if not exists cart_service;
create database if not exists shipping_service;
create database if not exists order_service;
create database if not exists payment_service;
create database if not exists notification_service;

grant all privileges on `member_service`.* to 'commerce'@'%';
grant all privileges on `catalog_service`.* to 'commerce'@'%';
grant all privileges on `inventory_service`.* to 'commerce'@'%';
grant all privileges on `cart_service`.* to 'commerce'@'%';
grant all privileges on `shipping_service`.* to 'commerce'@'%';
grant all privileges on `order_service`.* to 'commerce'@'%';
grant all privileges on `payment_service`.* to 'commerce'@'%';
grant all privileges on `notification_service`.* to 'commerce'@'%';
