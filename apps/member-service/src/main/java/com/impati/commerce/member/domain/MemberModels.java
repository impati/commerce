package com.impati.commerce.member.domain;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
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
            this.id = Ids.newId("addr");
            this.alias = required(alias, "address alias is required");
            this.recipient = required(recipient, "recipient is required");
            this.phone = required(phone, "phone is required");
            this.line1 = required(line1, "address line is required");
            this.city = required(city, "city is required");
            this.postalCode = required(postalCode, "postal code is required");
            this.defaultAddress = defaultAddress;
        }

        public String id() {
            return id;
        }

        public boolean defaultAddress() {
            return defaultAddress;
        }

        public void markDefault(boolean value) {
            this.defaultAddress = value;
        }

        public AddressResponse toResponse() {
            return new AddressResponse(id, alias, recipient, phone, line1, city, postalCode, defaultAddress);
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

        public String id() {
            return id;
        }

        public String email() {
            return email;
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

        public MemberResponse toResponse() {
            return new MemberResponse(
                    id,
                    email,
                    name,
                    status,
                    addresses.stream().map(Address::toResponse).toList()
            );
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation(message);
        }
        return value;
    }
}
