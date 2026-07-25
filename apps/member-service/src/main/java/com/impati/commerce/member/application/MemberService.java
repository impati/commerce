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

    public MemberService(MemberRepository members) {
        this.members = members;
    }

    public MemberResponse register(String email, String name) {
        members.findByEmail(email).ifPresent(existing -> {
            throw DomainException.conflict("member email already exists");
        });
        var member = new Member(email, name);
        members.save(member);
        return MemberMapper.toResponse(member);
    }

    public MemberResponse seed(String memberId, String email, String name) {
        var existing = members.findByEmail(email);
        if (existing.isPresent()) {
            return MemberMapper.toResponse(existing.get());
        }
        var member = new Member(memberId, email, name);
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

    private Member getMember(String memberId) {
        return members.findById(memberId)
                .orElseThrow(() -> DomainException.notFound("member not found"));
    }
}
