package com.impati.commerce.member.domain;

import com.impati.commerce.common.DomainException;
import com.impati.commerce.common.Ids;

import java.time.Instant;
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
     * 이메일 소유 인증 토큰. 해시만 보관한다.
     *
     * <p>단일 사용이며 만료가 있다. 이미 쓴 토큰이나 만료된 토큰으로는 인증되지 않는다.
     */
    public static final class EmailVerification {
        private final String tokenHash;
        private final String memberId;
        private final Instant expiresAt;
        private Instant usedAt;

        public EmailVerification(String tokenHash, String memberId, Instant expiresAt) {
            this(tokenHash, memberId, expiresAt, null);
        }

        private EmailVerification(String tokenHash, String memberId, Instant expiresAt, Instant usedAt) {
            this.tokenHash = required(tokenHash, "verification token is required");
            this.memberId = required(memberId, "member id is required");
            this.expiresAt = expiresAt;
            this.usedAt = usedAt;
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static EmailVerification restore(
                String tokenHash,
                String memberId,
                Instant expiresAt,
                Instant usedAt
        ) {
            return new EmailVerification(tokenHash, memberId, expiresAt, usedAt);
        }

        public String tokenHash() {
            return tokenHash;
        }

        public String memberId() {
            return memberId;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public Instant usedAt() {
            return usedAt;
        }

        /**
         * 토큰을 사용 처리한다.
         *
         * <p>이미 쓴 토큰과 만료된 토큰을 같은 메시지로 거절한다. 어느 쪽인지 알려주면 유효한
         * 토큰이 존재했다는 사실이 드러난다.
         */
        public void use(Instant now) {
            if (usedAt != null || now.isAfter(expiresAt)) {
                throw DomainException.validation("verification token is not usable");
            }
            this.usedAt = now;
        }
    }

    /**
     * 로그인 세션. 해시만 보관한다.
     *
     * <p>불투명 토큰이므로 서버가 상태를 갖는다. 그 대가로 폐기가 즉시 된다.
     */
    public static final class Session {
        private final String tokenHash;
        private final String memberId;
        private final Instant expiresAt;
        private Instant revokedAt;

        public Session(String tokenHash, String memberId, Instant expiresAt) {
            this(tokenHash, memberId, expiresAt, null);
        }

        private Session(String tokenHash, String memberId, Instant expiresAt, Instant revokedAt) {
            this.tokenHash = required(tokenHash, "session token is required");
            this.memberId = required(memberId, "member id is required");
            this.expiresAt = expiresAt;
            this.revokedAt = revokedAt;
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static Session restore(String tokenHash, String memberId, Instant expiresAt, Instant revokedAt) {
            return new Session(tokenHash, memberId, expiresAt, revokedAt);
        }

        public String tokenHash() {
            return tokenHash;
        }

        public String memberId() {
            return memberId;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public Instant revokedAt() {
            return revokedAt;
        }

        public boolean isUsable(Instant now) {
            return revokedAt == null && !now.isAfter(expiresAt);
        }

        public void revoke(Instant now) {
            this.revokedAt = now;
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
            if (passwordHash == null) {
                throw DomainException.validation("password is required");
            }
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

    /**
     * 인증 메일의 발송 상태.
     *
     * <p>PENDING이 남아 있는 것 자체가 관측 대상이다. 한도를 넘긴 것을 FAILED로 따로 두는
     * 이유는 "아직 못 보냈다"와 "포기했다"가 다른 사실이기 때문이다 — 하나로 합치면 실패가
     * 재시도 대기에 섞여 보이지 않는다.
     */
    public enum VerificationMailStatus {
        PENDING, SENT, FAILED
    }

    /**
     * 보내야 할 인증 메일 한 통. 아웃박스 항목이다 (ADR-0010).
     *
     * <p>가입 트랜잭션은 이것을 적고 끝나며 발송은 별도 진입점이 가져간다. 그래서 알림
     * 서비스의 응답 시간이 가입 트랜잭션의 길이가 되지 않는다.
     *
     * <p>다음 시도 시각은 여기에 없다. 언제 다시 집을지는 도메인 사실이 아니라 작업 큐의
     * 사정이며, 저장소가 점유하면서 정한다 (ADR-0009와 같은 이유).
     */
    public static final class VerificationMail {
        /**
         * 실패 이유로 남기는 최대 길이.
         *
         * <p>기록이 데이터 이유로 실패하면 그 항목은 종단 상태에 영영 도달하지 못한다 —
         * 시도 횟수가 늘지 않아 한도에 닿지 못하고 무한히 재시도한다. 그래서 <b>결과를 적는
         * 쓰기는 그것이 기록하는 작업보다 실패할 확률이 낮아야 한다.</b> 컬럼 폭보다 짧게
         * 잡아 길이 때문에 실패할 수 없게 한다.
         *
         * <p>진단은 로그가 갖는다. 여기 남기는 것은 무엇 때문이었는지 알아볼 정도의 앞부분이다.
         */
        private static final int MAX_ERROR_LENGTH = 200;

        private final String id;
        private final String memberId;
        private final String email;
        private String token;
        private VerificationMailStatus status;
        private int attempts;
        private String lastError;

        public VerificationMail(String memberId, String email, String token) {
            this(Ids.newId("vmail"), memberId, email, token,
                    VerificationMailStatus.PENDING, 0, null);
        }

        private VerificationMail(
                String id,
                String memberId,
                String email,
                String token,
                VerificationMailStatus status,
                int attempts,
                String lastError
        ) {
            this.id = required(id, "verification mail id is required");
            this.memberId = required(memberId, "member id is required");
            this.email = required(email, "verification mail recipient is required");
            this.token = token;
            this.status = status;
            this.attempts = attempts;
            this.lastError = lastError;
        }

        /** 저장된 상태에서 복원한다. 영속화 어댑터만 쓴다. */
        public static VerificationMail restore(
                String id,
                String memberId,
                String email,
                String token,
                VerificationMailStatus status,
                int attempts,
                String lastError
        ) {
            return new VerificationMail(id, memberId, email, token, status, attempts, lastError);
        }

        public String id() {
            return id;
        }

        public String memberId() {
            return memberId;
        }

        public String email() {
            return email;
        }

        /** 보낼 원문 토큰. 종단 상태가 된 뒤에는 비어 있다. */
        public String token() {
            return token;
        }

        public VerificationMailStatus status() {
            return status;
        }

        public int attempts() {
            return attempts;
        }

        public String lastError() {
            return lastError;
        }

        public boolean isPending() {
            return status == VerificationMailStatus.PENDING;
        }

        public boolean isSent() {
            return status == VerificationMailStatus.SENT;
        }

        public void markSent() {
            this.attempts += 1;
            this.status = VerificationMailStatus.SENT;
            this.lastError = null;
            discardToken();
        }

        /**
         * 실패를 기록한다. 한도 전이면 PENDING으로 남아 다음 주기에 다시 집힌다.
         *
         * <p>한도를 넘기면 포기한다. 사용자에게 재발송 경로가 있고, 무한 재시도는 만료된
         * 토큰을 계속 보내려 들기 때문이다 (ADR-0010).
         */
        public void markFailed(String error, int maxAttempts) {
            this.attempts += 1;
            this.lastError = shorten(error);
            if (attempts >= maxAttempts) {
                this.status = VerificationMailStatus.FAILED;
                discardToken();
            }
        }

        private static String shorten(String error) {
            if (error == null || error.length() <= MAX_ERROR_LENGTH) {
                return error;
            }
            return error.substring(0, MAX_ERROR_LENGTH);
        }

        /**
         * 더 보낼 일이 없어지면 원문 토큰을 버린다.
         *
         * <p>이 클래스가 평문 토큰을 들고 있는 유일한 이유는 나중에 보내야 하기 때문이다.
         * 그 이유가 사라진 뒤에도 남겨두면 DB 유출 시 계정 인증 수단이 그대로 나간다.
         */
        private void discardToken() {
            this.token = null;
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation(message);
        }
        return value;
    }
}
