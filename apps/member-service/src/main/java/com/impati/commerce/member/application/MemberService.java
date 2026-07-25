package com.impati.commerce.member.application;

import com.impati.commerce.common.ApiContracts.AddressResponse;
import com.impati.commerce.common.ApiContracts.MemberResponse;
import com.impati.commerce.common.DomainException;
import com.impati.commerce.member.domain.MemberModels.Address;
import com.impati.commerce.member.domain.MemberModels.Member;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberService {
    private final MemberRepository members;
    private final PasswordHasher passwordHasher;

    public MemberService(MemberRepository members, PasswordHasher passwordHasher) {
        this.members = members;
        this.passwordHasher = passwordHasher;
    }

    /**
     * 가입은 이메일 소유가 확인되지 않은 상태로 끝난다. 로그인은 확인 후에만 된다.
     *
     * <p>평문 비밀번호는 이 메서드를 넘어가지 않는다. 해시로 바꿔 도메인에 넘긴다.
     */
    public MemberResponse register(String email, String name, String rawPassword) {
        members.findByEmail(email).ifPresent(existing -> {
            throw DomainException.conflict("member email already exists");
        });
        requirePassword(rawPassword);
        var member = new Member(email, name, passwordHasher.hash(rawPassword));
        members.save(member);
        return MemberMapper.toResponse(member);
    }

    /** 로컬 데모용 시드. 이메일 확인을 건너뛰고 바로 활성 상태로 만든다. */
    public MemberResponse seed(String memberId, String email, String name, String rawPassword) {
        var existing = members.findByEmail(email);
        if (existing.isPresent()) {
            return MemberMapper.toResponse(existing.get());
        }
        var member = new Member(memberId, email, name, passwordHasher.hash(rawPassword));
        member.activate();
        members.save(member);
        return MemberMapper.toResponse(member);
    }

    public AddressResponse addAddress(
            String memberId,
            String alias,
            String recipient,
            String phone,
            String line1,
            String city,
            String postalCode,
            boolean defaultAddress
    ) {
        var member = getMember(memberId);
        var address = new Address(alias, recipient, phone, line1, city, postalCode, defaultAddress);
        member.addAddress(address);
        members.save(member);
        return MemberMapper.toResponse(address);
    }

    public MemberResponse get(String memberId) {
        return MemberMapper.toResponse(getMember(memberId));
    }

    public List<MemberResponse> list() {
        return members.findAll().stream().map(MemberMapper::toResponse).toList();
    }

    private void requirePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw DomainException.validation("password must be at least 8 characters");
        }
    }

    private Member getMember(String memberId) {
        return members.findById(memberId)
                .orElseThrow(() -> DomainException.notFound("member not found"));
    }
}
