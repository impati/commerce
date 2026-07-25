package com.impati.commerce.member.adapter.out.security;

import com.impati.commerce.member.application.PasswordHasher;
import com.impati.commerce.member.domain.MemberModels.PasswordHash;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt로 비밀번호를 해싱한다.
 *
 * <p>솔트가 해시 문자열에 포함되므로 별도 컬럼이 필요하지 않다. 같은 비밀번호를 두 번 해싱해도
 * 결과가 다르기 때문에 해시끼리 비교하면 안 되고 {@link #matches}를 써야 한다.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return encoder.matches(rawPassword, hash.value());
    }
}
