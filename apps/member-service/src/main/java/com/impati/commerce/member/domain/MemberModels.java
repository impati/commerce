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

    public static final class Member {
        private final String id;
        private final String email;
        private final String name;
        private final String status;
        private final List<Address> addresses = new ArrayList<>();

        public Member(String email, String name) {
            this(Ids.newId("mem"), email, name);
        }

        public Member(String id, String email, String name) {
            if (email == null || !email.contains("@")) {
                throw DomainException.validation("email must contain @");
            }
            this.id = required(id, "member id is required");
            this.email = email;
            this.name = required(name, "member name is required");
            this.status = "ACTIVE";
        }

        /**
         * 저장된 상태에서 복원한다.
         *
         * <p>주소는 {@link #addAddress}를 거치지 않고 그대로 채운다. addAddress는 기본 배송지를
         * 재조정하므로, 복원에 쓰면 저장된 기본 배송지가 바뀔 수 있다. 영속화 어댑터만 쓴다.
         */
        public static Member restore(String id, String email, String name, List<Address> addresses) {
            var member = new Member(id, email, name);
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
