package com.impati.commerce.member.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.util.ArrayList;
import java.util.List;

public final class MemberModels {
    private MemberModels() {
    }

    public static final class Address {
        private final String id;
        private final String alias;
        private final String recipient;
        private final String phone;
        private final String line1;
        private final String city;
        private final String postalCode;
        private boolean defaultAddress;

        public Address(
                String alias,
                String recipient,
                String phone,
                String line1,
                String city,
                String postalCode,
                boolean defaultAddress
        ) {
            this(Ids.newId("addr"), alias, recipient, phone, line1, city, postalCode, defaultAddress);
        }

        private Address(
                String id,
                String alias,
                String recipient,
                String phone,
                String line1,
                String city,
                String postalCode,
                boolean defaultAddress
        ) {
            this.id = id;
            this.alias = required(alias, "address alias is required");
            this.recipient = required(recipient, "recipient is required");
            this.phone = required(phone, "phone is required");
            this.line1 = required(line1, "address line is required");
            this.city = required(city, "city is required");
            this.postalCode = required(postalCode, "postal code is required");
            this.defaultAddress = defaultAddress;
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static Address restore(
                String id,
                String alias,
                String recipient,
                String phone,
                String line1,
                String city,
                String postalCode,
                boolean defaultAddress
        ) {
            return new Address(id, alias, recipient, phone, line1, city, postalCode, defaultAddress);
        }

        public String id() {
            return id;
        }

        public String alias() {
            return alias;
        }

        public String recipient() {
            return recipient;
        }

        public String phone() {
            return phone;
        }

        public String line1() {
            return line1;
        }

        public String city() {
            return city;
        }

        public String postalCode() {
            return postalCode;
        }

        public boolean defaultAddress() {
            return defaultAddress;
        }

        public void markDefault(boolean value) {
            this.defaultAddress = value;
        }
    }

    /**
     * 비밀번호 해시. 도메인은 해시 알고리즘을 모른다 — 해싱은 응용 계층의 포트가 맡는다.
     *
     * <p>평문 비밀번호가 도메인에 들어오지 않게 하는 것이 이 타입의 목적이다. 평문을 필드로
     * 들고 있으면 로그나 직렬화로 새어 나갈 경로가 생긴다.
     */
    public record PasswordHash(String value) {
        public PasswordHash {
            if (value == null || value.isBlank()) {
                throw DomainException.validation("password hash is required");
            }
        }
    }

    public static final class Member {
        /** 이메일 소유가 확인되지 않은 상태. 로그인할 수 없다. */
        public static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";
        public static final String ACTIVE = "ACTIVE";

        private final String id;
        private final String email;
        private final String name;
        private final PasswordHash passwordHash;
        private String status;
        private final List<Address> addresses = new ArrayList<>();

        public Member(String email, String name, PasswordHash passwordHash) {
            this(Ids.newId("mem"), email, name, passwordHash);
        }

        public Member(String id, String email, String name, PasswordHash passwordHash) {
            if (email == null || !email.contains("@")) {
                throw DomainException.validation("email must contain @");
            }
            this.id = required(id, "member id is required");
            this.email = email;
            this.name = required(name, "member name is required");
            this.passwordHash = passwordHash;
            this.status = PENDING_VERIFICATION;
        }

        /**
         * 저장된 상태에서 복원한다.
         *
         * <p>주소는 {@link #addAddress}를 거치지 않고 그대로 채운다. addAddress는 기본 배송지를
         * 재조정하므로, 복원에 쓰면 저장된 기본 배송지가 바뀔 수 있다. 영속화 어댑터만 쓴다.
         */
        public static Member restore(
                String id,
                String email,
                String name,
                PasswordHash passwordHash,
                String status,
                List<Address> addresses
        ) {
            var member = new Member(id, email, name, passwordHash);
            member.status = status;
            member.addresses.addAll(addresses);
            return member;
        }

        public String id() {
            return id;
        }

        public String email() {
            return email;
        }

        public String name() {
            return name;
        }

        public String status() {
            return status;
        }

        public PasswordHash passwordHash() {
            return passwordHash;
        }

        public boolean isActive() {
            return status.equals(ACTIVE);
        }

        /** 이메일 소유가 확인됐다. 이미 활성이면 다시 확인해도 문제가 없도록 멱등하게 둔다. */
        public void activate() {
            this.status = ACTIVE;
        }

        public List<Address> addresses() {
            return List.copyOf(addresses);
        }

        public void addAddress(Address address) {
            if (addresses.isEmpty() || address.defaultAddress()) {
                addresses.forEach(existing -> existing.markDefault(false));
                address.markDefault(true);
            }
            addresses.add(address);
        }

        public Address address(String addressId) {
            if (addressId == null || addressId.isBlank()) {
                return addresses.stream()
                        .filter(Address::defaultAddress)
                        .findFirst()
                        .orElseThrow(() -> DomainException.validation("member has no delivery address"));
            }
            return addresses.stream()
                    .filter(address -> address.id().equals(addressId))
                    .findFirst()
                    .orElseThrow(() -> DomainException.validation("address does not belong to member"));
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation(message);
        }
        return value;
    }
}
